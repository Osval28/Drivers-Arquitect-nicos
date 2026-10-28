# ADR-32: Seguridad y ciclo de vida de archivos

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-SEG-0004
- ESC-CAL-SEG-0008
- ESC-CAL-AUD-0008

## Contexto

Los usuarios podrán cargar imágenes y documentos asociados con publicaciones y negociaciones. Estos archivos pueden tener tamaños o formatos no permitidos, contener material peligroso o quedar accesibles para personas que no deberían consultarlos. DAZMA necesita controlar su carga, acceso, conservación y eliminación durante todo su ciclo de vida.

## Factores de decisión

- Se deben limitar formatos y tamaños antes de completar la carga.
- El tipo real del archivo debe validarse y no confiar únicamente en su nombre.
- Las imágenes deben limitarse también en dimensiones (píxeles) y no solo en peso, porque ocupan en memoria mucho más de lo que pesan.
- Los archivos privados deben requerir autorización o permisos temporales.
- Los archivos eliminados o abandonados no deben conservarse indefinidamente.
- La solución debe permitir incorporar análisis de contenido malicioso según el riesgo.

## Opciones

- **A.** Contenedor completamente público: facilita la consulta, pero no permite proteger documentos privados.
- **B.** Cargas directas sin validación: simplifican el backend, pero permiten archivos inesperados, excesivos o peligrosos.
- **C.** Acceso controlado con permisos temporales, validación y reglas de ciclo de vida: protege archivos sin hacer que el backend transporte todo su contenido.

## Decisión

Se adopta conservar privados los archivos que no deban ser públicos y utilizar permisos temporales para carga o descarga. El backend autorizará la operación, definirá tamaño y tipo permitidos y guardará en PostgreSQL el propietario, propósito, estado y referencia del objeto. Al finalizar cada carga se verifica el tipo real y se aplica el control de contenido correspondiente al riesgo. Las imágenes se recodifican antes de publicarse, con un máximo de megapíxeles que se comprueba antes de decodificarlas.

## Consecuencias

**Ventajas:** reduce exposición de documentos, abuso de almacenamiento y riesgo por archivos inesperados.

**Desventajas:** requiere controlar permisos temporales, validar contenido y mantener limpieza de archivos. El análisis avanzado puede añadir costo y tiempo de procesamiento, por lo que deberá ajustarse al tipo de archivo aceptado.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige mantener privado el contenedor de archivos y usar permisos temporales para cargar y descargar. Cuando un usuario quiere subir un archivo, el backend comprueba que puede hacerlo, registra en PostgreSQL el propietario, el propósito, el tamaño y el tipo declarados y el estado pendiente, y entrega un enlace firmado de corta duración. El archivo viaja directo del navegador al almacenamiento, sin pasar por el backend, y solo se considera válido cuando termina la verificación posterior a la carga.

El nombre, la extensión y el tipo que declara el navegador los controla el cliente, así que no sirven como validación. Después de la carga se leen los primeros bytes del objeto para comprobar su formato real. El tamaño tampoco puede confiarse solo al enlace: R2 no admite las cargas firmadas por formulario que permiten fijar un rango de tamaño en otros servicios compatibles con S3. Por eso el tamaño declarado se incluye en la firma y el tamaño real se comprueba al confirmar la carga. Los archivos que no superan la verificación, o que nunca se confirman, permanecen pendientes y se eliminan con una regla de ciclo de vida.

Las imágenes de las publicaciones se volverán a codificar antes de publicarse. Esto neutraliza contenido inesperado incrustado en la imagen y elimina los metadatos EXIF, que en fotos tomadas con celular suelen incluir coordenadas GPS. En un marketplace hiperlocal, muchas fotos de productos se toman en la casa del vendedor, y publicarlas con esos metadatos expondría su ubicación. Antes de decodificar una imagen se leen sus dimensiones desde la cabecera y se rechaza si supera el máximo de megapíxeles permitido, porque una foto de 12 megapíxeles ocupa unos 48 MB en memoria al decodificarse aunque pese pocos MB comprimida. Las fotos validadas pueden servirse públicamente. Los documentos asociados con negociaciones permanecen privados y solo se entregan mediante enlaces temporales a quien participa en ellas.

Un contenedor completamente público impide proteger documentos privados, y las cargas directas sin validación permiten archivos excesivos o peligrosos. La opción elegida exige más trabajo: firmar enlaces, verificar después de la carga y limpiar pendientes. A cambio, el backend no transporta el contenido de los documentos durante la subida, lo que evita consumir la memoria y la transferencia de la instancia; las imágenes sí las lee después para recodificarlas. Los plazos de conservación deben definirse por propósito, porque la Ley 1581 obliga a no conservar datos personales más tiempo del necesario, y la conciliación y las disputas de ADR-30 necesitan conservar evidencia. Ambas exigencias no se resuelven con una sola regla general.

R2 cobra actualmente USD 0,015 por GB al mes, con 10 GB gratuitos, además de un valor por operaciones de escritura y lectura y sin cobro por transferencia de salida. El análisis de contenido malicioso es el componente que puede encarecer la solución: un antivirus propio, como ClamAV, requiere otra instancia con memoria suficiente. Por eso se propone reservar el análisis para documentos y proteger las imágenes mediante la recodificación, ajustando esa decisión al tipo de archivo que DAZMA acepte finalmente.
