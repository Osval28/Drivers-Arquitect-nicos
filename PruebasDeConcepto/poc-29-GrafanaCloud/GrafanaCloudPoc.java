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
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Prueba de concepto del ADR-29: Grafana Cloud como plataforma de observabilidad.
 *
 * Envía por OTLP (el estándar abierto que usa el backend) métricas de infraestructura, de errores por endpoint, de
 * latencia de proveedores externos y de negocio; registros correlacionados con una traza; y una traza que cruza
 * frontend, backend y procesamiento asíncrono. Después las consulta en Grafana para comprobar que las tres señales
 * quedaron en un solo lugar y relacionadas, y revisa que existan reglas de alerta con un canal externo.
 */
public class GrafanaCloudPoc {

    private final String otlpEndpoint;
    private final String otlpAuthorization;
    private final String grafanaUrl;
    private final String grafanaToken;
    private final String serviceName;
    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private final String traceId = randomHex(16);
    private final String frontendSpan = randomHex(8);
    private final String backendSpan = randomHex(8);
    private final String asyncSpan = randomHex(8);
    private final long startNanos = Instant.now().toEpochMilli() * 1_000_000L;
    private boolean metricsSent;
    private boolean logsSent;
    private boolean tracesSent;

    GrafanaCloudPoc() {
        String endpoint = Env.get("OTEL_EXPORTER_OTLP_ENDPOINT");
        this.otlpEndpoint = endpoint == null ? null : Http.trimSlash(endpoint);
        this.otlpAuthorization = authorization();
        String grafana = Env.get("GRAFANA_URL");
        this.grafanaUrl = grafana == null ? null : Http.trimSlash(grafana);
        this.grafanaToken = Env.get("GRAFANA_TOKEN");
        this.serviceName = Env.orDefault("POC_SERVICE_NAME", "dazma-poc");
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: Grafana Cloud", "ADR-29");
        GrafanaCloudPoc poc = new GrafanaCloudPoc();
        report.context("Endpoint OTLP", poc.otlpEndpoint);
        report.context("Grafana", poc.grafanaUrl);
        report.context("Ejecución", poc.runId + " (etiqueta poc_run)");
        report.context("Traza", poc.traceId);

        report.run("C1", "Métricas por OTLP",
                "El gateway acepta CPU, memoria, errores por endpoint, latencia de proveedores externos y una métrica de negocio",
                poc::sendMetrics);
        report.run("C2", "Registros por OTLP",
                "El gateway acepta registros con trace_id y span_id",
                poc::sendLogs);
        report.run("C3", "Traza frontend → backend → asíncrono por OTLP",
                "El gateway acepta tres spans de servicios distintos en una misma traza",
                poc::sendTraces);
        report.run("C4", "Métricas consultables",
                "Las cinco métricas de esta ejecución aparecen en Prometheus",
                poc::queryMetrics);
        report.run("C5", "Registros consultables y correlacionados",
                "El registro de error de esta ejecución aparece en Loki con el mismo trace_id de la traza",
                poc::queryLogs);
        report.run("C6", "Traza completa consultable",
                "Tempo devuelve la traza con los tres spans y su relación padre-hijo",
                poc::queryTrace);
        report.run("C7", "Alertas hacia un canal externo",
                "Existe al menos una regla de alerta y un punto de contacto de correo, Slack o webhook (ADR-04)",
                poc::alerting);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- envío

    String sendMetrics() throws Exception {
        requireOtlp();
        long now = nowNanos();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        double cpu = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        if (cpu < 0) {
            cpu = Runtime.getRuntime().availableProcessors();
        }
        List<Object> metrics = List.of(
                gauge("dazma_poc_process_cpu_load", cpu, now, Map.of()),
                gauge("dazma_poc_jvm_memory_used", memory.getHeapMemoryUsage().getUsed(), now, Map.of("area", "heap")),
                counter("dazma_poc_http_server_errors", 3, now, Map.of("endpoint", "/publications/api/v1/publications", "status", "500")),
                histogram("dazma_poc_external_call_duration_ms", List.of(120.0, 180.0, 950.0, 2100.0), now,
                        Map.of("dependency", "resend", "outcome", "error")),
                counter("dazma_poc_negotiations_failed", 2, now, Map.of("reason", "pago_rechazado")));
        Map<String, Object> body = Map.of("resourceMetrics", List.of(Map.of(
                "resource", resource(serviceName),
                "scopeMetrics", List.of(Map.of("scope", Map.of("name", "dazma-poc"), "metrics", metrics)))));
        Http.Response response = post("/v1/metrics", body);
        check(response.status() >= 200 && response.status() < 300, "El gateway respondió " + response.brief());
        metricsSent = true;
        return "5 métricas aceptadas (HTTP " + response.status() + ", " + response.millis() + " ms)";
    }

    String sendLogs() throws Exception {
        requireOtlp();
        long now = nowNanos();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timeUnixNano", Long.toString(now));
        record.put("severityNumber", 17);
        record.put("severityText", "ERROR");
        record.put("body", Map.of("stringValue", "Fallo al notificar la negociación poc-" + runId + ": Resend respondió 503"));
        record.put("traceId", traceId);
        record.put("spanId", asyncSpan);
        record.put("attributes", attributes(Map.of("poc_run", runId, "dependency", "resend")));
        Map<String, Object> body = Map.of("resourceLogs", List.of(Map.of(
                "resource", resource(serviceName),
                "scopeLogs", List.of(Map.of("scope", Map.of("name", "dazma-poc"), "logRecords", List.of(record))))));
        Http.Response response = post("/v1/logs", body);
        check(response.status() >= 200 && response.status() < 300, "El gateway respondió " + response.brief());
        logsSent = true;
        return "Registro de error aceptado con trace_id=" + traceId;
    }

    String sendTraces() throws Exception {
        requireOtlp();
        long end = nowNanos();
        Map<String, Object> frontend = span(frontendSpan, null, "GET /negociaciones/[id]", 3, startNanos, end);
        Map<String, Object> backend = span(backendSpan, frontendSpan, "POST /negotiations/api/v1/negotiations/{id}/payments", 2,
                startNanos + 20_000_000, end - 20_000_000);
        Map<String, Object> async = span(asyncSpan, backendSpan, "notifications send-email", 5,
                startNanos + 60_000_000, end - 5_000_000);
        async.put("status", Map.of("code", 2, "message", "Resend respondió 503"));
        Map<String, Object> body = Map.of("resourceSpans", List.of(
                Map.of("resource", resource(serviceName + "-frontend"),
                        "scopeSpans", List.of(Map.of("scope", Map.of("name", "dazma-poc"), "spans", List.of(frontend)))),
                Map.of("resource", resource(serviceName),
                        "scopeSpans", List.of(Map.of("scope", Map.of("name", "dazma-poc"), "spans", List.of(backend, async))))));
        Http.Response response = post("/v1/traces", body);
        check(response.status() >= 200 && response.status() < 300, "El gateway respondió " + response.brief());
        tracesSent = true;
        return "3 spans aceptados en la traza " + traceId;
    }

    // ------------------------------------------------------------- consultas

    String queryMetrics() throws Exception {
        requireGrafana();
        if (!metricsSent) {
            throw new Skip("C1 no envió métricas");
        }
        String uid = datasource("prometheus");
        List<String> expected = List.of("dazma_poc_process_cpu_load", "dazma_poc_jvm_memory_used",
                "dazma_poc_http_server_errors", "dazma_poc_external_call_duration_ms", "dazma_poc_negotiations_failed");
        String query = "group by (__name__) ({__name__=~\"dazma_poc_.*\", poc_run=\"" + runId + "\"})";
        List<String> found = waitFor(() -> {
            Http.Response response = grafana("/api/datasources/proxy/uid/" + uid + "/api/v1/query?query=" + Http.encode(query));
            List<String> names = new ArrayList<>();
            Object result = Json.at(response.json(), "data.result");
            if (result instanceof List<?> list) {
                list.forEach(item -> names.add(Json.str(item, "metric.__name__")));
            }
            boolean all = expected.stream().allMatch(name -> names.stream().anyMatch(n -> n != null && n.startsWith(name)));
            return all ? names : null;
        });
        check(found != null, "No aparecieron las cinco métricas en " + waitSeconds() + " s");
        Collections.sort(found);
        return "Series encontradas: " + String.join(", ", found);
    }

    String queryLogs() throws Exception {
        requireGrafana();
        if (!logsSent) {
            throw new Skip("C2 no envió registros");
        }
        String uid = datasource("loki");
        long start = startNanos - 60_000_000_000L;
        String query = "{service_name=\"" + serviceName + "\"} | trace_id=\"" + traceId + "\" |= \"poc-" + runId + "\"";
        String line = waitFor(() -> {
            Http.Response response = grafana("/api/datasources/proxy/uid/" + uid + "/loki/api/v1/query_range?query="
                    + Http.encode(query) + "&start=" + start + "&limit=10");
            Object result = Json.at(response.json(), "data.result");
            if (result instanceof List<?> list && !list.isEmpty()) {
                return Json.write(list.get(0));
            }
            return null;
        });
        check(line != null, "Loki no devolvió el registro al filtrar por trace_id=" + traceId + " en " + waitSeconds() + " s");
        return "Loki devuelve el registro al filtrar por trace_id=" + traceId + " (correlación registro ↔ traza)";
    }

    String queryTrace() throws Exception {
        requireGrafana();
        if (!tracesSent) {
            throw new Skip("C3 no envió la traza");
        }
        String uid = datasource("tempo");
        String trace = waitFor(() -> {
            Http.Response response = grafana("/api/datasources/proxy/uid/" + uid + "/api/traces/" + traceId);
            return response.status() == 200 ? response.body() : null;
        });
        check(trace != null, "La traza no apareció en Tempo en " + waitSeconds() + " s");
        List<String> missing = new ArrayList<>();
        for (String name : List.of("GET /negociaciones/[id]", "POST /negotiations/api/v1/negotiations/{id}/payments", "notifications send-email")) {
            if (!trace.contains(name)) {
                missing.add(name);
            }
        }
        check(missing.isEmpty(), "Faltan spans: " + missing);
        return "Traza con 3 spans: frontend → backend → envío asíncrono (con estado de error)";
    }

    String alerting() throws Exception {
        requireGrafana();
        Http.Response rules = grafana("/api/v1/provisioning/alert-rules");
        Http.Response contacts = grafana("/api/v1/provisioning/contact-points");
        check(rules.status() == 200 && contacts.status() == 200,
                "La API de alertas respondió " + rules.status() + "/" + contacts.status() + " (el token necesita permisos de lectura de alertas)");
        Object ruleList = Json.parse(rules.body());
        List<String> ruleTitles = new ArrayList<>();
        if (ruleList instanceof List<?> list) {
            list.forEach(rule -> ruleTitles.add(Json.str(rule, "title")));
        }
        Object contactList = Json.parse(contacts.body());
        List<String> externalContacts = new ArrayList<>();
        if (contactList instanceof List<?> list) {
            for (Object contact : list) {
                String type = Json.str(contact, "type");
                if (type != null && List.of("email", "slack", "webhook").contains(type.toLowerCase())) {
                    externalContacts.add(Json.str(contact, "name") + " (" + type + ")");
                }
            }
        }
        check(!ruleTitles.isEmpty(), "No hay reglas de alerta");
        check(!externalContacts.isEmpty(), "No hay puntos de contacto de correo, Slack o webhook");
        return ruleTitles.size() + " reglas: " + String.join(", ", ruleTitles.subList(0, Math.min(8, ruleTitles.size())))
                + ". Canales externos: " + String.join(", ", externalContacts);
    }

    // ------------------------------------------------------------ OTLP JSON

    private Map<String, Object> resource(String service) {
        return Map.of("attributes", attributes(Map.of(
                "service.name", service,
                "deployment.environment.name", Env.orDefault("POC_ENVIRONMENT", "poc"))));
    }

    private Map<String, Object> gauge(String name, double value, long now, Map<String, String> labels) {
        Map<String, Object> point = point(now, labels);
        point.put("asDouble", value);
        return Map.of("name", name, "unit", "", "gauge", Map.of("dataPoints", List.of(point)));
    }

    private Map<String, Object> counter(String name, long value, long now, Map<String, String> labels) {
        Map<String, Object> point = point(now, labels);
        point.put("asInt", Long.toString(value));
        return Map.of("name", name, "unit", "", "sum", Map.of("aggregationTemporality", 2, "isMonotonic", true, "dataPoints", List.of(point)));
    }

    private Map<String, Object> histogram(String name, List<Double> samples, long now, Map<String, String> labels) {
        List<Double> bounds = List.of(100.0, 250.0, 500.0, 1000.0, 2000.0);
        long[] buckets = new long[bounds.size() + 1];
        for (double sample : samples) {
            int index = 0;
            while (index < bounds.size() && sample > bounds.get(index)) {
                index++;
            }
            buckets[index]++;
        }
        List<String> bucketCounts = new ArrayList<>();
        for (long bucket : buckets) {
            bucketCounts.add(Long.toString(bucket));
        }
        Map<String, Object> point = point(now, labels);
        point.put("count", Long.toString(samples.size()));
        point.put("sum", samples.stream().mapToDouble(Double::doubleValue).sum());
        point.put("bucketCounts", bucketCounts);
        point.put("explicitBounds", bounds);
        return Map.of("name", name, "unit", "", "histogram", Map.of("aggregationTemporality", 2, "dataPoints", List.of(point)));
    }

    private Map<String, Object> point(long now, Map<String, String> labels) {
        Map<String, String> all = new LinkedHashMap<>(labels);
        all.put("poc_run", runId);
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("startTimeUnixNano", Long.toString(startNanos));
        point.put("timeUnixNano", Long.toString(now));
        point.put("attributes", attributes(all));
        return point;
    }

    private Map<String, Object> span(String spanId, String parent, String name, int kind, long start, long end) {
        Map<String, Object> span = new LinkedHashMap<>();
        span.put("traceId", traceId);
        span.put("spanId", spanId);
        if (parent != null) {
            span.put("parentSpanId", parent);
        }
        span.put("name", name);
        span.put("kind", kind);
        span.put("startTimeUnixNano", Long.toString(start));
        span.put("endTimeUnixNano", Long.toString(end));
        span.put("attributes", attributes(Map.of("poc_run", runId)));
        span.put("status", Map.of("code", 1));
        return span;
    }

    private static List<Object> attributes(Map<String, String> values) {
        List<Object> attributes = new ArrayList<>();
        values.forEach((key, value) -> attributes.add(Map.of("key", key, "value", Map.of("stringValue", value))));
        return attributes;
    }

    // ------------------------------------------------------------- utilidades

    private Http.Response post(String path, Object body) throws Exception {
        HttpRequest.Builder builder = Http.to(otlpEndpoint + path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if (otlpAuthorization != null) {
            builder.header("Authorization", otlpAuthorization);
        }
        return Http.send(builder.build());
    }

    private Http.Response grafana(String path) throws Exception {
        HttpRequest.Builder builder = Http.to(grafanaUrl + path).GET().header("Accept", "application/json");
        if (grafanaToken != null) {
            builder.header("Authorization", "Bearer " + grafanaToken);
        }
        return Http.send(builder.build());
    }

    private final Map<String, String> datasources = new LinkedHashMap<>();

    private String datasource(String type) throws Exception {
        String configured = Env.get("GRAFANA_" + type.toUpperCase() + "_UID");
        if (configured != null) {
            return configured;
        }
        if (datasources.isEmpty()) {
            Http.Response response = grafana("/api/datasources");
            check(response.status() == 200, "No se pudieron listar los orígenes de datos: HTTP " + response.status());
            Object list = Json.parse(response.body());
            if (list instanceof List<?> items) {
                for (Object item : items) {
                    datasources.putIfAbsent(Json.str(item, "type"), Json.str(item, "uid"));
                }
            }
        }
        String uid = datasources.get(type);
        check(uid != null, "No hay origen de datos de tipo " + type + "; se puede indicar con GRAFANA_" + type.toUpperCase() + "_UID");
        return uid;
    }

    interface Attempt<T> {
        T get() throws Exception;
    }

    private <T> T waitFor(Attempt<T> attempt) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(waitSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            T value = attempt.get();
            if (value != null) {
                return value;
            }
            Thread.sleep(5_000);
        }
        return null;
    }

    private static int waitSeconds() {
        return Env.intOrDefault("GRAFANA_QUERY_WAIT_SECONDS", 120);
    }

    private static String authorization() {
        String header = Env.get("OTLP_AUTHORIZATION");
        if (header != null) {
            return header;
        }
        String instance = Env.get("GRAFANA_OTLP_INSTANCE_ID");
        String token = Env.get("GRAFANA_OTLP_TOKEN");
        if (instance != null && token != null) {
            return "Basic " + java.util.Base64.getEncoder().encodeToString((instance + ":" + token).getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    private static long nowNanos() {
        return Instant.now().toEpochMilli() * 1_000_000L;
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private void requireOtlp() {
        if (otlpEndpoint == null) {
            throw new Skip("falta OTEL_EXPORTER_OTLP_ENDPOINT (gateway OTLP de Grafana Cloud o http://localhost:4318 con otel-lgtm)");
        }
    }

    private void requireGrafana() {
        if (grafanaUrl == null) {
            throw new Skip("falta GRAFANA_URL (URL del stack de Grafana o http://localhost:3000 con otel-lgtm)");
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
