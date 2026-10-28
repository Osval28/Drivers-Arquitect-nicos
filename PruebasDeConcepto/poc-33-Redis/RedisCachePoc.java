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
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSocketFactory;

/**
 * Prueba de concepto del ADR-33: Redis administrado como memoria caché.
 *
 * Se conecta al Redis contratado con la misma URL que usa el backend (cache.url) y comprueba los factores del ADR:
 * tipos de datos, vencimiento de 10 minutos con prefijo versionado, persistencia, política de memoria, latencia,
 * el patrón cache-aside con invalidación y que una caída de Redis no tumba la consulta.
 */
public class RedisCachePoc {

    private final String url;
    private final String prefix;
    private final int ttlSeconds;
    private final int timeoutMillis;
    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private final List<String> createdKeys = new ArrayList<>();

    RedisCachePoc() {
        this.url = Env.get("REDIS_URL");
        this.prefix = Env.orDefault("REDIS_KEY_PREFIX", "dazma:poc:publications:v1:");
        this.ttlSeconds = Env.intOrDefault("REDIS_TTL_SECONDS", 600);
        this.timeoutMillis = Env.intOrDefault("REDIS_TIMEOUT_MS", 500);
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: Redis como caché", "ADR-33");
        RedisCachePoc poc = new RedisCachePoc();
        report.context("Redis", poc.url == null ? null : Redis.describe(poc.url));
        report.context("Prefijo de claves", poc.prefix);
        report.context("Tiempo máximo de espera", poc.timeoutMillis + " ms");

        report.run("C1", "Conexión autenticada",
                "La conexión con la URL del backend responde PING; se informa versión y si usa TLS",
                poc::connect);
        report.run("C2", "Varios tipos de datos",
                "Texto, objeto (hash), lista, conjunto y contador se guardan y se leen correctamente",
                poc::dataTypes);
        report.run("C3", "Vencimiento de 10 minutos",
                "Una entrada guardada con prefijo versionado queda con TTL entre " + (poc.ttlSeconds - 10) + " y " + poc.ttlSeconds + " s",
                poc::expiration);
        report.run("C4", "Copia en disco",
                "El servidor informa persistencia activa (AOF o instantáneas RDB)",
                poc::persistence);
        report.run("C5", "Control de memoria",
                "Hay límite de memoria y una política que elimina entradas en vez de rechazar escrituras; se informan las entradas eliminadas",
                poc::memoryPolicy);
        report.run("C6", "Latencia de lectura",
                "El p95 de las lecturas desde este equipo es menor o igual al umbral configurado",
                poc::readLatency);
        report.run("C7", "Cache-aside con invalidación",
                "Primera lectura va a la base de datos, la segunda sale de Redis; al cambiar la publicación se invalida y se lee el dato nuevo",
                poc::cacheAside);
        report.run("C8", "Redis caído no tumba la consulta",
                "Con Redis inalcanzable la lectura responde desde la base de datos dentro del tiempo máximo de espera (ESC-CAL-CON-0011)",
                poc::fallbackWhenUnavailable);
        report.run("C9", "El backend desplegado usa la caché",
                "Después de consultar una publicación en el backend, existe su clave con vencimiento de 10 minutos o menos",
                poc::backendUsesCache);
        report.run("C10", "Limpieza",
                "Las claves creadas por la prueba se eliminan",
                poc::cleanup);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String connect() throws Exception {
        try (Redis redis = open()) {
            check("PONG".equals(redis.command("PING")), "PING no respondió PONG");
            String server = redis.infoField("server", "redis_version");
            return "PONG; versión " + (server == null ? "no informada" : server) + "; TLS " + (redis.tls ? "sí" : "no")
                    + "; conexión en " + redis.connectMillis + " ms";
        }
    }

    String dataTypes() throws Exception {
        try (Redis redis = open()) {
            String base = key("tipos");
            redis.command("SET", base + ":texto", "Bicicleta de ruta talla M");
            redis.command("HSET", base + ":objeto", "titulo", "Bicicleta", "precio", "850000", "estado", "PUBLICADA");
            redis.command("RPUSH", base + ":lista", "foto-1", "foto-2", "foto-3");
            redis.command("SADD", base + ":conjunto", "deportes", "ciclismo", "deportes");
            redis.command("INCRBY", base + ":contador", "3");
            redis.command("INCR", base + ":contador");
            List.of(":texto", ":objeto", ":lista", ":conjunto", ":contador").forEach(suffix -> createdKeys.add(base + suffix));

            check("Bicicleta de ruta talla M".equals(redis.command("GET", base + ":texto")), "Texto distinto");
            check("850000".equals(redis.command("HGET", base + ":objeto", "precio")), "Campo del objeto distinto");
            check(List.of("foto-1", "foto-2", "foto-3").equals(redis.command("LRANGE", base + ":lista", "0", "-1")), "Lista distinta");
            check(Long.valueOf(2).equals(redis.command("SCARD", base + ":conjunto")), "El conjunto no eliminó el repetido");
            check("4".equals(String.valueOf(redis.command("GET", base + ":contador"))), "Contador distinto");
            return "5/5 tipos correctos (string, hash, list, set, contador)";
        }
    }

    String expiration() throws Exception {
        try (Redis redis = open()) {
            String key = key("publicacion-" + UUID.randomUUID());
            createdKeys.add(key);
            redis.command("SET", key, "{\"id\":\"x\"}", "EX", Integer.toString(ttlSeconds));
            long ttl = (Long) redis.command("TTL", key);
            check(ttl > ttlSeconds - 10 && ttl <= ttlSeconds, "TTL inesperado: " + ttl);
            return "TTL " + ttl + " s en " + key.substring(0, prefix.length()) + "…";
        }
    }

    String persistence() throws Exception {
        try (Redis redis = open()) {
            Map<String, String> config = redis.config("save", "appendonly");
            if (!config.isEmpty()) {
                String save = config.getOrDefault("save", "");
                String appendOnly = config.getOrDefault("appendonly", "no");
                check("yes".equals(appendOnly) || !save.isBlank(),
                        "Persistencia desactivada en la configuración: appendonly=" + appendOnly + ", save=\"" + save + "\"");
                return "Configuración: appendonly=" + appendOnly + ", save=\"" + save + "\"";
            }
            Map<String, String> info = redis.info("persistence");
            if (info.isEmpty()) {
                throw new Skip("el proveedor no expone CONFIG ni INFO persistence; revisar la persistencia en su consola");
            }
            String aof = info.getOrDefault("aof_enabled", "0");
            String saves = info.getOrDefault("rdb_saves", "0");
            String lastBgsave = info.getOrDefault("rdb_last_bgsave_time_sec", "-1");
            boolean rdb = !"0".equals(saves) || !"-1".equals(lastBgsave);
            check("1".equals(aof) || rdb, "Sin persistencia visible: aof_enabled=" + aof + ", rdb_saves=" + saves
                    + ", rdb_last_bgsave_time_sec=" + lastBgsave + " (CONFIG no disponible; confirmar en la consola del proveedor)");
            return "INFO (CONFIG restringido): aof_enabled=" + aof + ", rdb_saves=" + saves + ", rdb_last_bgsave_time_sec=" + lastBgsave;
        }
    }

    String memoryPolicy() throws Exception {
        try (Redis redis = open()) {
            Map<String, String> memory = redis.info("memory");
            Map<String, String> stats = redis.info("stats");
            if (memory.isEmpty()) {
                throw new Skip("el proveedor no expone INFO memory");
            }
            String policy = memory.getOrDefault("maxmemory_policy", "?");
            String max = memory.getOrDefault("maxmemory_human", memory.getOrDefault("maxmemory", "?"));
            String used = memory.getOrDefault("used_memory_human", "?");
            String evicted = stats.getOrDefault("evicted_keys", "?");
            check(!"noeviction".equals(policy), "La política es noeviction: con la memoria llena Redis rechazaría escrituras."
                    + " Para caché conviene allkeys-lru o volatile-lru. Memoria usada " + used + " de " + max);
            return "maxmemory_policy=" + policy + ", usada " + used + " de " + max + ", evicted_keys=" + evicted;
        }
    }

    String readLatency() throws Exception {
        int samples = Env.intOrDefault("REDIS_SAMPLES", 200);
        long threshold = Env.intOrDefault("REDIS_MAX_P95_MS", 50);
        try (Redis redis = open()) {
            String key = key("latencia");
            createdKeys.add(key);
            redis.command("SET", key, "x".repeat(2048), "EX", Integer.toString(ttlSeconds));
            List<Long> micros = new ArrayList<>();
            for (int i = 0; i < samples; i++) {
                long start = System.nanoTime();
                redis.command("GET", key);
                micros.add((System.nanoTime() - start) / 1_000);
            }
            List<Long> millis = micros.stream().map(m -> m / 1000).toList();
            long p95 = Stats.percentile(millis, 95);
            check(p95 <= threshold, "p95 " + p95 + " ms supera " + threshold + " ms");
            return samples + " lecturas de 2 KB: p50=" + Stats.percentile(micros, 50) / 1000.0 + " ms, p95="
                    + Stats.percentile(micros, 95) / 1000.0 + " ms, máx=" + Collections.max(micros) / 1000.0 + " ms";
        }
    }

    String cacheAside() throws Exception {
        try (Redis redis = open()) {
            PublicationRepository database = new PublicationRepository(Env.intOrDefault("REDIS_DB_LATENCY_MS", 30));
            PublicationReader reader = new PublicationReader(() -> redis, database, prefix + runId + ":", ttlSeconds);
            String id = UUID.randomUUID().toString();
            database.save(id, "{\"titulo\":\"Guitarra acústica\",\"precio\":420000}");
            createdKeys.add(prefix + runId + ":" + id);

            String first = reader.find(id);
            String second = reader.find(id);
            check(database.reads.get() == 1, "La segunda lectura volvió a la base de datos");
            check(first.equals(second), "La copia en caché no coincide con la base de datos");

            database.save(id, "{\"titulo\":\"Guitarra acústica\",\"precio\":390000}");
            reader.invalidate(id);
            String afterUpdate = reader.find(id);
            check(afterUpdate.contains("390000"), "Después de invalidar se leyó el dato viejo");
            check(database.reads.get() == 2, "La lectura después de invalidar no fue a la base de datos");
            return "Lecturas a la base de datos: 1 (fallo de caché) + 0 (acierto) + 1 (tras invalidar). Aciertos " + reader.hits
                    + ", fallos " + reader.misses + ". Tiempo de acierto " + reader.lastHitMicros / 1000.0
                    + " ms frente a " + database.latencyMillis + " ms simulados de la base de datos";
        }
    }

    String fallbackWhenUnavailable() throws Exception {
        String unreachable = Env.orDefault("REDIS_UNREACHABLE_URL", "redis://10.255.255.1:6379");
        PublicationRepository database = new PublicationRepository(Env.intOrDefault("REDIS_DB_LATENCY_MS", 30));
        PublicationReader reader = new PublicationReader(() -> Redis.connect(unreachable, timeoutMillis), database,
                prefix + runId + ":", ttlSeconds);
        String id = UUID.randomUUID().toString();
        database.save(id, "{\"titulo\":\"Patineta\"}");
        long start = System.nanoTime();
        String value = reader.find(id);
        long millis = (System.nanoTime() - start) / 1_000_000;
        check(value.contains("Patineta"), "No se obtuvo la publicación");
        check(reader.failures == 1, "No se registró la falla de Redis");
        long budget = timeoutMillis + database.latencyMillis + 500L;
        check(millis <= budget, "Tardó " + millis + " ms; el máximo esperado era " + budget + " ms");
        return "Respuesta desde la base de datos en " + millis + " ms (límite " + budget + " ms); falla registrada: "
                + reader.lastFailure;
    }

    String backendUsesCache() throws Exception {
        String endpoint = Env.get("REDIS_BACKEND_PUBLICATION_URL");
        if (endpoint == null) {
            throw new Skip("se activa con REDIS_BACKEND_PUBLICATION_URL (URL del detalle de una publicación existente)");
        }
        String publicationId = Env.require("REDIS_BACKEND_PUBLICATION_ID");
        String backendPrefix = Env.orDefault("REDIS_BACKEND_KEY_PREFIX", "dazma:publications:v1:");
        try (Redis redis = open()) {
            String cacheKey = backendPrefix + publicationId;
            redis.command("DEL", cacheKey);
            HttpRequest.Builder request = Http.to(endpoint).GET();
            String cookie = Env.get("REDIS_BACKEND_COOKIE");
            if (cookie != null) {
                request.header("Cookie", cookie);
            }
            Http.Response first = Http.send(request.build());
            check(first.status() == 200, "El backend respondió " + first.status());
            Object ttl = redis.command("TTL", cacheKey);
            Http.Response second = Http.send(request.build());
            check(ttl instanceof Long seconds && seconds > 0 && seconds <= 600, "La clave " + cacheKey + " no quedó en Redis con TTL (TTL=" + ttl + ")");
            return "Clave creada con TTL " + ttl + " s. Primera consulta " + first.millis() + " ms (base de datos), segunda "
                    + second.millis() + " ms (caché)";
        }
    }

    String cleanup() throws Exception {
        if (createdKeys.isEmpty()) {
            throw new Skip("no se crearon claves");
        }
        try (Redis redis = open()) {
            String[] keys = createdKeys.toArray(new String[0]);
            String[] arguments = new String[keys.length + 1];
            arguments[0] = "DEL";
            System.arraycopy(keys, 0, arguments, 1, keys.length);
            Object deleted = redis.command(arguments);
            String[] exists = new String[keys.length + 1];
            exists[0] = "EXISTS";
            System.arraycopy(keys, 0, exists, 1, keys.length);
            check(Long.valueOf(0).equals(redis.command(exists)), "Quedaron claves de la prueba");
            return deleted + " claves eliminadas";
        }
    }

    // --------------------------------------------------- cache-aside de DAZMA

    interface RedisSource {
        Redis get() throws Exception;
    }

    /** Equivale al repositorio de PostgreSQL; la latencia es un parámetro de la prueba. */
    static final class PublicationRepository {
        final Map<String, String> rows = new ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        final long latencyMillis;

        PublicationRepository(long latencyMillis) {
            this.latencyMillis = latencyMillis;
        }

        void save(String id, String json) {
            rows.put(id, json);
        }

        String find(String id) throws InterruptedException {
            reads.incrementAndGet();
            Thread.sleep(latencyMillis);
            return rows.get(id);
        }
    }

    /** Mismo comportamiento que RedisPublicationCacheAdapter: busca en Redis, si no está consulta y guarda con TTL. */
    static final class PublicationReader {
        private final RedisSource redis;
        private final PublicationRepository database;
        private final String keyPrefix;
        private final int ttlSeconds;
        int hits;
        int misses;
        int failures;
        long lastHitMicros;
        String lastFailure;

        PublicationReader(RedisSource redis, PublicationRepository database, String keyPrefix, int ttlSeconds) {
            this.redis = redis;
            this.database = database;
            this.keyPrefix = keyPrefix;
            this.ttlSeconds = ttlSeconds;
        }

        String find(String id) throws Exception {
            Redis connection = null;
            try {
                long start = System.nanoTime();
                connection = redis.get();
                Object cached = connection.command("GET", keyPrefix + id);
                if (cached instanceof String json) {
                    hits++;
                    lastHitMicros = (System.nanoTime() - start) / 1_000;
                    return json;
                }
            } catch (Exception failure) {
                failures++;
                lastFailure = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                connection = null;
            }
            misses++;
            String json = database.find(id);
            if (connection != null && json != null) {
                try {
                    connection.command("SET", keyPrefix + id, json, "EX", Integer.toString(ttlSeconds));
                } catch (Exception failure) {
                    failures++;
                    lastFailure = failure.getMessage();
                }
            }
            return json;
        }

        void invalidate(String id) throws Exception {
            redis.get().command("DEL", keyPrefix + id);
        }
    }

    // ------------------------------------------------------- cliente RESP

    private Redis open() throws Exception {
        if (url == null) {
            throw new Skip("falta la variable de entorno REDIS_URL (redis:// o rediss://usuario:clave@host:puerto)");
        }
        return Redis.connect(url, timeoutMillis);
    }

    private String key(String suffix) {
        return prefix + runId + ":" + suffix;
    }

    /** Cliente mínimo del protocolo RESP2 de Redis, con TLS para URLs rediss://. */
    static final class Redis implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        final boolean tls;
        final long connectMillis;

        private Redis(Socket socket, boolean tls, long connectMillis) throws IOException {
            this.socket = socket;
            this.in = new BufferedInputStream(socket.getInputStream());
            this.out = socket.getOutputStream();
            this.tls = tls;
            this.connectMillis = connectMillis;
        }

        static Redis connect(String url, int timeoutMillis) throws Exception {
            URI uri = URI.create(url);
            boolean tls = "rediss".equalsIgnoreCase(uri.getScheme());
            int port = uri.getPort() == -1 ? 6379 : uri.getPort();
            long start = System.nanoTime();
            Socket plain = new Socket();
            plain.connect(new InetSocketAddress(uri.getHost(), port), timeoutMillis);
            plain.setSoTimeout(timeoutMillis);
            Socket socket = plain;
            if (tls) {
                javax.net.ssl.SSLSocket secure = (javax.net.ssl.SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(plain, uri.getHost(), port, true);
                javax.net.ssl.SSLParameters parameters = secure.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                secure.setSSLParameters(parameters);
                secure.startHandshake();
                socket = secure;
            }
            Redis redis = new Redis(socket, tls, (System.nanoTime() - start) / 1_000_000);
            String userInfo = uri.getRawUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                String decoded = java.net.URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                Object result = colon < 0 ? redis.command("AUTH", decoded)
                        : colon == 0 ? redis.command("AUTH", decoded.substring(1))
                        : redis.command("AUTH", decoded.substring(0, colon), decoded.substring(colon + 1));
                check("OK".equals(result), "Redis rechazó la autenticación");
            }
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                redis.command("SELECT", path.substring(1));
            }
            return redis;
        }

        static String describe(String url) {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + ":" + (uri.getPort() == -1 ? 6379 : uri.getPort());
        }

        Object command(String... arguments) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            buffer.write(("*" + arguments.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String argument : arguments) {
                byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
                buffer.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
                buffer.write(bytes);
                buffer.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(buffer.toByteArray());
            out.flush();
            Object reply = read();
            if (reply instanceof RedisError error) {
                throw new IOException("Redis respondió " + error.message());
            }
            return reply;
        }

        Map<String, String> info(String section) throws IOException {
            Map<String, String> values = new LinkedHashMap<>();
            Object reply;
            try {
                reply = command("INFO", section);
            } catch (IOException restricted) {
                return values;
            }
            for (String line : String.valueOf(reply).split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && !line.startsWith("#")) {
                    values.put(line.substring(0, colon), line.substring(colon + 1).trim());
                }
            }
            return values;
        }

        /** CONFIG GET de varios parámetros; vacío si el proveedor no permite CONFIG. */
        Map<String, String> config(String... names) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String name : names) {
                try {
                    Object reply = command("CONFIG", "GET", name);
                    if (reply instanceof List<?> pair && pair.size() == 2) {
                        values.put(String.valueOf(pair.get(0)), String.valueOf(pair.get(1)));
                    }
                } catch (IOException restricted) {
                    return Map.of();
                }
            }
            return values;
        }

        String infoField(String section, String field) throws IOException {
            return info(section).get(field);
        }

        record RedisError(String message) {
        }

        private Object read() throws IOException {
            int type = in.read();
            if (type == -1) {
                throw new IOException("Redis cerró la conexión");
            }
            String line = readLine();
            return switch (type) {
                case '+' -> line;
                case '-' -> new RedisError(line);
                case ':' -> Long.parseLong(line);
                case '$' -> {
                    int length = Integer.parseInt(line);
                    if (length < 0) {
                        yield null;
                    }
                    byte[] data = in.readNBytes(length);
                    in.readNBytes(2);
                    yield new String(data, StandardCharsets.UTF_8);
                }
                case '*' -> {
                    int count = Integer.parseInt(line);
                    if (count < 0) {
                        yield null;
                    }
                    List<Object> items = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        items.add(read());
                    }
                    yield items;
                }
                default -> throw new IOException("Respuesta RESP desconocida: " + (char) type);
            };
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int previous = -1;
            while (true) {
                int current = in.read();
                if (current == -1) {
                    throw new IOException("Respuesta incompleta");
                }
                if (previous == '\r' && current == '\n') {
                    byte[] bytes = line.toByteArray();
                    return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
                }
                line.write(current);
                previous = current;
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // la conexión ya estaba cerrada
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
