# ADR-26: Definición del tipo de aplicación (Reactiva/No reactiva/Ambas)

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0011
- ESC-CAL-REN-0010
- ESC-CAL-ESC-0003

## Contexto

DAZMA tendrá operaciones de negocio que deben responder con claridad al usuario y también integraciones externas que pueden tardar, fallar o quedar pendientes, como pagos, notificaciones y eventos de proveedores. Se necesita definir si el backend trabajará con un modelo tradicional, un modelo completamente reactivo o una combinación controlada de ambos.

## Factores de decisión

- El modelo de ejecución debe permitir atender solicitudes concurrentes sin complicar innecesariamente el desarrollo de reglas de negocio
- Debe facilitar transacciones claras, manejo de errores, trazabilidad y pruebas en operaciones críticas como pagos, confirmaciones y cambios de estado
- Las tareas que no requieren respuesta inmediata deben poder diferirse a workers para no bloquear la solicitud principal
- La decisión debe cuidar rendimiento, mantenibilidad y costo operativo sin introducir un estilo difícil de depurar para el equipo

## Opciones

- **A.** Aplicación tradicional bloqueante con Spring MVC: modelo simple de entender, buen soporte transaccional y suficiente para APIs de negocio si se usan timeouts, pools y workers
- **B.** Aplicación completamente reactiva con WebFlux: aprovecha mejor recursos en I/O intensivo, pero aumenta complejidad de programación, pruebas y depuración
- **C.** Modelo mixto: API principal tradicional y procesamiento diferible mediante workers/outbox para tareas lentas o externas

## Decisión

Se adopta un modelo mixto: API principal tradicional con Spring MVC para las operaciones de negocio y workers para tareas diferibles registradas mediante Outbox. Este enfoque mantiene simples y claras las transacciones del dominio, permite responder al usuario sin esperar trabajos secundarios y evita convertir toda la aplicación a programación reactiva cuando los beneficios no compensan su complejidad.

## Consecuencias

**Ventajas:** facilita entender, probar y mantener reglas de negocio críticas, conserva un manejo transaccional claro con PostgreSQL y permite diferir notificaciones, reintentos o tareas externas sin bloquear la operación principal

**Desventajas:** requiere configurar correctamente pools, timeouts y límites de concurrencia. Los workers deben monitorearse y diseñarse para no duplicar efectos. Si DAZMA tuviera en el futuro cargas altamente intensivas en I/O simultáneo, habría que reevaluar partes específicas con enfoque reactivo.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige una API tradicional con Spring MVC para las solicitudes del negocio y workers para las tareas que no necesitan terminar dentro de la respuesta al usuario. Esto significa que el backend principal no será una aplicación WebFlux completamente reactiva. La combinación se refiere a separar el trabajo inmediato del trabajo en segundo plano, no a mezclar dos modelos de programación dentro de cada operación.

Spring MVC permite expresar con claridad transacciones, validaciones, cambios de estado y manejo de errores. La concurrencia se controlará mediante límites de conexiones, tiempos máximos de espera y capacidad definida para cada proceso. Las llamadas lentas o recuperables, como notificaciones y tareas pendientes, se enviarán al worker mediante la Outbox y el worker definidos para el procesamiento diferible.

WebFlux puede usar recursos de forma eficiente cuando existe una cantidad muy alta de operaciones simultáneas esperando respuestas externas, pero esa ventaja exige que toda la cadena utilice librerías no bloqueantes. Mezclar código bloqueante dentro de una aplicación reactiva reduce el beneficio y hace más difícil seguir errores y transacciones. Los factores actuales priorizan claridad, consistencia y facilidad de prueba, por lo que Spring MVC satisface mejor la necesidad.

Spring MVC y WebFlux forman parte de Spring y no tienen una tarifa de licencia diferente. El costo relevante está en el cómputo y en el esfuerzo de construcción, prueba y soporte. El modelo escogido evita pagar esa complejidad cuando todavía no se necesita y conserva procesos separados para que el trabajo lento no ocupe las solicitudes principales.
