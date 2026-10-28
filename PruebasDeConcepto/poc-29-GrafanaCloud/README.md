# Prueba de concepto · ADR-29 Observabilidad (Grafana Cloud)

**Objetivo.** Comprobar en el stack de Grafana Cloud los factores del ADR-29: que con OpenTelemetry (el estándar que
ya usa el backend) llegan métricas de infraestructura, errores por endpoint, latencia de proveedores externos y
métricas de negocio; que métricas, registros y trazas quedan en un solo lugar y se pueden relacionar entre frontend,
backend y procesamiento asíncrono; y que hay alertas enviadas a un canal externo.

**Escenarios relacionados:** ESC-CAL-CON-0010 y ESC-CAL-CON-0011.

**Tipo:** contra el servicio real. También se puede ejecutar contra el contenedor `grafana/otel-lgtm` del
`compose.yaml` del backend (`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318`, `GRAFANA_URL=http://localhost:3000`),
que usa los mismos componentes (Prometheus, Loki y Tempo) sin cuenta.

## Casos de prueba

| Caso | Qué se prueba | Factor del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Envío de 5 métricas por OTLP: carga de CPU, memoria de la JVM, errores por endpoint, histograma de latencia de Resend y negociaciones fallidas | CPU, memoria, errores de servicios externos y métricas de negocio con estándar abierto | El gateway responde 2xx |
| C2 | Registro de error con `trace_id` | Registros en el mismo lugar | 2xx |
| C3 | Traza con spans de frontend, backend y envío asíncrono | Correlación entre frontend, backend y asíncrono | 2xx |
| C4 | Consulta en Prometheus | Paneles a medida sobre las métricas | Las 5 métricas con la etiqueta `poc_run` de la ejecución |
| C5 | Consulta en Loki filtrando por `trace_id` | Registros consultables y relacionados con la traza | Loki devuelve el registro al filtrar por el `trace_id` de la traza |
| C6 | Consulta en Tempo | Trazas consultables | La traza trae los 3 spans |
| C7 | Reglas y puntos de contacto | Alertas fuera del servicio afectado (ADR-04) | ≥ 1 regla y ≥ 1 canal de correo, Slack o webhook |

Cada ejecución usa un identificador propio (`poc_run`) y un `trace_id` aleatorio, así que las consultas encuentran
solo lo que envió esa ejecución. Las métricas tienen el prefijo `dazma_poc_` para no mezclarse con las del backend.
La ingesta tarda unos segundos; C4–C6 reintentan durante `GRAFANA_QUERY_WAIT_SECONDS`.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `GrafanaCloudPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → GrafanaCloudPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java GrafanaCloudPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Para C1–C3 | Gateway OTLP, por ejemplo `https://otlp-gateway-prod-<región>.grafana.net/otlp` |
| `OTLP_AUTHORIZATION` | No | Valor completo de la cabecera, igual al campo `otlpAuthorization` del secreto `dazma/prod/observability` |
| `GRAFANA_OTLP_INSTANCE_ID` | No | Alternativa a la anterior: identificador de la instancia |
| `GRAFANA_OTLP_TOKEN` | No | Alternativa a la anterior: token con permiso de escritura |
| `GRAFANA_URL` | Para C4–C7 | URL del stack, por ejemplo `https://<stack>.grafana.net` |
| `GRAFANA_TOKEN` | Para C4–C7 en la nube | Token de una cuenta de servicio con lectura de datos y de alertas |
| `GRAFANA_PROMETHEUS_UID` | No | UID del origen de datos, si hay varios del mismo tipo |
| `GRAFANA_LOKI_UID` | No | Ídem para Loki |
| `GRAFANA_TEMPO_UID` | No | Ídem para Tempo |
| `GRAFANA_QUERY_WAIT_SECONDS` | No | Espera máxima de C4–C6. Por defecto `120` |
| `POC_SERVICE_NAME` | No | `service.name` de los datos. Por defecto `dazma-poc` |
| `POC_ENVIRONMENT` | No | `deployment.environment.name`. Por defecto `poc` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- Las métricas y la traza son datos de prueba con valores fijos; lo que se valida es el recorrido de cada señal
  (envío por OTLP, almacenamiento, consulta y correlación), no el estado real del backend.
- Con el contenedor local C7 normalmente falla porque no trae reglas de alerta ni canales configurados.

## Resultados

Pendiente de ejecución contra el stack de Grafana Cloud. Pegar aquí el resumen del archivo generado en `resultados/`.
