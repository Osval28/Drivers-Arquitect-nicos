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
import java.util.Set;

/**
 * Prueba de concepto del ADR-14: Cloudflare WAF delante de la API pública.
 *
 * Verifica con la API de Cloudflare (solo lectura) que el dominio de la API pasa por el proxy, que están activos el
 * conjunto administrado de Cloudflare y el de OWASP, que hay reglas de limitación de solicitudes y que los eventos de
 * seguridad se pueden consultar. Con tráfico normal comprueba que los usuarios y los webhooks no se bloquean y que el
 * origen no acepta tráfico directo.
 */
public class CloudflareWafPoc {

    private static final String CLOUDFLARE_MANAGED = "Cloudflare Managed Ruleset";
    private static final String OWASP_MANAGED = "Cloudflare OWASP Core Ruleset";

    private final String apiUrl;
    private final String token;
    private final String zoneId;
    private final String apiHostname;
    private final String publicBaseUrl;

    CloudflareWafPoc() {
        this.apiUrl = Http.trimSlash(Env.orDefault("CF_API_URL", "https://api.cloudflare.com/client/v4"));
        this.token = Env.get("CF_API_TOKEN");
        this.zoneId = Env.get("CF_ZONE_ID");
        this.apiHostname = Env.get("WAF_API_HOSTNAME");
        String base = Env.get("WAF_PUBLIC_BASE_URL");
        this.publicBaseUrl = base != null ? Http.trimSlash(base) : apiHostname == null ? null : "https://" + apiHostname;
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: Cloudflare WAF", "ADR-14");
        CloudflareWafPoc poc = new CloudflareWafPoc();
        report.context("Dominio de la API", poc.apiHostname);
        report.context("URL pública", poc.publicBaseUrl);

        report.run("C1", "La API pasa por Cloudflare",
                "El registro DNS del dominio de la API está en modo proxy; si no, el WAF no inspecciona sus solicitudes",
                poc::dnsIsProxied);
        report.run("C2", "Reglas administradas y OWASP activas",
                "La fase de reglas administradas ejecuta el conjunto de Cloudflare y el de OWASP sin estar deshabilitados",
                poc::managedRulesetsEnabled);
        report.run("C3", "Limitación de solicitudes",
                "Hay al menos una regla de limitación de solicitudes activa",
                poc::rateLimitingRules);
        report.run("C4", "Tráfico legítimo no se bloquea",
                "Solicitudes normales a las rutas de la API y un webhook de prueba atraviesan Cloudflare sin bloqueo ni desafío",
                poc::legitimateTrafficPasses);
        report.run("C5", "Eventos de seguridad consultables",
                "La API de analítica devuelve los eventos de las últimas 24 h con acción, regla y ruta",
                poc::securityEvents);
        report.run("C6", "Origen protegido",
                "Una solicitud directa al origen, sin pasar por el acceso autorizado, no obtiene respuesta exitosa",
                poc::originNotDirectlyReachable);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String dnsIsProxied() throws Exception {
        requireApi();
        if (apiHostname == null) {
            throw new Skip("falta WAF_API_HOSTNAME (por ejemplo api.dazma.store)");
        }
        Http.Response response = api("/zones/" + zoneId + "/dns_records?name=" + Http.encode(apiHostname));
        check(response.status() == 200, "La API respondió " + response.brief());
        Object records = response.json().get("result");
        check(records instanceof List<?> list && !list.isEmpty(), "No hay registro DNS para " + apiHostname);
        List<String> lines = new ArrayList<>();
        boolean allProxied = true;
        for (Object record : (List<?>) records) {
            boolean proxied = Boolean.TRUE.equals(Json.at(record, "proxied"));
            allProxied &= proxied;
            lines.add(Json.str(record, "type") + " " + Json.str(record, "name") + " proxied=" + proxied);
        }
        check(allProxied, "Hay registros sin proxy: " + lines);
        return String.join("; ", lines);
    }

    String managedRulesetsEnabled() throws Exception {
        requireApi();
        Map<String, String> names = new LinkedHashMap<>();
        Http.Response list = api("/zones/" + zoneId + "/rulesets");
        check(list.status() == 200, "La API respondió " + list.brief());
        Object rulesets = list.json().get("result");
        if (rulesets instanceof List<?> items) {
            items.forEach(ruleset -> names.put(Json.str(ruleset, "id"), Json.str(ruleset, "name")));
        }
        Http.Response entrypoint = api("/zones/" + zoneId + "/rulesets/phases/http_request_firewall_managed/entrypoint");
        check(entrypoint.status() == 200, "No hay reglas administradas desplegadas en la zona: " + entrypoint.brief());
        Set<String> active = new java.util.TreeSet<>();
        Object rules = Json.at(entrypoint.json(), "result.rules");
        if (rules instanceof List<?> items) {
            for (Object rule : items) {
                boolean enabled = !Boolean.FALSE.equals(Json.at(rule, "enabled"));
                String executed = Json.str(rule, "action_parameters.id");
                if ("execute".equals(Json.str(rule, "action")) && enabled && executed != null) {
                    active.add(names.getOrDefault(executed, executed));
                }
            }
        }
        check(active.contains(CLOUDFLARE_MANAGED), "No se ejecuta " + CLOUDFLARE_MANAGED + ". Activos: " + active);
        check(active.contains(OWASP_MANAGED), "No se ejecuta " + OWASP_MANAGED + ". Activos: " + active);
        return "Conjuntos activos: " + String.join(", ", active);
    }

    String rateLimitingRules() throws Exception {
        requireApi();
        Http.Response response = api("/zones/" + zoneId + "/rulesets/phases/http_ratelimit/entrypoint");
        check(response.status() == 200, "No hay reglas de limitación desplegadas: " + response.brief());
        List<String> lines = new ArrayList<>();
        Object rules = Json.at(response.json(), "result.rules");
        if (rules instanceof List<?> items) {
            for (Object rule : items) {
                if (Boolean.FALSE.equals(Json.at(rule, "enabled"))) {
                    continue;
                }
                lines.add("\"" + Json.str(rule, "description") + "\": " + Json.str(rule, "ratelimit.requests_per_period")
                        + " solicitudes / " + Json.str(rule, "ratelimit.period") + " s → " + Json.str(rule, "action")
                        + " (" + Json.str(rule, "expression") + ")");
            }
        }
        check(!lines.isEmpty(), "No hay reglas de limitación activas");
        return String.join("; ", lines);
    }

    String legitimateTrafficPasses() throws Exception {
        if (publicBaseUrl == null) {
            throw new Skip("falta WAF_PUBLIC_BASE_URL o WAF_API_HOSTNAME");
        }
        List<String> paths = List.of(Env.orDefault("WAF_LEGIT_PATHS", "/actuator/health").split(","));
        int repetitions = Env.intOrDefault("WAF_LEGIT_REPETITIONS", 5);
        int total = 0;
        List<String> blocked = new ArrayList<>();
        Map<Integer, Integer> statuses = new java.util.TreeMap<>();
        for (String raw : paths) {
            for (int i = 0; i < repetitions; i++) {
                Http.Response response = Http.send(Http.to(publicBaseUrl + raw.trim())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DAZMA-PoC")
                        .header("Accept", "application/json").GET().build());
                total++;
                statuses.merge(response.status(), 1, Integer::sum);
                check(response.header("cf-ray") != null, "La respuesta no pasó por Cloudflare (sin cf-ray)");
                if (blockedByEdge(response)) {
                    blocked.add(raw.trim() + " → " + response.status());
                }
            }
        }
        String webhook = Env.get("WAF_WEBHOOK_PATH");
        if (webhook != null) {
            Http.Response response = Http.send(Http.to(publicBaseUrl + webhook)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", Env.orDefault("WAF_WEBHOOK_USER_AGENT", "Svix-Webhooks/1.0"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"poc\"}}")).build());
            total++;
            statuses.merge(response.status(), 1, Integer::sum);
            if (blockedByEdge(response)) {
                blocked.add(webhook + " (webhook) → " + response.status());
            }
        }
        check(blocked.isEmpty(), "Cloudflare bloqueó o desafió tráfico legítimo: " + blocked);
        return total + " solicitudes normales sin bloqueo del borde. Estados devueltos por la API: " + statuses
                + (webhook == null ? "" : " (el webhook sin firma válida lo rechaza la aplicación, no el WAF)");
    }

    String securityEvents() throws Exception {
        requireApi();
        String since = Instant.now().minus(Duration.ofHours(24)).toString();
        String query = "query($zone: String!, $since: Time!) { viewer { zones(filter: {zoneTag: $zone}) { "
                + "firewallEventsAdaptive(filter: {datetime_geq: $since}, limit: 200, orderBy: [datetime_DESC]) "
                + "{ action source ruleId clientRequestPath datetime } } } }";
        Map<String, Object> body = Map.of("query", query, "variables", Map.of("zone", zoneId, "since", since));
        Http.Response response = Http.postJson(apiUrl + "/graphql", body, "Authorization", "Bearer " + token);
        check(response.status() == 200, "GraphQL respondió " + response.brief());
        Object errors = response.json().get("errors");
        check(errors == null || (errors instanceof List<?> list && list.isEmpty()), "GraphQL devolvió errores: " + Json.write(errors));
        Object events = Json.at(response.json(), "data.viewer.zones.0.firewallEventsAdaptive");
        Map<String, Integer> byAction = new java.util.TreeMap<>();
        Map<String, Integer> bySource = new java.util.TreeMap<>();
        int count = 0;
        if (events instanceof List<?> list) {
            for (Object event : list) {
                count++;
                byAction.merge(String.valueOf(Json.str(event, "action")), 1, Integer::sum);
                bySource.merge(String.valueOf(Json.str(event, "source")), 1, Integer::sum);
            }
        }
        return count + " eventos en 24 h" + (count == 0 ? " (consulta disponible; sin eventos en la ventana)"
                : ". Por acción: " + byAction + ". Por origen de la regla: " + bySource);
    }

    String originNotDirectlyReachable() throws Exception {
        String origin = Env.get("WAF_ORIGIN_URL");
        if (origin == null) {
            throw new Skip("falta WAF_ORIGIN_URL (URL directa del origen, por ejemplo la de execute-api o la de Render)");
        }
        String path = Env.orDefault("WAF_ORIGIN_PATH", "/actuator/health");
        Http.Response response;
        try {
            response = Http.send(Http.to(Http.trimSlash(origin) + path).timeout(Duration.ofSeconds(10)).GET().build());
        } catch (IOException unreachable) {
            return "El origen no responde a conexiones directas (" + unreachable.getClass().getSimpleName() + ")";
        }
        check(response.status() < 200 || response.status() >= 300,
                "El origen respondió " + response.status() + " a una solicitud directa: el WAF se puede eludir");
        return "Solicitud directa al origen → HTTP " + response.status();
    }

    // ------------------------------------------------------------- utilidades

    /** Bloqueo o desafío generado por Cloudflare (no una respuesta de la aplicación). */
    private static boolean blockedByEdge(Http.Response response) {
        if (response.header("cf-mitigated") != null) {
            return true;
        }
        if (response.status() != 403 && response.status() != 429 && response.status() != 503) {
            return false;
        }
        String body = response.body() == null ? "" : response.body();
        return body.contains("Cloudflare Ray ID") || body.contains("cf-error-details") || body.contains("Attention Required");
    }

    private Http.Response api(String path) throws Exception {
        return Http.get(apiUrl + path, "Authorization", "Bearer " + token);
    }

    private void requireApi() {
        List<String> missing = Env.missing("CF_API_TOKEN", "CF_ZONE_ID");
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
