# Prueba de concepto · ADR-14 Protección con WAF (Cloudflare)

**Objetivo.** Comprobar en la zona real de Cloudflare lo que el ADR-14 decidió: que la API pública pasa por el WAF,
que el plan tiene activas las reglas administradas completas y el conjunto OWASP, que hay limitación de solicitudes,
que el tráfico normal y los webhooks no se bloquean, que se puede revisar qué se bloqueó y por qué, y que el origen
no se puede usar para saltarse las reglas.

**Escenarios relacionados:** ESC-CAL-SEG-0008, ESC-CAL-CON-0010 y ESC-CAL-SEG-0004.

**Tipo:** contra el servicio real. La configuración se lee con la API de Cloudflare usando un token de solo
lectura. La prueba solo envía tráfico normal; no envía cargas de ataque. Que una regla administrada bloquee un
patrón concreto es responsabilidad del conjunto de Cloudflare y se ve en los eventos de C5.

## Casos de prueba

| Caso | Qué se prueba | Factor o decisión del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Registro DNS del dominio de la API | El WAF protege los endpoints de la API pública | `proxied=true` |
| C2 | Fase `http_request_firewall_managed` | Reglas administradas completas y OWASP (plan Pro) | Se ejecutan *Cloudflare Managed Ruleset* y *Cloudflare OWASP Core Ruleset* |
| C3 | Fase `http_ratelimit` | Limitar abuso de rutas sensibles | ≥ 1 regla activa (se listan umbral, periodo y expresión) |
| C4 | Solicitudes normales y un webhook de prueba | No complicar el acceso de usuarios y proveedores | Ninguna bloqueada ni desafiada por Cloudflare |
| C5 | Analítica GraphQL `firewallEventsAdaptive` | Revisar qué se bloqueó y ajustar reglas | La consulta funciona y devuelve acción, origen de la regla y ruta |
| C6 | Solicitud directa al origen (opcional) | El origen acepta solo el acceso autorizado | Sin respuesta exitosa |

En C4, una respuesta 401 o 400 de la aplicación al webhook de prueba es correcta: significa que llegó a la
aplicación y que fue la verificación de firma la que lo rechazó, no el WAF.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `CloudflareWafPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → CloudflareWafPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java CloudflareWafPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `CF_API_TOKEN` | Para C1–C3 y C5 | Token de Cloudflare con lectura de DNS, WAF y Analytics de la zona |
| `CF_ZONE_ID` | Para C1–C3 y C5 | Identificador de la zona `dazma.store` |
| `WAF_API_HOSTNAME` | Para C1 | Dominio de la API, por ejemplo `api.dazma.store` |
| `WAF_PUBLIC_BASE_URL` | No | URL base de la API. Por defecto `https://` + `WAF_API_HOSTNAME` |
| `WAF_LEGIT_PATHS` | No | Rutas normales separadas por coma. Por defecto `/actuator/health` |
| `WAF_LEGIT_REPETITIONS` | No | Repeticiones por ruta en C4. Por defecto `5` |
| `WAF_WEBHOOK_PATH` | No | Ruta de webhooks para C4 |
| `WAF_WEBHOOK_USER_AGENT` | No | User-Agent del proveedor que envía webhooks. Por defecto el de Svix (Resend) |
| `WAF_ORIGIN_URL` | Para C6 | URL directa del origen (`execute-api` o Render) |
| `WAF_ORIGIN_PATH` | No | Ruta usada en C6. Por defecto `/actuator/health` |
| `CF_API_URL` | No | Por defecto `https://api.cloudflare.com/client/v4` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Resultados

Pendiente de ejecución con el token de la zona. Pegar aquí el resumen del archivo generado en `resultados/`.
