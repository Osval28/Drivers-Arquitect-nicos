# Prueba de concepto · ADR-24 PaaS del backend (Render)

**Objetivo.** Comprobar sobre el servicio desplegado los factores del ADR-24: un backend persistente con HTTPS,
revisión de salud, configuración separada del código, despliegues ordenados, recuperación ante reinicios y la
medición de memoria que el propio ADR exige ("la capacidad real debe comprobarse mediante mediciones").

**Escenarios relacionados:** ESC-CAL-DIS-0006, ESC-CAL-ESC-0003 y ESC-CAL-CON-0010.

**Tipo:** contra el servicio real. C1–C7 solo leen. C8 reinicia el servicio y C9 dispara un despliegue, así que
están apagados por defecto y se activan a propósito, idealmente en un horario sin usuarios.

## Casos de prueba

| Caso | Qué se prueba | Factor o escenario | Criterio de éxito |
|---|---|---|---|
| C1 | Salud por HTTPS | Servicio persistente con URL segura | 200 `{"status":"UP"}` con certificado válido |
| C2 | Sondas `liveness` y `readiness` | Comprobar si el servicio funciona | Ambas UP |
| C3 | Configuración del servicio | Producción fuera del plan gratuito; revisión de salud | `web_service`, plan distinto de `free`, `healthCheckPath` configurado |
| C4 | Variables del servicio (solo nombres) | Configuración separada del código y secretos en el baúl | Están las de arranque y no hay secretos de negocio |
| C5 | Historial de despliegues | Despliegues ordenados | Último despliegue `live` |
| C6 | Métricas de memoria | Capacidad medida, no supuesta | Uso máximo ≤ 85 % del límite en la ventana |
| C7 | Latencia de la salud | Respuesta al frontend | p95 ≤ `RENDER_MAX_P95_MS` |
| C8 | Reinicio (opcional) | Recuperación ante fallos | Vuelve a UP dentro del máximo |
| C9 | Despliegue (opcional) | ESC-CAL-DIS-0006 | Segundos sin respuesta ≤ `RENDER_MAX_DOWNTIME_SECONDS` |

En C4 se esperan solo las variables de arranque del ADR-35 (perfil, región y la clave de IAM con la que el backend
lee el baúl). Cualquier nombre que parezca un secreto de negocio (contraseña de base de datos, claves de Resend,
Firebase, R2, OTLP o Redis) se reporta como falla. Los valores nunca se imprimen.

El escenario DIS-0006 deja el máximo de indisponibilidad como "X, por definir". C9 usa 0 segundos por defecto;
el valor acordado se pone en `RENDER_MAX_DOWNTIME_SECONDS`.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `RenderBackendPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → RenderBackendPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java RenderBackendPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `RENDER_BACKEND_URL` | Para C1, C2, C7–C9 | URL pública del backend, por ejemplo `https://<servicio>.onrender.com` |
| `RENDER_API_KEY` | Para C3–C6, C8, C9 | API key de Render |
| `RENDER_SERVICE_ID` | Para C3–C6, C8, C9 | Identificador del servicio (`srv-...`) |
| `RENDER_HEALTH_PATH` | No | Por defecto `/actuator/health` |
| `RENDER_EXPECTED_ENV_KEYS` | No | Variables de arranque esperadas. Por defecto `SPRING_PROFILES_ACTIVE,AWS_REGION,AWS_ACCESS_KEY_ID,AWS_SECRET_ACCESS_KEY` |
| `RENDER_FORBIDDEN_ENV_PATTERNS` | No | Fragmentos de nombre que no deberían estar en Render |
| `RENDER_METRICS_HOURS` | No | Ventana de C6. Por defecto `24` |
| `RENDER_MAX_MEMORY_RATIO` | No | Umbral de C6. Por defecto `0.85` |
| `RENDER_MAX_P95_MS` | No | Umbral de C7. Por defecto `1000` |
| `RENDER_LATENCY_SAMPLES` | No | Muestras de C7. Por defecto `30` |
| `RENDER_ALLOW_RESTART` | No | `true` para ejecutar C8 |
| `RENDER_MAX_RECOVERY_SECONDS` | No | Máximo de C8. Por defecto `300` |
| `RENDER_ALLOW_DEPLOY` | No | `true` para ejecutar C9 |
| `RENDER_MAX_DOWNTIME_SECONDS` | No | Máximo de C9. Por defecto `0` |
| `RENDER_DEPLOY_WAIT_SECONDS` | No | Espera máxima del despliegue. Por defecto `900` |
| `RENDER_PROBE_INTERVAL_MS` | No | Frecuencia de las comprobaciones en C8 y C9. Por defecto `1000` |
| `RENDER_API_URL` | No | Por defecto `https://api.render.com/v1` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- Si el backend se consulta a través del API Gateway, `RENDER_BACKEND_URL` debe ser la URL directa de Render para
  medir el servicio sin el gateway.
- Si la cuenta no tiene acceso a la API de métricas, C6 queda OMITIDO y el dato se toma del panel de Render.

## Resultados

Pendiente de ejecución contra el servicio de Render. Pegar aquí el resumen del archivo generado en `resultados/`.
