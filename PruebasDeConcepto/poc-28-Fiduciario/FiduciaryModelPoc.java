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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Prueba de concepto del ADR-28: modelo fiduciario para administrar y liberar pagos.
 *
 * Sin contrato con Skandia ni con Cobre, la fiducia se representa con el adaptador simulado que describe el ADR:
 * un libro de cuentas donde los recursos de cada negociación quedan en un encargo separado de la cuenta operativa de
 * DAZMA y solo se mueven por instrucciones con referencia única. Sobre él se ejecutan las reglas de DAZMA:
 * comisión calculada una vez, liberación en dos instrucciones independientes, reintento solo de lo pendiente,
 * devolución sin comisión, desacuerdos y una línea de tiempo de solo adición.
 */
public class FiduciaryModelPoc {

    public static void main(String[] args) {
        BigDecimal rate = new BigDecimal(Env.orDefault("DAZMA_COMMISSION_RATE", "0.05"));
        long seed = Env.get("POC_SEED") != null ? Long.parseLong(Env.get("POC_SEED")) : new SecureRandom().nextLong();
        int randomNegotiations = Env.intOrDefault("FIDUCIARY_RANDOM_NEGOTIATIONS", 300);

        Report report = new Report("Prueba de concepto: modelo fiduciario (adaptador simulado)", "ADR-28");
        report.context("Comisión usada en la prueba", rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + " % (parámetro, no decisión de negocio)");
        report.context("Semilla de la simulación aleatoria", Long.toString(seed));
        Cases cases = new Cases(rate, seed, randomNegotiations);

        report.run("C1", "Recursos separados de la cuenta operativa",
                "Después del recaudo el dinero está en el encargo de la negociación y la cuenta operativa de DAZMA no recibe nada",
                cases::fundsAreSeparated);
        report.run("C2", "Comisión calculada una sola vez y sin pérdidas",
                "Para 10.000 valores, comisión + neto = valor recaudado; repetir el registro del pago no recalcula la distribución",
                cases::commissionComputedOnce);
        report.run("C3", "No se libera antes de la condición",
                "Con una sola confirmación de entrega la liberación se rechaza; con las dos se permite (ESC-CAL-SEG-0007)",
                cases::noReleaseBeforeCondition);
        report.run("C4", "Liberación en dos instrucciones independientes",
                "Se crean una instrucción al vendedor y otra de comisión, con referencias únicas y estado propio; el encargo queda en 0",
                cases::releaseInTwoInstructions);
        report.run("C5", "Falla parcial de la liberación",
                "Si la comisión se paga y el pago al vendedor falla, solo se reintenta el pendiente y la comisión no se paga dos veces (ESC-CAL-CON-0001)",
                cases::partialFailureRetriesOnlyPending);
        report.run("C6", "Instrucción repetida",
                "Pedir la liberación 5 veces produce exactamente 2 movimientos ejecutados",
                cases::repeatedReleaseIsIdempotent);
        report.run("C7", "Cancelación antes de liberar",
                "Se devuelve el valor completo al comprador y no se cobra comisión",
                cases::cancellationRefundsWithoutCommission);
        report.run("C8", "Desacuerdo",
                "Mientras hay desacuerdo no se puede liberar ni devolver; resuelto, se ejecuta solo la salida decidida",
                cases::disputeBlocksMovements);
        report.run("C9", "Línea de tiempo para reclamaciones",
                "Todos los eventos de una negociación quedan en orden cronológico, ligados a ella, y el registro no se puede modificar (ESC-CAL-AUD-0008)",
                cases::auditTimeline);
        report.run("C10", "Simulación aleatoria con fallas",
                "En " + randomNegotiations + " negociaciones con fallas, respuestas inciertas, cancelaciones y desacuerdos: 0 violaciones de conservación del dinero, estados válidos y movimientos duplicados",
                cases::randomizedSimulation);
        System.exit(report.finish());
    }

    // =============================================================== casos

    record Cases(BigDecimal rate, long seed, int randomNegotiations) {

        String fundsAreSeparated() {
            World world = new World(rate);
            String negotiation = world.service.registerPayment("neg-1", "comprador-1", "vendedor-1", 420_000);
            check(world.fiduciary.balance("encargo:" + negotiation) == 420_000, "El encargo no tiene el valor recaudado");
            check(world.fiduciary.balance(Fiduciary.OPERATING) == 0, "La cuenta operativa recibió dinero en el recaudo");
            check(world.fiduciary.balance("comprador:comprador-1") == -420_000, "El dinero no salió del comprador");
            return "Encargo neg-1 = 420.000; cuenta operativa DAZMA = 0";
        }

        String commissionComputedOnce() {
            World world = new World(rate);
            Random random = new Random(seed);
            for (int i = 0; i < 10_000; i++) {
                long amount = 1_000 + (long) (random.nextDouble() * 9_999_000);
                Distribution distribution = Distribution.of(amount, rate);
                check(distribution.commission() + distribution.sellerNet() == amount, "Pérdida o sobrante en " + amount);
                check(distribution.commission() >= 0 && distribution.sellerNet() > 0, "Valores negativos en " + amount);
            }
            world.service.registerPayment("neg-2", "c", "v", 99_999);
            Distribution first = world.service.distribution("neg-2");
            world.service.registerPayment("neg-2", "c", "v", 99_999);
            check(world.service.distribution("neg-2") == first, "La distribución se recalculó al repetir el registro");
            check(world.fiduciary.executions("neg-2-recaudo") == 1, "El recaudo se registró dos veces");
            return "10.000/10.000 valores sin pérdidas; 99.999 → comisión " + first.commission() + " + neto " + first.sellerNet()
                    + "; repetir el registro no recalcula ni duplica el recaudo";
        }

        String noReleaseBeforeCondition() {
            World world = new World(rate);
            world.service.registerPayment("neg-3", "c", "v", 200_000);
            world.service.confirmDelivery("neg-3", Party.BUYER);
            boolean rejected = rejects(() -> world.service.release("neg-3"));
            check(rejected, "Se liberó con una sola confirmación");
            check(world.fiduciary.balance("encargo:neg-3") == 200_000, "Salió dinero del encargo");
            world.service.confirmDelivery("neg-3", Party.SELLER);
            world.service.release("neg-3");
            check(world.service.state("neg-3") == State.RELEASED, "Con ambas confirmaciones no quedó LIBERADA");
            return "1 confirmación → rechazada, encargo intacto; 2 confirmaciones → LIBERADA";
        }

        String releaseInTwoInstructions() {
            World world = new World(rate);
            world.service.registerPayment("neg-4", "c", "v", 1_000_000);
            confirmBoth(world, "neg-4");
            world.service.release("neg-4");
            Distribution distribution = world.service.distribution("neg-4");
            Instruction seller = world.service.instruction("neg-4-vendedor");
            Instruction commission = world.service.instruction("neg-4-comision");
            check(seller.state() == InstructionState.CONFIRMED && commission.state() == InstructionState.CONFIRMED, "Instrucciones no confirmadas");
            check(world.fiduciary.balance("encargo:neg-4") == 0, "El encargo no quedó en 0");
            check(world.fiduciary.balance("vendedor:v") == distribution.sellerNet(), "El vendedor no recibió el neto");
            check(world.fiduciary.balance(Fiduciary.OPERATING) == distribution.commission(), "DAZMA no recibió la comisión");
            return "neg-4-vendedor " + distribution.sellerNet() + " CONFIRMADA; neg-4-comision " + distribution.commission()
                    + " CONFIRMADA; encargo = 0";
        }

        String partialFailureRetriesOnlyPending() {
            World world = new World(rate);
            world.service.registerPayment("neg-5", "c", "v", 500_000);
            confirmBoth(world, "neg-5");
            world.fiduciary.failNext("neg-5-vendedor", 1);
            world.service.release("neg-5");
            check(world.service.state("neg-5") == State.RELEASING, "Con una instrucción fallida se esperaba LIBERANDO, quedó " + world.service.state("neg-5"));
            check(world.service.instruction("neg-5-comision").state() == InstructionState.CONFIRMED, "La comisión no quedó confirmada");
            check(world.service.instruction("neg-5-vendedor").state() == InstructionState.FAILED, "El pago al vendedor no quedó FALLIDO");
            world.service.release("neg-5");
            check(world.service.state("neg-5") == State.RELEASED, "El reintento no completó la liberación");
            check(world.fiduciary.executions("neg-5-comision") == 1, "La comisión se ejecutó " + world.fiduciary.executions("neg-5-comision") + " veces");
            check(world.fiduciary.executions("neg-5-vendedor") == 1, "El pago al vendedor se ejecutó " + world.fiduciary.executions("neg-5-vendedor") + " veces");
            return "Primer intento: comisión CONFIRMADA, vendedor FALLIDO, negociación LIBERANDO. Reintento: solo vendedor; "
                    + "ejecuciones comisión=1, vendedor=1";
        }

        String repeatedReleaseIsIdempotent() {
            World world = new World(rate);
            world.service.registerPayment("neg-6", "c", "v", 300_000);
            confirmBoth(world, "neg-6");
            world.fiduciary.uncertainNext("neg-6-vendedor");
            for (int i = 0; i < 5; i++) {
                world.service.release("neg-6");
            }
            int executed = world.fiduciary.executions("neg-6-vendedor") + world.fiduciary.executions("neg-6-comision");
            check(executed == 2, "Se ejecutaron " + executed + " movimientos");
            check(world.service.state("neg-6") == State.RELEASED, "No quedó LIBERADA");
            return "5 solicitudes (una con respuesta perdida) → 2 movimientos ejecutados; consultas de estado: "
                    + world.service.statusQueries;
        }

        String cancellationRefundsWithoutCommission() {
            World world = new World(rate);
            world.service.registerPayment("neg-7", "comprador-7", "v", 250_000);
            world.service.confirmDelivery("neg-7", Party.BUYER);
            world.service.cancel("neg-7");
            check(world.service.state("neg-7") == State.REFUNDED, "No quedó DEVUELTA");
            check(world.fiduciary.balance("comprador:comprador-7") == 0, "El comprador no recuperó todo su dinero");
            check(world.fiduciary.balance(Fiduciary.OPERATING) == 0, "Se cobró comisión en una cancelación");
            check(rejects(() -> world.service.release("neg-7")), "Se pudo liberar una negociación devuelta");
            return "Devolución de 250.000 CONFIRMADA; comisión 0; liberar después de devolver se rechaza";
        }

        String disputeBlocksMovements() {
            World world = new World(rate);
            world.service.registerPayment("neg-8", "comprador-8", "v", 180_000);
            confirmBoth(world, "neg-8");
            world.service.openDispute("neg-8");
            boolean releaseBlocked = rejects(() -> world.service.release("neg-8"));
            boolean refundBlocked = rejects(() -> world.service.cancel("neg-8"));
            check(releaseBlocked && refundBlocked, "Durante el desacuerdo se permitió mover dinero");
            check(world.fiduciary.balance("encargo:neg-8") == 180_000, "Salió dinero durante el desacuerdo");
            world.service.resolveDispute("neg-8", Party.BUYER);
            check(world.service.state("neg-8") == State.REFUNDED, "Resuelto a favor del comprador no quedó DEVUELTA");
            check(world.fiduciary.executions("neg-8-vendedor") == 0 && world.fiduciary.executions("neg-8-comision") == 0,
                    "Se ejecutó una liberación además de la devolución");
            return "Liberar y devolver bloqueados durante el desacuerdo; resuelto a favor del comprador → solo devolución";
        }

        String auditTimeline() {
            World world = new World(rate);
            world.service.registerPayment("neg-9", "c", "v", 640_000);
            world.service.confirmDelivery("neg-9", Party.SELLER);
            world.service.confirmDelivery("neg-9", Party.BUYER);
            world.fiduciary.failNext("neg-9-vendedor", 1);
            world.service.release("neg-9");
            world.service.release("neg-9");
            List<LedgerEntry> timeline = world.service.timeline("neg-9");
            for (int i = 1; i < timeline.size(); i++) {
                check(timeline.get(i).sequence() > timeline.get(i - 1).sequence(), "Eventos fuera de orden");
            }
            check(timeline.stream().allMatch(e -> "neg-9".equals(e.negotiationId())), "Hay eventos sin relación con la negociación");
            boolean immutable;
            try {
                world.service.ledger().add(new LedgerEntry(0, "neg-9", "x", "ALTERADO", 0, Instant.now()));
                immutable = false;
            } catch (UnsupportedOperationException expected) {
                immutable = true;
            }
            check(immutable, "El registro se pudo modificar");
            List<String> lines = new ArrayList<>();
            timeline.forEach(e -> lines.add(e.sequence() + ". " + e.event() + (e.reference().isEmpty() ? "" : " " + e.reference())));
            return timeline.size() + " eventos en orden: " + String.join(" | ", lines);
        }

        String randomizedSimulation() {
            World world = new World(rate);
            Random random = new Random(seed);
            Map<String, String> paths = new HashMap<>();
            for (int i = 0; i < randomNegotiations; i++) {
                String negotiation = "sim-" + i;
                long amount = 10_000 + (long) (random.nextDouble() * 4_990_000);
                world.service.registerPayment(negotiation, "comprador-" + i, "vendedor-" + i, amount);
                if (random.nextInt(4) == 0) {
                    world.service.registerPayment(negotiation, "comprador-" + i, "vendedor-" + i, amount);
                }
                if (random.nextBoolean()) {
                    world.fiduciary.failNext(negotiation + "-vendedor", 1 + random.nextInt(2));
                }
                if (random.nextBoolean()) {
                    world.fiduciary.failNext(negotiation + "-comision", 1 + random.nextInt(2));
                }
                if (random.nextInt(3) == 0) {
                    world.fiduciary.uncertainNext(negotiation + "-vendedor");
                }
                if (random.nextInt(5) == 0) {
                    world.fiduciary.failNext(negotiation + "-devolucion", 1);
                }
                int path = random.nextInt(10);
                String label;
                if (path < 5) {
                    label = "liberación";
                    world.service.confirmDelivery(negotiation, Party.BUYER);
                    rejects(() -> world.service.release(negotiation));
                    world.service.confirmDelivery(negotiation, Party.SELLER);
                } else if (path < 7) {
                    label = "cancelación";
                    world.service.cancel(negotiation);
                } else {
                    label = "desacuerdo";
                    world.service.confirmDelivery(negotiation, Party.BUYER);
                    world.service.openDispute(negotiation);
                    rejects(() -> world.service.release(negotiation));
                    world.service.resolveDispute(negotiation, random.nextBoolean() ? Party.BUYER : Party.SELLER);
                }
                for (int attempt = 0; attempt < 6 && !world.service.state(negotiation).isFinal(); attempt++) {
                    if (world.service.state(negotiation) == State.REFUNDING) {
                        world.service.cancel(negotiation);
                    } else {
                        world.service.release(negotiation);
                    }
                }
                paths.merge(label + " → " + world.service.state(negotiation), "1", (a, b) -> Integer.toString(Integer.parseInt(a) + 1));
            }

            List<String> violations = new ArrayList<>();
            long operatingExpected = 0;
            for (int i = 0; i < randomNegotiations; i++) {
                String negotiation = "sim-" + i;
                State state = world.service.state(negotiation);
                Distribution distribution = world.service.distribution(negotiation);
                long collected = distribution.amount();
                long sellerPaid = world.fiduciary.balance("vendedor:vendedor-" + i);
                long refunded = collected + world.fiduciary.balance("comprador:comprador-" + i);
                long held = world.fiduciary.balance("encargo:" + negotiation);
                long commissionPaid = world.fiduciary.executions(negotiation + "-comision") == 1 ? distribution.commission() : 0;
                if (!state.isFinal()) {
                    violations.add(negotiation + " terminó en " + state);
                }
                if (collected != sellerPaid + commissionPaid + refunded + held) {
                    violations.add(negotiation + " no conserva el dinero");
                }
                if (state == State.RELEASED && (sellerPaid != distribution.sellerNet() || commissionPaid != distribution.commission() || held != 0)) {
                    violations.add(negotiation + " liberada con valores incorrectos");
                }
                if (state == State.REFUNDED && (refunded != collected || commissionPaid != 0 || sellerPaid != 0)) {
                    violations.add(negotiation + " devuelta con valores incorrectos");
                }
                if (state == State.RELEASED) {
                    operatingExpected += distribution.commission();
                }
            }
            violations.addAll(world.fiduciary.duplicatedExecutions());
            if (world.fiduciary.balance(Fiduciary.OPERATING) != operatingExpected) {
                violations.add("la cuenta operativa no coincide con las comisiones liberadas");
            }
            violations.addAll(world.service.releasesBeforeCondition());
            check(violations.isEmpty(), violations.size() + " violaciones: " + violations.subList(0, Math.min(5, violations.size())));
            List<String> summary = new ArrayList<>();
            new java.util.TreeMap<>(paths).forEach((key, value) -> summary.add(key + ": " + value));
            return randomNegotiations + " negociaciones, 0 violaciones. " + String.join("; ", summary)
                    + ". Fallas inyectadas: " + world.fiduciary.injectedFailures + ", respuestas inciertas: " + world.fiduciary.injectedUncertain
                    + ". Comisiones en la cuenta operativa: " + operatingExpected;
        }

        private static void confirmBoth(World world, String negotiation) {
            world.service.confirmDelivery(negotiation, Party.BUYER);
            world.service.confirmDelivery(negotiation, Party.SELLER);
        }

        private static boolean rejects(Runnable action) {
            try {
                action.run();
                return false;
            } catch (IllegalStateException expected) {
                return true;
            }
        }
    }

    // ========================================================== modelo DAZMA

    enum Party { BUYER, SELLER }

    enum State {
        FUNDS_HELD("FONDOS_EN_FIDUCIA"), DISPUTED("EN_DESACUERDO"), RELEASING("LIBERANDO"),
        RELEASED("LIBERADA"), REFUNDING("DEVOLVIENDO"), REFUNDED("DEVUELTA");

        private final String label;

        State(String label) {
            this.label = label;
        }

        boolean isFinal() {
            return this == RELEASED || this == REFUNDED;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum InstructionState {
        PENDING("PENDIENTE"), UNCERTAIN("INCIERTA"), CONFIRMED("CONFIRMADA"), FAILED("FALLIDA");

        private final String label;

        InstructionState(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Distribución esperada de la venta: se calcula una vez al registrar el pago y no mueve dinero. */
    record Distribution(long amount, long commission, long sellerNet) {
        static Distribution of(long amount, BigDecimal rate) {
            long commission = BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
            return new Distribution(amount, commission, amount - commission);
        }
    }

    record Instruction(String reference, String negotiationId, String from, String to, long amount, InstructionState state) {
        Instruction with(InstructionState newState) {
            return new Instruction(reference, negotiationId, from, to, amount, newState);
        }
    }

    record LedgerEntry(long sequence, String negotiationId, String reference, String event, long amount, Instant at) {
    }

    record Negotiation(String id, String buyer, String seller, State state, Set<Party> confirmations) {
        Negotiation with(State newState) {
            return new Negotiation(id, buyer, seller, newState, confirmations);
        }
    }

    /** Contrato interno hacia la fiducia (ADR-09). */
    interface FiduciaryGateway {
        InstructionState execute(Instruction instruction);

        InstructionState query(String reference);
    }

    static final class FiduciaryService {
        private final FiduciaryGateway gateway;
        private final BigDecimal rate;
        private final Map<String, Negotiation> negotiations = new LinkedHashMap<>();
        private final Map<String, Distribution> distributions = new LinkedHashMap<>();
        private final Map<String, Instruction> instructions = new LinkedHashMap<>();
        private final List<LedgerEntry> ledger = new ArrayList<>();
        private long sequence;
        int statusQueries;

        FiduciaryService(FiduciaryGateway gateway, BigDecimal rate) {
            this.gateway = gateway;
            this.rate = rate;
        }

        String registerPayment(String negotiationId, String buyer, String seller, long amount) {
            if (negotiations.containsKey(negotiationId)) {
                return negotiationId;
            }
            Instruction collection = new Instruction(negotiationId + "-recaudo", negotiationId,
                    "comprador:" + buyer, "encargo:" + negotiationId, amount, InstructionState.PENDING);
            check(gateway.execute(collection) == InstructionState.CONFIRMED, "El recaudo no se confirmó");
            instructions.put(collection.reference(), collection.with(InstructionState.CONFIRMED));
            Distribution distribution = Distribution.of(amount, rate);
            distributions.put(negotiationId, distribution);
            negotiations.put(negotiationId, new Negotiation(negotiationId, buyer, seller, State.FUNDS_HELD, new HashSet<>()));
            append(negotiationId, collection.reference(), "RECAUDO_CONFIRMADO", amount);
            append(negotiationId, "", "DISTRIBUCION comisión=" + distribution.commission() + " neto=" + distribution.sellerNet(), 0);
            return negotiationId;
        }

        void confirmDelivery(String negotiationId, Party party) {
            Negotiation negotiation = negotiation(negotiationId);
            if (negotiation.confirmations().add(party)) {
                append(negotiationId, "", "ENTREGA_CONFIRMADA_" + (party == Party.BUYER ? "COMPRADOR" : "VENDEDOR"), 0);
            }
        }

        void release(String negotiationId) {
            Negotiation negotiation = negotiation(negotiationId);
            if (negotiation.state() == State.RELEASED) {
                return;
            }
            if (negotiation.state() != State.FUNDS_HELD && negotiation.state() != State.RELEASING) {
                throw new IllegalStateException("No se puede liberar en estado " + negotiation.state());
            }
            if (!negotiation.confirmations().containsAll(Set.of(Party.BUYER, Party.SELLER))) {
                throw new IllegalStateException("Falta confirmar la entrega");
            }
            Distribution distribution = distributions.get(negotiationId);
            if (negotiation.state() == State.FUNDS_HELD) {
                negotiations.put(negotiationId, negotiation.with(State.RELEASING));
                append(negotiationId, "", "LIBERACION_AUTORIZADA", distribution.amount());
            }
            boolean seller = run(instructions.computeIfAbsent(negotiationId + "-vendedor", reference -> new Instruction(reference,
                    negotiationId, "encargo:" + negotiationId, "vendedor:" + negotiation.seller(), distribution.sellerNet(), InstructionState.PENDING)));
            boolean commission = run(instructions.computeIfAbsent(negotiationId + "-comision", reference -> new Instruction(reference,
                    negotiationId, "encargo:" + negotiationId, Fiduciary.OPERATING, distribution.commission(), InstructionState.PENDING)));
            if (seller && commission) {
                negotiations.put(negotiationId, negotiation(negotiationId).with(State.RELEASED));
                append(negotiationId, "", "NEGOCIACION_LIBERADA", 0);
            }
        }

        void cancel(String negotiationId) {
            Negotiation negotiation = negotiation(negotiationId);
            if (negotiation.state() == State.REFUNDED) {
                return;
            }
            if (negotiation.state() != State.FUNDS_HELD && negotiation.state() != State.REFUNDING) {
                throw new IllegalStateException("No se puede devolver en estado " + negotiation.state());
            }
            refund(negotiationId);
        }

        void openDispute(String negotiationId) {
            Negotiation negotiation = negotiation(negotiationId);
            if (negotiation.state() != State.FUNDS_HELD) {
                throw new IllegalStateException("Solo hay desacuerdo con fondos en fiducia");
            }
            negotiations.put(negotiationId, negotiation.with(State.DISPUTED));
            append(negotiationId, "", "DESACUERDO_ABIERTO", 0);
        }

        void resolveDispute(String negotiationId, Party winner) {
            Negotiation negotiation = negotiation(negotiationId);
            if (negotiation.state() != State.DISPUTED) {
                throw new IllegalStateException("No hay desacuerdo abierto");
            }
            append(negotiationId, "", "DESACUERDO_RESUELTO_A_FAVOR_DEL_" + (winner == Party.BUYER ? "COMPRADOR" : "VENDEDOR"), 0);
            if (winner == Party.BUYER) {
                refund(negotiationId);
            } else {
                negotiation.confirmations().addAll(Set.of(Party.BUYER, Party.SELLER));
                negotiations.put(negotiationId, negotiation.with(State.FUNDS_HELD));
                release(negotiationId);
            }
        }

        private void refund(String negotiationId) {
            Negotiation negotiation = negotiation(negotiationId);
            Distribution distribution = distributions.get(negotiationId);
            if (negotiation.state() != State.REFUNDING) {
                negotiations.put(negotiationId, negotiation.with(State.REFUNDING));
                append(negotiationId, "", "DEVOLUCION_AUTORIZADA", distribution.amount());
            }
            boolean done = run(instructions.computeIfAbsent(negotiationId + "-devolucion", reference -> new Instruction(reference,
                    negotiationId, "encargo:" + negotiationId, "comprador:" + negotiation.buyer(), distribution.amount(), InstructionState.PENDING)));
            if (done) {
                negotiations.put(negotiationId, negotiation(negotiationId).with(State.REFUNDED));
                append(negotiationId, "", "NEGOCIACION_DEVUELTA", 0);
            }
        }

        /** Ejecuta una instrucción si hace falta. Una respuesta incierta se consulta antes de volver a ejecutar. */
        private boolean run(Instruction instruction) {
            if (instruction.state() == InstructionState.CONFIRMED) {
                return true;
            }
            InstructionState result;
            if (instruction.state() == InstructionState.UNCERTAIN) {
                statusQueries++;
                result = gateway.query(instruction.reference());
                if (result == InstructionState.FAILED || result == InstructionState.PENDING) {
                    result = gateway.execute(instruction);
                }
            } else {
                result = gateway.execute(instruction);
            }
            instructions.put(instruction.reference(), instruction.with(result));
            append(instruction.negotiationId(), instruction.reference(), "INSTRUCCION_" + result, instruction.amount());
            return result == InstructionState.CONFIRMED;
        }

        private void append(String negotiationId, String reference, String event, long amount) {
            ledger.add(new LedgerEntry(++sequence, negotiationId, reference, event, amount, Instant.now()));
        }

        private Negotiation negotiation(String id) {
            Negotiation negotiation = negotiations.get(id);
            if (negotiation == null) {
                throw new IllegalStateException("Negociación desconocida " + id);
            }
            return negotiation;
        }

        State state(String id) {
            return negotiation(id).state();
        }

        Distribution distribution(String id) {
            return distributions.get(id);
        }

        Instruction instruction(String reference) {
            return instructions.get(reference);
        }

        List<LedgerEntry> ledger() {
            return Collections.unmodifiableList(ledger);
        }

        List<LedgerEntry> timeline(String negotiationId) {
            return ledger.stream().filter(entry -> entry.negotiationId().equals(negotiationId)).toList();
        }

        /** Revisa en el registro que ninguna instrucción de liberación ocurra antes de las dos confirmaciones. */
        List<String> releasesBeforeCondition() {
            List<String> violations = new ArrayList<>();
            Map<String, Integer> confirmations = new HashMap<>();
            Set<String> disputeWonBySeller = new HashSet<>();
            for (LedgerEntry entry : ledger) {
                if (entry.event().startsWith("ENTREGA_CONFIRMADA")) {
                    confirmations.merge(entry.negotiationId(), 1, Integer::sum);
                }
                if (entry.event().equals("DESACUERDO_RESUELTO_A_FAVOR_DEL_VENDEDOR")) {
                    disputeWonBySeller.add(entry.negotiationId());
                }
                if (entry.event().equals("LIBERACION_AUTORIZADA")
                        && confirmations.getOrDefault(entry.negotiationId(), 0) < 2
                        && !disputeWonBySeller.contains(entry.negotiationId())) {
                    violations.add(entry.negotiationId() + " liberada sin las dos confirmaciones");
                }
            }
            return violations;
        }
    }

    // =========================================== fiducia simulada (Skandia + Cobre)

    /**
     * Libro de cuentas simulado. Ejecuta movimientos solo por instrucción y no deduplica: si recibe dos veces la
     * misma referencia la ejecuta dos veces, para que la protección dependa de DAZMA. Permite inyectar fallas y
     * respuestas perdidas (el movimiento se ejecuta pero DAZMA no recibe la confirmación).
     */
    static final class Fiduciary implements FiduciaryGateway {
        static final String OPERATING = "dazma:cuenta-operativa";
        private final Map<String, Long> balances = new HashMap<>();
        private final Map<String, Integer> executions = new HashMap<>();
        private final Map<String, Integer> pendingFailures = new HashMap<>();
        private final Set<String> pendingUncertain = new HashSet<>();
        int injectedFailures;
        int injectedUncertain;

        void failNext(String reference, int times) {
            pendingFailures.put(reference, times);
        }

        void uncertainNext(String reference) {
            pendingUncertain.add(reference);
        }

        @Override
        public InstructionState execute(Instruction instruction) {
            Integer failures = pendingFailures.get(instruction.reference());
            if (failures != null && failures > 0) {
                pendingFailures.put(instruction.reference(), failures - 1);
                injectedFailures++;
                return InstructionState.FAILED;
            }
            if (balance(instruction.from()) < instruction.amount() && instruction.from().startsWith("encargo:")) {
                return InstructionState.FAILED;
            }
            balances.merge(instruction.from(), -instruction.amount(), Long::sum);
            balances.merge(instruction.to(), instruction.amount(), Long::sum);
            executions.merge(instruction.reference(), 1, Integer::sum);
            if (pendingUncertain.remove(instruction.reference())) {
                injectedUncertain++;
                return InstructionState.UNCERTAIN;
            }
            return InstructionState.CONFIRMED;
        }

        @Override
        public InstructionState query(String reference) {
            return executions.getOrDefault(reference, 0) > 0 ? InstructionState.CONFIRMED : InstructionState.FAILED;
        }

        long balance(String account) {
            return balances.getOrDefault(account, 0L);
        }

        int executions(String reference) {
            return executions.getOrDefault(reference, 0);
        }

        List<String> duplicatedExecutions() {
            List<String> duplicated = new ArrayList<>();
            executions.forEach((reference, count) -> {
                if (count > 1) {
                    duplicated.add(reference + " ejecutada " + count + " veces");
                }
            });
            return duplicated;
        }
    }

    static final class World {
        final Fiduciary fiduciary = new Fiduciary();
        final FiduciaryService service;

        World(BigDecimal rate) {
            this.service = new FiduciaryService(fiduciary, rate);
        }
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
