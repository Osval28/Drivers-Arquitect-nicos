# ADR-12: Almacenamiento de imágenes y archivos de publicaciones

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-ESC-0003

## Contexto

Las publicaciones pueden incluir imágenes y documentos. Si estos archivos crecen en cantidad y tamaño, guardarlos junto con los datos principales afectaría la latencia de las consultas, respaldos y costos. DAZMA necesita una estrategia específica para almacenarlos.

## Factores de decisión

- Los archivos de publicaciones, como imágenes y documentos, deben almacenarse separados de la base de datos principal
- La base de datos debe guardar información necesaria sobre cada archivo, como lo son dueño, estados, permisos y referencias de la ubicación
- La solución debe contemplar tamaños, tipos de archivo, cargas incompletas y eliminación de contenido para evitar costos innecesarios o alguna exposición de archivo que no debería ser pública

## Opciones

- **A.** Guardar imágenes como BLOB dentro de la base relacional: simplifica la consistencia referencial, pero aumenta el tamaño y carga de la base transaccional y de sus respaldos, lo que reduce su velocidad
- **B.** Sistema de archivos local del servidor: simple al inicio, pero dificulta replicación, despliegue en varias instancias y recuperación.
- **C.** Object/Blob Storage: Se almacenan archivos como objetos independientes y DAZMA conserva metadatos/referencias en la base de datos transaccional.
- **D.** Object Storage + CDN: agrega una capa de distribución/caché para contenido muy consultado. Es útil si el volumen y la distribución geográfica justifican la complejidad adicional.

## Decisión

Se adopta Object/Blob Storage para imágenes y archivos, manteniendo en la base transaccional únicamente metadatos, propietario, estado y referencia al objeto. La CDN queda como táctica condicionada a mediciones de tráfico, latencia y carga del origen; no se adopta automáticamente desde el primer despliegue.

## Consecuencias

**Ventajas:** desacopla el crecimiento de archivos de la base principal, facilita escalar almacenamiento y permite incorporar CDN posteriormente. También evita que los respaldos transaccionales crezcan por contenido binario.

**Desventajas:** se introduce consistencia entre dos recursos: registro en base de datos y objeto almacenado. Deben manejarse cargas incompletas, eliminación de objetos huérfanos, seguridad de acceso y ciclo de vida. Una CDN agrega invalidación, costos de transferencia y posibles inconsistencias temporales de caché.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige guardar las imágenes y documentos como objetos independientes en un servicio de Object/Blob Storage. La base de datos transaccional solo guarda lo necesario para controlarlos: identificador, propietario, publicación o negociación relacionada, tipo, tamaño, estado, nivel de acceso y referencia del objeto. Así las consultas, copias de seguridad y restauraciones de los datos principales no crecen por tener archivos binarios.

Cada carga empieza en estado pendiente. El backend define qué usuario puede subir el archivo y revisa que el tipo, el tamaño y la relación con el recurso de DAZMA sean válidos antes de marcarlo como disponible, así que una carga incompleta nunca aparece como archivo aceptado. Los objetos abandonados o que ya no estén relacionados con un recurso válido se identifican y se eliminan, para no exponerlos ni pagar almacenamiento de más.

El acceso se decide con los metadatos y permisos de DAZMA. Los archivos públicos se consultan directamente y los privados requieren autorización y un permiso temporal limitado a ese objeto y a esa operación. Conocer la dirección de un objeto no da acceso a todo el almacenamiento.

Guardar los archivos como BLOB en la base relacional aumentaría el tamaño de las copias de seguridad y el trabajo del motor transaccional. Guardarlos en el disco local del servidor complicaría correr varias instancias, recuperarse de fallos y reemplazar el servicio. Object Storage separa esos problemas y cobra el almacenamiento y las operaciones con un modelo pensado para archivos.
