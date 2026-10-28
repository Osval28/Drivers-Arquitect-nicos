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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueba de concepto del ADR-23: Vercel como PaaS del frontend Next.js.
 *
 * Revisa el frontend publicado (dominio propio con HTTPS, redirección, CDN, rendimiento y que el navegador no reciba
 * secretos) y, con la API de Vercel, las vistas previas por cambio, las variables por ambiente y los registros de
 * construcción. Solo hace lecturas.
 */
public class VercelFrontendPoc {

    private static final Map<String, Pattern> SECRET_PATTERNS = new LinkedHashMap<>();

    static {
        SECRET_PATTERNS.put("llave privada PEM", Pattern.compile("-----BEGIN (?:RSA |EC )?PRIVATE KEY-----"));
        SECRET_PATTERNS.put("llave de cuenta de servicio", Pattern.compile("\"private_key\"\\s*:"));
        SECRET_PATTERNS.put("clave de acceso de AWS", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"));
        SECRET_PATTERNS.put("API key de Resend", Pattern.compile("\\bre_[A-Za-z0-9]{8,}_[A-Za-z0-9]{16,}\\b"));
        SECRET_PATTERNS.put("secreto de webhook", Pattern.compile("\\bwhsec_[A-Za-z0-9+/=]{20,}"));
        SECRET_PATTERNS.put("clave secreta de pagos", Pattern.compile("\\bsk_(?:live|test)_[A-Za-z0-9]{16,}"));
        SECRET_PATTERNS.put("URL con credenciales", Pattern.compile("\\b(?:postgres(?:ql)?|rediss?)://[^\\s\"'@/]+:[^\\s\"'@/]+@"));
        SECRET_PATTERNS.put("variable de servidor", Pattern.compile("\\b(?:RESEND_API_KEY|R2_SECRET_ACCESS_KEY|AWS_SECRET_ACCESS_KEY|ABLY_API_KEY|FIREBASE_SERVICE_ACCOUNT)\\b"));
    }

    private final String appUrl;
    private final String apiUrl;
    private final String token;
    private final String projectId;
    private final String teamId;

    VercelFrontendPoc() {
        String app = Env.get("VERCEL_APP_URL");
        this.appUrl = app == null ? null : Http.trimSlash(app);
        this.apiUrl = Http.trimSlash(Env.orDefault("VERCEL_API_URL", "https://api.vercel.com"));
        this.token = Env.get("VERCEL_TOKEN");
        this.projectId = Env.get("VERCEL_PROJECT_ID");
        this.teamId = Env.get("VERCEL_TEAM_ID");
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: frontend en Vercel", "ADR-23");
        VercelFrontendPoc poc = new VercelFrontendPoc();
        report.context("Frontend", poc.appUrl);
        report.context("Proyecto", poc.projectId);

        report.run("C1", "Dominio propio con HTTPS",
                "La página responde 200 por HTTPS con certificado válido, servida por Vercel y con HSTS",
                poc::customDomainHttps);
        report.run("C2", "Redirección a HTTPS",
                "La versión http:// redirige a https://",
                poc::httpRedirects);
        report.run("C3", "Páginas públicas desde la CDN",
                "Las páginas públicas responden 200, las repeticiones salen de la caché de Vercel y el p95 queda bajo el umbral",
                poc::publicPagesFromCdn);
        report.run("C4", "Sin secretos en el navegador",
                "El HTML y los JavaScript que descarga el navegador no contienen llaves, claves de API ni variables de servidor (ESC-CAL-SEG-0008)",
                poc::noSecretsInBrowser);
        report.run("C5", "URL del backend por ambiente",
                "El frontend de producción apunta a la URL de API esperada para ese ambiente",
                poc::backendUrlPerEnvironment);
        report.run("C6", "Vistas previas por cambio",
                "Hay despliegues de vista previa listos además del de producción",
                poc::previewDeployments);
        report.run("C7", "Variables por ambiente",
                "Hay variables separadas por ambiente y ninguna variable pública (NEXT_PUBLIC_) tiene nombre de secreto",
                poc::environmentVariables);
        report.run("C8", "Registros de construcción",
                "El último despliegue de producción expone sus registros de construcción",
                poc::buildLogs);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String customDomainHttps() throws Exception {
        requireApp();
        check(appUrl.startsWith("https://"), "VERCEL_APP_URL no usa HTTPS");
        Http.Response response = Http.get(appUrl + "/");
        check(response.status() == 200, "La página respondió " + response.status());
        String server = response.header("server");
        String vercelId = response.header("x-vercel-id");
        String hsts = response.header("strict-transport-security");
        check(vercelId != null || "Vercel".equalsIgnoreCase(server), "La respuesta no viene de Vercel (server=" + server + ")");
        check(hsts != null, "Falta la cabecera Strict-Transport-Security");
        return "HTTP 200, server=" + server + ", x-vercel-id=" + vercelId + ", HSTS=" + hsts;
    }

    String httpRedirects() throws Exception {
        requireApp();
        String insecure = appUrl.replaceFirst("^https://", "http://");
        Http.Response response = Http.get(insecure + "/");
        String location = response.header("location");
        check(response.status() >= 300 && response.status() < 400, "http:// respondió " + response.status());
        check(location != null && location.startsWith("https://"), "La redirección va a " + location);
        return "HTTP " + response.status() + " → " + location;
    }

    String publicPagesFromCdn() throws Exception {
        requireApp();
        List<String> paths = List.of(Env.orDefault("VERCEL_PUBLIC_PATHS", "/").split(","));
        int repetitions = Env.intOrDefault("VERCEL_REPETITIONS", 10);
        long threshold = Env.intOrDefault("VERCEL_MAX_P95_MS", 1500);
        List<Long> millis = new ArrayList<>();
        Map<String, Integer> cache = new java.util.TreeMap<>();
        for (String raw : paths) {
            String path = raw.trim();
            for (int i = 0; i < repetitions; i++) {
                Http.Response response = Http.get(appUrl + path);
                check(response.status() == 200, path + " respondió " + response.status());
                millis.add(response.millis());
                if (i > 0) {
                    cache.merge(String.valueOf(response.header("x-vercel-cache")), 1, Integer::sum);
                }
            }
        }
        int fromCache = cache.getOrDefault("HIT", 0) + cache.getOrDefault("STALE", 0) + cache.getOrDefault("PRERENDER", 0);
        long p95 = Stats.percentile(millis, 95);
        check(fromCache > 0, "Ninguna repetición salió de la caché de Vercel: " + cache);
        check(p95 <= threshold, "p95 " + p95 + " ms supera " + threshold + " ms");
        return paths.size() + " páginas × " + repetitions + ": " + Stats.summary(millis) + "; x-vercel-cache en repeticiones " + cache;
    }

    String noSecretsInBrowser() throws Exception {
        requireApp();
        Map<String, String> assets = browserAssets();
        List<String> findings = new ArrayList<>();
        int publicFirebaseKeys = 0;
        for (Map.Entry<String, String> asset : assets.entrySet()) {
            for (Map.Entry<String, Pattern> pattern : SECRET_PATTERNS.entrySet()) {
                if (pattern.getValue().matcher(asset.getValue()).find()) {
                    findings.add(pattern.getKey() + " en " + shortName(asset.getKey()));
                }
            }
            Matcher firebase = Pattern.compile("AIza[0-9A-Za-z_-]{35}").matcher(asset.getValue());
            while (firebase.find()) {
                publicFirebaseKeys++;
            }
        }
        check(findings.isEmpty(), "Posibles secretos expuestos: " + findings);
        return assets.size() + " archivos revisados (HTML y JavaScript), 0 hallazgos."
                + (publicFirebaseKeys > 0 ? " Se ve la clave web de Firebase, que es pública por diseño." : "");
    }

    String backendUrlPerEnvironment() throws Exception {
        requireApp();
        String expected = Env.get("VERCEL_EXPECTED_API_URL");
        if (expected == null) {
            throw new Skip("falta VERCEL_EXPECTED_API_URL (URL de la API que debe usar producción)");
        }
        Map<String, String> assets = browserAssets();
        String where = assets.entrySet().stream().filter(asset -> asset.getValue().contains(expected))
                .map(asset -> shortName(asset.getKey())).findFirst().orElse(null);
        check(where != null, "Ningún archivo del frontend contiene " + expected);
        String forbidden = Env.get("VERCEL_FORBIDDEN_API_URL");
        if (forbidden != null) {
            check(assets.values().stream().noneMatch(content -> content.contains(forbidden)), "Producción contiene la URL de otro ambiente: " + forbidden);
        }
        return expected + " encontrada en " + where + (forbidden == null ? "" : "; " + forbidden + " ausente");
    }

    String previewDeployments() throws Exception {
        requireApi();
        Http.Response response = api("/v6/deployments?projectId=" + Http.encode(projectId) + "&limit=50");
        check(response.status() == 200, "La API respondió " + response.brief());
        Map<String, Integer> byTarget = new java.util.TreeMap<>();
        Object deployments = response.json().get("deployments");
        if (deployments instanceof List<?> list) {
            for (Object deployment : list) {
                String target = Json.str(deployment, "target");
                String state = Json.str(deployment, "state");
                if (state == null) {
                    state = Json.str(deployment, "readyState");
                }
                byTarget.merge((target == null ? "preview" : target) + ":" + state, 1, Integer::sum);
            }
        }
        check(byTarget.getOrDefault("production:READY", 0) > 0, "No hay despliegue de producción listo: " + byTarget);
        check(byTarget.getOrDefault("preview:READY", 0) > 0, "No hay vistas previas listas: " + byTarget);
        return "Últimos despliegues por destino y estado: " + byTarget;
    }

    String environmentVariables() throws Exception {
        requireApi();
        Http.Response response = api("/v9/projects/" + Http.encode(projectId) + "/env");
        check(response.status() == 200, "La API respondió " + response.brief());
        List<Map.Entry<String, Set<String>>> entries = new ArrayList<>();
        Object envs = response.json().get("envs");
        if (envs instanceof List<?> list) {
            for (Object env : list) {
                Set<String> targets = new java.util.TreeSet<>();
                Object value = Json.at(env, "target");
                if (value instanceof List<?> values) {
                    values.forEach(target -> targets.add(String.valueOf(target)));
                } else if (value != null) {
                    targets.add(String.valueOf(value));
                }
                entries.add(Map.entry(Json.str(env, "key"), targets));
            }
        }
        check(!entries.isEmpty(), "El proyecto no tiene variables");
        Pattern secretName = Pattern.compile("SECRET|PRIVATE|PASSWORD|TOKEN|SERVICE_ACCOUNT");
        List<String> exposed = entries.stream().map(Map.Entry::getKey).distinct()
                .filter(key -> key.startsWith("NEXT_PUBLIC_") && secretName.matcher(key).find()).toList();
        check(exposed.isEmpty(), "Variables públicas con nombre de secreto: " + exposed);
        Set<String> environments = new java.util.TreeSet<>();
        entries.forEach(entry -> environments.addAll(entry.getValue()));
        boolean separated = entries.stream().anyMatch(entry -> entry.getValue().size() < environments.size());
        List<String> lines = new ArrayList<>();
        entries.forEach(entry -> lines.add(entry.getKey() + " " + entry.getValue()));
        check(environments.size() > 1 && separated, "Todas las variables aplican igual a todos los ambientes: " + lines);
        return "Ambientes " + environments + ". Variables (solo nombres): " + String.join(", ", lines);
    }

    String buildLogs() throws Exception {
        requireApi();
        Http.Response list = api("/v6/deployments?projectId=" + Http.encode(projectId) + "&target=production&limit=1");
        check(list.status() == 200, "La API respondió " + list.brief());
        String id = Json.str(list.json(), "deployments.0.uid");
        check(id != null, "No hay despliegues de producción");
        Http.Response events = api("/v3/deployments/" + id + "/events?limit=200");
        check(events.status() == 200, "Los registros respondieron " + events.brief());
        Object parsed = Json.parse(events.body());
        int count = parsed instanceof List<?> lines ? lines.size() : 0;
        check(count > 0, "El despliegue no tiene registros");
        return count + " líneas de registro de construcción en el despliegue " + id;
    }

    // ------------------------------------------------------------- utilidades

    private Map<String, String> cachedAssets;

    /** Descarga el HTML de las páginas públicas y los JavaScript de Next.js que referencian. */
    private Map<String, String> browserAssets() throws Exception {
        if (cachedAssets != null) {
            return cachedAssets;
        }
        Map<String, String> assets = new LinkedHashMap<>();
        Set<String> scripts = new LinkedHashSet<>();
        Pattern scriptPattern = Pattern.compile("[\"'(](/_next/static/[^\"'()\\s]+?\\.js)");
        for (String raw : Env.orDefault("VERCEL_PUBLIC_PATHS", "/").split(",")) {
            Http.Response page = Http.get(appUrl + raw.trim());
            assets.put(appUrl + raw.trim(), page.body());
            Matcher matcher = scriptPattern.matcher(page.body());
            while (matcher.find()) {
                scripts.add(matcher.group(1));
            }
        }
        int limit = Env.intOrDefault("VERCEL_MAX_SCRIPTS", 60);
        for (String script : scripts.stream().limit(limit).toList()) {
            Http.Response response = Http.get(appUrl + script);
            if (response.status() == 200) {
                assets.put(script, response.body());
            }
        }
        cachedAssets = assets;
        return assets;
    }

    private static String shortName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private Http.Response api(String path) throws Exception {
        String url = apiUrl + path + (teamId == null ? "" : (path.contains("?") ? "&" : "?") + "teamId=" + Http.encode(teamId));
        return Http.get(url, "Authorization", "Bearer " + token);
    }

    private void requireApp() {
        if (appUrl == null) {
            throw new Skip("falta la variable de entorno VERCEL_APP_URL");
        }
    }

    private void requireApi() {
        List<String> missing = Env.missing("VERCEL_TOKEN", "VERCEL_PROJECT_ID");
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
