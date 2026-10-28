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
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Prueba de concepto del ADR-08: Ably como proveedor del chat.
 *
 * Simula una negociación con comprador y vendedor. El "backend" (esta prueba) emite a cada participante
 * un token que solo sirve para la sala de su negociación, y se comprueba aislamiento entre salas,
 * identidad del remitente, historial, deduplicación, presencia, reconexión y latencia extremo a extremo.
 */
public class AblyChatPoc {

    private static final List<String> PARTICIPANT_RIGHTS = List.of("publish", "subscribe", "history", "presence");

    private final String apiKey;
    private final String restUrl;
    private final String realtimeUrl;
    private final String protocolVersion;
    private final String roomA;
    private final String roomB;
    private final String buyer = "comprador-" + UUID.randomUUID().toString().substring(0, 6);
    private final String seller = "vendedor-" + UUID.randomUUID().toString().substring(0, 6);
    private final String outsider = "ajeno-" + UUID.randomUUID().toString().substring(0, 6);

    AblyChatPoc() {
        this.apiKey = Env.get("ABLY_API_KEY");
        this.restUrl = Http.trimSlash(Env.orDefault("ABLY_REST_URL", "https://rest.ably.io"));
        this.realtimeUrl = Http.trimSlash(Env.orDefault("ABLY_REALTIME_URL", "wss://realtime.ably.io"));
        this.protocolVersion = Env.orDefault("ABLY_PROTOCOL_VERSION", "2");
        String prefix = Env.orDefault("ABLY_CHANNEL_PREFIX", "dazma-poc");
        String suffix = Env.orDefault("ABLY_CHANNEL_SUFFIX", "");
        String run = UUID.randomUUID().toString().substring(0, 8);
        this.roomA = prefix + ":negociacion-" + run + "-a" + suffix;
        this.roomB = prefix + ":negociacion-" + run + "-b" + suffix;
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: chat con Ably", "ADR-08");
        AblyChatPoc poc = new AblyChatPoc();
        report.context("Sala de la negociación", poc.roomA);
        report.context("Sala de otra negociación", poc.roomB);
        report.context("Versión de protocolo realtime", poc.protocolVersion);

        report.run("C1", "Participante publica en la sala de su negociación",
                "Con el token emitido por el backend, el comprador publica en su sala (HTTP 201)",
                poc::participantPublishes);
        report.run("C2", "Aislamiento entre negociaciones",
                "El 100 % de los intentos sobre una sala ajena (publicar, leer historial) es rechazado (ESC-CAL-SEG-0004)",
                poc::roomsAreIsolated);
        report.run("C3", "Identidad del remitente",
                "Ably rechaza que un participante publique a nombre de otro y registra el clientId del token",
                poc::senderIdentityEnforced);
        report.run("C4", "Historial de la conversación",
                "El historial devuelve todos los mensajes de la sala en orden y con su remitente",
                poc::historyInOrder);
        report.run("C5", "Deduplicación de mensajes",
                "Publicar dos veces el mismo id deja un solo mensaje en el historial",
                poc::duplicatePublishIsIgnored);
        report.run("C6", "Entrega en tiempo real ≤ 2 s",
                "Todos los mensajes llegan al otro participante por WebSocket en el umbral configurado (ESC-CAL-REN-0010)",
                poc::realtimeLatency);
        report.run("C7", "Presencia del otro participante",
                "Cuando el vendedor está conectado, la sala lo reporta como presente",
                poc::presence);
        report.run("C8", "Reconexión sin perder mensajes",
                "Los mensajes enviados mientras un participante estaba desconectado se recuperan al reconectarse",
                poc::reconnectRecoversMessages);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String participantPublishes() throws Exception {
        Http.Response response = publish(token(buyer, roomA), roomA, Map.of("name", "chat.message", "data", "Hola, ¿sigue disponible?"));
        check(response.status() == 201, "Publicación rechazada: " + response.brief());
        return "HTTP 201 en " + response.millis() + " ms";
    }

    String roomsAreIsolated() throws Exception {
        String outsiderToken = token(outsider, roomB);
        String buyerToken = token(buyer, roomA);
        List<String> attempts = new ArrayList<>();
        int rejected = 0;

        Http.Response p1 = publish(outsiderToken, roomA, Map.of("name", "chat.message", "data", "intrusión"));
        attempts.add("ajeno publica en sala A: HTTP " + p1.status() + code(p1));
        rejected += p1.status() >= 400 ? 1 : 0;

        Http.Response h1 = history(outsiderToken, roomA);
        attempts.add("ajeno lee historial de sala A: HTTP " + h1.status() + code(h1));
        rejected += h1.status() >= 400 ? 1 : 0;

        Http.Response p2 = publish(buyerToken, roomB, Map.of("name", "chat.message", "data", "sala equivocada"));
        attempts.add("comprador publica en sala B: HTTP " + p2.status() + code(p2));
        rejected += p2.status() >= 400 ? 1 : 0;

        check(rejected == 3, rejected + "/3 intentos rechazados. " + String.join("; ", attempts));
        return "3/3 rechazados. " + String.join("; ", attempts);
    }

    String senderIdentityEnforced() throws Exception {
        Map<String, Object> impersonation = new LinkedHashMap<>();
        impersonation.put("name", "chat.message");
        impersonation.put("data", "mensaje suplantado");
        impersonation.put("clientId", seller);
        Http.Response response = publish(token(buyer, roomA), roomA, impersonation);
        check(response.status() >= 400, "Ably aceptó un mensaje con clientId de otra persona: " + response.brief());

        Http.Response history = history(token(seller, roomA), roomA);
        check(history.status() == 200, "No se pudo leer el historial: " + history.brief());
        List<Object> messages = list(history.body());
        boolean buyerTagged = messages.stream().anyMatch(m -> buyer.equals(Json.str(m, "clientId")));
        boolean impersonated = messages.stream().anyMatch(m -> "mensaje suplantado".equals(Json.str(m, "data")));
        check(buyerTagged, "Los mensajes del comprador no llevan su clientId");
        check(!impersonated, "El mensaje suplantado quedó en el historial");
        return "Suplantación rechazada (HTTP " + response.status() + code(response) + "); mensajes del comprador llevan clientId=" + buyer;
    }

    String historyInOrder() throws Exception {
        String buyerToken = token(buyer, roomA);
        String sellerToken = token(seller, roomA);
        List<String> sent = List.of("Ofrezco 350.000", "Puedo en 380.000", "Trato hecho");
        check(publish(buyerToken, roomA, Map.of("name", "chat.message", "data", sent.get(0))).status() == 201, "falló el envío 1");
        check(publish(sellerToken, roomA, Map.of("name", "chat.message", "data", sent.get(1))).status() == 201, "falló el envío 2");
        check(publish(buyerToken, roomA, Map.of("name", "chat.message", "data", sent.get(2))).status() == 201, "falló el envío 3");

        Http.Response response = history(sellerToken, roomA);
        check(response.status() == 200, "No se pudo leer el historial: " + response.brief());
        List<String> received = new ArrayList<>();
        for (Object message : list(response.body())) {
            String data = Json.str(message, "data");
            if (sent.contains(data)) {
                received.add(data);
            }
        }
        check(received.equals(sent), "Orden recibido " + received + " distinto al enviado " + sent);
        return "3 mensajes en orden, consulta de historial en " + response.millis() + " ms";
    }

    String duplicatePublishIsIgnored() throws Exception {
        String buyerToken = token(buyer, roomA);
        String messageId = "dedup-" + UUID.randomUUID();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", messageId);
        message.put("name", "chat.message");
        message.put("data", "Mensaje enviado dos veces por reintento");
        Http.Response first = publish(buyerToken, roomA, message);
        Http.Response second = publish(buyerToken, roomA, message);
        check(first.status() == 201, "Primer envío rechazado: " + first.brief());

        Http.Response response = history(buyerToken, roomA);
        long copies = list(response.body()).stream().filter(m -> messageId.equals(Json.str(m, "id"))).count();
        check(copies == 1, "El historial tiene " + copies + " copias del mismo id");
        return "Dos envíos (HTTP " + first.status() + " y " + second.status() + "), 1 copia en el historial";
    }

    String realtimeLatency() throws Exception {
        int messages = Env.intOrDefault("ABLY_LATENCY_MESSAGES", 20);
        long threshold = Env.intOrDefault("ABLY_MAX_LATENCY_MS", 2000);
        String buyerToken = token(buyer, roomA);
        try (Realtime sellerConnection = Realtime.connect(realtimeUrl, protocolVersion, token(seller, roomA))) {
            sellerConnection.attach(roomA, null);
            Map<String, Long> sentAt = new LinkedHashMap<>();
            List<Long> latencies = new ArrayList<>();
            for (int i = 0; i < messages; i++) {
                String id = "lat-" + UUID.randomUUID();
                sentAt.put(id, System.nanoTime());
                Http.Response response = publish(buyerToken, roomA, Map.of("id", id, "name", "chat.message", "data", "latencia " + i));
                check(response.status() == 201, "Publicación " + i + " rechazada: " + response.brief());
                Thread.sleep(150);
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            String senderSeen = null;
            while (latencies.size() < messages && System.nanoTime() < deadline) {
                Map<String, Object> message = sellerConnection.nextMessage(roomA, 1000);
                if (message == null) {
                    continue;
                }
                Long start = sentAt.remove(Json.str(message, "id"));
                if (start != null) {
                    long receivedAt = ((Number) message.get("_receivedAt")).longValue();
                    latencies.add((receivedAt - start) / 1_000_000);
                    senderSeen = Json.str(message, "clientId");
                }
            }
            check(latencies.size() == messages, "Llegaron " + latencies.size() + " de " + messages + " mensajes");
            long over = latencies.stream().filter(l -> l > threshold).count();
            check(over == 0, over + " mensajes superaron " + threshold + " ms. " + Stats.summary(latencies));
            return messages + "/" + messages + " recibidos por el vendedor (remitente " + senderSeen + "). " + Stats.summary(latencies);
        }
    }

    String presence() throws Exception {
        try (Realtime sellerConnection = Realtime.connect(realtimeUrl, protocolVersion, token(seller, roomA))) {
            sellerConnection.attach(roomA, null);
            sellerConnection.enterPresence(roomA, "en línea");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                Http.Response response = Http.get(restUrl + "/channels/" + Http.encode(roomA) + "/presence",
                        "Authorization", bearer(token(buyer, roomA)));
                check(response.status() == 200, "Consulta de presencia rechazada: " + response.brief());
                boolean present = list(response.body()).stream().anyMatch(m -> seller.equals(Json.str(m, "clientId")));
                if (present) {
                    return "El comprador ve al vendedor presente (" + response.millis() + " ms)";
                }
                Thread.sleep(500);
            }
            throw new CheckFailed("El vendedor no apareció como presente en 10 s");
        }
    }

    String reconnectRecoversMessages() throws Exception {
        String buyerToken = token(buyer, roomA);
        String sellerToken = token(seller, roomA);
        try (Realtime first = Realtime.connect(realtimeUrl, protocolVersion, sellerToken)) {
            first.attach(roomA, null);
        }
        List<String> missed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String id = "offline-" + UUID.randomUUID();
            missed.add(id);
            check(publish(buyerToken, roomA, Map.of("id", id, "name", "chat.message", "data", "enviado sin conexión " + i)).status() == 201,
                    "falló un envío durante la desconexión");
        }

        List<String> recovered = new ArrayList<>();
        try (Realtime second = Realtime.connect(realtimeUrl, protocolVersion, sellerToken)) {
            second.attach(roomA, Map.of("rewind", "2m"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (recovered.size() < missed.size() && System.nanoTime() < deadline) {
                Map<String, Object> message = second.nextMessage(roomA, 500);
                if (message != null && missed.contains(Json.str(message, "id")) && !recovered.contains(Json.str(message, "id"))) {
                    recovered.add(Json.str(message, "id"));
                }
            }
        }
        if (recovered.size() == missed.size()) {
            return "3/3 mensajes entregados al reconectar (parámetro rewind del canal)";
        }
        Http.Response response = history(sellerToken, roomA);
        long inHistory = list(response.body()).stream().filter(m -> missed.contains(Json.str(m, "id"))).count();
        check(inHistory == missed.size(), "Al reconectar se recuperaron " + recovered.size() + " por tiempo real y "
                + inHistory + " por historial, de " + missed.size());
        return recovered.size() + "/3 por tiempo real (rewind) y 3/3 recuperables desde el historial al reconectar";
    }

    // ------------------------------------------------------------ backend

    /** Token JWT de Ably firmado con la clave del backend: solo sirve para una sala y un clientId. */
    String token(String clientId, String room) throws Exception {
        if (apiKey == null) {
            throw new Skip("falta la variable de entorno ABLY_API_KEY");
        }
        int separator = apiKey.indexOf(':');
        check(separator > 0, "ABLY_API_KEY debe tener la forma appId.keyId:secreto");
        String keyName = apiKey.substring(0, separator);
        String secret = apiKey.substring(separator + 1);
        long now = Instant.now().getEpochSecond();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "HS256");
        header.put("kid", keyName);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iat", now);
        claims.put("exp", now + 3600);
        claims.put("x-ably-capability", Json.write(Map.of(room, PARTICIPANT_RIGHTS)));
        claims.put("x-ably-clientId", clientId);
        String unsigned = base64Url(Json.write(header)) + "." + base64Url(Json.write(claims));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
    }

    private Http.Response publish(String token, String room, Map<String, Object> message) throws Exception {
        return Http.postJson(restUrl + "/channels/" + Http.encode(room) + "/messages", message, "Authorization", bearer(token));
    }

    private Http.Response history(String token, String room) throws Exception {
        return Http.get(restUrl + "/channels/" + Http.encode(room) + "/messages?direction=forwards&limit=100",
                "Authorization", bearer(token));
    }

    private static String bearer(String token) {
        return "Bearer " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private static String code(Http.Response response) {
        try {
            Object code = Json.at(response.json(), "error.code");
            return code == null ? "" : " (código " + ((Number) code).longValue() + ")";
        } catch (RuntimeException notJson) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(String body) {
        Object parsed = Json.parse(body);
        return parsed instanceof List<?> items ? (List<Object>) items : List.of();
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------- cliente realtime mínimo

    /** Conexión realtime de Ably sobre WebSocket con el protocolo JSON. */
    static final class Realtime implements AutoCloseable {
        private static final int CONNECTED = 4;
        private static final int ERROR = 9;
        private static final int ATTACH = 10;
        private static final int ATTACHED = 11;
        private static final int PRESENCE = 14;
        private static final int MESSAGE = 15;
        private static final int PRESENCE_ENTER = 2;

        private final BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        private final List<Map<String, Object>> pendingMessages = new ArrayList<>();
        private WebSocket socket;
        private long msgSerial;

        static Realtime connect(String baseUrl, String version, String token) throws Exception {
            Realtime realtime = new Realtime();
            String url = baseUrl + "/?access_token=" + Http.encode(token) + "&format=json&heartbeats=true&v=" + Http.encode(version);
            realtime.socket = Http.CLIENT.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(url), realtime.listener())
                    .get(15, TimeUnit.SECONDS);
            realtime.await(CONNECTED, null, 10_000);
            return realtime;
        }

        void attach(String channel, Map<String, String> params) throws Exception {
            Map<String, Object> attach = new LinkedHashMap<>();
            attach.put("action", ATTACH);
            attach.put("channel", channel);
            if (params != null) {
                attach.put("params", params);
            }
            send(attach);
            await(ATTACHED, channel, 10_000);
        }

        void enterPresence(String channel, String data) throws Exception {
            Map<String, Object> presence = new LinkedHashMap<>();
            presence.put("action", PRESENCE);
            presence.put("channel", channel);
            presence.put("msgSerial", msgSerial++);
            presence.put("presence", List.of(Map.of("action", PRESENCE_ENTER, "data", data)));
            send(presence);
        }

        /** Devuelve el siguiente mensaje de chat recibido en el canal, con _receivedAt en nanosegundos. */
        Map<String, Object> nextMessage(String channel, long timeoutMillis) throws Exception {
            if (!pendingMessages.isEmpty()) {
                return pendingMessages.remove(0);
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (System.nanoTime() < deadline) {
                Map<String, Object> frame = inbox.poll(Math.max(1, (deadline - System.nanoTime()) / 1_000_000), TimeUnit.MILLISECONDS);
                if (frame == null) {
                    return null;
                }
                queueMessages(frame, channel);
                if (!pendingMessages.isEmpty()) {
                    return pendingMessages.remove(0);
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private void queueMessages(Map<String, Object> frame, String channel) {
            if (number(frame.get("action")) == MESSAGE && channel.equals(frame.get("channel"))) {
                Object messages = frame.get("messages");
                if (messages instanceof List<?> list) {
                    for (Object item : list) {
                        Map<String, Object> message = new LinkedHashMap<>((Map<String, Object>) item);
                        message.put("_receivedAt", frame.get("_receivedAt"));
                        pendingMessages.add(message);
                    }
                }
            }
            if (number(frame.get("action")) == ERROR) {
                throw new CheckFailed("Error del servidor realtime: " + Json.write(frame.get("error")));
            }
        }

        private void await(int action, String channel, long timeoutMillis) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (System.nanoTime() < deadline) {
                Map<String, Object> frame = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }
                int received = number(frame.get("action"));
                if (received == ERROR || (frame.get("error") != null && received == action)) {
                    throw new CheckFailed("El servidor realtime respondió error: " + Json.write(frame.get("error")));
                }
                if (received == action && (channel == null || channel.equals(frame.get("channel")))) {
                    return;
                }
                queueMessages(frame, channel == null ? "" : channel);
            }
            throw new CheckFailed("No llegó la acción " + action + " del protocolo realtime en " + timeoutMillis + " ms");
        }

        private void send(Map<String, Object> message) throws Exception {
            socket.sendText(Json.write(message), true).get(10, TimeUnit.SECONDS);
        }

        private WebSocket.Listener listener() {
            return new WebSocket.Listener() {
                private final StringBuilder buffer = new StringBuilder();

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    buffer.append(data);
                    if (last) {
                        long receivedAt = System.nanoTime();
                        Map<String, Object> frame = Json.object(buffer.toString());
                        frame.put("_receivedAt", receivedAt);
                        inbox.add(frame);
                        buffer.setLength(0);
                    }
                    webSocket.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    return CompletableFuture.completedFuture(null);
                }
            };
        }

        private static int number(Object value) {
            return value instanceof Number number ? number.intValue() : -1;
        }

        @Override
        public void close() {
            if (socket != null) {
                socket.abort();
            }
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
