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
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Prueba de concepto del ADR-13: Cloudflare R2 como almacenamiento de fotos de publicaciones.
 *
 * Reproduce contra un bucket real el ciclo que implementa CloudflareR2PublicationPhotoStorageAdapter:
 * carga directa con URL prefirmada (tipo y tamaño firmados), verificación con HEAD, promoción con COPY,
 * descarga privada con URL prefirmada y limpieza. Solo usa operaciones S3 soportadas por R2.
 */
public class CloudflareR2Poc {

    private static final String REGION = "auto";
    private static final String SERVICE = "s3";
    private static final String CONTENT_TYPE = "image/jpeg";

    private final String endpoint;
    private final String bucket;
    private final SigV4.Credentials credentials;
    private final long uploadValidity;
    private final long downloadValidity;
    private final byte[] photo;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID photoId = UUID.randomUUID();
    private final String pendingKey;
    private final String publishedKey;
    private int classA;
    private int classB;
    private boolean uploaded;
    private boolean promoted;

    CloudflareR2Poc() {
        String explicitEndpoint = Env.get("R2_ENDPOINT");
        String accountId = Env.get("R2_ACCOUNT_ID");
        this.endpoint = explicitEndpoint != null ? Http.trimSlash(explicitEndpoint)
                : accountId != null ? "https://" + accountId + ".r2.cloudflarestorage.com" : null;
        this.bucket = Env.get("R2_BUCKET");
        String accessKey = Env.get("R2_ACCESS_KEY_ID");
        String secretKey = Env.get("R2_SECRET_ACCESS_KEY");
        this.credentials = accessKey == null || secretKey == null ? null : new SigV4.Credentials(accessKey, secretKey, null);
        this.uploadValidity = Env.intOrDefault("R2_UPLOAD_VALIDITY_SECONDS", 300);
        this.downloadValidity = Env.intOrDefault("R2_DOWNLOAD_VALIDITY_SECONDS", 600);
        this.photo = new byte[Env.intOrDefault("R2_TEST_OBJECT_KB", 256) * 1024];
        new SecureRandom().nextBytes(photo);
        String prefix = Env.orDefault("R2_KEY_PREFIX", "poc");
        this.pendingKey = prefix + "/pending/" + ownerId + "/" + photoId;
        this.publishedKey = prefix + "/publications/" + ownerId + "/" + UUID.randomUUID();
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: Cloudflare R2", "ADR-13");
        CloudflareR2Poc poc = new CloudflareR2Poc();
        report.context("Endpoint", poc.endpoint);
        report.context("Bucket", poc.bucket);
        report.context("Objeto de prueba", poc.photo.length / 1024 + " KB, " + CONTENT_TYPE);

        report.run("C1", "Carga directa con URL prefirmada",
                "PUT con el tipo y el tamaño firmados responde 200 (vigencia " + poc.uploadValidity + " s, como dazma.publications.photo-upload.validity)",
                poc::presignedUpload);
        report.run("C2", "Carga con otro tipo de contenido",
                "La misma URL rechaza un Content-Type distinto al firmado (403)",
                poc::rejectsOtherContentType);
        report.run("C3", "Carga con otro tamaño",
                "La misma URL rechaza un archivo de tamaño distinto al firmado (403)",
                poc::rejectsOtherSize);
        report.run("C4", "Permiso vencido",
                "Una URL prefirmada de 1 s se rechaza después de vencer",
                poc::rejectsExpiredUrl);
        report.run("C5", "Vigencia configurable hasta 7 días",
                "Una URL de 604800 s funciona y una de 604801 s se rechaza",
                poc::maximumValidity);
        report.run("C6", "Verificación y promoción de la foto",
                "HEAD confirma tipo y tamaño en pending/ y COPY la deja en publications/ con el mismo tamaño",
                poc::headAndCopy);
        report.run("C7", "Archivos privados por defecto",
                "Una lectura sin firma del objeto publicado no devuelve el archivo",
                poc::privateByDefault);
        report.run("C8", "Lectura temporal",
                "Una URL prefirmada de " + poc.downloadValidity + " s devuelve exactamente el mismo archivo (SHA-256 igual)",
                poc::presignedDownload);
        report.run("C9", "Lecturas repetidas del marketplace",
                "El 100 % de las lecturas repetidas responde 200; se reportan latencia, volumen descargado y operaciones",
                poc::repeatedReads);
        report.run("C10", "Limpieza",
                "Los objetos de prueba se eliminan y HEAD responde 404",
                poc::cleanup);
        report.context("Operaciones contadas", "clase A=" + poc.classA + ", clase B=" + poc.classB);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String presignedUpload() throws Exception {
        String url = presignUpload(pendingKey, CONTENT_TYPE, photo.length, uploadValidity);
        Http.Response response = put(url, CONTENT_TYPE, photo);
        classA++;
        check(response.status() == 200, "La carga respondió " + response.brief());
        uploaded = true;
        return "HTTP 200 en " + response.millis() + " ms; firma incluye content-length;content-type;host";
    }

    String rejectsOtherContentType() throws Exception {
        requireConfig();
        String url = presignUpload(pendingKey + "-tipo", CONTENT_TYPE, photo.length, uploadValidity);
        Http.Response response = put(url, "application/x-msdownload", photo);
        classA++;
        check(response.status() == 403, "Se esperaba 403 y llegó " + response.brief());
        return "HTTP 403 " + s3Code(response);
    }

    String rejectsOtherSize() throws Exception {
        requireConfig();
        String url = presignUpload(pendingKey + "-tamano", CONTENT_TYPE, photo.length, uploadValidity);
        byte[] bigger = new byte[photo.length + 1024];
        Http.Response response = put(url, CONTENT_TYPE, bigger);
        classA++;
        check(response.status() == 403, "Se esperaba 403 y llegó " + response.brief());
        return "HTTP 403 " + s3Code(response);
    }

    String rejectsExpiredUrl() throws Exception {
        requireConfig();
        String url = presignUpload(pendingKey + "-vencida", CONTENT_TYPE, photo.length, 1);
        Thread.sleep(3_000);
        Http.Response response = put(url, CONTENT_TYPE, photo);
        classA++;
        check(response.status() == 403, "Se esperaba 403 y llegó " + response.brief());
        return "HTTP 403 " + s3Code(response) + " tres segundos después";
    }

    String maximumValidity() throws Exception {
        requireUploaded();
        Http.Response sevenDays = Http.send(Http.to(presignGet(pendingKey, 604_800)).GET().build());
        Http.Response overLimit = Http.send(Http.to(presignGet(pendingKey, 604_801)).GET().build());
        classB += 2;
        check(sevenDays.status() == 200, "La URL de 7 días respondió " + sevenDays.status());
        check(overLimit.status() >= 400, "La URL de 7 días + 1 s fue aceptada");
        return "604800 s → HTTP 200; 604801 s → HTTP " + overLimit.status() + " " + s3Code(overLimit);
    }

    String headAndCopy() throws Exception {
        requireUploaded();
        Http.Response head = signed("HEAD", pendingKey, Map.of());
        classB++;
        check(head.status() == 200, "HEAD respondió " + head.status());
        String type = head.header("content-type");
        long length = Long.parseLong(head.header("content-length"));
        check(CONTENT_TYPE.equals(type) && length == photo.length,
                "HEAD devolvió " + type + " / " + length + " bytes");

        Http.Response copy = signed("PUT", publishedKey, Map.of("x-amz-copy-source", "/" + bucket + "/" + path(pendingKey)));
        classA++;
        check(copy.status() == 200, "COPY respondió " + copy.brief());
        promoted = true;

        Http.Response publishedHead = signed("HEAD", publishedKey, Map.of());
        classB++;
        check(publishedHead.status() == 200 && Long.parseLong(publishedHead.header("content-length")) == photo.length,
                "El objeto publicado no tiene el tamaño esperado");
        return "HEAD " + type + " " + length + " bytes (" + head.millis() + " ms); COPY " + copy.millis() + " ms";
    }

    String privateByDefault() throws Exception {
        requirePromoted();
        Http.Response response = Http.get(objectUrl(publishedKey));
        classB++;
        check(response.status() != 200, "El objeto se pudo leer sin firma");
        return "Lectura sin firma → HTTP " + response.status() + " " + s3Code(response);
    }

    String presignedDownload() throws Exception {
        requirePromoted();
        HttpResponse<byte[]> response = Http.CLIENT.send(Http.to(presignGet(publishedKey, downloadValidity)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        classB++;
        check(response.statusCode() == 200, "La descarga respondió " + response.statusCode());
        String expected = SigV4.sha256Hex(photo);
        String actual = SigV4.sha256Hex(response.body());
        check(expected.equals(actual), "El archivo descargado no coincide con el subido");
        return "HTTP 200, " + response.body().length + " bytes, SHA-256 igual";
    }

    String repeatedReads() throws Exception {
        requirePromoted();
        int reads = Env.intOrDefault("R2_REPEATED_READS", 30);
        List<Long> millis = new ArrayList<>();
        long bytes = 0;
        int ok = 0;
        for (int i = 0; i < reads; i++) {
            long start = System.nanoTime();
            HttpResponse<byte[]> response = Http.CLIENT.send(Http.to(presignGet(publishedKey, downloadValidity)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            millis.add((System.nanoTime() - start) / 1_000_000);
            classB++;
            if (response.statusCode() == 200) {
                ok++;
                bytes += response.body().length;
            }
        }
        check(ok == reads, ok + " de " + reads + " lecturas respondieron 200");
        return reads + "/" + reads + " lecturas, " + bytes / 1024 + " KB descargados. " + Stats.summary(millis);
    }

    String cleanup() throws Exception {
        requireConfig();
        for (String rejected : List.of(pendingKey + "-tipo", pendingKey + "-tamano", pendingKey + "-vencida")) {
            signed("DELETE", rejected, Map.of());
        }
        List<String> results = new ArrayList<>();
        Map<String, String> objects = new LinkedHashMap<>();
        objects.put("pending", pendingKey);
        objects.put("publications", publishedKey);
        for (Map.Entry<String, String> object : objects.entrySet()) {
            Http.Response delete = signed("DELETE", object.getValue(), Map.of());
            Http.Response head = signed("HEAD", object.getValue(), Map.of());
            classB++;
            results.add(object.getKey() + "/: DELETE " + delete.status() + ", HEAD " + head.status());
            check(head.status() == 404, "El objeto " + object.getValue() + " sigue existiendo");
        }
        return String.join("; ", results);
    }

    // ------------------------------------------------------------- utilidades

    private String presignUpload(String key, String contentType, long length, long validity) {
        requireConfig();
        return SigV4.presign(credentials, REGION, SERVICE, "PUT", URI.create(objectUrl(key)),
                Map.of("content-type", contentType, "content-length", Long.toString(length)), validity, Instant.now());
    }

    private String presignGet(String key, long validity) {
        return SigV4.presign(credentials, REGION, SERVICE, "GET", URI.create(objectUrl(key)), Map.of(), validity, Instant.now());
    }

    private static Http.Response put(String url, String contentType, byte[] body) throws Exception {
        return Http.send(Http.to(url).header("Content-Type", contentType).PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build());
    }

    private Http.Response signed(String method, String key, Map<String, String> headers) throws Exception {
        URI uri = URI.create(objectUrl(key));
        String emptyHash = SigV4.sha256Hex(new byte[0]);
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.put("x-amz-content-sha256", emptyHash);
        Map<String, String> signedHeaders = SigV4.sign(credentials, REGION, SERVICE, method, uri, all, emptyHash, Instant.now());
        HttpRequest.Builder builder = Http.to(uri.toString()).method(method, HttpRequest.BodyPublishers.noBody());
        signedHeaders.forEach(builder::header);
        return Http.send(builder.build());
    }

    private String objectUrl(String key) {
        return endpoint + "/" + bucket + "/" + path(key);
    }

    private static String path(String key) {
        StringBuilder out = new StringBuilder();
        for (String segment : key.split("/")) {
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(SigV4.encode(segment, true));
        }
        return out.toString();
    }

    private static String s3Code(Http.Response response) {
        String body = response.body() == null ? "" : response.body();
        int start = body.indexOf("<Code>");
        int end = body.indexOf("</Code>");
        return start >= 0 && end > start ? "(" + body.substring(start + 6, end) + ")" : "";
    }

    private void requireConfig() {
        List<String> missing = new ArrayList<>();
        if (endpoint == null) {
            missing.add("R2_ACCOUNT_ID o R2_ENDPOINT");
        }
        missing.addAll(Env.missing("R2_BUCKET", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY"));
        if (!missing.isEmpty()) {
            throw new Skip("faltan variables de entorno: " + String.join(", ", missing));
        }
    }

    private void requireUploaded() {
        requireConfig();
        if (!uploaded) {
            throw new Skip("la foto de prueba no se subió (C1 no se completó)");
        }
    }

    private void requirePromoted() {
        requireUploaded();
        if (!promoted) {
            throw new Skip("la foto no se promovió (C6 no se completó)");
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
