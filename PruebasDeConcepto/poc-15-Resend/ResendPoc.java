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
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Prueba de concepto del ADR-15: Resend como notification gateway.
 *
 * Valida contra la API real lo que el ADR exige: envío con clave de idempotencia, reintentos sin duplicados,
 * estado consultable que distingue entregado de rebotado, clasificación de errores para Retry y Circuit Breaker,
 * y webhooks firmados. Usa las direcciones de prueba de Resend, que simulan entrega y rebote sin enviar correo real.
 */
public class ResendPoc {

    private static final long WEBHOOK_TOLERANCE_SECONDS = 5 * 60;

    private final String apiUrl;
    private final String apiKey;
    private final String from;
    private final String deliveredAddress;
    private final String bouncedAddress;
    private final String webhookSecret;
    private String deliveredEmailId;
    private String bouncedEmailId;
    private String idempotencyKey;
    private WebhookReceiver receiver;

    ResendPoc() {
        this.apiUrl = Http.trimSlash(Env.orDefault("RESEND_API_URL", "https://api.resend.com"));
        this.apiKey = Env.get("RESEND_API_KEY");
        this.from = Env.get("RESEND_FROM");
        this.deliveredAddress = Env.orDefault("RESEND_TEST_DELIVERED", "delivered@resend.dev");
        this.bouncedAddress = Env.orDefault("RESEND_TEST_BOUNCED", "bounced@resend.dev");
        this.webhookSecret = Env.get("RESEND_WEBHOOK_SECRET");
    }

    public static void main(String[] args) throws Exception {
        Report report = new Report("Prueba de concepto: Resend", "ADR-15");
        ResendPoc poc = new ResendPoc();
        report.context("Remitente", poc.from);
        report.context("Destino entregado", poc.deliveredAddress);
        report.context("Destino rebotado", poc.bouncedAddress);
        if (Env.flag("RESEND_WEBHOOK_LISTEN") && poc.webhookSecret != null) {
            poc.receiver = WebhookReceiver.start(Env.intOrDefault("RESEND_WEBHOOK_PORT", 8089), poc.webhookSecret);
            report.context("Receptor de webhooks", "puerto " + Env.intOrDefault("RESEND_WEBHOOK_PORT", 8089));
        }

        report.run("C1", "Envío con clave de idempotencia",
                "La API acepta el correo y devuelve su identificador",
                poc::sendWithIdempotencyKey);
        report.run("C2", "Reintento con la misma clave",
                "Repetir el envío con la misma clave devuelve el mismo identificador y no crea otro correo",
                poc::retryReturnsSameEmail);
        report.run("C3", "Misma clave con contenido distinto",
                "La API rechaza la petición (409) en vez de enviar un correo diferente",
                poc::conflictingRetryRejected);
        report.run("C4", "Estado de entrega consultable",
                "El correo enviado a la dirección de prueba llega al estado delivered",
                poc::deliveredStatus);
        report.run("C5", "Rebote distinguible de entrega",
                "El correo a la dirección de rebote termina en bounced, no en delivered",
                poc::bouncedStatus);
        report.run("C6", "Errores no reintentables",
                "Datos inválidos y credencial inválida responden 4xx; con la regla del gateway (429 o 5xx) no se reintentan",
                poc::clientErrorsAreNotRetryable);
        report.run("C7", "Verificación de firma de webhooks",
                "Se acepta la firma de referencia de Svix y se rechazan un cuerpo alterado y una marca de tiempo vieja",
                ResendPoc::webhookSignatureOffline);
        report.run("C8", "Webhooks reales de la cuenta",
                "Llegan eventos firmados de los correos de esta ejecución; un evento repetido se procesa una sola vez",
                poc::realWebhooks);
        if (poc.receiver != null) {
            poc.receiver.stop();
        }
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String sendWithIdempotencyKey() throws Exception {
        requireApi();
        idempotencyKey = "dazma-poc/" + UUID.randomUUID();
        Http.Response response = send(email(deliveredAddress, "Tu publicación fue creada"), idempotencyKey, apiKey);
        check(response.status() == 200, "El envío respondió " + response.brief());
        deliveredEmailId = Json.str(response.json(), "id");
        check(deliveredEmailId != null, "La respuesta no trae id");
        return "id=" + deliveredEmailId + " en " + response.millis() + " ms" + limits(response);
    }

    String retryReturnsSameEmail() throws Exception {
        requireSent();
        pause();
        Http.Response response = send(email(deliveredAddress, "Tu publicación fue creada"), idempotencyKey, apiKey);
        check(response.status() == 200, "El reintento respondió " + response.brief());
        String id = Json.str(response.json(), "id");
        check(deliveredEmailId.equals(id), "El reintento creó otro correo: " + id);
        return "Mismo id " + id;
    }

    String conflictingRetryRejected() throws Exception {
        requireSent();
        pause();
        Http.Response response = send(email(deliveredAddress, "Asunto distinto"), idempotencyKey, apiKey);
        check(response.status() == 409, "Se esperaba 409 y llegó " + response.brief());
        return "HTTP 409 " + errorName(response);
    }

    String deliveredStatus() throws Exception {
        requireSent();
        return waitForEvent(deliveredEmailId, "delivered");
    }

    String bouncedStatus() throws Exception {
        requireApi();
        pause();
        Http.Response response = send(email(bouncedAddress, "Prueba de rebote"), "dazma-poc/" + UUID.randomUUID(), apiKey);
        check(response.status() == 200, "El envío respondió " + response.brief());
        bouncedEmailId = Json.str(response.json(), "id");
        return waitForEvent(bouncedEmailId, "bounced");
    }

    String clientErrorsAreNotRetryable() throws Exception {
        requireApi();
        pause();
        Http.Response invalidRecipient = send(email("esto-no-es-un-correo", "Dato inválido"), "dazma-poc/" + UUID.randomUUID(), apiKey);
        pause();
        Http.Response invalidKey = send(email(deliveredAddress, "Credencial inválida"), "dazma-poc/" + UUID.randomUUID(), "re_clave_invalida");
        List<String> details = List.of(
                "destinatario inválido → HTTP " + invalidRecipient.status() + " " + errorName(invalidRecipient)
                        + ", reintentable=" + isRetryable(invalidRecipient.status()),
                "credencial inválida → HTTP " + invalidKey.status() + " " + errorName(invalidKey)
                        + ", reintentable=" + isRetryable(invalidKey.status()));
        check(invalidRecipient.status() >= 400 && invalidRecipient.status() < 500 && !isRetryable(invalidRecipient.status()),
                details.get(0));
        check(invalidKey.status() >= 400 && invalidKey.status() < 500 && !isRetryable(invalidKey.status()), details.get(1));
        return String.join("; ", details);
    }

    static String webhookSignatureOffline() {
        // Ejemplo publicado en la documentación de Svix para verificar implementaciones; no es un secreto real.
        String secret = "whsec_" + "MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
        String id = "msg_p5jXN8AQM9LWM0D4loKWxJek";
        String timestamp = "1614265330";
        String body = "{\"test\": 2432232314}";
        String header = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";
        long reference = Long.parseLong(timestamp);

        check(verifySignature(secret, id, timestamp, body, header, reference),
                "La firma de referencia publicada por Svix no se validó");
        check(!verifySignature(secret, id, timestamp, body.replace("2432232314", "2432232315"), header, reference),
                "Se aceptó un cuerpo alterado");
        check(!verifySignature(secret, id, timestamp, body, header, reference + WEBHOOK_TOLERANCE_SECONDS + 1),
                "Se aceptó una marca de tiempo fuera de la tolerancia de 5 minutos");
        check(!verifySignature("whsec_" + Base64.getEncoder().encodeToString("otro-secreto".getBytes(StandardCharsets.UTF_8)),
                id, timestamp, body, header, reference), "Se aceptó la firma con otro secreto");
        return "Firma de referencia aceptada; cuerpo alterado, marca vieja y secreto distinto rechazados";
    }

    String realWebhooks() throws Exception {
        if (receiver == null) {
            throw new Skip("se activa con RESEND_WEBHOOK_LISTEN=true y RESEND_WEBHOOK_SECRET, con el puerto expuesto y registrado en Resend");
        }
        requireSent();
        long deadline = System.nanoTime() + Duration.ofSeconds(Env.intOrDefault("RESEND_WEBHOOK_WAIT_SECONDS", 90)).toNanos();
        Set<String> wanted = bouncedEmailId == null ? Set.of(deliveredEmailId) : Set.of(deliveredEmailId, bouncedEmailId);
        List<String> wantedTypes = bouncedEmailId == null ? List.of("email.delivered") : List.of("email.delivered", "email.bounced");
        while (System.nanoTime() < deadline && !receiver.eventsFor(wanted).containsAll(wantedTypes)) {
            Thread.sleep(1000);
        }
        List<String> events = receiver.describe(wanted);
        check(!events.isEmpty(), "No llegaron eventos de los correos de esta ejecución. Rechazados por firma: " + receiver.rejected.size());
        check(receiver.rejected.isEmpty(), receiver.rejected.size() + " eventos con firma inválida");
        return String.join("; ", events) + ". Duplicados ignorados: " + receiver.duplicates.get();
    }

    // ------------------------------------------------------------- utilidades

    private Map<String, Object> email(String to, String subject) {
        Map<String, Object> email = new LinkedHashMap<>();
        email.put("from", from);
        email.put("to", List.of(to));
        email.put("subject", "[Prueba DAZMA] " + subject);
        email.put("html", "<p>Correo de prueba de concepto del ADR-15.</p>");
        return email;
    }

    private Http.Response send(Map<String, Object> email, String key, String credential) throws Exception {
        return Http.postJson(apiUrl + "/emails", email,
                "Authorization", "Bearer " + credential,
                "Idempotency-Key", key);
    }

    private String waitForEvent(String id, String expected) throws Exception {
        long start = System.nanoTime();
        long deadline = start + Duration.ofSeconds(Env.intOrDefault("RESEND_STATUS_WAIT_SECONDS", 60)).toNanos();
        List<String> seen = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            Http.Response response = Http.get(apiUrl + "/emails/" + id, "Authorization", "Bearer " + apiKey);
            check(response.status() == 200, "La consulta respondió " + response.brief());
            String event = Json.str(response.json(), "last_event");
            if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(event)) {
                seen.add(event);
            }
            if (expected.equals(event)) {
                long seconds = (System.nanoTime() - start) / 1_000_000_000;
                return "Estados observados: " + String.join(" → ", seen) + " (" + seconds + " s)";
            }
            if ("delivered".equals(expected) ? "bounced".equals(event) : "delivered".equals(event)) {
                throw new CheckFailed("Terminó en " + event + " y se esperaba " + expected);
            }
            Thread.sleep(2000);
        }
        throw new CheckFailed("No llegó a " + expected + "; estados observados: " + seen);
    }

    /** Misma regla que ResendNotificationGateway.isRetryable. */
    static boolean isRetryable(Integer statusCode) {
        return statusCode == null || statusCode == 429 || statusCode >= 500;
    }

    static boolean verifySignature(String secret, String id, String timestamp, String body, String header, long nowSeconds) {
        try {
            long sentAt = Long.parseLong(timestamp);
            if (Math.abs(nowSeconds - sentAt) > WEBHOOK_TOLERANCE_SECONDS) {
                return false;
            }
            byte[] key = Base64.getDecoder().decode(secret.startsWith("whsec_") ? secret.substring(6) : secret);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            for (String candidate : header.split(" ")) {
                int comma = candidate.indexOf(',');
                if (comma > 0 && candidate.startsWith("v1")
                        && MessageDigest.isEqual(expected, Base64.getDecoder().decode(candidate.substring(comma + 1)))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException | java.security.GeneralSecurityException exception) {
            return false;
        }
    }

    private static String errorName(Http.Response response) {
        try {
            String name = Json.str(response.json(), "name");
            return name == null ? "" : "(" + name + ")";
        } catch (RuntimeException notJson) {
            return "";
        }
    }

    private static String limits(Http.Response response) {
        List<String> values = new ArrayList<>();
        response.headers().map().forEach((name, value) -> {
            String lower = name.toLowerCase();
            if (lower.startsWith("ratelimit") || lower.startsWith("x-ratelimit") || lower.contains("quota")) {
                values.add(lower + "=" + String.join(",", value));
            }
        });
        return values.isEmpty() ? "" : "; límites informados: " + String.join(", ", values);
    }

    /** Resend limita las peticiones por segundo; se espera un poco entre llamadas. */
    private static void pause() throws InterruptedException {
        Thread.sleep(Env.intOrDefault("RESEND_PAUSE_MS", 700));
    }

    private void requireApi() {
        Env.requireAll("RESEND_API_KEY", "RESEND_FROM");
    }

    private void requireSent() {
        requireApi();
        if (deliveredEmailId == null) {
            throw new Skip("no hay correo enviado (C1 no se completó)");
        }
    }

    /** Receptor local de webhooks: valida la firma y descarta eventos repetidos por svix-id. */
    static final class WebhookReceiver {
        final Map<String, Map<String, Object>> processed = new ConcurrentHashMap<>();
        final List<String> rejected = new CopyOnWriteArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger duplicates = new java.util.concurrent.atomic.AtomicInteger();
        private HttpServer server;

        static WebhookReceiver start(int port, String secret) throws IOException {
            WebhookReceiver receiver = new WebhookReceiver();
            receiver.server = HttpServer.create(new InetSocketAddress(port), 0);
            receiver.server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String id = exchange.getRequestHeaders().getFirst("svix-id");
                String timestamp = exchange.getRequestHeaders().getFirst("svix-timestamp");
                String signature = exchange.getRequestHeaders().getFirst("svix-signature");
                int status;
                if (id == null || timestamp == null || signature == null
                        || !verifySignature(secret, id, timestamp, body, signature, Instant.now().getEpochSecond())) {
                    receiver.rejected.add(String.valueOf(id));
                    status = 401;
                } else if (receiver.processed.putIfAbsent(id, Json.object(body)) != null) {
                    receiver.duplicates.incrementAndGet();
                    status = 200;
                } else {
                    status = 200;
                }
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            receiver.server.start();
            return receiver;
        }

        List<String> eventsFor(Set<String> emailIds) {
            List<String> types = new ArrayList<>();
            processed.values().forEach(event -> {
                if (emailIds.contains(Json.str(event, "data.email_id"))) {
                    types.add(Json.str(event, "type"));
                }
            });
            return types;
        }

        List<String> describe(Set<String> emailIds) {
            List<String> lines = new ArrayList<>();
            processed.values().forEach(event -> {
                if (emailIds.contains(Json.str(event, "data.email_id"))) {
                    lines.add(Json.str(event, "type") + " de " + Json.str(event, "data.email_id"));
                }
            });
            Collections.sort(lines);
            return lines;
        }

        void stop() {
            server.stop(0);
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
