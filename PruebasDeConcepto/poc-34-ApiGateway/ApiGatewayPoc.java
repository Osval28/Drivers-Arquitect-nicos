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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Prueba de concepto del ADR-34: Amazon API Gateway (HTTP API) como punto de entrada de la API.
 *
 * Comprueba contra el gateway desplegado que enruta hacia el backend externo, que responde él mismo a rutas no
 * declaradas, que las rutas versionadas llegan al backend, el límite de 10 MB, el control de solicitudes, cuánta
 * latencia agrega frente a llamar al backend directo, y que su configuración (OpenAPI, límites y registros) se puede
 * leer desde la API de AWS.
 */
public class ApiGatewayPoc {

    private final String gatewayUrl;
    private final String backendUrl;
    private final String healthPath;
    private final String region;
    private final String apiId;
    private final SigV4.Credentials credentials;

    ApiGatewayPoc() {
        String gateway = Env.get("GATEWAY_BASE_URL");
        this.gatewayUrl = gateway == null ? null : Http.trimSlash(gateway);
        String backend = Env.get("BACKEND_DIRECT_URL");
        this.backendUrl = backend == null ? null : Http.trimSlash(backend);
        this.healthPath = Env.orDefault("GATEWAY_HEALTH_PATH", "/actuator/health");
        this.region = Env.get("AWS_REGION");
        this.apiId = Env.get("GATEWAY_API_ID");
        String key = Env.get("AWS_ACCESS_KEY_ID");
        String secret = Env.get("AWS_SECRET_ACCESS_KEY");
        this.credentials = key == null || secret == null ? null : new SigV4.Credentials(key, secret, Env.get("AWS_SESSION_TOKEN"));
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: Amazon API Gateway", "ADR-34");
        ApiGatewayPoc poc = new ApiGatewayPoc();
        report.context("Gateway", poc.gatewayUrl);
        report.context("Backend directo", poc.backendUrl);

        report.run("C1", "Entrada única hacia un backend fuera de AWS",
                "La ruta de salud responde 200 a través del gateway y la respuesta trae apigw-requestid",
                poc::routesToExternalBackend);
        report.run("C2", "Rutas no declaradas",
                "Una ruta que no existe la responde el propio gateway con 404 {\"message\":\"Not Found\"} sin llegar al backend",
                poc::undeclaredRoutesStopAtGateway);
        report.run("C3", "Rutas versionadas",
                "Cada ruta /{contexto}/api/v1/... configurada llega al backend (la respuesta no es el 404 del gateway)",
                poc::versionedRoutesReachBackend);
        report.run("C4", "Límite de 10 MB por solicitud",
                "Una solicitud de 10,5 MB la rechaza el gateway con 413",
                poc::payloadLimit);
        report.run("C5", "Control de avalanchas de solicitudes",
                "Una ráfaga por encima del límite configurado recibe 429 del gateway y ninguna respuesta 5xx (ADR-04)",
                poc::throttling);
        report.run("C6", "Latencia agregada por el gateway",
                "La diferencia de p50 entre pasar por el gateway y llamar directo al backend es menor o igual al umbral",
                poc::addedLatency);
        report.run("C7", "Contrato OpenAPI",
                "La API exporta su definición OpenAPI 3 y contiene las rutas versionadas configuradas",
                poc::openApiExport);
        report.run("C8", "Límites y registros configurados",
                "La etapa tiene límites de solicitudes y registros de acceso activos",
                poc::stageSettings);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String routesToExternalBackend() throws Exception {
        requireGateway();
        Http.Response response = Http.get(gatewayUrl + healthPath);
        check(response.status() == 200, "La ruta de salud respondió " + response.brief());
        String requestId = response.header("apigw-requestid");
        check(requestId != null, "La respuesta no trae apigw-requestid: no pasó por un HTTP API de API Gateway");
        return "HTTP 200 en " + response.millis() + " ms, apigw-requestid=" + requestId;
    }

    String undeclaredRoutesStopAtGateway() throws Exception {
        requireGateway();
        String path = Env.orDefault("GATEWAY_UNKNOWN_PATH", "/poc-no-existe-" + UUID.randomUUID().toString().substring(0, 6) + "/api/v9/recurso");
        Http.Response response = Http.get(gatewayUrl + path);
        check(response.status() == 404, "Se esperaba 404 y llegó " + response.brief());
        check(isGatewayNotFound(response), "El 404 no lo generó el gateway: " + response.brief());
        return "HTTP 404 " + response.body().trim() + " (apigw-requestid presente)";
    }

    String versionedRoutesReachBackend() throws Exception {
        requireGateway();
        String paths = Env.get("GATEWAY_VERSIONED_PATHS");
        if (paths == null) {
            throw new Skip("falta GATEWAY_VERSIONED_PATHS, por ejemplo /publications/api/v1/publications");
        }
        List<String> lines = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (String path : paths.split(",")) {
            Http.Response response = Http.get(gatewayUrl + path.trim());
            lines.add(path.trim() + " → " + response.status());
            if (isGatewayNotFound(response) || response.status() >= 500) {
                blocked.add(path.trim() + " → " + response.brief());
            }
        }
        check(blocked.isEmpty(), "No llegaron al backend: " + blocked);
        return String.join("; ", lines) + " (respuestas del backend)";
    }

    String payloadLimit() throws Exception {
        requireGateway();
        String path = Env.orDefault("GATEWAY_PAYLOAD_PATH", healthPath);
        byte[] body = new byte[10 * 1024 * 1024 + 512 * 1024];
        java.util.Arrays.fill(body, (byte) 'a');
        Http.Response response = Http.send(Http.to(gatewayUrl + path)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
        check(response.status() == 413, "Se esperaba 413 y llegó " + response.brief());
        return "10,5 MB → HTTP 413 " + response.body().trim();
    }

    String throttling() throws Exception {
        requireGateway();
        if (!Env.flag("GATEWAY_BURST_TEST")) {
            throw new Skip("se activa con GATEWAY_BURST_TEST=true; envía una ráfaga de solicitudes a la ruta de salud");
        }
        int total = Env.intOrDefault("GATEWAY_BURST_REQUESTS", 200);
        int concurrency = Env.intOrDefault("GATEWAY_BURST_CONCURRENCY", 50);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<Http.Response>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> Http.get(gatewayUrl + healthPath)));
            }
            Map<Integer, Integer> statuses = new java.util.TreeMap<>();
            int throttledByGateway = 0;
            for (Future<Http.Response> future : futures) {
                Http.Response response = future.get();
                statuses.merge(response.status(), 1, Integer::sum);
                if (response.status() == 429 && response.header("apigw-requestid") != null) {
                    throttledByGateway++;
                }
            }
            long serverErrors = statuses.entrySet().stream().filter(e -> e.getKey() >= 500).mapToLong(Map.Entry::getValue).sum();
            check(throttledByGateway > 0, "Ninguna solicitud fue limitada por el gateway. Estados: " + statuses);
            check(serverErrors == 0, serverErrors + " respuestas 5xx. Estados: " + statuses);
            return total + " solicitudes con " + concurrency + " en paralelo → " + statuses + "; 429 del gateway: " + throttledByGateway;
        } finally {
            pool.shutdownNow();
        }
    }

    String addedLatency() throws Exception {
        requireGateway();
        if (backendUrl == null) {
            throw new Skip("falta BACKEND_DIRECT_URL (URL pública del backend en Render) para comparar");
        }
        int samples = Env.intOrDefault("GATEWAY_LATENCY_SAMPLES", 30);
        long threshold = Env.intOrDefault("GATEWAY_MAX_OVERHEAD_MS", 150);
        Http.get(gatewayUrl + healthPath);
        Http.get(backendUrl + healthPath);
        List<Long> viaGateway = new ArrayList<>();
        List<Long> direct = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            viaGateway.add(Http.get(gatewayUrl + healthPath).millis());
            direct.add(Http.get(backendUrl + healthPath).millis());
        }
        long added = Stats.percentile(viaGateway, 50) - Stats.percentile(direct, 50);
        check(added <= threshold, "El gateway agrega " + added + " ms en p50 (umbral " + threshold + " ms)");
        return "Por el gateway: " + Stats.summary(viaGateway) + ". Directo: " + Stats.summary(direct) + ". Agregado en p50: " + added + " ms";
    }

    String openApiExport() throws Exception {
        requireAws();
        Http.Response response = aws("GET", "/v2/apis/" + apiId + "/exports/OAS30?outputType=JSON&includeExtensions=false");
        check(response.status() == 200, "La exportación respondió " + response.brief());
        Map<String, Object> spec = response.json();
        check(String.valueOf(spec.get("openapi")).startsWith("3"), "La exportación no es OpenAPI 3");
        Object paths = spec.get("paths");
        List<String> routes = new ArrayList<>();
        if (paths instanceof Map<?, ?> map) {
            map.forEach((path, operations) -> {
                if (operations instanceof Map<?, ?> methods) {
                    methods.keySet().forEach(method -> routes.add(String.valueOf(method).toUpperCase() + " " + path));
                }
            });
        }
        check(!routes.isEmpty(), "La definición no tiene rutas");
        List<String> missing = new ArrayList<>();
        String expected = Env.get("GATEWAY_VERSIONED_PATHS");
        if (expected != null) {
            for (String path : expected.split(",")) {
                boolean declared = routes.stream().anyMatch(route -> covers(route.substring(route.indexOf(' ') + 1), path.trim()));
                if (!declared) {
                    missing.add(path.trim());
                }
            }
        }
        check(missing.isEmpty(), "Rutas sin declarar en OpenAPI: " + missing);
        return "OpenAPI " + spec.get("openapi") + " con " + routes.size() + " rutas: " + String.join(", ", routes.subList(0, Math.min(12, routes.size())))
                + (routes.size() > 12 ? "…" : "");
    }

    String stageSettings() throws Exception {
        requireAws();
        Http.Response response = aws("GET", "/v2/apis/" + apiId + "/stages");
        check(response.status() == 200, "La consulta de etapas respondió " + response.brief());
        Object items = response.json().get("items");
        check(items instanceof List<?> list && !list.isEmpty(), "La API no tiene etapas");
        List<String> lines = new ArrayList<>();
        boolean throttled = false;
        boolean logged = false;
        for (Object stage : (List<?>) items) {
            String name = Json.str(stage, "stageName");
            String burst = Json.str(stage, "defaultRouteSettings.throttlingBurstLimit");
            String rate = Json.str(stage, "defaultRouteSettings.throttlingRateLimit");
            String logs = Json.str(stage, "accessLogSettings.destinationArn");
            throttled |= burst != null || rate != null;
            logged |= logs != null;
            lines.add(name + ": ráfaga=" + burst + ", tasa=" + rate + "/s, registros=" + (logs == null ? "no" : "sí"));
        }
        check(throttled, "Ninguna etapa tiene límites de solicitudes: " + lines);
        check(logged, "Ninguna etapa tiene registros de acceso: " + lines);
        return String.join("; ", lines);
    }

    // ------------------------------------------------------------- utilidades

    private static boolean isGatewayNotFound(Http.Response response) {
        return response.status() == 404 && response.header("apigw-requestid") != null
                && response.body() != null && response.body().replace(" ", "").contains("{\"message\":\"NotFound\"}");
    }

    /** Indica si una ruta de OpenAPI (con {parametros} o {proxy+}) cubre una ruta concreta. */
    private static boolean covers(String template, String path) {
        String regex = template.replaceAll("\\{[^}]+\\+}", ".+").replaceAll("\\{[^}]+}", "[^/]+");
        return path.matches(regex) || template.equals(path);
    }

    private Http.Response aws(String method, String pathAndQuery) throws Exception {
        String override = Env.get("AWS_ENDPOINT_URL");
        String base = override != null ? Http.trimSlash(override) : "https://apigateway." + region + ".amazonaws.com";
        URI uri = URI.create(base + pathAndQuery);
        String emptyHash = SigV4.sha256Hex(new byte[0]);
        Map<String, String> signed = SigV4.sign(credentials, region, "apigateway", method, uri, Map.of(), emptyHash, Instant.now());
        HttpRequest.Builder builder = Http.to(uri.toString()).method(method, HttpRequest.BodyPublishers.noBody());
        signed.forEach(builder::header);
        return Http.send(builder.build());
    }

    private void requireGateway() {
        if (gatewayUrl == null) {
            throw new Skip("falta la variable de entorno GATEWAY_BASE_URL");
        }
    }

    private void requireAws() {
        List<String> missing = Env.missing("AWS_REGION", "GATEWAY_API_ID", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY");
        if (!missing.isEmpty()) {
            throw new Skip("faltan variables de entorno: " + String.join(", ", missing));
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

    /** Firma Signature Version 4 de AWS, la misma que aceptan las API compatibles con S3. */
    static final class SigV4 {
        static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
        private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(java.time.ZoneOffset.UTC);
        private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(java.time.ZoneOffset.UTC);

        record Credentials(String accessKeyId, String secretAccessKey, String sessionToken) {
        }

        private SigV4() {
        }

        /** Devuelve las cabeceras que deben acompañar la petición (sin Host, que lo pone el cliente HTTP). */
        static Map<String, String> sign(Credentials credentials, String region, String service, String method,
                                        URI uri, Map<String, String> headers, String payloadHash, Instant now) {
            String amzDate = AMZ_DATE.format(now);
            java.util.TreeMap<String, String> canonical = new java.util.TreeMap<>();
            headers.forEach((key, value) -> canonical.put(key.toLowerCase(java.util.Locale.ROOT), value.trim()));
            canonical.put("host", hostOf(uri));
            canonical.put("x-amz-date", amzDate);
            if (credentials.sessionToken() != null) {
                canonical.put("x-amz-security-token", credentials.sessionToken());
            }
            String signedHeaders = String.join(";", canonical.keySet());
            String canonicalRequest = method + "\n"
                    + canonicalPath(uri) + "\n"
                    + canonicalQuery(uri.getRawQuery()) + "\n"
                    + canonicalHeaders(canonical) + "\n"
                    + signedHeaders + "\n"
                    + payloadHash;
            String scope = SHORT_DATE.format(now) + "/" + region + "/" + service + "/aws4_request";
            String signature = signature(credentials, scope, amzDate, canonicalRequest, region, service, now);

            Map<String, String> result = new LinkedHashMap<>();
            canonical.forEach((key, value) -> {
                if (!key.equals("host")) {
                    result.put(key, value);
                }
            });
            result.put("Authorization", "AWS4-HMAC-SHA256 Credential=" + credentials.accessKeyId() + "/" + scope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
            return result;
        }

        /** Construye una URL prefirmada. extraHeaders son cabeceras que el cliente deberá enviar con el mismo valor. */
        static String presign(Credentials credentials, String region, String service, String method, URI uri,
                              Map<String, String> extraHeaders, long expiresSeconds, Instant now) {
            String amzDate = AMZ_DATE.format(now);
            String scope = SHORT_DATE.format(now) + "/" + region + "/" + service + "/aws4_request";
            java.util.TreeMap<String, String> canonical = new java.util.TreeMap<>();
            extraHeaders.forEach((key, value) -> canonical.put(key.toLowerCase(java.util.Locale.ROOT), value.trim()));
            canonical.put("host", hostOf(uri));
            String signedHeaders = String.join(";", canonical.keySet());

            java.util.TreeMap<String, String> query = new java.util.TreeMap<>();
            query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            query.put("X-Amz-Credential", credentials.accessKeyId() + "/" + scope);
            query.put("X-Amz-Date", amzDate);
            query.put("X-Amz-Expires", Long.toString(expiresSeconds));
            query.put("X-Amz-SignedHeaders", signedHeaders);
            if (credentials.sessionToken() != null) {
                query.put("X-Amz-Security-Token", credentials.sessionToken());
            }
            StringBuilder rawQuery = new StringBuilder();
            query.forEach((key, value) -> {
                if (!rawQuery.isEmpty()) {
                    rawQuery.append('&');
                }
                rawQuery.append(encode(key, true)).append('=').append(encode(value, true));
            });
            String canonicalRequest = method + "\n"
                    + canonicalPath(uri) + "\n"
                    + rawQuery + "\n"
                    + canonicalHeaders(canonical) + "\n"
                    + signedHeaders + "\n"
                    + UNSIGNED_PAYLOAD;
            String signature = signature(credentials, scope, amzDate, canonicalRequest, region, service, now);
            String base = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
            return base + "?" + rawQuery + "&X-Amz-Signature=" + signature;
        }

        static String sha256Hex(byte[] data) {
            try {
                return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        /** Codificación de AWS: solo quedan sin codificar A-Z a-z 0-9 - _ . ~ (y / si se pide). */
        static String encode(String value, boolean encodeSlash) {
            StringBuilder out = new StringBuilder();
            for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
                char c = (char) (b & 0xFF);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                        || c == '-' || c == '_' || c == '.' || c == '~' || (c == '/' && !encodeSlash)) {
                    out.append(c);
                } else {
                    out.append('%').append(String.format("%02X", b & 0xFF));
                }
            }
            return out.toString();
        }

        private static String signature(Credentials credentials, String scope, String amzDate, String canonicalRequest,
                                        String region, String service, Instant now) {
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                    + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            byte[] key = hmac(("AWS4" + credentials.secretAccessKey()).getBytes(StandardCharsets.UTF_8), SHORT_DATE.format(now));
            key = hmac(key, region);
            key = hmac(key, service);
            key = hmac(key, "aws4_request");
            return java.util.HexFormat.of().formatHex(hmac(key, stringToSign));
        }

        private static byte[] hmac(byte[] key, String data) {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
                return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            } catch (java.security.GeneralSecurityException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static String hostOf(URI uri) {
            int port = uri.getPort();
            boolean defaultPort = port == -1
                    || (port == 443 && "https".equals(uri.getScheme()))
                    || (port == 80 && "http".equals(uri.getScheme()));
            return defaultPort ? uri.getHost() : uri.getHost() + ":" + port;
        }

        private static String canonicalPath(URI uri) {
            String path = uri.getRawPath();
            return path == null || path.isEmpty() ? "/" : path;
        }

        private static String canonicalQuery(String rawQuery) {
            if (rawQuery == null || rawQuery.isEmpty()) {
                return "";
            }
            java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
            for (String pair : rawQuery.split("&")) {
                int equals = pair.indexOf('=');
                String key = java.net.URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
                String value = equals < 0 ? "" : java.net.URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                sorted.put(encode(key, true), encode(value, true));
            }
            StringBuilder out = new StringBuilder();
            sorted.forEach((key, value) -> {
                if (!out.isEmpty()) {
                    out.append('&');
                }
                out.append(key).append('=').append(value);
            });
            return out.toString();
        }

        private static String canonicalHeaders(Map<String, String> headers) {
            StringBuilder out = new StringBuilder();
            headers.forEach((key, value) -> out.append(key).append(':').append(value.replaceAll("\\s+", " ")).append('\n'));
            return out.toString();
        }
    }
}
