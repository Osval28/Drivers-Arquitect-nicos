import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prueba de concepto del ADR-17: infraestructura de pagos con Cobre, validada con el adaptador simulado.
 *
 * Mientras no exista contrato ni documentación técnica de la integración Cobre–Skandia, la prueba levanta un
 * proveedor simulado por HTTP que se comporta como el peor caso razonable: confirma de forma asíncrona con webhooks
 * firmados, reenvía notificaciones, las entrega fuera de orden, responde tarde, se cae y no deduplica instrucciones.
 * Sobre él se ejecuta el lado de DAZMA: el contrato interno de pagos (ADR-09), el adaptador que traduce al
 * proveedor y el servicio que guarda estados, aplica idempotencia y concilia.
 */
public class CobrePaymentsPoc {

    public static void main(String[] args) throws Exception {
        Report report = new Report("Prueba de concepto: pagos con Cobre (adaptador simulado)", "ADR-17");
        Duration clientTimeout = Duration.ofMillis(Env.intOrDefault("PAYMENTS_CLIENT_TIMEOUT_MS", 800));
        int duplicateDeliveries = Env.intOrDefault("PAYMENTS_DUPLICATE_DELIVERIES", 5);
        String webhookSecret = HexFormat.of().formatHex(randomBytes(32));
        report.context("Proveedor", "simulado en 127.0.0.1 (sin deduplicación propia, peor caso)");
        report.context("Tiempo máximo de espera del adaptador", clientTimeout.toMillis() + " ms");
        report.context("Secreto de webhooks", "generado aleatoriamente en cada ejecución");

        try (SimulatedProvider provider = SimulatedProvider.start(webhookSecret);
             WebhookEndpoint endpoint = WebhookEndpoint.start()) {
            PaymentsService payments = new PaymentsService(
                    new CobreSimulatedAdapter(provider.baseUrl(), webhookSecret, clientTimeout));
            endpoint.bind(payments);
            provider.webhookUrl(endpoint.url());
            Scenario scenario = new Scenario(provider, payments, duplicateDeliveries);

            report.run("C1", "Recaudo por cada canal habilitado",
                    "Bre-B, PSE, Botón Bancolombia y Nequi: cada recaudo queda ligado a su negociación y pasa a CONFIRMADO al llegar la confirmación",
                    scenario::collectionsPerChannel);
            report.run("C2", "Confirmación con firma inválida",
                    "La notificación con firma alterada se rechaza (401) y no cambia ningún estado (ESC-CAL-SEG-0008)",
                    scenario::invalidSignatureRejected);
            report.run("C3", "Notificación repetida",
                    "Al recibir N veces la misma notificación se produce exactamente 1 cambio de estado y 1 registro (ESC-CAL-CON-0005)",
                    scenario::duplicateNotificationsSingleEffect);
            report.run("C4", "Notificaciones fuera de orden",
                    "Una notificación vieja que llega después de la confirmación no devuelve el pago a PENDIENTE",
                    scenario::outOfOrderNotificationIgnored);
            report.run("C5", "Respuesta incierta del proveedor",
                    "Si la respuesta no llega a tiempo, DAZMA consulta el estado antes de reintentar y el proveedor termina con 1 sola instrucción",
                    scenario::uncertainResponseNoDuplicate);
            report.run("C6", "Desembolso con resultado verificable",
                    "Un desembolso exitoso termina CONFIRMADO y uno rechazado termina FALLIDO con el motivo del proveedor",
                    scenario::disbursementOutcomes);
            report.run("C7", "Devolución al comprador",
                    "La devolución por el valor recaudado se confirma; una devolución mayor al recaudo se rechaza antes de llegar al proveedor",
                    scenario::refunds);
            report.run("C8", "Conciliación",
                    "Sin diferencias cuando todo cuadra; una diferencia de valor y un movimiento desconocido quedan visibles sin corregirse solos (ADR-30)",
                    scenario::reconciliation);
            report.run("C9", "Proveedor no disponible",
                    "Con el proveedor caído la operación termina en un error controlado, sin estado parcial, y al recuperarse se completa una sola vez (ESC-CAL-CON-0011)",
                    scenario::providerDown);
            report.run("C10", "Cambio de proveedor sin tocar las reglas",
                    "El mismo servicio de pagos, con otro adaptador simulado de vocabulario distinto, produce los mismos resultados de negocio (ESC-CAL-INT-0002)",
                    scenario::providerSwap);
        }
        System.exit(report.finish());
    }

    // =============================================================== escenarios

    record Scenario(SimulatedProvider provider, PaymentsService payments, int duplicates) {

        String collectionsPerChannel() throws Exception {
            List<String> lines = new ArrayList<>();
            for (Channel channel : Channel.values()) {
                String negotiation = negotiationId();
                Instruction collection = Instruction.collection(negotiation, channel, 350_000);
                Movement movement = payments.instruct(collection);
                check(movement.state() == MovementState.PENDING, channel + ": se esperaba PENDIENTE y quedó " + movement.state());
                int status = provider.complete(collection.reference(), "COMPLETED", null);
                check(status == 200, channel + ": el webhook respondió " + status);
                Movement confirmed = payments.movement(collection.reference()).orElseThrow();
                check(confirmed.state() == MovementState.CONFIRMED, channel + ": quedó " + confirmed.state());
                check(negotiation.equals(confirmed.negotiationId()), channel + ": no quedó ligado a la negociación");
                lines.add(channel.label + " → " + confirmed.reference() + " CONFIRMADO");
            }
            return "4/4 canales. " + String.join("; ", lines);
        }

        String invalidSignatureRejected() throws Exception {
            Instruction collection = Instruction.collection(negotiationId(), Channel.PSE, 120_000);
            payments.instruct(collection);
            int ledgerBefore = payments.ledgerSize();
            int status = provider.complete(collection.reference(), "COMPLETED", "firma-alterada");
            check(status == 401, "Se esperaba 401 y el webhook respondió " + status);
            check(payments.movement(collection.reference()).orElseThrow().state() == MovementState.PENDING, "El estado cambió");
            check(payments.ledgerSize() == ledgerBefore, "Se agregó un registro con una notificación no válida");
            return "HTTP 401; el pago sigue PENDIENTE y el registro financiero no cambió";
        }

        String duplicateNotificationsSingleEffect() throws Exception {
            Instruction collection = Instruction.collection(negotiationId(), Channel.BRE_B, 480_000);
            payments.instruct(collection);
            int ledgerBefore = payments.ledgerSize();
            int transitionsBefore = payments.transitions(collection.reference());
            int inboxBefore = payments.inboxDuplicates.get();
            provider.setStatus(collection.reference(), "COMPLETED");
            String eventId = provider.prepareEvent(collection.reference(), "COMPLETED");
            for (int i = 0; i < duplicates; i++) {
                check(provider.deliver(eventId) == 200, "El reenvío " + (i + 1) + " no respondió 200");
            }
            int newEntries = payments.ledgerSize() - ledgerBefore;
            int newTransitions = payments.transitions(collection.reference()) - transitionsBefore;
            int detectedByInbox = payments.inboxDuplicates.get() - inboxBefore;
            check(newEntries == 1 && newTransitions == 1,
                    duplicates + " entregas produjeron " + newEntries + " registros y " + newTransitions + " transiciones");
            check(detectedByInbox == duplicates - 1,
                    "El Inbox identificó " + detectedByInbox + " de " + (duplicates - 1) + " duplicados");
            return duplicates + " entregas del mismo evento → 1 registro, 1 transición; el Inbox identificó "
                    + detectedByInbox + " duplicados por su event_id";
        }

        String outOfOrderNotificationIgnored() throws Exception {
            Instruction collection = Instruction.collection(negotiationId(), Channel.NEQUI, 90_000);
            payments.instruct(collection);
            String stalePending = provider.prepareEvent(collection.reference(), "PENDING");
            provider.complete(collection.reference(), "COMPLETED", null);
            int staleBefore = payments.staleIgnored.get();
            provider.deliver(stalePending);
            MovementState state = payments.movement(collection.reference()).orElseThrow().state();
            check(state == MovementState.CONFIRMED, "Quedó en " + state + " después de la notificación vieja");
            check(payments.staleIgnored.get() - staleBefore == 1, "La máquina de estados no registró la notificación vieja como ignorada");
            return "Confirmado y luego notificación vieja de PENDIENTE: sigue CONFIRMADO";
        }

        String uncertainResponseNoDuplicate() throws Exception {
            Instruction collection = Instruction.collection(negotiationId(), Channel.BOTON_BANCOLOMBIA, 260_000);
            provider.slowNextResponse(true);
            Movement movement = payments.instruct(collection);
            int atProvider = provider.countByReference(collection.reference());
            Movement again = payments.instruct(collection);
            int afterRetry = provider.countByReference(collection.reference());
            check(atProvider == 1 && afterRetry == 1,
                    "El proveedor tiene " + afterRetry + " instrucciones para la misma referencia");
            check(movement.providerId() != null && movement.providerId().equals(again.providerId()),
                    "DAZMA no quedó con el identificador del proveedor");
            return "Tiempo de espera agotado → consulta por referencia → movimiento existente adoptado. "
                    + "Instrucciones en el proveedor: " + afterRetry + ". Consultas de estado: " + payments.statusQueries.get();
        }

        String disbursementOutcomes() throws Exception {
            String negotiation = negotiationId();
            Instruction ok = Instruction.disbursement(negotiation, "vendedor", 300_000, "pago-vendedor");
            Instruction rejected = Instruction.disbursement(negotiationId(), "vendedor", 150_000, "pago-vendedor");
            payments.instruct(ok);
            payments.instruct(rejected);
            provider.complete(ok.reference(), "COMPLETED", null);
            provider.complete(rejected.reference(), "REJECTED:cuenta_destino_inexistente", null);
            Movement okMovement = payments.movement(ok.reference()).orElseThrow();
            Movement rejectedMovement = payments.movement(rejected.reference()).orElseThrow();
            check(okMovement.state() == MovementState.CONFIRMED, "El desembolso exitoso quedó " + okMovement.state());
            check(rejectedMovement.state() == MovementState.FAILED, "El rechazado quedó " + rejectedMovement.state());
            check("cuenta_destino_inexistente".equals(rejectedMovement.detail()), "No se conservó el motivo del rechazo");
            return "Exitoso → CONFIRMADO; rechazado → FALLIDO (" + rejectedMovement.detail() + ")";
        }

        String refunds() throws Exception {
            String negotiation = negotiationId();
            Instruction collection = Instruction.collection(negotiation, Channel.PSE, 200_000);
            payments.instruct(collection);
            provider.complete(collection.reference(), "COMPLETED", null);

            Instruction tooMuch = Instruction.refund(negotiation, 250_000);
            int before = provider.totalMovements();
            try {
                payments.instruct(tooMuch);
                throw new CheckFailed("Se aceptó una devolución mayor al recaudo");
            } catch (IllegalStateException expected) {
                check(provider.totalMovements() == before, "La devolución inválida llegó al proveedor");
            }
            Instruction refund = Instruction.refund(negotiation, 200_000);
            payments.instruct(refund);
            provider.complete(refund.reference(), "COMPLETED", null);
            check(payments.movement(refund.reference()).orElseThrow().state() == MovementState.CONFIRMED, "La devolución no se confirmó");
            return "Devolución de 250.000 sobre 200.000 rechazada localmente; devolución de 200.000 CONFIRMADA";
        }

        String reconciliation() throws Exception {
            List<String> clean = payments.reconcile(provider.report());
            check(clean.isEmpty(), "Diferencias inesperadas: " + clean);

            Instruction collection = Instruction.collection(negotiationId(), Channel.PSE, 75_000);
            payments.instruct(collection);
            provider.complete(collection.reference(), "COMPLETED", null);
            provider.tamperAmount(collection.reference(), 70_000);
            provider.addUnknownMovement(55_000);
            List<String> differences = payments.reconcile(provider.report());
            check(differences.size() == 2, "Se esperaban 2 diferencias y se encontraron " + differences);
            check(payments.movement(collection.reference()).orElseThrow().amount() == 75_000, "El valor interno se corrigió solo");
            return "Primera conciliación: 0 diferencias en " + provider.totalMovements() + " movimientos. Con diferencias: "
                    + String.join("; ", differences);
        }

        String providerDown() throws Exception {
            String negotiation = negotiationId();
            Instruction collection = Instruction.collection(negotiation, Channel.NEQUI, 64_000);
            provider.down(true);
            try {
                payments.instruct(collection);
                throw new CheckFailed("La instrucción se registró como hecha con el proveedor caído");
            } catch (ProviderUnavailable expected) {
                Optional<Movement> stored = payments.movement(collection.reference());
                check(stored.isEmpty() || stored.get().state() == MovementState.NOT_SENT, "Quedó un estado parcial: " + stored);
            } finally {
                provider.down(false);
            }
            payments.instruct(collection);
            payments.instruct(collection);
            check(provider.countByReference(collection.reference()) == 1, "Al recuperarse se crearon "
                    + provider.countByReference(collection.reference()) + " instrucciones");
            return "Con el proveedor caído: error controlado (" + ProviderUnavailable.class.getSimpleName()
                    + ") y estado NO_ENVIADO. Recuperado: 1 instrucción aunque se pidió 2 veces";
        }

        String providerSwap() throws Exception {
            List<String> outcomesA = businessFlow(payments, provider::complete);
            OtherProviderAdapter other = new OtherProviderAdapter();
            PaymentsService withOther = new PaymentsService(other);
            List<String> outcomesB = businessFlow(withOther, (reference, status, signature) -> {
                other.notifyResult(withOther, reference, status.startsWith("COMPLETED") ? "APPROVED" : "DECLINED");
                return 200;
            });
            check(outcomesA.equals(outcomesB), "Resultados distintos: " + outcomesA + " vs " + outcomesB);
            return "Mismos resultados con ambos adaptadores: " + outcomesA;
        }

        private static List<String> businessFlow(PaymentsService service, Completer completer) throws Exception {
            String negotiation = negotiationId();
            Instruction collection = Instruction.collection(negotiation, Channel.PSE, 410_000);
            Instruction payout = Instruction.disbursement(negotiation, "vendedor", 380_000, "pago-vendedor");
            Instruction failing = Instruction.disbursement(negotiation, "dazma", 30_000, "comision");
            service.instruct(collection);
            completer.complete(collection.reference(), "COMPLETED", null);
            service.instruct(payout);
            completer.complete(payout.reference(), "COMPLETED", null);
            service.instruct(failing);
            completer.complete(failing.reference(), "REJECTED:motivo", null);
            return List.of(
                    "recaudo=" + service.movement(collection.reference()).orElseThrow().state(),
                    "vendedor=" + service.movement(payout.reference()).orElseThrow().state(),
                    "comision=" + service.movement(failing.reference()).orElseThrow().state());
        }
    }

    @FunctionalInterface
    interface Completer {
        int complete(String reference, String status, String signatureOverride) throws Exception;
    }

    // ========================================================= lado de DAZMA

    enum Channel {
        BRE_B("Bre-B"), PSE("PSE"), BOTON_BANCOLOMBIA("Botón Bancolombia"), NEQUI("Botón Nequi");

        final String label;

        Channel(String label) {
            this.label = label;
        }
    }

    enum Kind { COLLECTION, DISBURSEMENT, REFUND }

    enum MovementState {
        NOT_SENT("NO_ENVIADO"), PENDING("PENDIENTE"), CONFIRMED("CONFIRMADO"), FAILED("FALLIDO");

        private final String label;

        MovementState(String label) {
            this.label = label;
        }

        boolean isFinal() {
            return this == CONFIRMED || this == FAILED;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Instrucción con referencia única: la misma operación de negocio siempre produce la misma referencia. */
    record Instruction(String reference, String negotiationId, Kind kind, Channel channel, String beneficiary, long amount) {
        static Instruction collection(String negotiationId, Channel channel, long amount) {
            return new Instruction(negotiationId + "-recaudo", negotiationId, Kind.COLLECTION, channel, "fiducia", amount);
        }

        static Instruction disbursement(String negotiationId, String beneficiary, long amount, String purpose) {
            return new Instruction(negotiationId + "-" + purpose, negotiationId, Kind.DISBURSEMENT, null, beneficiary, amount);
        }

        static Instruction refund(String negotiationId, long amount) {
            return new Instruction(negotiationId + "-devolucion-" + amount, negotiationId, Kind.REFUND, null, "comprador", amount);
        }
    }

    record Movement(String reference, String negotiationId, Kind kind, long amount, MovementState state,
                    String providerId, String detail, Instant updatedAt) {
        Movement with(MovementState newState, String newProviderId, String newDetail) {
            return new Movement(reference, negotiationId, kind, amount, newState,
                    newProviderId == null ? providerId : newProviderId, newDetail == null ? detail : newDetail, Instant.now());
        }
    }

    /** Evento del proveedor ya traducido a conceptos de DAZMA. */
    record ProviderEvent(String eventId, String reference, MovementState state, String detail, Instant occurredAt) {
    }

    record ProviderMovement(String providerId, String reference, long amount, MovementState state) {
    }

    /** Contrato interno de pagos (ADR-09). El servicio solo conoce esta interfaz. */
    interface PaymentGateway {
        ProviderMovement request(Instruction instruction) throws ProviderUnavailable, UncertainOutcome;

        Optional<ProviderMovement> findByReference(String reference) throws ProviderUnavailable;
    }

    static final class ProviderUnavailable extends Exception {
        private static final long serialVersionUID = 1L;

        ProviderUnavailable(String message) {
            super(message);
        }
    }

    static final class UncertainOutcome extends Exception {
        private static final long serialVersionUID = 1L;

        UncertainOutcome(String message) {
            super(message);
        }
    }

    /** Servicio de pagos: estados, idempotencia, Inbox de notificaciones y registro financiero de solo adición. */
    static final class PaymentsService {
        private final PaymentGateway gateway;
        private final Map<String, Movement> movements = new LinkedHashMap<>();
        private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
        private final List<String> ledger = new ArrayList<>();
        private final Map<String, AtomicInteger> transitions = new ConcurrentHashMap<>();
        final AtomicInteger statusQueries = new AtomicInteger();
        final AtomicInteger inboxDuplicates = new AtomicInteger();
        final AtomicInteger staleIgnored = new AtomicInteger();

        PaymentsService(PaymentGateway gateway) {
            this.gateway = gateway;
        }

        synchronized Movement instruct(Instruction instruction) throws ProviderUnavailable {
            validate(instruction);
            Movement existing = movements.get(instruction.reference());
            if (existing != null && existing.state() != MovementState.NOT_SENT) {
                return existing;
            }
            if (existing != null) {
                Optional<Movement> adopted = adoptIfAlreadyAtProvider(existing);
                if (adopted.isPresent()) {
                    return adopted.get();
                }
            }
            Movement draft = existing != null ? existing : new Movement(instruction.reference(), instruction.negotiationId(),
                    instruction.kind(), instruction.amount(), MovementState.NOT_SENT, null, null, Instant.now());
            movements.put(draft.reference(), draft);
            try {
                ProviderMovement created = gateway.request(instruction);
                return save(draft.with(MovementState.PENDING, created.providerId(), null), "instrucción enviada");
            } catch (UncertainOutcome uncertain) {
                return adoptIfAlreadyAtProvider(draft)
                        .orElseThrow(() -> new IllegalStateException("Resultado incierto y sin rastro en el proveedor: " + uncertain.getMessage()));
            }
        }

        private Optional<Movement> adoptIfAlreadyAtProvider(Movement draft) throws ProviderUnavailable {
            statusQueries.incrementAndGet();
            Optional<ProviderMovement> found = gateway.findByReference(draft.reference());
            return found.map(providerMovement -> save(draft.with(
                    providerMovement.state() == MovementState.NOT_SENT ? MovementState.PENDING : providerMovement.state(),
                    providerMovement.providerId(), null), "instrucción confirmada por consulta"));
        }

        /** Inbox: cada evento se aplica una sola vez y nunca hace retroceder un estado final. */
        synchronized boolean onProviderEvent(ProviderEvent event) {
            if (!processedEvents.add(event.eventId())) {
                inboxDuplicates.incrementAndGet();
                return false;
            }
            Movement current = movements.get(event.reference());
            if (current == null || current.state().isFinal() || event.state() == MovementState.PENDING) {
                staleIgnored.incrementAndGet();
                return false;
            }
            save(current.with(event.state(), null, event.detail()), "notificación " + event.eventId());
            return true;
        }

        private void validate(Instruction instruction) {
            if (instruction.amount() <= 0) {
                throw new IllegalArgumentException("El valor debe ser positivo");
            }
            if (instruction.kind() == Kind.REFUND) {
                long collected = movements.values().stream()
                        .filter(m -> m.negotiationId().equals(instruction.negotiationId()))
                        .filter(m -> m.kind() == Kind.COLLECTION && m.state() == MovementState.CONFIRMED)
                        .mapToLong(Movement::amount).sum();
                long refunded = movements.values().stream()
                        .filter(m -> m.negotiationId().equals(instruction.negotiationId()))
                        .filter(m -> m.kind() == Kind.REFUND && m.state() != MovementState.FAILED)
                        .mapToLong(Movement::amount).sum();
                if (refunded + instruction.amount() > collected) {
                    throw new IllegalStateException("La devolución supera lo recaudado");
                }
            }
        }

        private Movement save(Movement movement, String reason) {
            Movement previous = movements.put(movement.reference(), movement);
            if (previous == null || previous.state() != movement.state()) {
                transitions.computeIfAbsent(movement.reference(), key -> new AtomicInteger()).incrementAndGet();
                ledger.add(Instant.now() + " " + movement.reference() + " " + movement.state() + " " + movement.amount() + " (" + reason + ")");
            }
            return movement;
        }

        synchronized Optional<Movement> movement(String reference) {
            return Optional.ofNullable(movements.get(reference));
        }

        synchronized int ledgerSize() {
            return ledger.size();
        }

        int transitions(String reference) {
            return transitions.getOrDefault(reference, new AtomicInteger()).get();
        }

        /** Compara el registro propio con el reporte del proveedor; no corrige nada, solo informa. */
        synchronized List<String> reconcile(List<ProviderMovement> providerReport) {
            List<String> differences = new ArrayList<>();
            Map<String, ProviderMovement> byReference = new LinkedHashMap<>();
            for (ProviderMovement movement : providerReport) {
                byReference.put(movement.reference(), movement);
                if (!movements.containsKey(movement.reference())) {
                    differences.add("movimiento desconocido " + movement.reference() + " por " + movement.amount());
                }
            }
            for (Movement own : movements.values()) {
                ProviderMovement theirs = byReference.get(own.reference());
                if (own.state() == MovementState.NOT_SENT) {
                    continue;
                }
                if (theirs == null) {
                    differences.add("falta en el proveedor " + own.reference());
                } else if (theirs.amount() != own.amount()) {
                    differences.add("valor distinto en " + own.reference() + ": DAZMA " + own.amount() + ", proveedor " + theirs.amount());
                } else if (own.state().isFinal() && theirs.state() != own.state()) {
                    differences.add("estado distinto en " + own.reference() + ": DAZMA " + own.state() + ", proveedor " + theirs.state());
                }
            }
            return differences;
        }
    }

    /** Adaptador hacia el proveedor simulado: traduce su API, sus estados y sus webhooks al contrato interno. */
    static final class CobreSimulatedAdapter implements PaymentGateway {
        private final String baseUrl;
        private final String webhookSecret;
        private final Duration timeout;

        CobreSimulatedAdapter(String baseUrl, String webhookSecret, Duration timeout) {
            this.baseUrl = baseUrl;
            this.webhookSecret = webhookSecret;
            this.timeout = timeout;
        }

        @Override
        public ProviderMovement request(Instruction instruction) throws ProviderUnavailable, UncertainOutcome {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("external_id", instruction.reference());
            body.put("type", instruction.kind().name());
            body.put("channel", instruction.channel() == null ? "TRANSFER" : instruction.channel().name());
            body.put("amount", instruction.amount());
            body.put("currency", "COP");
            body.put("counterparty", instruction.beneficiary());
            body.put("metadata", Map.of("negotiation_id", instruction.negotiationId()));
            try {
                HttpRequest request = Http.to(baseUrl + "/money-movements").timeout(timeout)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build();
                Http.Response response = Http.send(request);
                if (response.status() >= 500) {
                    throw new ProviderUnavailable("HTTP " + response.status());
                }
                check(response.status() == 201, "El proveedor rechazó la instrucción: " + response.brief());
                return toMovement(response.json());
            } catch (HttpTimeoutException timeoutException) {
                throw new UncertainOutcome("sin respuesta en " + timeout.toMillis() + " ms");
            } catch (IOException | InterruptedException exception) {
                throw new ProviderUnavailable(exception.getClass().getSimpleName());
            }
        }

        @Override
        public Optional<ProviderMovement> findByReference(String reference) throws ProviderUnavailable {
            try {
                Http.Response response = Http.send(Http.to(baseUrl + "/money-movements?external_id=" + Http.encode(reference))
                        .timeout(Duration.ofSeconds(5)).GET().build());
                if (response.status() >= 500) {
                    throw new ProviderUnavailable("HTTP " + response.status());
                }
                Object items = Json.parse(response.body());
                if (items instanceof List<?> list && !list.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> first = (Map<String, Object>) list.get(0);
                    return Optional.of(toMovement(first));
                }
                return Optional.empty();
            } catch (IOException | InterruptedException exception) {
                throw new ProviderUnavailable(exception.getClass().getSimpleName());
            }
        }

        /** Valida la firma del webhook y lo traduce. Devuelve vacío si la firma no es válida. */
        Optional<ProviderEvent> parseWebhook(String signatureHeader, String body) {
            if (signatureHeader == null || !signatureHeader.contains("t=") || !signatureHeader.contains(",v1=")) {
                return Optional.empty();
            }
            String timestamp = signatureHeader.substring(2, signatureHeader.indexOf(','));
            String signature = signatureHeader.substring(signatureHeader.indexOf(",v1=") + 4);
            byte[] expected = hmac(webhookSecret, timestamp + "." + body);
            byte[] received;
            try {
                received = HexFormat.of().parseHex(signature);
            } catch (IllegalArgumentException notHex) {
                return Optional.empty();
            }
            if (!MessageDigest.isEqual(expected, received)) {
                return Optional.empty();
            }
            Map<String, Object> event = Json.object(body);
            String status = Json.str(event, "movement.status");
            String detail = status != null && status.contains(":") ? status.substring(status.indexOf(':') + 1) : null;
            return Optional.of(new ProviderEvent(Json.str(event, "event_id"), Json.str(event, "movement.external_id"),
                    mapStatus(status), detail, Instant.parse(Json.str(event, "occurred_at"))));
        }

        private static ProviderMovement toMovement(Map<String, Object> json) {
            return new ProviderMovement(Json.str(json, "id"), Json.str(json, "external_id"),
                    ((Number) json.get("amount")).longValue(), mapStatus(Json.str(json, "status")));
        }

        private static MovementState mapStatus(String status) {
            if (status == null) {
                return MovementState.PENDING;
            }
            if (status.startsWith("COMPLETED")) {
                return MovementState.CONFIRMED;
            }
            if (status.startsWith("REJECTED") || status.startsWith("FAILED")) {
                return MovementState.FAILED;
            }
            return MovementState.PENDING;
        }
    }

    /** Endpoint de webhooks de DAZMA (equivale al controlador que recibiría las notificaciones del proveedor). */
    static final class WebhookEndpoint implements AutoCloseable {
        private HttpServer server;
        private PaymentsService payments;
        private CobreSimulatedAdapter adapter;

        static WebhookEndpoint start() throws IOException {
            WebhookEndpoint endpoint = new WebhookEndpoint();
            endpoint.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            endpoint.server.createContext("/payments/api/v1/provider-events", endpoint::handle);
            endpoint.server.start();
            return endpoint;
        }

        void bind(PaymentsService service) {
            this.payments = service;
            this.adapter = (CobreSimulatedAdapter) service.gateway;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/payments/api/v1/provider-events";
        }

        private void handle(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Optional<ProviderEvent> event = adapter.parseWebhook(exchange.getRequestHeaders().getFirst("X-Sim-Signature"), body);
            int status = 401;
            if (event.isPresent()) {
                payments.onProviderEvent(event.get());
                status = 200;
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    /** Segundo adaptador, en memoria, con otro vocabulario de estados. Sirve para C10. */
    static final class OtherProviderAdapter implements PaymentGateway {
        private final Map<String, String> statuses = new LinkedHashMap<>();
        private final Map<String, Long> amounts = new LinkedHashMap<>();

        @Override
        public ProviderMovement request(Instruction instruction) {
            statuses.put(instruction.reference(), "IN_PROCESS");
            amounts.put(instruction.reference(), instruction.amount());
            return new ProviderMovement("otro-" + UUID.randomUUID(), instruction.reference(), instruction.amount(), MovementState.PENDING);
        }

        @Override
        public Optional<ProviderMovement> findByReference(String reference) {
            return Optional.ofNullable(statuses.get(reference)).map(status ->
                    new ProviderMovement("otro", reference, amounts.get(reference), translate(status)));
        }

        void notifyResult(PaymentsService service, String reference, String status) {
            statuses.put(reference, status);
            service.onProviderEvent(new ProviderEvent(UUID.randomUUID().toString(), reference, translate(status),
                    "DECLINED".equals(status) ? "motivo" : null, Instant.now()));
        }

        private static MovementState translate(String status) {
            return switch (status) {
                case "APPROVED" -> MovementState.CONFIRMED;
                case "DECLINED" -> MovementState.FAILED;
                default -> MovementState.PENDING;
            };
        }
    }

    // ===================================================== proveedor simulado

    /**
     * Proveedor de pagos simulado. No reproduce la API real de Cobre: reproduce los comportamientos que el ADR-17
     * obliga a manejar. No deduplica por external_id para que la protección contra duplicados dependa de DAZMA.
     */
    static final class SimulatedProvider implements AutoCloseable {
        private final Map<String, Map<String, Object>> movements = new ConcurrentHashMap<>();
        private final Map<String, String> preparedEvents = new ConcurrentHashMap<>();
        private final String secret;
        private HttpServer server;
        private volatile String webhookUrl;
        private volatile boolean down;
        private volatile boolean slowNext;

        private SimulatedProvider(String secret) {
            this.secret = secret;
        }

        static SimulatedProvider start(String secret) throws IOException {
            SimulatedProvider provider = new SimulatedProvider(secret);
            provider.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            provider.server.createContext("/money-movements", provider::handle);
            provider.server.start();
            return provider;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void webhookUrl(String url) {
            this.webhookUrl = url;
        }

        void down(boolean value) {
            this.down = value;
        }

        void slowNextResponse(boolean value) {
            this.slowNext = value;
        }

        private void handle(HttpExchange exchange) throws IOException {
            if (down) {
                respond(exchange, 503, "{\"error\":\"service_unavailable\"}");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, Object> request = Json.object(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                Map<String, Object> movement = new LinkedHashMap<>(request);
                movement.put("id", "mm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                movement.put("status", "PENDING");
                movements.put(String.valueOf(movement.get("id")), movement);
                if (slowNext) {
                    slowNext = false;
                    sleep(2_500);
                }
                respond(exchange, 201, Json.write(movement));
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            String reference = query != null && query.startsWith("external_id=")
                    ? java.net.URLDecoder.decode(query.substring("external_id=".length()), StandardCharsets.UTF_8) : null;
            List<Object> found = new ArrayList<>();
            movements.values().forEach(movement -> {
                if (reference == null || reference.equals(movement.get("external_id"))) {
                    found.add(movement);
                }
            });
            respond(exchange, 200, Json.write(found));
        }

        void setStatus(String reference, String status) {
            byReference(reference).put("status", status);
        }

        /** Cambia el estado de un movimiento y envía el webhook. signatureOverride permite enviar una firma alterada. */
        int complete(String reference, String status, String signatureOverride) throws Exception {
            setStatus(reference, status);
            String eventId = prepareEvent(reference, status);
            return deliver(eventId, signatureOverride);
        }

        String prepareEvent(String reference, String status) {
            Map<String, Object> movement = byReference(reference);
            Map<String, Object> snapshot = new LinkedHashMap<>(movement);
            snapshot.put("status", status);
            String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", eventId);
            event.put("type", "money_movement.status_changed");
            event.put("occurred_at", Instant.now().toString());
            event.put("movement", snapshot);
            preparedEvents.put(eventId, Json.write(event));
            return eventId;
        }

        int deliver(String eventId) throws Exception {
            return deliver(eventId, null);
        }

        private int deliver(String eventId, String signatureOverride) throws Exception {
            String body = preparedEvents.get(eventId);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String signature = signatureOverride != null ? "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(signatureOverride.getBytes(StandardCharsets.UTF_8))
                    : "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hmac(secret, timestamp + "." + body));
            HttpRequest request = Http.to(webhookUrl)
                    .header("Content-Type", "application/json")
                    .header("X-Sim-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            return Http.send(request).status();
        }

        int countByReference(String reference) {
            return (int) movements.values().stream().filter(m -> reference.equals(m.get("external_id"))).count();
        }

        int totalMovements() {
            return movements.size();
        }

        void tamperAmount(String reference, long amount) {
            byReference(reference).put("amount", amount);
        }

        void addUnknownMovement(long amount) {
            Map<String, Object> movement = new LinkedHashMap<>();
            movement.put("id", "mm_desconocido");
            movement.put("external_id", "sin-referencia-dazma");
            movement.put("amount", amount);
            movement.put("status", "COMPLETED");
            movements.put("mm_desconocido", movement);
        }

        List<ProviderMovement> report() {
            List<ProviderMovement> report = new ArrayList<>();
            movements.values().forEach(movement -> report.add(new ProviderMovement(String.valueOf(movement.get("id")),
                    String.valueOf(movement.get("external_id")), ((Number) movement.get("amount")).longValue(),
                    CobreSimulatedAdapter.mapStatus(String.valueOf(movement.get("status"))))));
            return report;
        }

        private Map<String, Object> byReference(String reference) {
            return movements.values().stream().filter(m -> reference.equals(m.get("external_id"))).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No existe movimiento para " + reference));
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException clientGone) {
                // El cliente ya cerró la conexión por tiempo de espera; el movimiento queda creado igual.
            } finally {
                exchange.close();
            }
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    // ============================================================ utilidades

    static String negotiationId() {
        return "neg-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static byte[] hmac(String secret, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    // ------------------------------------------------------------------
    // Soporte común: configuración, reporte de casos y JSON.
    // Se repite en cada prueba para que cada archivo se ejecute solo,
    // sin dependencias ni compilación previa (java Archivo.java).
    // ------------------------------------------------------------------

    static final class Env {
        private Env() {
        }

        static String get(String name) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? null : value.trim();
        }

        static String require(String name) {
            String value = get(name);
            if (value == null) {
                throw new Skip("falta la variable de entorno " + name);
            }
            return value;
        }

        static String orDefault(String name, String fallback) {
            String value = get(name);
            return value == null ? fallback : value;
        }

        static int intOrDefault(String name, int fallback) {
            String value = get(name);
            return value == null ? fallback : Integer.parseInt(value);
        }

        static boolean flag(String name) {
            return "true".equalsIgnoreCase(get(name));
        }

        static List<String> missing(String... names) {
            List<String> missing = new ArrayList<>();
            for (String name : names) {
                if (get(name) == null) {
                    missing.add(name);
                }
            }
            return missing;
        }

        static void requireAll(String... names) {
            List<String> missing = missing(names);
            if (!missing.isEmpty()) {
                throw new Skip("faltan variables de entorno: " + String.join(", ", missing));
            }
        }
    }

    /** Señala un caso que no se puede ejecutar con la configuración actual. */
    static final class Skip extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Skip(String message) {
            super(message);
        }
    }

    /** Señala que un criterio de éxito no se cumplió. */
    static final class CheckFailed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CheckFailed(String message) {
            super(message);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new CheckFailed(message);
        }
    }

    @FunctionalInterface
    interface CaseBody {
        String run() throws Exception;
    }

    static final class Report {
        enum Status {
            PASSED("PASÓ"), FAILED("FALLÓ"), SKIPPED("OMITIDO");

            final String label;

            Status(String label) {
                this.label = label;
            }
        }

        record CaseResult(String id, String name, String criterion, Status status, String evidence, long millis) {
        }

        private final String title;
        private final String adr;
        private final Instant startedAt = Instant.now();
        private final List<CaseResult> results = new ArrayList<>();
        private final Map<String, String> context = new LinkedHashMap<>();

        Report(String title, String adr) {
            this.title = title;
            this.adr = adr;
            System.out.println();
            System.out.println("== " + title + " (" + adr + ") ==");
        }

        /** Datos no sensibles que ayudan a interpretar el resultado (región, bucket, URL pública...). */
        void context(String key, String value) {
            context.put(key, value == null ? "(sin configurar)" : value);
        }

        void run(String id, String name, String criterion, CaseBody body) {
            long start = System.nanoTime();
            Status status;
            String evidence;
            try {
                evidence = body.run();
                status = Status.PASSED;
            } catch (Skip skip) {
                status = Status.SKIPPED;
                evidence = skip.getMessage();
            } catch (CheckFailed failure) {
                status = Status.FAILED;
                evidence = failure.getMessage();
            } catch (Exception | AssertionError error) {
                status = Status.FAILED;
                evidence = error.getClass().getSimpleName() + ": " + error.getMessage();
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            CaseResult result = new CaseResult(id, name, criterion, status, evidence == null ? "" : evidence, millis);
            results.add(result);
            System.out.printf("[%s] %s %s (%d ms)%n", status.label, id, name, millis);
            if (!result.evidence().isBlank()) {
                System.out.println("        " + result.evidence().replace("\n", "\n        "));
            }
        }

        long count(Status status) {
            return results.stream().filter(result -> result.status() == status).count();
        }

        Path write() throws IOException {
            Path directory = Path.of(Env.orDefault("POC_RESULTS_DIR", "resultados"));
            Files.createDirectories(directory);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneId.systemDefault())
                    .format(startedAt);
            Path file = directory.resolve("resultado-" + stamp + ".md");

            StringBuilder markdown = new StringBuilder();
            markdown.append("# ").append(title).append("\n\n");
            markdown.append("- ADR: ").append(adr).append("\n");
            markdown.append("- Ejecutado: ").append(DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .withZone(ZoneId.systemDefault()).format(startedAt)).append("\n");
            markdown.append("- Java: ").append(System.getProperty("java.version")).append("\n");
            context.forEach((key, value) -> markdown.append("- ").append(key).append(": ").append(value).append("\n"));
            markdown.append("- Resumen: ")
                    .append(count(Status.PASSED)).append(" pasaron, ")
                    .append(count(Status.FAILED)).append(" fallaron, ")
                    .append(count(Status.SKIPPED)).append(" omitidos\n\n");
            markdown.append("| Caso | Prueba | Criterio de éxito | Resultado | Evidencia | ms |\n");
            markdown.append("|---|---|---|---|---|---|\n");
            for (CaseResult result : results) {
                markdown.append("| ").append(result.id())
                        .append(" | ").append(cell(result.name()))
                        .append(" | ").append(cell(result.criterion()))
                        .append(" | **").append(result.status().label).append("**")
                        .append(" | ").append(cell(result.evidence()))
                        .append(" | ").append(result.millis())
                        .append(" |\n");
            }
            Files.writeString(file, markdown.toString(), StandardCharsets.UTF_8);
            return file;
        }

        private static String cell(String text) {
            return text.replace("|", "\\|").replace("\r", "").replace("\n", "<br>");
        }

        int finish() {
            System.out.println();
            System.out.printf("Resumen: %d pasaron, %d fallaron, %d omitidos%n",
                    count(Status.PASSED), count(Status.FAILED), count(Status.SKIPPED));
            try {
                System.out.println("Resultado guardado en " + write().toAbsolutePath());
            } catch (IOException exception) {
                System.out.println("No se pudo guardar el resultado: " + exception.getMessage());
            }
            return count(Status.FAILED) > 0 ? 1 : 0;
        }
    }

    static final class Stats {
        private Stats() {
        }

        static long percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) {
                return -1;
            }
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        static String summary(List<Long> millis) {
            return "n=" + millis.size()
                    + ", p50=" + percentile(millis, 50) + " ms"
                    + ", p95=" + percentile(millis, 95) + " ms"
                    + ", máx=" + (millis.isEmpty() ? -1 : Collections.max(millis)) + " ms";
        }
    }

    /** Muestra solo el inicio y la longitud de un valor sensible. */
    static String mask(String value) {
        if (value == null) {
            return "(vacío)";
        }
        return value.length() <= 4 ? "****" : value.substring(0, 2) + "…(" + value.length() + " caracteres)";
    }

    /** Lector y escritor JSON mínimo: objetos como Map, arreglos como List, números como Double. */
    static final class Json {
        private final String text;
        private int position;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json parser = new Json(text);
            parser.skipWhitespace();
            Object value = parser.readValue();
            parser.skipWhitespace();
            if (parser.position != text.length()) {
                throw new IllegalArgumentException("JSON con contenido sobrante en la posición " + parser.position);
            }
            return value;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(String text) {
            return (Map<String, Object>) parse(text);
        }

        /** Navega rutas como "users.0.email". Devuelve null si algún tramo no existe. */
        @SuppressWarnings("unchecked")
        static Object at(Object root, String path) {
            Object current = root;
            for (String part : path.split("\\.")) {
                if (current instanceof Map<?, ?> map) {
                    current = ((Map<String, Object>) map).get(part);
                } else if (current instanceof List<?> list && part.chars().allMatch(Character::isDigit)) {
                    int index = Integer.parseInt(part);
                    current = index < list.size() ? list.get(index) : null;
                } else {
                    return null;
                }
            }
            return current;
        }

        static String str(Object root, String path) {
            Object value = at(root, path);
            if (value instanceof Double number && number == Math.rint(number) && !number.isInfinite() && Math.abs(number) < 1e15) {
                return Long.toString(number.longValue());
            }
            return value == null ? null : String.valueOf(value);
        }

        static String write(Object value) {
            StringBuilder out = new StringBuilder();
            writeValue(out, value);
            return out.toString();
        }

        private static void writeValue(StringBuilder out, Object value) {
            if (value == null) {
                out.append("null");
            } else if (value instanceof String string) {
                writeString(out, string);
            } else if (value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else if (value instanceof Map<?, ?> map) {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeString(out, String.valueOf(entry.getKey()));
                    out.append(':');
                    writeValue(out, entry.getValue());
                }
                out.append('}');
            } else if (value instanceof Collection<?> collection) {
                out.append('[');
                boolean first = true;
                for (Object item : collection) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeValue(out, item);
                }
                out.append(']');
            } else {
                writeString(out, value.toString());
            }
        }

        private static void writeString(StringBuilder out, String value) {
            out.append('"');
            for (char c : value.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }

        private Object readValue() {
            if (position >= text.length()) {
                throw new IllegalArgumentException("JSON incompleto");
            }
            char c = text.charAt(position);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            position++;
            skipWhitespace();
            if (text.charAt(position) == '}') {
                position++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();
                char c = text.charAt(position++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Se esperaba , o } en la posición " + (position - 1));
                }
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            position++;
            skipWhitespace();
            if (text.charAt(position) == ']') {
                position++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();
                char c = text.charAt(position++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Se esperaba , o ] en la posición " + (position - 1));
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    char escaped = text.charAt(position++);
                    switch (escaped) {
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                            position += 4;
                        }
                        default -> out.append(escaped);
                    }
                } else {
                    out.append(c);
                }
            }
        }

        private Object readNumber() {
            int start = position;
            while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("Valor JSON inesperado en la posición " + start);
            }
            return Double.parseDouble(text.substring(start, position));
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, position)) {
                throw new IllegalArgumentException("Valor JSON inesperado en la posición " + position);
            }
            position += word.length();
            return value;
        }

        private void expect(char expected) {
            if (text.charAt(position) != expected) {
                throw new IllegalArgumentException("Se esperaba '" + expected + "' en la posición " + position);
            }
            position++;
        }

        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }
    }

    static final class Http {
        static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        private Http() {
        }

        record Response(int status, String body, HttpHeaders headers, long millis) {
            Map<String, Object> json() {
                return Json.object(body);
            }

            String header(String name) {
                return headers.firstValue(name).orElse(null);
            }

            String brief() {
                String compact = body == null ? "" : body.replaceAll("\\s+", " ").trim();
                return "HTTP " + status + (compact.isEmpty() ? "" : " " + (compact.length() > 300 ? compact.substring(0, 300) + "…" : compact));
            }
        }

        static HttpRequest.Builder to(String url) {
            return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        }

        static Response send(HttpRequest request) throws IOException, InterruptedException {
            long start = System.nanoTime();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long millis = (System.nanoTime() - start) / 1_000_000;
            return new Response(response.statusCode(), response.body(), response.headers(), millis);
        }

        static Response postJson(String url, Object body, String... headers) throws IOException, InterruptedException {
            HttpRequest.Builder builder = to(url)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
            for (int i = 0; i + 1 < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
            return send(builder.build());
        }

        static Response get(String url, String... headers) throws IOException, InterruptedException {
            HttpRequest.Builder builder = to(url).GET();
            for (int i = 0; i + 1 < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
            return send(builder.build());
        }

        static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        }

        static String form(Map<String, String> fields) {
            StringBuilder out = new StringBuilder();
            fields.forEach((key, value) -> {
                if (!out.isEmpty()) {
                    out.append('&');
                }
                out.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            });
            return out.toString();
        }

        static String trimSlash(String url) {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }
}
