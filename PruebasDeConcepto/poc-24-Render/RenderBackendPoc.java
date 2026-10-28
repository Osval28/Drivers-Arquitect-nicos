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

/**
 * Prueba de concepto del ADR-24: Render como PaaS del backend.
 *
 * Revisa el servicio desplegado desde afuera (HTTPS, salud y sondas, latencia) y desde la API de Render
 * (plan, revisión de salud, variables solo de arranque, despliegues, memoria). Con permiso explícito mide además
 * cuánto tarda en recuperarse tras un reinicio y si un despliegue deja el servicio sin responder (ESC-CAL-DIS-0006).
 */
public class RenderBackendPoc {

    private final String backendUrl;
    private final String healthPath;
    private final String apiUrl;
    private final String apiKey;
    private final String serviceId;

    RenderBackendPoc() {
        String backend = Env.get("RENDER_BACKEND_URL");
        this.backendUrl = backend == null ? null : Http.trimSlash(backend);
        this.healthPath = Env.orDefault("RENDER_HEALTH_PATH", "/actuator/health");
        this.apiUrl = Http.trimSlash(Env.orDefault("RENDER_API_URL", "https://api.render.com/v1"));
        this.apiKey = Env.get("RENDER_API_KEY");
        this.serviceId = Env.get("RENDER_SERVICE_ID");
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: backend en Render", "ADR-24");
        RenderBackendPoc poc = new RenderBackendPoc();
        report.context("Backend", poc.backendUrl);
        report.context("Servicio", poc.serviceId);

        report.run("C1", "Servicio persistente con HTTPS",
                "La ruta de salud responde 200 con status UP por HTTPS con certificado válido",
                poc::healthOverHttps);
        report.run("C2", "Sondas de vida y disponibilidad",
                "/liveness y /readiness responden UP (management.endpoint.health.probes.enabled=true)",
                poc::probes);
        report.run("C3", "Plan y revisión de salud",
                "El servicio es web_service, no usa el plan gratuito y tiene configurada la ruta de revisión de salud",
                poc::serviceConfiguration);
        report.run("C4", "Configuración separada del código",
                "Las variables del servicio son solo las de arranque esperadas; los secretos de negocio no están en Render (ADR-25/35)",
                poc::environmentVariables);
        report.run("C5", "Despliegues ordenados",
                "El último despliegue está activo (live) y se informa el historial reciente",
                poc::deployHistory);
        report.run("C6", "Memoria medida",
                "En la ventana consultada el uso máximo de memoria queda por debajo del umbral respecto al límite de la instancia",
                poc::memoryUsage);
        report.run("C7", "Latencia del backend",
                "El p95 de la ruta de salud es menor o igual al umbral configurado",
                poc::latency);
        report.run("C8", "Recuperación tras reinicio",
                "Tras pedir un reinicio el servicio vuelve a responder UP dentro del tiempo máximo",
                poc::restartRecovery);
        report.run("C9", "Despliegue sin indisponibilidad",
                "Durante un despliegue nuevo la ruta de salud no deja de responder más del máximo definido (ESC-CAL-DIS-0006)",
                poc::deployWithoutDowntime);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String healthOverHttps() throws Exception {
        requireBackend();
        check(backendUrl.startsWith("https://"), "RENDER_BACKEND_URL no usa HTTPS");
        Http.Response response = Http.get(backendUrl + healthPath);
        check(response.status() == 200, "La salud respondió " + response.brief());
        check("UP".equals(Json.str(response.json(), "status")), "Estado distinto de UP: " + response.brief());
        return "HTTP 200 {status: UP} en " + response.millis() + " ms; certificado validado por el JDK";
    }

    String probes() throws Exception {
        requireBackend();
        List<String> lines = new ArrayList<>();
        for (String probe : List.of("liveness", "readiness")) {
            Http.Response response = Http.get(backendUrl + healthPath + "/" + probe);
            check(response.status() == 200 && "UP".equals(Json.str(response.json(), "status")), probe + " respondió " + response.brief());
            lines.add(probe + " UP");
        }
        return String.join(", ", lines);
    }

    String serviceConfiguration() throws Exception {
        requireApi();
        Http.Response response = api("/services/" + serviceId);
        check(response.status() == 200, "La API respondió " + response.brief());
        Map<String, Object> service = response.json();
        String type = Json.str(service, "type");
        String plan = Json.str(service, "serviceDetails.plan");
        String healthCheck = Json.str(service, "serviceDetails.healthCheckPath");
        String autoDeploy = Json.str(service, "autoDeploy");
        String instances = Json.str(service, "serviceDetails.numInstances");
        String region = Json.str(service, "serviceDetails.region");
        check("web_service".equals(type), "Tipo de servicio " + type);
        check(plan != null && !plan.equalsIgnoreCase("free"), "El servicio usa el plan gratuito, que Render no recomienda para producción");
        check(healthCheck != null && !healthCheck.isBlank(), "No hay ruta de revisión de salud configurada");
        return "tipo=" + type + ", plan=" + plan + ", región=" + region + ", instancias=" + instances
                + ", healthCheckPath=" + healthCheck + ", autoDeploy=" + autoDeploy;
    }

    String environmentVariables() throws Exception {
        requireApi();
        Http.Response response = api("/services/" + serviceId + "/env-vars?limit=100");
        check(response.status() == 200, "La API respondió " + response.brief());
        List<String> keys = new ArrayList<>();
        Object items = Json.parse(response.body());
        if (items instanceof List<?> list) {
            for (Object item : list) {
                String key = Json.str(item, "envVar.key");
                keys.add(key != null ? key : Json.str(item, "key"));
            }
        }
        List<String> expected = List.of(Env.orDefault("RENDER_EXPECTED_ENV_KEYS",
                "SPRING_PROFILES_ACTIVE,AWS_REGION,AWS_ACCESS_KEY_ID,AWS_SECRET_ACCESS_KEY").split(","));
        List<String> forbiddenPatterns = List.of(Env.orDefault("RENDER_FORBIDDEN_ENV_PATTERNS",
                "PASSWORD,DATASOURCE,RESEND,FIREBASE,R2_,OTLP,REDIS").split(","));
        List<String> missing = expected.stream().map(String::trim).filter(key -> !keys.contains(key)).toList();
        List<String> forbidden = keys.stream()
                .filter(key -> forbiddenPatterns.stream().anyMatch(pattern -> key.toUpperCase().contains(pattern.trim())))
                .toList();
        check(missing.isEmpty(), "Faltan variables de arranque: " + missing);
        check(forbidden.isEmpty(), "Hay secretos de negocio en Render en vez del baúl: " + forbidden);
        return keys.size() + " variables (solo nombres): " + String.join(", ", keys);
    }

    String deployHistory() throws Exception {
        requireApi();
        Http.Response response = api("/services/" + serviceId + "/deploys?limit=10");
        check(response.status() == 200, "La API respondió " + response.brief());
        List<String> statuses = new ArrayList<>();
        Object items = Json.parse(response.body());
        if (items instanceof List<?> list) {
            for (Object item : list) {
                String status = Json.str(item, "deploy.status");
                statuses.add(status != null ? status : Json.str(item, "status"));
            }
        }
        check(!statuses.isEmpty(), "No hay despliegues");
        check("live".equals(statuses.get(0)), "El último despliegue está en " + statuses.get(0));
        Map<String, Long> counts = new java.util.TreeMap<>();
        statuses.forEach(status -> counts.merge(status, 1L, Long::sum));
        return "Último: live. Últimos " + statuses.size() + ": " + counts;
    }

    String memoryUsage() throws Exception {
        requireApi();
        int hours = Env.intOrDefault("RENDER_METRICS_HOURS", 24);
        String window = "&startTime=" + Http.encode(Instant.now().minus(Duration.ofHours(hours)).toString())
                + "&endTime=" + Http.encode(Instant.now().toString());
        Http.Response usage = api("/metrics/memory?resource=" + serviceId + window);
        Http.Response limit = api("/metrics/memory-limit?resource=" + serviceId + window);
        if (usage.status() == 404 || limit.status() == 404) {
            throw new Skip("la API de métricas no está disponible para esta cuenta o servicio; revisar en el panel de Render");
        }
        check(usage.status() == 200 && limit.status() == 200, "La API de métricas respondió " + usage.status() + "/" + limit.status());
        double maxUsed = maxValue(Json.parse(usage.body()));
        double maxLimit = maxValue(Json.parse(limit.body()));
        check(maxUsed > 0 && maxLimit > 0, "No hay datos de memoria en las últimas " + hours + " h");
        double ratio = maxUsed / maxLimit;
        double threshold = Double.parseDouble(Env.orDefault("RENDER_MAX_MEMORY_RATIO", "0.85"));
        String detail = String.format("máximo %.0f MB de %.0f MB (%.0f %%) en las últimas %d h", maxUsed / 1_048_576, maxLimit / 1_048_576, ratio * 100, hours);
        check(ratio <= threshold, "Uso de memoria alto: " + detail + "; umbral " + (int) (threshold * 100) + " %");
        return detail;
    }

    String latency() throws Exception {
        requireBackend();
        int samples = Env.intOrDefault("RENDER_LATENCY_SAMPLES", 30);
        long threshold = Env.intOrDefault("RENDER_MAX_P95_MS", 1000);
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            millis.add(Http.get(backendUrl + healthPath).millis());
        }
        long p95 = Stats.percentile(millis, 95);
        check(p95 <= threshold, "p95 " + p95 + " ms supera " + threshold + " ms");
        return Stats.summary(millis);
    }

    String restartRecovery() throws Exception {
        requireApi();
        requireBackend();
        if (!Env.flag("RENDER_ALLOW_RESTART")) {
            throw new Skip("se activa con RENDER_ALLOW_RESTART=true; reinicia el servicio real");
        }
        Http.Response restart = Http.send(Http.to(apiUrl + "/services/" + serviceId + "/restart")
                .header("Authorization", "Bearer " + apiKey).header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        check(restart.status() >= 200 && restart.status() < 300, "El reinicio respondió " + restart.brief());
        Probe probe = watchHealth(Duration.ofSeconds(Env.intOrDefault("RENDER_MAX_RECOVERY_SECONDS", 300)), Duration.ofSeconds(20), null);
        check(probe.recovered, "No volvió a UP en " + probe.elapsedSeconds + " s");
        return "Reinicio pedido; " + probe.describe();
    }

    String deployWithoutDowntime() throws Exception {
        requireApi();
        requireBackend();
        if (!Env.flag("RENDER_ALLOW_DEPLOY")) {
            throw new Skip("se activa con RENDER_ALLOW_DEPLOY=true; dispara un despliegue real del último commit");
        }
        Http.Response deploy = Http.send(Http.to(apiUrl + "/services/" + serviceId + "/deploys")
                .header("Authorization", "Bearer " + apiKey).header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"clearCache\":\"do_not_clear\"}")).build());
        check(deploy.status() == 201 || deploy.status() == 200, "El despliegue respondió " + deploy.brief());
        String deployId = Json.str(deploy.json(), "id");
        Probe probe = watchHealth(Duration.ofSeconds(Env.intOrDefault("RENDER_DEPLOY_WAIT_SECONDS", 900)), Duration.ZERO, deployId);
        long allowed = Env.intOrDefault("RENDER_MAX_DOWNTIME_SECONDS", 0);
        check("live".equals(probe.deployStatus), "El despliegue terminó en " + probe.deployStatus);
        check(probe.downSeconds <= allowed, "Sin respuesta " + probe.downSeconds + " s (máximo " + allowed + " s). " + probe.describe());
        return "Despliegue " + deployId + " live; " + probe.describe();
    }

    // ------------------------------------------------------------- utilidades

    final class Probe {
        boolean recovered;
        long elapsedSeconds;
        long downSeconds;
        int checks;
        int failures;
        String deployStatus;

        String describe() {
            return checks + " comprobaciones de salud, " + failures + " sin UP (≈" + downSeconds + " s), "
                    + (recovered ? "UP" : "sin recuperar") + " a los " + elapsedSeconds + " s";
        }
    }

    /** Consulta la salud cada segundo. Termina cuando el despliegue queda live o, sin despliegue, al volver a UP. */
    private Probe watchHealth(Duration maximum, Duration minimum, String deployId) throws Exception {
        Probe probe = new Probe();
        long interval = Env.intOrDefault("RENDER_PROBE_INTERVAL_MS", 1000);
        long start = System.nanoTime();
        boolean sawFailure = false;
        while (Duration.ofNanos(System.nanoTime() - start).compareTo(maximum) < 0) {
            boolean up;
            try {
                Http.Response response = Http.send(Http.to(backendUrl + healthPath).timeout(Duration.ofSeconds(5)).GET().build());
                up = response.status() == 200 && "UP".equals(Json.str(response.json(), "status"));
            } catch (IOException | RuntimeException failure) {
                up = false;
            }
            probe.checks++;
            if (!up) {
                probe.failures++;
                sawFailure = true;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            if (deployId != null && probe.checks % 5 == 0) {
                Http.Response status = api("/services/" + serviceId + "/deploys/" + deployId);
                probe.deployStatus = Json.str(status.json(), "status");
                if (List.of("live", "build_failed", "update_failed", "canceled", "deactivated").contains(probe.deployStatus)) {
                    probe.recovered = up;
                    break;
                }
            } else if (deployId == null && up && (sawFailure || elapsed.compareTo(minimum) >= 0)) {
                probe.recovered = true;
                break;
            }
            Thread.sleep(interval);
        }
        probe.elapsedSeconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
        probe.downSeconds = probe.failures * interval / 1000;
        return probe;
    }

    private Http.Response api(String path) throws Exception {
        return Http.get(apiUrl + path, "Authorization", "Bearer " + apiKey, "Accept", "application/json");
    }

    private static double maxValue(Object json) {
        double max = 0;
        if (json instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("value".equals(entry.getKey()) && entry.getValue() instanceof Number number) {
                    max = Math.max(max, number.doubleValue());
                } else {
                    max = Math.max(max, maxValue(entry.getValue()));
                }
            }
        } else if (json instanceof List<?> list) {
            for (Object item : list) {
                max = Math.max(max, maxValue(item));
            }
        }
        return max;
    }

    private void requireBackend() {
        if (backendUrl == null) {
            throw new Skip("falta la variable de entorno RENDER_BACKEND_URL");
        }
    }

    private void requireApi() {
        List<String> missing = Env.missing("RENDER_API_KEY", "RENDER_SERVICE_ID");
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
}
