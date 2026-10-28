# ADR-19: Lenguaje y framework para el backend de DAZMA

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-SEG-0004
- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-REN-0010
- ESC-CAL-INT-0002

## Contexto

DAZMA necesita una capa backend donde se concentren las reglas principales del negocio: usuarios, publicaciones, negociaciones, pagos, autorizaciones, auditoría, notificaciones e integraciones externas. Esta capa debe proteger operaciones críticas, validar tokens de identidad, coordinar transacciones con la base de datos y exponer una API clara al frontend.

## Factores de decisión

- El lenguaje/framework seleccionado debe facilitar la inyección de dependencias y abstracción nativamente porque esto reduce el lock in en cualquier integración con servicios externos
- Se valorará que tenga funciones o integraciones automáticas para facilitar las transacciones en PostgreSQL porque se necesita garantizar que los fallos no provoquen estados inconsistentes
- La opción seleccionada debe soportar de forma nativa mecanismos de health checks porque se necesita detectar señales de degradación antes de una falla
- La opción seleccionada debe tener estandarizados mecanismos de monitoreo como logs y métricas para garantizar que se pueda observar cuántas negociaciones fallan, la latencia de un endpoint

## Opciones

- **A.** Java + Spring Boot: ecosistema maduro para APIs, seguridad, transacciones, validación, observabilidad, pruebas y despliegue en contenedores
- **B.** C# + ASP.NET Core: alternativa madura, rápida y sólida para APIs empresariales, con buen soporte de seguridad y despliegue
- **C.** TypeScript + NestJS: permite compartir lenguaje con el frontend y ofrece estructura modular, aunque su ecosistema transaccional y de persistencia es distinto al de Spring
- **D.** Python + FastAPI: rápido para crear APIs y prototipos, pero requiere más disciplina para mantener estructura, transacciones y patrones de negocio complejos

## Decisión

Se adopta Java con Spring Boot como tecnología para trabajar el backend de DAZMA. La decisión se basa en su soporte maduro para APIs REST, Spring Security, validación, integración con PostgreSQL, manejo transaccional, pruebas, health checks, métricas y ejecución en contenedores. Spring Boot permite construir un monolito modular donde las reglas de negocio se mantengan dentro del backend y las integraciones externas se conecten mediante adaptadores claros.

## Consecuencias

**Ventajas:** ofrece una base robusta para operaciones críticas, seguridad server-side, consistencia transaccional e integración con los proveedores elegidos. Facilita separar controladores, servicios, repositorios, adaptadores y trabajos en segundo plano sin convertir la solución en microservicios innecesarios

**Desventajas:** Spring Boot exige más estructura y aprendizaje que opciones más livianas. Si no se organizan bien los módulos, el monolito puede volverse acoplado. También se deben definir estándares de pruebas, manejo de errores, configuración por ambiente y migraciones de base de datos para que la robustez de la tecnología se refleje en el sistema real

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Java con Spring Boot para el backend de DAZMA. Spring ofrece inyección de dependencias y contratos claros para separar las reglas del negocio de las integraciones externas. Su manejo declarativo de transacciones permite agrupar cambios relacionados en PostgreSQL y confirmar o revertir la unidad completa cuando ocurre un error. Spring Security, validación y su ecosistema de pruebas ofrecen una base coherente para proteger y verificar las operaciones críticas del sistema.

Spring Boot Actuator expone comprobaciones técnicas de salud y se integra con Micrometer para publicar métricas del proceso, la JVM y las solicitudes. Esto cubre la necesidad de saber si el servicio funciona y de observar latencia y errores técnicos con mecanismos estandarizados. Las métricas del negocio, por ejemplo, negociaciones fallidas, pagos pendientes o fallos de un proveedor concreto, no aparecen automáticamente: DAZMA deberá registrarlas con nombres y criterios definidos para que la herramienta de monitoreo pueda utilizarlas.

ASP.NET Core también satisface estas capacidades con una base empresarial madura. NestJS permite compartir TypeScript con el frontend y FastAPI permite construir APIs en Python, pero ambos implican escoger y gobernar de otra forma la persistencia, las transacciones y la observabilidad. Se escoge Spring Boot porque trae de forma nativa lo que piden los cuatro factores de decisión: inyección de dependencias, transacciones con la base relacional, health checks y métricas estandarizadas.

Spring Boot es software abierto bajo licencia Apache 2.0 y no tiene cobro de licencia. Las alternativas mencionadas también cuentan con ediciones abiertas, por lo que el costo decisivo será el cómputo, memoria, almacenamiento, monitoreo y trabajo de mantenimiento en la plataforma de despliegue. Todavía no se puede dar un precio mensual porque depende del proveedor y del tamaño de infraestructura que se escojan. El equipo deberá usar una versión con soporte activo, aplicar actualizaciones de seguridad y mantener pruebas y migraciones; el framework no elimina ese trabajo.
