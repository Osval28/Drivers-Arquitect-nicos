# ADR-21: Estrategia de mensajería asíncrona y procesamiento diferible

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0005
- ESC-CAL-CON-0011
- ESC-CAL-INT-0002
- Relacionado: ESC-CAL-REN-0010

## Contexto

Algunas tareas de DAZMA no tienen que completarse en el mismo instante de la acción principal, como enviar notificaciones, procesar eventos o reintentar operaciones fallidas. Se necesita definir cómo manejar ese trabajo pendiente sin perderlo ni duplicarlo.

## Factores de decisión

- Las tareas que no necesitan completarse de inmediato deben quedar registradas para ejecutarse después
- Esto aplica a notificaciones, procesamiento de eventos o trabajos que pueden fallar temporalmente y necesitar reintento
- La solución debe permitir ver errores, controlar tareas pendientes y evitar que una misma tarea produzca resultados duplicados

## Opciones

- **A.** Outbox en PostgreSQL + worker: registra tareas con el cambio de negocio y reutiliza infraestructura.
- **B.** Outbox + RabbitMQ: facilita consumidores y rutas independientes; agrega broker y su operación.
- **C.** Cola administrada: delega infraestructura, con tarifas e integración propias.

## Decisión

Se adopta una Outbox transaccional en PostgreSQL y un worker con concurrencia acotada, reintentos y estado de error visible. Las tareas se reclamarán de forma segura entre instancias y cada consumidor será idempotente. Se vigilarán antigüedad y cantidad de pendientes.

## Consecuencias

**Ventajas:** Evita la ventana entre guardar la operación y registrar el trabajo, con menos componentes iniciales.

**Desventajas:** El worker y la limpieza consumen recursos de base y requieren monitoreo. Puede haber entregas repetidas si el proceso falla después de ejecutar el efecto; Outbox no ofrece exactamente una ejecución externa. Este mecanismo no transportará el chat en vivo.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige registrar las tareas pendientes en una tabla Outbox de PostgreSQL y procesarlas mediante un worker. El cambio principal del negocio y la tarea pendiente se guardarán en la misma transacción: si la operación se confirma, ambos quedan registrados; si falla, ninguno queda a medias. Esto evita que una notificación o trabajo se pierda porque la aplicación se detuvo después de guardar el cambio principal.

El worker tomará las tareas pendientes, registrará si fueron completadas o fallaron y aplicará reintentos limitados. Cada tarea tendrá un identificador único para impedir que una repetición produzca dos correos, dos instrucciones de pago o dos cambios equivalentes. El listado de tareas permitirá consultar cuáles siguen pendientes, cuánto tiempo llevan esperando y por qué fallaron.

RabbitMQ aportaría colas, rutas y consumidores independientes, pero también añadiría otro servicio que debe configurarse, monitorearse, respaldarse y recuperarse. Los factores de decisión se pueden cumplir con la base transaccional y un worker, sin necesidad de agregar un broker. Una cola administrada reduciría parte de esa operación, aunque agregaría una tarifa y otro proveedor sin eliminar la necesidad de controlar duplicados y errores en DAZMA.

La técnica Outbox no tiene licencia propia. Su costo proviene del espacio y las consultas adicionales en PostgreSQL y del cómputo del worker. Como referencia del proveedor de despliegue seleccionado, Render cobra actualmente USD 7 al mes por 512 MB o USD 25 al mes por 1 CPU y 2 GB para servicios y workers. Un worker desplegado como proceso separado se cobra como una instancia adicional; el plan gratuito de Render no incluye background workers.
