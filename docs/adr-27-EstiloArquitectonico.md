# ADR-27: Definición del estilo arquitectónico

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-INT-0002
- ESC-CAL-DIS-0006
- ESC-CAL-AUD-0008

## Contexto

Se decidieron estrategias para manejar consistencia de estados de operaciones críticas con ACID, pero manejar esto depende del tipo de arquitectura que se vaya a utilizar, por lo que se necesita definir cuál será el estilo arquitectónico del sistema.

## Factores de decisión

- La arquitectura debe mantener juntas las reglas de negocio que necesitan consistencia, especialmente pagos, estados de negociación y confirmaciones
- Debe permitir separar módulos por responsabilidad para que el código no se convierta en una sola masa difícil de mantener
- Debe reducir costos de operación, despliegue y observabilidad frente a una arquitectura distribuida que aún no aporta beneficios claros

## Opciones

- **A.** Monolito modular: una sola aplicación desplegable, organizada internamente por módulos de negocio con límites claros
- **B.** Microservicios: varios servicios independientes con bases, despliegues y comunicación distribuida. Permite independencia, pero aumenta complejidad, costos y problemas de consistencia
- **C.** Monolito por capas sin límites de módulo: simple al comienzo, pero puede degradarse si todas las reglas se mezclan
- **D.** Arquitectura serverless dominante: útil para eventos puntuales, pero menos clara para el núcleo transaccional del negocio

## Decisión

Se adopta un monolito modular para DAZMA. La aplicación se desplegará como una unidad, pero el código se organizará por módulos de negocio como usuarios, publicaciones, negociaciones, pagos, notificaciones y chats. Cada módulo tendrá responsabilidades claras y se comunicará mediante interfaces internas. Los servicios externos se integrarán mediante adaptadores, sin partir el dominio en microservicios.

## Consecuencias

**Ventajas:** reduce la complejidad operativa, facilita mantener transacciones y reglas consistentes, simplifica despliegue y permite trabajar el backend con una estructura ordenada.

**Desventajas:** exige disciplina para respetar límites internos. Si los módulos se acoplan entre sí, el monolito pierde claridad. Además, todo el backend comparte ciclo de despliegue, por lo que se requieren pruebas, contratos internos y una organización estricta del código

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige construir DAZMA como un monolito modular. El backend será una sola aplicación desplegable, pero el código tendrá límites claros entre las responsabilidades del negocio. Cada módulo controlará sus reglas y datos, y la comunicación interna se realizará mediante interfaces definidas en lugar de acceder libremente a cualquier parte de la aplicación.

Esta estructura mantiene dentro de una misma transacción las operaciones que necesitan consistencia, como cambios de negociación, confirmaciones e instrucciones relacionadas con pagos. También evita la comunicación por red, coordinación distribuida y conciliación adicional que aparecerían si esas reglas estuvieran repartidas entre varios servicios.

Los microservicios permiten desplegar y escalar componentes de manera independiente, pero cada servicio requiere proceso de ejecución, configuración, registros, monitoreo, seguridad y recuperación propios. En Render, cada servicio siempre activo se cobra por separado desde USD 7 mensuales por 512 MB o USD 25 por 1 CPU y 2 GB. Dividir módulos sin una necesidad concreta multiplicaría instancias y tareas operativas antes de aportar una ventaja demostrable.

Un monolito sin límites internos tendría una factura similar, pero permitiría mezclar responsabilidades hasta volver difícil el mantenimiento. Por eso la modularidad forma parte de la decisión y debe comprobarse mediante dependencias controladas entre módulos. El modelo seleccionado reduce despliegues y puntos de falla, mantiene las transacciones claras y conserva separación dentro del código.
