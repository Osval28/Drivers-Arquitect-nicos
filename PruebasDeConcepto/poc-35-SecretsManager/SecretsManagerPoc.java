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
import java.util.Arrays;

/**
 * Prueba de concepto del ADR-35: AWS Secrets Manager como baúl de secretos.
 *
 * Usa la misma credencial de IAM que el backend tiene en Render y comprueba, solo con lecturas, que puede leer
 * los seis secretos con las claves que espera application-prod.properties, que no puede leer ni enumerar nada más,
 * que los parámetros no secretos están en Parameter Store y, con una credencial de auditoría, que CloudTrail
 * registra cada lectura. Nunca imprime ni guarda el valor de un secreto.
 */
public class SecretsManagerPoc {

    private static final String DEFAULT_SECRETS =
            "dazma/prod/database=username,password,host,port,dbname;"
                    + "dazma/prod/firebase=serviceAccount;"
                    + "dazma/prod/r2=accessKeyId,secretAccessKey;"
                    + "dazma/prod/observability=otlpEndpoint,otlpAuthorization;"
                    + "dazma/prod/resend=apiKey;"
                    + "dazma/prod/cache=url";

    private final String region;
    private final SigV4.Credentials backend;
    private final SigV4.Credentials auditor;
    private final Map<String, List<String>> expectedSecrets = new LinkedHashMap<>();
    private String backendArn;

    SecretsManagerPoc() {
        this.region = Env.get("AWS_REGION");
        this.backend = credentials("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN");
        this.auditor = credentials("AUDIT_AWS_ACCESS_KEY_ID", "AUDIT_AWS_SECRET_ACCESS_KEY", "AUDIT_AWS_SESSION_TOKEN");
        for (String entry : Env.orDefault("SECRETS_EXPECTED", DEFAULT_SECRETS).split(";")) {
            String[] parts = entry.split("=", 2);
            expectedSecrets.put(parts[0].trim(), parts.length < 2 ? List.of() : Arrays.stream(parts[1].split(",")).map(String::trim).toList());
        }
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: AWS Secrets Manager", "ADR-35");
        SecretsManagerPoc poc = new SecretsManagerPoc();
        report.context("Región", poc.region);
        report.context("Secretos esperados", String.join(", ", poc.expectedSecrets.keySet()));

        report.run("C1", "Identidad que usa el backend",
                "STS confirma qué usuario o rol de IAM representa la credencial configurada en Render",
                poc::callerIdentity);
        report.run("C2", "Lectura de los secretos del backend",
                "Los " + poc.expectedSecrets.size() + " secretos se leen y cada uno tiene las claves que espera application-prod.properties",
                poc::readExpectedSecrets);
        report.run("C3", "No puede leer secretos ajenos",
                "Leer un secreto fuera de su alcance responde AccessDeniedException (ESC-CAL-SEG-0008)",
                poc::cannotReadOtherSecrets);
        report.run("C4", "No puede enumerar el baúl",
                "ListSecrets responde AccessDeniedException: cada servicio conoce solo sus propias credenciales",
                poc::cannotListSecrets);
        report.run("C5", "Parámetros no secretos en Parameter Store",
                "GetParametersByPath sobre la ruta del ambiente devuelve al menos un parámetro",
                poc::parameterStore);
        report.run("C6", "Auditoría de accesos",
                "CloudTrail muestra lecturas GetSecretValue de los secretos de DAZMA hechas por la identidad del backend",
                poc::auditTrail);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String callerIdentity() throws Exception {
        requireBackend();
        URI uri = endpoint("sts");
        String body = "Action=GetCallerIdentity&Version=2011-06-15";
        Http.Response response = signedPost(backend, "sts", uri, Map.of("content-type", "application/x-www-form-urlencoded; charset=utf-8"), body);
        check(response.status() == 200, "STS respondió HTTP " + response.status() + " " + xml(response.body(), "Message"));
        backendArn = xml(response.body(), "Arn");
        return "Identidad " + maskAccount(backendArn);
    }

    String readExpectedSecrets() throws Exception {
        requireBackend();
        List<String> lines = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        long totalMillis = 0;
        for (Map.Entry<String, List<String>> secret : expectedSecrets.entrySet()) {
            Http.Response response = secretsManager(backend, "GetSecretValue", Map.of("SecretId", secret.getKey()));
            totalMillis += response.millis();
            if (response.status() != 200) {
                problems.add(secret.getKey() + ": " + awsError(response));
                continue;
            }
            String value = Json.str(response.json(), "SecretString");
            Map<String, Object> fields;
            try {
                fields = Json.object(value);
            } catch (RuntimeException notJson) {
                problems.add(secret.getKey() + ": el valor no es un objeto JSON");
                continue;
            }
            List<String> missing = secret.getValue().stream().filter(key -> !fields.containsKey(key)).toList();
            if (!missing.isEmpty()) {
                problems.add(secret.getKey() + ": faltan claves " + missing);
            }
            List<String> empty = fields.entrySet().stream()
                    .filter(field -> field.getValue() == null || String.valueOf(field.getValue()).isBlank())
                    .map(Map.Entry::getKey).toList();
            if (!empty.isEmpty()) {
                problems.add(secret.getKey() + ": claves vacías " + empty);
            }
            lines.add(secret.getKey() + " → claves " + String.join(", ", fields.keySet()) + " en " + response.millis() + " ms");
        }
        check(problems.isEmpty(), String.join("; ", problems));
        return expectedSecrets.size() + "/" + expectedSecrets.size() + " secretos completos; lectura total "
                + totalMillis + " ms al arrancar. " + String.join(" | ", lines);
    }

    String cannotReadOtherSecrets() throws Exception {
        requireBackend();
        String forbidden = Env.orDefault("SECRETS_FORBIDDEN_ID", "dazma/otro-servicio/poc-no-autorizado");
        Http.Response response = secretsManager(backend, "GetSecretValue", Map.of("SecretId", forbidden));
        String error = awsError(response);
        check(response.status() != 200, "La credencial del backend pudo leer " + forbidden);
        check(error.startsWith("AccessDenied"), "Se esperaba AccessDeniedException y llegó " + error
                + ". Un ResourceNotFound indica que la política permite intentar leer fuera de dazma/prod/");
        return forbidden + " → " + error;
    }

    String cannotListSecrets() throws Exception {
        requireBackend();
        Http.Response response = secretsManager(backend, "ListSecrets", Map.of("MaxResults", 1));
        String error = awsError(response);
        check(response.status() != 200, "La credencial del backend puede enumerar los secretos de la cuenta");
        check(error.startsWith("AccessDenied"), "Se esperaba AccessDeniedException y llegó " + error);
        return "ListSecrets → " + error;
    }

    String parameterStore() throws Exception {
        requireBackend();
        String path = Env.orDefault("PARAMETER_STORE_PATH", "/dazma/prod/");
        URI uri = endpoint("ssm");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Path", path);
        body.put("Recursive", true);
        body.put("WithDecryption", false);
        body.put("MaxResults", 10);
        Http.Response response = signedPost(backend, "ssm", uri, Map.of(
                "content-type", "application/x-amz-json-1.1",
                "x-amz-target", "AmazonSSM.GetParametersByPath"), Json.write(body));
        check(response.status() == 200, "Parameter Store respondió " + awsError(response));
        Object parameters = response.json().get("Parameters");
        List<String> names = new ArrayList<>();
        if (parameters instanceof List<?> list) {
            list.forEach(item -> names.add(Json.str(item, "Name") + " (" + Json.str(item, "Type") + ")"));
        }
        check(!names.isEmpty(), "No hay parámetros bajo " + path);
        return names.size() + " parámetros: " + String.join(", ", names);
    }

    String auditTrail() throws Exception {
        if (auditor == null) {
            throw new Skip("se activa con AUDIT_AWS_ACCESS_KEY_ID y AUDIT_AWS_SECRET_ACCESS_KEY (credencial con cloudtrail:LookupEvents)."
                    + " CloudTrail tarda hasta unos 15 minutos en mostrar los eventos");
        }
        requireBackend();
        long since = Instant.now().minus(Duration.ofMinutes(Env.intOrDefault("SECRETS_AUDIT_SINCE_MINUTES", 120))).getEpochSecond();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("LookupAttributes", List.of(Map.of("AttributeKey", "EventName", "AttributeValue", "GetSecretValue")));
        body.put("StartTime", since);
        body.put("MaxResults", 50);
        URI uri = endpoint("cloudtrail");
        Http.Response response = signedPost(auditor, "cloudtrail", uri, Map.of(
                "content-type", "application/x-amz-json-1.1",
                "x-amz-target", "com.amazonaws.cloudtrail.v20131101.CloudTrail_20131101.LookupEvents"), Json.write(body));
        check(response.status() == 200, "CloudTrail respondió " + awsError(response));
        List<String> found = new ArrayList<>();
        Object events = response.json().get("Events");
        if (events instanceof List<?> list) {
            for (Object event : list) {
                Map<String, Object> detail = Json.object(Json.str(event, "CloudTrailEvent"));
                String secretId = Json.str(detail, "requestParameters.secretId");
                String who = Json.str(detail, "userIdentity.arn");
                if (secretId != null && secretId.contains("dazma/") && (backendArn == null || backendArn.equals(who))) {
                    Object time = Json.at(event, "EventTime");
                    String when = time instanceof Number seconds
                            ? Instant.ofEpochMilli((long) (seconds.doubleValue() * 1000)).toString() : String.valueOf(time);
                    found.add(when + " " + secretId);
                }
            }
        }
        check(!found.isEmpty(), "No hay lecturas registradas de la identidad del backend en la ventana consultada;"
                + " si la prueba acaba de ejecutarse, repetirla en unos minutos");
        return found.size() + " lecturas registradas, por ejemplo: " + String.join("; ", found.subList(0, Math.min(3, found.size())))
                + ". Identidad: " + maskAccount(backendArn);
    }

    // ------------------------------------------------------------- utilidades

    private Http.Response secretsManager(SigV4.Credentials credentials, String action, Map<String, Object> body) throws Exception {
        URI uri = endpoint("secretsmanager");
        return signedPost(credentials, "secretsmanager", uri, Map.of(
                "content-type", "application/x-amz-json-1.1",
                "x-amz-target", "secretsmanager." + action), Json.write(body));
    }

    private Http.Response signedPost(SigV4.Credentials credentials, String service, URI uri, Map<String, String> headers, String body)
            throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Map<String, String> signed = SigV4.sign(credentials, region, service, "POST", uri, headers, SigV4.sha256Hex(bytes), Instant.now());
        HttpRequest.Builder builder = Http.to(uri.toString()).POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        signed.forEach(builder::header);
        return Http.send(builder.build());
    }

    /** Endpoint público de cada servicio; AWS_ENDPOINT_URL lo reemplaza, como en los SDK de AWS. */
    private URI endpoint(String service) {
        String override = Env.get("AWS_ENDPOINT_URL");
        return URI.create(override != null ? Http.trimSlash(override) + "/" : "https://" + service + "." + region + ".amazonaws.com/");
    }

    private static String awsError(Http.Response response) {
        if (response.status() == 200) {
            return "HTTP 200";
        }
        try {
            String type = Json.str(response.json(), "__type");
            if (type != null && type.contains("#")) {
                type = type.substring(type.indexOf('#') + 1);
            }
            return (type == null ? "HTTP " + response.status() : type);
        } catch (RuntimeException notJson) {
            String code = xml(response.body(), "Code");
            return code.isEmpty() ? "HTTP " + response.status() : code;
        }
    }

    private static String xml(String body, String tag) {
        if (body == null) {
            return "";
        }
        int start = body.indexOf("<" + tag + ">");
        int end = body.indexOf("</" + tag + ">");
        return start >= 0 && end > start ? body.substring(start + tag.length() + 2, end) : "";
    }

    /** Oculta el número de cuenta en un ARN. */
    private static String maskAccount(String arn) {
        return arn == null ? "(desconocida)" : arn.replaceAll(":(\\d{4})\\d{4}(\\d{4}):", ":$1****$2:");
    }

    private static SigV4.Credentials credentials(String keyVariable, String secretVariable, String tokenVariable) {
        String key = Env.get(keyVariable);
        String secret = Env.get(secretVariable);
        return key == null || secret == null ? null : new SigV4.Credentials(key, secret, Env.get(tokenVariable));
    }

    private void requireBackend() {
        List<String> missing = Env.missing("AWS_REGION", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY");
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
