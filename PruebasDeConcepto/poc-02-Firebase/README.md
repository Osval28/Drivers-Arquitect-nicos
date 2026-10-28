# Prueba de concepto · ADR-02 Proveedor de identidad (Firebase Authentication)

**Objetivo.** Comprobar contra un proyecto real de Firebase que el flujo de identidad decidido en el ADR-02 funciona
como lo usa el backend de DAZMA: Firebase confirma quién es el usuario y el backend valida esa identidad sin depender
de Firebase en cada solicitud, emite una cookie de sesión de 8 horas, puede revocarla y puede exigir correo verificado
y un segundo factor TOTP sin costo por mensaje.

**Escenarios relacionados:** ESC-CAL-SEG-0004 y ESC-CAL-SEG-0008.

**Tipo:** contra el servicio real. Crea un usuario de prueba con un correo aleatorio (`dazma-poc-xxxx@<dominio>`),
no envía correos ni SMS y elimina el usuario al final.

## Casos de prueba

| Caso | Qué se prueba | Factor o decisión del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Registro con correo y contraseña | Registro administrado por el proveedor | Firebase entrega ID token y `localId` |
| C2 | Validación local del ID token | El backend valida la identidad emitida | Firma RS256 válida con llaves públicas de Google; `iss`, `aud`, `exp`, `sub` correctos; `email_verified=false` en cuenta nueva |
| C3 | Tokens manipulados | ESC-CAL-SEG-0008 | 3 de 3 rechazados: contenido cambiado, firma cambiada, otra audiencia |
| C4 | Inicio de sesión reciente | Cookie solo con inicio reciente | `auth_time` de 5 minutos o menos |
| C5 | Cookie de sesión | Cookie HttpOnly de 8 h | Cookie firmada por `session.firebase.google.com/<proyecto>` con vigencia de 8 h |
| C6 | Revocación | `checkRevoked` en operaciones que modifican datos | Cookie aceptada antes y rechazada después de revocar |
| C7 | Verificación de correo | El backend puede exigirla | Nuevo token con `email_verified=true` |
| C8 | MFA TOTP | MFA al menor costo (sin SMS) | Sin código no hay token; código inválido rechazado; código válido entrega token con `sign_in_second_factor=totp` |
| C9 | Limpieza | | El usuario de prueba se elimina |

C8 es opcional porque necesita TOTP habilitado en Identity Platform. Se activa con `FIREBASE_TOTP_CASE=true`.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `FirebaseIdentityPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → FirebaseIdentityPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java FirebaseIdentityPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `FIREBASE_PROJECT_ID` | Sí | Identificador del proyecto de Firebase |
| `FIREBASE_WEB_API_KEY` | Sí | Clave web de la app (la misma que usa el frontend) |
| `GOOGLE_APPLICATION_CREDENTIALS` | Para C5–C9 | Ruta al JSON de la cuenta de servicio. Guárdenlo fuera del repositorio |
| `FIREBASE_TOTP_CASE` | No | `true` para ejecutar C8 |
| `FIREBASE_TEST_EMAIL_DOMAIN` | No | Dominio del correo de prueba. Por defecto `example.com` |
| `FIREBASE_AUTH_URL` | No | URL de la API de Identity Toolkit. Por defecto la pública de Google |
| `FIREBASE_ID_TOKEN_CERTS_URL` | No | Llaves públicas de los ID token. Por defecto la URL publicada por Google |
| `FIREBASE_SESSION_CERTS_URL` | No | Llaves públicas de las cookies de sesión. Por defecto la URL publicada por Google |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- La validación del token y de la cookie se hace con las llaves públicas y las reglas de la documentación oficial
  (algoritmo, emisor, audiencia, vencimiento). Es lo mismo que hace `firebase-admin` en el backend, escrito sin la
  librería para que la prueba no dependa de Maven.
- La revocación se comprueba igual que el SDK: una cookie se rechaza si su `auth_time` es anterior a `validSince`
  del usuario.
- La portabilidad de contraseñas (exportación con `firebase auth:export`) no se ejecuta aquí; se documenta en el ADR.
- Ni el token, ni la cookie, ni la contraseña generada se imprimen ni se guardan en los resultados.

## Resultados

Pendiente de ejecución con las credenciales del proyecto. Pegar aquí el resumen del archivo generado en `resultados/`.
