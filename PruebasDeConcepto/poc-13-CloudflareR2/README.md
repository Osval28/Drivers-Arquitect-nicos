# Prueba de concepto · ADR-13 Servicio de Blob Storage (Cloudflare R2)

**Objetivo.** Comprobar contra un bucket real de R2 que las operaciones compatibles con S3 que usa el adaptador
`CloudflareR2PublicationPhotoStorageAdapter` funcionan como exige el ADR-13: permisos temporales para cargar y
consultar un objeto concreto, archivos privados por defecto y lecturas repetidas sin costo de egreso.

**Escenario relacionado:** ESC-CAL-ESC-0003.

**Tipo:** contra el servicio real. Trabaja bajo el prefijo `poc/` (configurable), sube un objeto de 256 KB y lo borra
al final. Las operaciones que hace caben en la cuota gratuita mensual.

## Casos de prueba

| Caso | Qué se prueba | Factor o decisión del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Carga con URL prefirmada a `pending/{ownerId}/{photoId}` | Permisos limitados en el tiempo para cargar | PUT con tipo y tamaño firmados → 200 |
| C2 | Otro tipo de contenido con la misma URL | El permiso cubre una operación concreta | 403 |
| C3 | Otro tamaño con la misma URL | El permiso cubre una operación concreta | 403 |
| C4 | URL vencida | Vigencia limitada | 403 al vencer |
| C5 | Límite de vigencia | Vigencia entre 1 s y 7 días | 604800 s funciona y 604801 s se rechaza |
| C6 | HEAD y COPY a `publications/` | Compatibilidad S3 en las operaciones usadas | Tipo y tamaño correctos; copia con el mismo tamaño |
| C7 | Lectura sin firma | Archivos privados | No devuelve el archivo |
| C8 | Lectura con URL prefirmada de 10 min | Permiso temporal de consulta | 200 y SHA-256 igual al original |
| C9 | 30 lecturas seguidas del mismo objeto | Se sube una vez y se ve muchas veces | 100 % con 200; se reportan latencia, KB descargados y operaciones |
| C10 | Borrado | Limpieza de objetos | HEAD → 404 |

La firma de las URL replica la del SDK de AWS que usa el backend: `X-Amz-SignedHeaders=content-length;content-type;host`.
La implementación de la firma se validó con los vectores de prueba publicados por AWS (`get-vanilla` y el ejemplo
de URL prefirmada de S3).

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `CloudflareR2Poc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → CloudflareR2Poc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java CloudflareR2Poc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `R2_ACCOUNT_ID` | Sí, o `R2_ENDPOINT` | Cuenta de Cloudflare; arma `https://<cuenta>.r2.cloudflarestorage.com` |
| `R2_ENDPOINT` | No | Endpoint completo, si se prefiere a `R2_ACCOUNT_ID` |
| `R2_BUCKET` | Sí | Bucket de pruebas o el de DAZMA (la prueba solo toca `poc/`) |
| `R2_ACCESS_KEY_ID` | Sí | Token de API de R2 con lectura y escritura sobre el bucket |
| `R2_SECRET_ACCESS_KEY` | Sí | Secreto del token |
| `R2_KEY_PREFIX` | No | Prefijo de los objetos. Por defecto `poc` |
| `R2_UPLOAD_VALIDITY_SECONDS` | No | Vigencia de carga. Por defecto `300` (5 min) |
| `R2_DOWNLOAD_VALIDITY_SECONDS` | No | Vigencia de lectura. Por defecto `600` (10 min) |
| `R2_TEST_OBJECT_KB` | No | Tamaño del objeto. Por defecto `256` |
| `R2_REPEATED_READS` | No | Lecturas de C9. Por defecto `30` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- El contador de operaciones sigue la clasificación de Cloudflare: PUT y COPY son clase A, GET y HEAD clase B,
  DELETE no se cobra. Sirve para estimar el costo de un flujo real de publicación.
- C9 descarga varias veces el mismo objeto. En R2 ese volumen no genera cargo de egreso, que es el motivo central
  de la decisión frente a S3.

## Resultados

Pendiente de ejecución con las credenciales del bucket. Pegar aquí el resumen del archivo generado en `resultados/`.
