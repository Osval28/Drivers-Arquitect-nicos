# ADR-33: Selección de una alternativa para memoria caché

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-ESC-0003
- ESC-CAL-CON-0010
- ESC-CAL-CON-0011

## Contexto

Los usuarios consultarán la misma información muchas veces en un corto periodo de tiempo, por lo que tener una memoria caché reduciría las llamadas a la base de datos y la latencia al buscar productos en la plataforma.

## Factores de decisión

- La opción escogida debe permitir guardar varios tipos de datos para facilitar la visualización
- La opción escogida debe permitir guardar copias de la memoria en disco para que, en reinicios del servidor, no se tenga que volver a cargar
- Se valorará que la opción escogida tenga el menor costo posible en aspectos como el almacenamiento, el número de comandos de lectura y escritura y la disponibilidad, para evitar compromisos comerciales fuertes

## Opciones

- **A.** Redis: ofrece vencimiento de datos, límites de memoria, diferentes formas de eliminar información, persistencia opcional y estructuras como textos, objetos, listas, conjuntos y contadores. Tiene integración madura con Java y Spring y puede contratarse como servicio administrado.
- **B.** Memcached: ofrece un caché distribuido de tipo clave-valor con un funcionamiento sencillo y buen rendimiento. Tiene menos estructuras y funciones de recuperación, y está diseñado principalmente para información temporal.
- **C.** Dragonfly: es compatible con los protocolos de Redis y Memcached y utiliza un modelo de ejecución preparado para niveles altos de concurrencia. Ofrece persistencia y servicio administrado, pero tiene un ecosistema más reciente y un costo administrado de entrada superior.

## Decisión

Se adopta Redis como memoria caché compartida, contratado como servicio administrado y separado de la base de datos.

## Consecuencias

**Ventajas:** disminuye el tiempo de respuesta en consultas repetidas, reduce el trabajo sobre PostgreSQL y permite que varias instancias del backend compartan el mismo caché. Redis proporciona vencimiento automático, control de memoria, distintas estructuras de datos, persistencia opcional y una integración estable con Java y Spring.

**Desventajas:** se agrega un servicio que debe configurarse, protegerse, supervisarse y pagar. Será necesario definir qué información puede guardarse, durante cuánto tiempo y cuándo debe eliminarse. Una invalidación incorrecta puede hacer que el usuario vea información anterior durante algunos segundos o minutos.

La aplicación deberá medir cuántas consultas se resuelven desde Redis, cuánta memoria se utiliza, cuántos datos son eliminados por falta de espacio y cuánto tarda el servicio en responder. El backend deberá poder continuar desde PostgreSQL cuando Redis falle, para que una caída del caché no provoque una caída completa de DAZMA.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige utilizar Redis como memoria caché compartida, contratado como servicio administrado y separado de la base de datos. Cumple los tres factores: guarda distintos tipos de datos, ofrece persistencia opcional en disco y puede contratarse como servicio administrado.

Cuando el backend necesite información apta para caché, primero buscará una copia en Redis. Si no existe, consultará la base de datos, devolverá la respuesta y guardará temporalmente el resultado en Redis. Cuando una publicación cambie o sea eliminada, el backend deberá eliminar o actualizar las entradas relacionadas para evitar presentar información anterior.

Cada entrada tendrá un tiempo de vencimiento. No se guardarán como única copia datos críticos de usuarios, negociaciones, pagos, permisos o auditoría. Si Redis no se encuentra disponible, el backend podrá continuar consultando la base de datos, aunque con mayor tiempo de respuesta y carga.

Redis se selecciona frente a Memcached porque permite manejar distintos tipos de información, vencimientos, políticas de eliminación y persistencia opcional, con una integración más completa con Spring. Dragonfly ofrece ventajas de rendimiento para cargas muy elevadas, pero DAZMA no tiene un requisito que justifique asumir un producto con menor trayectoria y un costo administrado desde USD 36 mensuales.
