# ADR-13: Selección del servicio de Blob Storage

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-ESC-0003

## Contexto

Una vez definida la necesidad de almacenar archivos por separado, se debe escoger un servicio concreto para guardar imágenes y documentos de publicaciones. La elección debe cubrir acceso, costo, seguridad y facilidad de integración.

## Factores de decisión

- El servicio de almacenamiento debe guardar imágenes y archivos con una disponibilidad mayor o igual al 99.9% mensual y tener compatibilidad con la API estándar del mercado (S3) porque el proyecto necesita tener la mayor cantidad de documentación para conocer cómo integrar el sistema y solucionar errores eficientemente
- El costo debe evaluarse no solo por el espacio ocupado, sino también por la cantidad de veces que los archivos se suben, se consultan y se descargan. En DAZMA una imagen puede cargarse una sola vez, pero mostrarse muchas veces en el marketplace
- La solución también debe permitir decidir qué archivos son públicos y cuáles son privados, además de dar permisos limitados en el tiempo para cargar o consultar archivos sin exponer todo el almacenamiento

## Opciones

- **A.** Amazon S3: El estándar del mercado, con el SDK más maduro y mejor documentado para integrarse con Spring Boot; a cambio, tiene los costos de egreso más altos del grupo de opciones consideradas.
- **B.** Cloudflare R2: compatible con la API de S3 (misma integración, menor costo de migrar), con egreso gratuito, lo que es relevante porque las publicaciones exponen imágenes a muchos usuarios; a cambio, no ofrece versionado de objetos.
- **C.** Backblaze B2: es una de las opciones más económicas por GB almacenado, con egreso gratuito si se combina con una CDN de Cloudflare más adelante
- **D.** Supabase Storage: opción integrada tipo BaaS que empaqueta storage junto con Postgres y autenticación. Su ventaja aparece cuando también se usan esos otros productos.

## Decisión

Se adopta Cloudflare R2 Standard para almacenar las imágenes y archivos de las publicaciones. Su modelo de cobro se basa en el almacenamiento y las operaciones, sin cargos por el volumen de datos transferidos directamente a internet. Esto permite que las descargas crezcan sin añadir un costo por GB de egreso, una ventaja económica relevante para la distribución de imágenes del marketplace. La compatibilidad con las operaciones S3 soportadas facilita su integración con el backend Java. La elección prioriza este modelo de distribución; el costo total sigue dependiendo del almacenamiento y las solicitudes realizadas.

## Consecuencias

**Ventajas:** separa el crecimiento de los medios del núcleo transaccional y evita cargos de egreso directo desde R2.

**Desventajas:** la compatibilidad S3 es parcial; el adaptador debe limitarse a operaciones soportadas. Persisten costos de almacenamiento y operaciones. La recuperación, residencia de datos y ciclo de vida requieren configuración explícita, junto con la limpieza de objetos huérfanos y cargas incompletas.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Cloudflare R2 en la clase Standard para las imágenes y documentos de DAZMA porque se ajusta a cómo se usan los archivos en el marketplace: un archivo se sube una vez y se puede descargar muchas veces. R2 cobra por espacio y por operaciones, pero no por el volumen que sale de R2 hacia Internet, lo que hace el costo más predecible cuando las descargas son muchas comparadas con lo que se almacena.

La tarifa pública de R2 Standard es de USD 0,015 por GB/mes, USD 4,50 por cada millón de operaciones de clase A, entre ellas cargas y listados, y USD 0,36 por cada millón de operaciones de clase B, entre ellas lecturas. Incluye cada mes 10 GB-mes, un millón de operaciones de clase A y diez millones de operaciones de clase B sin cargo; la transferencia hacia Internet no tiene costo. Cloudflare redondea cada dimensión a su unidad de cobro. Por ejemplo, 100 GB almacenados de forma constante generan USD 1,35 mensuales por almacenamiento después de descontar los 10 GB incluidos, siempre que las operaciones permanezcan dentro de la cuota. Veinte millones de lecturas generan diez millones facturables, equivalentes a USD 3,60.

R2 no es más barato en todos los casos, la diferencia con las demás opciones depende de cómo se usen los archivos. Backblaze B2 publica USD 6,95 por TB-mes, ofrece los primeros 10 GB sin costo y transferencia gratuita hasta tres veces el almacenamiento mensual; por encima cobra USD 0,01 por GB. Por eso B2 puede costar menos cuando se almacena mucho y se descarga relativamente poco. Por ejemplo, con 100 GB almacenados y 1 TB descargado en el mes, R2 cobraría USD 1,35 por almacenamiento más las operaciones aplicables; B2 cobraría aproximadamente USD 0,695 por almacenamiento y USD 7 por los 700 GB que exceden tres veces el volumen almacenado, antes de otros conceptos. Con este ejemplo se ve por qué R2 conviene cuando los archivos se ven muchas veces, pero el costo real se tiene que calcular con el tráfico que tenga la plataforma.

Amazon S3 dispone del ecosistema y la documentación más amplios, pero combina almacenamiento, solicitudes y transferencia con precios que dependen de la región. Como referencia, S3 Standard en US East publica USD 0,023 por GB-mes y USD 0,0004 por cada mil solicitudes GET. Supabase Storage incluye más productos de los que DAZMA necesita para almacenar archivos; su plan gratuito incluye 1 GB y el plan Pro incluye 100 GB, con excedente de USD 0,0213 por GB-mes. Identidad y base de datos se resuelven en decisiones independientes del almacenamiento, por lo que esa integración adicional no mejora por sí misma el factor de almacenamiento.

R2 expone una API compatible con S3, aunque no implementa todas sus operaciones. La integración se limitará a las funciones oficialmente soportadas. Los archivos privados se entregarán mediante autorizaciones temporales para un objeto y una operación concretos; R2 admite URLs prefirmadas con vigencia configurable entre un segundo y siete días. Cloudflare publica un objetivo mensual de disponibilidad de 99,9 % para R2 y una durabilidad anual de 99,999999999 %, pero la cobertura contractual y los créditos deben verificarse en las condiciones del plan.
