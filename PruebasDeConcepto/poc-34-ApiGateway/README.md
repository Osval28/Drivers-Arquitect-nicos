# Prueba de concepto · ADR-34 API Gateway (Amazon API Gateway, HTTP API)

**Objetivo.** Comprobar contra el gateway desplegado lo que el ADR-34 espera de él: un punto de entrada estable que
envía las solicitudes a un backend alojado fuera de AWS, que responde por sí mismo a lo que no está declarado,
que publica las rutas versionadas `/{contexto}/api/v1/...`, que aplica el límite de 10 MB y el control de
avalanchas, cuánta latencia agrega y que su configuración se describe con OpenAPI.

**Escenario relacionado:** ESC-CAL-CON-0010.

**Tipo:** contra el servicio real. C1–C6 solo necesitan la URL pública; C7 y C8 leen la configuración con la API de
AWS (solo lectura). C5 envía una ráfaga a la ruta de salud y por eso está apagado por defecto.

## Casos de prueba

| Caso | Qué se prueba | Factor o decisión del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Ruta de salud por el gateway | Funciona con servicios alojados fuera del proveedor | 200 con cabecera `apigw-requestid` |
| C2 | Ruta inexistente | Los clientes no conocen la ubicación interna; rutas protegidas | 404 `{"message":"Not Found"}` generado por el gateway |
| C3 | Rutas versionadas configuradas | Publicar versiones sin romper clientes | Cada ruta llega al backend |
| C4 | Solicitud de 10,5 MB | Límite fijo de 10 MB (por eso los archivos van directo a R2) | 413 |
| C5 | Ráfaga de solicitudes (opcional) | Limitar avalanchas antes del backend (ADR-04) | Algún 429 del gateway y ningún 5xx |
| C6 | Gateway contra backend directo | Pequeño aumento de latencia | Diferencia de p50 ≤ `GATEWAY_MAX_OVERHEAD_MS` |
| C7 | Exportación OpenAPI 3 | Rutas descritas con OpenAPI | La definición existe y contiene las rutas de C3 |
| C8 | Configuración de la etapa | Control de solicitudes, métricas y registros | Límites y registros de acceso activos |

C6 compara las dos opciones de entrada: pasar por el gateway o llamar directo a Render. El resultado deja medido el
costo en latencia que el ADR acepta.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `ApiGatewayPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → ApiGatewayPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java ApiGatewayPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `GATEWAY_BASE_URL` | Sí | URL pública del gateway (dominio propio o `execute-api`) |
| `GATEWAY_HEALTH_PATH` | No | Ruta de salud publicada. Por defecto `/actuator/health` |
| `GATEWAY_VERSIONED_PATHS` | Para C3 | Rutas separadas por coma, por ejemplo `/publications/api/v1/publications` |
| `GATEWAY_PAYLOAD_PATH` | No | Ruta para el POST de C4. Por defecto la de salud |
| `GATEWAY_UNKNOWN_PATH` | No | Ruta inexistente para C2. Por defecto una aleatoria |
| `BACKEND_DIRECT_URL` | Para C6 | URL pública del backend en Render |
| `GATEWAY_MAX_OVERHEAD_MS` | No | Umbral de C6. Por defecto `150` |
| `GATEWAY_LATENCY_SAMPLES` | No | Muestras de C6. Por defecto `30` |
| `GATEWAY_BURST_TEST` | No | `true` para ejecutar C5 |
| `GATEWAY_BURST_REQUESTS` | No | Solicitudes de C5. Por defecto `200` |
| `GATEWAY_BURST_CONCURRENCY` | No | Solicitudes en paralelo de C5. Por defecto `50` |
| `AWS_REGION` | Para C7–C8 | Región del gateway |
| `GATEWAY_API_ID` | Para C7–C8 | Identificador del HTTP API |
| `AWS_ACCESS_KEY_ID` | Para C7–C8 | Credencial con `apigateway:GET` |
| `AWS_SECRET_ACCESS_KEY` | Para C7–C8 | Su secreto |
| `AWS_SESSION_TOKEN` | No | Solo si es temporal |
| `AWS_ENDPOINT_URL` | No | Reemplaza el endpoint de AWS, como en los SDK |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- Si delante del gateway está Cloudflare (ADR-14), C2 y C5 pueden recibir respuestas de Cloudflare. La prueba
  distingue las del gateway por la cabecera `apigw-requestid`; para aislar el gateway se puede usar su URL
  `execute-api` en `GATEWAY_BASE_URL`.
- El límite de 30 segundos por integración no se prueba porque exigiría una ruta lenta en el backend; está
  documentado en las cuotas de AWS citadas en el ADR.

## Resultados

Pendiente de ejecución contra el gateway desplegado. Pegar aquí el resumen del archivo generado en `resultados/`.
