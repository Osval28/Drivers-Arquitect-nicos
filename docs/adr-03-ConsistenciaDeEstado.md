# ADR-03: Consistencia de estado en operaciones críticas

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-SEG-0008
- Relacionado: RF-19

## Contexto

Las negociaciones y pagos pasan por varios estados. En esos flujos pueden ocurrir fallas, respuestas tardías, acciones simultáneas o notificaciones repetidas. DAZMA necesita evitar que una operación quede duplicada, incompleta o en un estado contradictorio.

## Factores de decisión

- Los cambios de propuesta, pagos o confirmación deben conservar un único estado válido aun si se presentan varias solicitudes al mismo tiempo, reinicios inesperados, o notificaciones repetidas
- El modelo debe permitir identificar transiciones válidas y auditar que solicitud produjo cada cambio
- La solución debería impedir efectos duplicados mediante identificadores persistentes y control de concurrencia

## Opciones

- **A.** Transacciones + máquina de estados + idempotency keys persistentes: confirma cambios relacionados como una unidad, define estados válidos y evita procesar dos veces el mismo evento.
- **B.** Event Sourcing completo: cada cambio se almacena como evento y el estado se reconstruye. Aporta trazabilidad, pero aumenta considerablemente la complejidad del modelo y la operación.
- **C.** Deduplicación sólo en memoria o caché: simple y rápida, pero puede fallar después de reinicios y frente a concurrencia.

## Decisión

Se adopta una fuente transaccional para el núcleo, una máquina de estados explícita y mecanismos persistentes de idempotencia mediante identificadores únicos y restricciones de unicidad. Event Sourcing no se adopta mientras los drivers actuales puedan satisfacerse con una solución más simple.

## Consecuencias

**Ventajas:** Evita estados parciales, permite reintentos seguros y reduce el riesgo de efectos económicos duplicados.

**Desventajas:** exige definir con precisión invariantes, estados y límites transaccionales. Se deben conservar identificadores ya procesados y contemplar concurrencia. Cuando la operación incluya un proveedor externo no será posible envolver todo en una sola transacción local; pueden requerirse compensación, reconciliación o procesamiento eventual controlado.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige utilizar transacciones, una máquina de estados explícita e identificadores persistentes de idempotencia para las negociaciones, confirmaciones y operaciones de pago. Cada operación crítica tendrá estados definidos y una lista de transiciones permitidas. Por ejemplo, una negociación cancelada no podrá volver a un estado de confirmación debido a una respuesta atrasada, y una operación ya completada no podrá ejecutarse otra vez. El backend comprobará el estado vigente antes de aplicar el cambio y registrará qué solicitud produjo la transición. Si dos solicitudes intentan modificar el mismo registro al mismo tiempo, se utilizará control de concurrencia en el almacenamiento persistente para que solo una confirme el cambio; la otra recibirá el estado que ya quedó vigente o un rechazo controlado.

Los cambios que deban ocurrir juntos se realizarán dentro de una transacción. Si una operación necesita cambiar el estado de la negociación y guardar la evidencia de la solicitud, ambos cambios se confirman juntos o ninguno queda aplicado. Esto evita que la plataforma muestre una negociación confirmada sin el registro que permite explicar quién la confirmó, o que registre un resultado sin haber actualizado el estado correspondiente.

Las solicitudes capaces de causar un cobro, una liberación, una devolución, una confirmación o cualquier otro efecto no repetible incluirán un identificador único. Ese identificador y el resultado quedarán guardados de forma persistente. Si el navegador, la red o un proveedor repite la misma solicitud, DAZMA reconocerá el identificador y devolverá el resultado ya registrado en vez de ejecutar otra vez el efecto. Guardar este control únicamente en memoria no es aceptable porque se pierde al reiniciar la aplicación y no coordina varias instancias.

No se adopta Event Sourcing completo. Aunque conservar cada cambio como evento permite reconstruir el historial, también exige que el estado se derive de esos eventos, mantener versiones de eventos y proyecciones, y adaptar las consultas y herramientas operativas a ese modelo. Los factores definidos requieren un estado válido, transiciones auditables y prevención de duplicados; transacciones, máquina de estados e idempotencia satisfacen esas necesidades con menos componentes y menor esfuerzo de mantenimiento.

No tiene costo de proveedor; el costo está en el tiempo de diseñar los estados, las restricciones, el almacenamiento de identificadores y las pruebas de concurrencia. Los registros de idempotencia deben guardarse mientras una operación todavía se pueda repetir, por lo que ese tiempo se definirá cuando se sepa cuánto puede tardar una solicitud válida en volver a enviarse.
