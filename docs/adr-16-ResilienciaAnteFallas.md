# ADR-16: Resiliencia frente a fallas parciales y dependencias degradadas

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0011
- Relacionado: ESC-CAL-CON-0010

## Contexto

DAZMA dependerá de proveedores y componentes que pueden fallar o responder lento. La plataforma debe evitar que una falla parcial afecte todo el sistema y debe responder de forma clara cuando una operación no pueda completarse de inmediato.

## Factores de decisión

- El sistema debe definir tiempos máximos de espera para no quedar detenido indefinidamente cuando un servicio externo no responde
- Debe permitir reintentos controlados cuando tenga sentido repetir una operación, evitando duplicar cobros, desembolsos, notificaciones o cambios de estado.
- Debe responder de forma clara al usuario cuando una operación quede pendiente, falle o necesite confirmación posterior
- Debe aislar fallas para que un problema en una integración no tumbe funciones que sí pueden seguir funcionando
- Debe registrar errores y estados pendientes para que puedan revisarse, reintentarse o conciliarse sin perder trazabilidad

## Opciones

- **A.** Reintentos simples sin control: intenta repetir operaciones cuando algo falla, pero puede duplicar efectos o aumentar la carga sobre un servicio que ya está fallando
- **B.** Resiliencia a nivel de aplicación: usa tiempos máximos de espera, reintentos limitados, aislamiento de fallas, respuestas claras y registro de estados pendientes
- **C.** Diferir operaciones no críticas: guarda tareas que pueden completarse después, como notificaciones o sincronizaciones, para no bloquear la acción principal
- **D.** Capa de infraestructura especializada: puede manejar parte de la resiliencia entre servicios, pero agrega complejidad operativa y no reemplaza las reglas del negocio

## Decisión

Se adopta manejar la resiliencia principalmente desde la aplicación, usando tiempos máximos de espera, reintentos limitados, aislamiento de fallas y estados pendientes visibles. Las operaciones críticas deben protegerse contra duplicados mediante identificadores e idempotencia. Las tareas que no necesitan completarse de inmediato se diferirán para procesamiento posterior. Cuando una integración externa no responda o entregue un estado incierto, DAZMA deberá registrar la situación, mostrar una respuesta clara al usuario y permitir conciliación o reintento seguro.

## Consecuencias

**Ventajas:** evita que una falla puntual detenga toda la plataforma, reduce duplicados en operaciones sensibles y permite que el sistema continúe funcionando parcialmente cuando una dependencia se degrada. También mejora la trazabilidad porque los estados pendientes y errores quedan registrados

**Desventajas:** requiere definir bien tiempos de espera, límites de reintento, estados pendientes y reglas de conciliación. Si los reintentos se configuran mal pueden aumentar la carga o repetir operaciones peligrosas. Si los mensajes al usuario no son claros, una operación pendiente puede confundirse con una operación fallida o confirmada

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige manejar la resiliencia desde la aplicación. Cada llamada a un proveedor externo tendrá un tiempo máximo de espera; los reintentos serán limitados y se aplicarán únicamente cuando repetir la operación sea seguro. Pagos, desembolsos, devoluciones y cambios de estado usarán un identificador persistente para impedir que una respuesta tardía o repetida produzca el mismo efecto dos veces.

Las funciones que no dependan del proveedor afectado seguirán disponibles. Cuando una operación crítica entregue un resultado incierto, DAZMA conservará el estado pendiente, informará al usuario que todavía no existe confirmación y permitirá consultar o conciliar el resultado antes de volver a ejecutarla. Los errores, intentos y respuestas necesarias para investigar el caso quedarán relacionados con la operación correspondiente.

Las tareas que no necesitan finalizar dentro de la respuesta al usuario, como el envío de notificaciones o una sincronización, se registrarán para procesarlas por separado. Si fallan, podrán intentarse nuevamente sin bloquear la acción principal ni perder trazabilidad. Esta combinación toma las opciones B y C: controles en la aplicación y procesamiento diferido de lo que no es crítico.

Los reintentos sin límites no se adoptan porque pueden aumentar la carga de un proveedor que ya está fallando y duplicar operaciones sensibles. Tampoco se introduce una capa de infraestructura especializada: agregaría operación y no puede decidir por sí sola si un pago es seguro de repetir o qué estado debe ver el usuario. Esta estrategia no tiene una licencia o plan independiente que permita dar una cifra. Su costo aparece en el tiempo de desarrollo, el almacenamiento de estados pendientes, la ejecución de tareas y las solicitudes adicionales a proveedores; esos consumos se medirán dentro de la plataforma de despliegue y de cada integración.
