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
import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Prueba de concepto del ADR-02: Firebase Authentication con Identity Platform.
 *
 * Valida contra el proyecto real de Firebase el flujo que usa DAZMA: registro, inicio de sesión,
 * validación del ID token, cookie de sesión de 8 horas, revocación, verificación de correo
 * y segundo factor TOTP. Crea un usuario de prueba y lo elimina al terminar.
 */
public class FirebaseIdentityPoc {

    private static final long SESSION_SECONDS = 8 * 60 * 60;
    private static final long MAX_SIGN_IN_AGE_SECONDS = 5 * 60;

    private final String projectId;
    private final String webApiKey;
    private final String authUrl;
    private final String idTokenCertsUrl;
    private final String sessionCertsUrl;
    private final Path serviceAccountFile;
    private String adminAccessToken;

    private final String email;
    private final String password;
    private String uid;
    private String idToken;
    private String sessionCookie;

    FirebaseIdentityPoc() {
        this.projectId = Env.get("FIREBASE_PROJECT_ID");
        this.webApiKey = Env.get("FIREBASE_WEB_API_KEY");
        this.authUrl = Http.trimSlash(Env.orDefault("FIREBASE_AUTH_URL", "https://identitytoolkit.googleapis.com"));
        this.idTokenCertsUrl = Env.orDefault("FIREBASE_ID_TOKEN_CERTS_URL",
                "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com");
        this.sessionCertsUrl = Env.orDefault("FIREBASE_SESSION_CERTS_URL",
                "https://www.googleapis.com/identitytoolkit/v3/relyingparty/publicKeys");
        String credentials = Env.get("GOOGLE_APPLICATION_CREDENTIALS");
        this.serviceAccountFile = credentials == null ? null : Path.of(credentials);
        String domain = Env.orDefault("FIREBASE_TEST_EMAIL_DOMAIN", "example.com");
        this.email = "dazma-poc-" + UUID.randomUUID().toString().substring(0, 8) + "@" + domain;
        this.password = randomPassword();
    }

    public static void main(String[] args) {
        Report report = new Report("Prueba de concepto: Firebase Authentication", "ADR-02");
        FirebaseIdentityPoc poc = new FirebaseIdentityPoc();
        report.context("Proyecto", poc.projectId);
        report.context("Usuario de prueba", poc.email);
        report.context("Cuenta de servicio", poc.serviceAccountFile == null ? null : "configurada");

        report.run("C1", "Registro con correo y contraseña",
                "Firebase crea la cuenta y entrega un ID token y un identificador de usuario",
                poc::signUp);
        report.run("C2", "El backend valida el ID token sin llamar a Firebase",
                "Firma RS256 válida con las llaves públicas de Google; iss, aud, exp y sub correctos; email_verified=false en una cuenta nueva",
                poc::verifyNewIdToken);
        report.run("C3", "Tokens alterados o de otro proyecto se rechazan",
                "El 100 % de los tokens manipulados (contenido cambiado, firma cambiada, otra audiencia) es rechazado (ESC-CAL-SEG-0008)",
                poc::rejectTamperedTokens);
        report.run("C4", "Inicio de sesión reciente",
                "signInWithPassword entrega un ID token con auth_time de hace 5 minutos o menos (dazma.session.maximum-sign-in-age)",
                poc::signInRecently);
        report.run("C5", "Cookie de sesión de 8 horas",
                "createSessionCookie entrega una cookie firmada, emitida por session.firebase.google.com y con vigencia de 8 h",
                poc::issueSessionCookie);
        report.run("C6", "Revocación de sesiones",
                "Antes de revocar la cookie es válida; después de revokeRefreshTokens se rechaza al comprobar revocación (checkRevoked)",
                poc::revokeSessions);
        report.run("C7", "Verificación de correo exigible por el backend",
                "Tras marcar el correo como verificado, un nuevo ID token trae email_verified=true",
                poc::emailVerification);
        report.run("C8", "Segundo factor TOTP sin SMS",
                "Con TOTP inscrito, la contraseña sola no entrega ID token; con un código válido sí, y el token indica sign_in_second_factor=totp; un código inválido se rechaza",
                poc::totpSecondFactor);
        report.run("C9", "Limpieza del usuario de prueba",
                "El usuario creado por la prueba se elimina",
                poc::deleteUser);
        System.exit(report.finish());
    }

    // ---------------------------------------------------------------- casos

    String signUp() throws Exception {
        requireClientConfig();
        Http.Response response = Http.postJson(authUrl + "/v1/accounts:signUp?key=" + webApiKey,
                Map.of("email", email, "password", password, "returnSecureToken", true));
        check(response.status() == 200, "signUp respondió " + response.brief());
        Map<String, Object> body = response.json();
        uid = Json.str(body, "localId");
        idToken = Json.str(body, "idToken");
        check(uid != null && idToken != null, "La respuesta no trae localId o idToken");
        return "uid=" + uid + ", ID token de " + idToken.length() + " caracteres, " + response.millis() + " ms";
    }

    String verifyNewIdToken() throws Exception {
        requireToken();
        Map<String, Object> claims = verifyJwt(idToken, idTokenCertsUrl, "https://securetoken.google.com/" + projectId, projectId);
        check(uid.equals(claims.get("sub")), "sub del token no coincide con el usuario creado");
        check(Boolean.FALSE.equals(claims.get("email_verified")), "Se esperaba email_verified=false en una cuenta nueva");
        return "Firma y claims válidos; sub=" + claims.get("sub") + ", email_verified=" + claims.get("email_verified")
                + ", proveedor=" + Json.str(claims, "firebase.sign_in_provider");
    }

    String rejectTamperedTokens() throws Exception {
        requireToken();
        String[] parts = idToken.split("\\.");
        Map<String, Object> payload = Json.object(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        payload.put("email", "otra-persona@" + email.substring(email.indexOf('@') + 1));
        String changedPayload = parts[0] + "." + base64Url(Json.write(payload).getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[signature.length / 2] ^= 0x01;
        String changedSignature = parts[0] + "." + parts[1] + "." + base64Url(signature);

        int rejected = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, String[]> attempt : List.of(
                Map.entry("contenido cambiado", new String[]{changedPayload, projectId}),
                Map.entry("firma cambiada", new String[]{changedSignature, projectId}),
                Map.entry("otra audiencia", new String[]{idToken, projectId + "-otro"}))) {
            try {
                String expectedAudience = attempt.getValue()[1];
                verifyJwt(attempt.getValue()[0], idTokenCertsUrl, "https://securetoken.google.com/" + expectedAudience, expectedAudience);
                details.add(attempt.getKey() + ": ACEPTADO");
            } catch (CheckFailed rejection) {
                rejected++;
                details.add(attempt.getKey() + ": rechazado (" + rejection.getMessage() + ")");
            }
        }
        check(rejected == 3, "Solo " + rejected + " de 3 tokens manipulados fueron rechazados: " + details);
        return "3/3 rechazados. " + String.join("; ", details);
    }

    String signInRecently() throws Exception {
        requireToken();
        Http.Response response = Http.postJson(authUrl + "/v1/accounts:signInWithPassword?key=" + webApiKey,
                Map.of("email", email, "password", password, "returnSecureToken", true));
        check(response.status() == 200, "signInWithPassword respondió " + response.brief());
        idToken = Json.str(response.json(), "idToken");
        Map<String, Object> claims = verifyJwt(idToken, idTokenCertsUrl, "https://securetoken.google.com/" + projectId, projectId);
        long authTime = ((Number) claims.get("auth_time")).longValue();
        long age = Instant.now().getEpochSecond() - authTime;
        check(age <= MAX_SIGN_IN_AGE_SECONDS, "auth_time tiene " + age + " s de antigüedad");
        return "auth_time hace " + age + " s, inicio de sesión en " + response.millis() + " ms";
    }

    String issueSessionCookie() throws Exception {
        requireToken();
        requireAdmin();
        Http.Response response = admin("POST", "/v1/projects/" + projectId + ":createSessionCookie",
                Map.of("idToken", idToken, "validDuration", SESSION_SECONDS));
        check(response.status() == 200, "createSessionCookie respondió " + response.brief());
        sessionCookie = Json.str(response.json(), "sessionCookie");
        check(sessionCookie != null, "La respuesta no trae sessionCookie");
        Map<String, Object> claims = verifyJwt(sessionCookie, sessionCertsUrl, "https://session.firebase.google.com/" + projectId, projectId);
        long lifetime = ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue();
        check(Math.abs(lifetime - SESSION_SECONDS) <= 5, "La cookie dura " + lifetime + " s y se esperaban " + SESSION_SECONDS);
        check(uid.equals(claims.get("sub")), "sub de la cookie no coincide con el usuario");
        return "Cookie válida por " + lifetime / 3600.0 + " h, emitida por " + claims.get("iss") + ", " + response.millis() + " ms";
    }

    String revokeSessions() throws Exception {
        requireAdmin();
        if (sessionCookie == null) {
            throw new Skip("no hay cookie de sesión (C5 no se completó)");
        }
        Map<String, Object> claims = verifyJwt(sessionCookie, sessionCertsUrl, "https://session.firebase.google.com/" + projectId, projectId);
        check(!isRevoked(claims), "La cookie aparece revocada antes de revocarla");

        Thread.sleep(1_100);
        Http.Response revoke = admin("POST", "/v1/projects/" + projectId + "/accounts:update",
                Map.of("localId", uid, "validSince", Long.toString(Instant.now().getEpochSecond())));
        check(revoke.status() == 200, "La revocación respondió " + revoke.brief());

        boolean signatureStillValid = verifyJwt(sessionCookie, sessionCertsUrl,
                "https://session.firebase.google.com/" + projectId, projectId) != null;
        check(isRevoked(claims), "La cookie sigue aceptándose después de revocar las sesiones");
        return "Antes: aceptada. Después: firma " + (signatureStillValid ? "aún válida" : "inválida")
                + " pero rechazada por revocación (por eso DAZMA usa checkRevoked en operaciones que modifican datos)";
    }

    String emailVerification() throws Exception {
        requireToken();
        requireAdmin();
        Http.Response update = admin("POST", "/v1/projects/" + projectId + "/accounts:update",
                Map.of("localId", uid, "emailVerified", true));
        check(update.status() == 200, "No se pudo marcar el correo como verificado: " + update.brief());
        Http.Response signIn = Http.postJson(authUrl + "/v1/accounts:signInWithPassword?key=" + webApiKey,
                Map.of("email", email, "password", password, "returnSecureToken", true));
        check(signIn.status() == 200, "signInWithPassword respondió " + signIn.brief());
        idToken = Json.str(signIn.json(), "idToken");
        Map<String, Object> claims = verifyJwt(idToken, idTokenCertsUrl, "https://securetoken.google.com/" + projectId, projectId);
        check(Boolean.TRUE.equals(claims.get("email_verified")), "El nuevo token no trae email_verified=true");
        return "email_verified pasó de false a true en el token";
    }

    String totpSecondFactor() throws Exception {
        if (!Env.flag("FIREBASE_TOTP_CASE")) {
            throw new Skip("se activa con FIREBASE_TOTP_CASE=true; requiere TOTP habilitado en Identity Platform");
        }
        requireToken();
        Http.Response start = Http.postJson(authUrl + "/v2/accounts/mfaEnrollment:start?key=" + webApiKey,
                Map.of("idToken", idToken, "totpEnrollmentInfo", Map.of()));
        check(start.status() == 200, "mfaEnrollment:start respondió " + start.brief()
                + " (revisar que TOTP esté habilitado y el correo verificado)");
        Object info = Json.at(start.json(), "totpSessionInfo");
        check(info instanceof Map, "La respuesta no trae totpSessionInfo: " + start.brief());
        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) info;
        String secret = Json.str(session, "sharedSecretKey");
        int digits = ((Number) session.getOrDefault("verificationCodeLength", 6.0)).intValue();
        int period = ((Number) session.getOrDefault("periodSec", 30.0)).intValue();
        String algorithm = String.valueOf(session.getOrDefault("hashingAlgorithm", "SHA1"));

        Http.Response finish = Http.postJson(authUrl + "/v2/accounts/mfaEnrollment:finalize?key=" + webApiKey,
                Map.of("idToken", idToken, "displayName", "PoC DAZMA",
                        "totpVerificationInfo", Map.of(
                                "sessionInfo", Json.str(session, "sessionInfo"),
                                "verificationCode", totp(secret, Instant.now(), digits, period, algorithm))));
        check(finish.status() == 200, "mfaEnrollment:finalize respondió " + finish.brief());

        Http.Response passwordOnly = Http.postJson(authUrl + "/v1/accounts:signInWithPassword?key=" + webApiKey,
                Map.of("email", email, "password", password, "returnSecureToken", true));
        Map<String, Object> pending = passwordOnly.json();
        check(pending.get("idToken") == null, "La contraseña sola entregó un ID token aunque hay TOTP inscrito");
        String pendingCredential = Json.str(pending, "mfaPendingCredential");
        String enrollmentId = Json.str(pending, "mfaInfo.0.mfaEnrollmentId");
        check(pendingCredential != null && enrollmentId != null, "No llegó mfaPendingCredential: " + passwordOnly.brief());

        Http.Response wrong = Http.postJson(authUrl + "/v2/accounts/mfaSignIn:finalize?key=" + webApiKey,
                Map.of("mfaPendingCredential", pendingCredential, "mfaEnrollmentId", enrollmentId,
                        "totpVerificationInfo", Map.of("verificationCode", "0".repeat(digits))));
        check(wrong.status() != 200, "Un código TOTP inválido fue aceptado");

        waitForNextPeriod(period);
        Http.Response right = Http.postJson(authUrl + "/v2/accounts/mfaSignIn:finalize?key=" + webApiKey,
                Map.of("mfaPendingCredential", pendingCredential, "mfaEnrollmentId", enrollmentId,
                        "totpVerificationInfo", Map.of("verificationCode", totp(secret, Instant.now(), digits, period, algorithm))));
        check(right.status() == 200, "mfaSignIn:finalize con código válido respondió " + right.brief());
        Map<String, Object> claims = verifyJwt(Json.str(right.json(), "idToken"), idTokenCertsUrl,
                "https://securetoken.google.com/" + projectId, projectId);
        String factor = Json.str(claims, "firebase.sign_in_second_factor");
        check("totp".equals(factor), "El token no indica segundo factor TOTP (sign_in_second_factor=" + factor + ")");
        return "Solo contraseña: sin token (mfaPendingCredential). Código inválido: HTTP " + wrong.status()
                + ". Código válido: token con sign_in_second_factor=totp. Ningún SMS enviado.";
    }

    String deleteUser() throws Exception {
        if (uid == null) {
            throw new Skip("no se creó usuario");
        }
        requireAdmin();
        Http.Response response = admin("POST", "/v1/projects/" + projectId + "/accounts:delete", Map.of("localId", uid));
        check(response.status() == 200, "accounts:delete respondió " + response.brief());
        return "Usuario " + uid + " eliminado";
    }

    // ------------------------------------------------------------ validación

    private boolean isRevoked(Map<String, Object> cookieClaims) throws Exception {
        Http.Response lookup = admin("POST", "/v1/projects/" + projectId + "/accounts:lookup", Map.of("localId", List.of(uid)));
        check(lookup.status() == 200, "accounts:lookup respondió " + lookup.brief());
        String validSince = Json.str(lookup.json(), "users.0.validSince");
        if (validSince == null) {
            return false;
        }
        long authTime = ((Number) cookieClaims.get("auth_time")).longValue();
        return authTime < Long.parseLong(validSince);
    }

    private final Map<String, Map<String, X509Certificate>> certificateCache = new LinkedHashMap<>();

    /** Verifica firma RS256, emisor, audiencia y vigencia; lanza CheckFailed si algo no cuadra. */
    private Map<String, Object> verifyJwt(String jwt, String certsUrl, String issuer, String audience) throws Exception {
        String[] parts = jwt.split("\\.");
        check(parts.length == 3, "no tiene formato JWT");
        Map<String, Object> header = Json.object(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        check("RS256".equals(header.get("alg")), "algoritmo " + header.get("alg") + " no permitido");
        X509Certificate certificate = certificates(certsUrl).get(String.valueOf(header.get("kid")));
        check(certificate != null, "kid desconocido");

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        check(verifier.verify(Base64.getUrlDecoder().decode(parts[2])), "firma inválida");

        Map<String, Object> claims = Json.object(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        long now = Instant.now().getEpochSecond();
        check(issuer.equals(claims.get("iss")), "emisor inesperado");
        check(audience.equals(claims.get("aud")), "audiencia inesperada");
        check(((Number) claims.get("exp")).longValue() > now, "token vencido");
        check(((Number) claims.get("iat")).longValue() <= now + 60, "emitido en el futuro");
        check(claims.get("sub") != null && !String.valueOf(claims.get("sub")).isBlank(), "sin sub");
        return claims;
    }

    private Map<String, X509Certificate> certificates(String url) throws Exception {
        Map<String, X509Certificate> cached = certificateCache.get(url);
        if (cached != null) {
            return cached;
        }
        Http.Response response = Http.get(url);
        check(response.status() == 200, "No se pudieron descargar las llaves públicas: " + response.brief());
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Map<String, X509Certificate> certificates = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : response.json().entrySet()) {
            certificates.put(entry.getKey(), (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(String.valueOf(entry.getValue()).getBytes(StandardCharsets.US_ASCII))));
        }
        certificateCache.put(url, certificates);
        return certificates;
    }

    // ----------------------------------------------------- acceso administrativo

    private Http.Response admin(String method, String path, Object body) throws Exception {
        HttpRequest request = Http.to(authUrl + path)
                .header("Authorization", "Bearer " + adminAccessToken())
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        return Http.send(request);
    }

    /** Intercambia la cuenta de servicio por un token OAuth 2.0 (flujo JWT bearer de Google). */
    private String adminAccessToken() throws Exception {
        if (adminAccessToken != null) {
            return adminAccessToken;
        }
        Map<String, Object> account = Json.object(Files.readString(serviceAccountFile));
        String tokenUri = Env.orDefault("GOOGLE_TOKEN_URL", Json.str(account, "token_uri"));
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", account.get("private_key_id"));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", account.get("client_email"));
        claims.put("scope", "https://www.googleapis.com/auth/identitytoolkit https://www.googleapis.com/auth/cloud-platform");
        claims.put("aud", tokenUri);
        claims.put("iat", now);
        claims.put("exp", now + 3600);
        String unsigned = base64Url(Json.write(header).getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(Json.write(claims).getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey(Json.str(account, "private_key")));
        signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        String assertion = unsigned + "." + base64Url(signer.sign());

        Http.Response response = Http.send(Http.to(tokenUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(Http.form(Map.of(
                        "grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer",
                        "assertion", assertion))))
                .build());
        check(response.status() == 200, "Google no entregó token de acceso para la cuenta de servicio: HTTP " + response.status());
        adminAccessToken = Json.str(response.json(), "access_token");
        return adminAccessToken;
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    // ------------------------------------------------------------------ TOTP

    /** Código TOTP según RFC 6238. */
    static String totp(String base32Secret, Instant at, int digits, int periodSeconds, String algorithm) throws Exception {
        String macName = switch (algorithm.toUpperCase().replace("-", "")) {
            case "SHA256" -> "HmacSHA256";
            case "SHA512" -> "HmacSHA512";
            default -> "HmacSHA1";
        };
        long counter = at.getEpochSecond() / periodSeconds;
        byte[] message = java.nio.ByteBuffer.allocate(8).putLong(counter).array();
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance(macName);
        mac.init(new javax.crypto.spec.SecretKeySpec(base32(base32Secret), macName));
        byte[] hash = mac.doFinal(message);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
        int code = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", code);
    }

    private static byte[] base32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String clean = value.toUpperCase().replace("=", "").replace(" ", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static void waitForNextPeriod(int period) throws InterruptedException {
        long millisIntoPeriod = System.currentTimeMillis() % (period * 1000L);
        Thread.sleep(period * 1000L - millisIntoPeriod + 500);
    }

    // ------------------------------------------------------------- utilidades

    private void requireClientConfig() {
        Env.requireAll("FIREBASE_PROJECT_ID", "FIREBASE_WEB_API_KEY");
    }

    private void requireToken() {
        requireClientConfig();
        if (idToken == null) {
            throw new Skip("no hay ID token (C1 no se completó)");
        }
    }

    private void requireAdmin() {
        requireClientConfig();
        if (serviceAccountFile == null) {
            throw new Skip("falta GOOGLE_APPLICATION_CREDENTIALS (ruta al JSON de la cuenta de servicio)");
        }
        if (!Files.isReadable(serviceAccountFile)) {
            throw new Skip("no se puede leer el archivo indicado en GOOGLE_APPLICATION_CREDENTIALS");
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return "Dz9!" + base64Url(bytes);
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
