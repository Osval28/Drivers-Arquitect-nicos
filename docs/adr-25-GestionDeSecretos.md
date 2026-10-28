# ADR-25: Gestión de secretos y credenciales técnicas

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-SEG-0008
- ESC-CAL-INT-0002

## Contexto

Se necesita guardar credenciales para la base de datos, pagos, identidad, notificaciones y otros servicios. Es necesario definir cómo se guardarán, separarán y rotarán esos secretos para evitar exposiciones y uso indebido.

## Factores de decisión

- Los secretos deben estar separados por ambiente: desarrollo, pruebas y producción
- No deben guardarse en el repositorio, en código fuente, en el frontend, en capturas, en logs ni en documentos compartidos sin control
- La solución debe permitir limitar quién puede ver o modificar cada secreto y dejar claro qué servicio lo usa

## Opciones

- **A.** Variables de entorno gestionadas manualmente en cada plataforma (Render, Vercel, GitHub): no requiere infraestructura adicional ni costos, pero la auditoría, la rotación y el control de acceso por secreto tendrían que ser manejados por el equipo, y los valores quedan repartidos entre varias plataformas
- **B.** Secret Manager administrado por un proveedor de nube: depende del proveedor y de su modelo de costos, pero ofrece control de acceso por roles, versionado de secretos, auditoría de accesos y rotación, y concentra los secretos en un solo lugar
- **C.** Vault autogestionado: Cuenta con las mismas funcionalidades que la opción anterior, pero se tiene independencia del proveedor de nube, a cambio, el equipo debe manejar toda la complejidad de desplegar, actualizar y mantener la disponibilidad del servicio

## Decisión

Se adopta un baúl de secretos administrado y dedicado, separado de la plataforma de despliegue, para producción y ambientes compartidos (opción B). El proveedor concreto se selecciona en ADR-35. Cada servicio tiene su propio secreto, con permisos limitados a quien lo necesita, y la aplicación recibe únicamente los secretos que usa. Para desarrollo local se usan archivos de configuración no versionados y archivos de ejemplo sin valores reales. Las credenciales se tratan como secretos técnicos y se reemplazan si se exponen.

## Consecuencias

**Ventajas:** reduce el riesgo de filtración, concentra los secretos fuera del repositorio y de las plataformas de despliegue, permite cambiar credenciales sin modificar código y controlar mejor quién accede a cada valor.

**Desventajas:** agrega una dependencia de un proveedor de nube y un costo por secreto. La aplicación necesita una credencial propia para leer el baúl, que pasa a ser el secreto más sensible. La seguridad depende de permisos bien configurados, y si el equipo copia secretos por fuera del mecanismo definido se pierde el control. También se requiere disciplina para no imprimir secretos en logs ni enviarlos al frontend.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige guardar las credenciales de producción en un baúl administrado. Desarrollo, pruebas y producción tienen valores independientes y cada aplicación recibe únicamente los secretos que necesita. Las credenciales no se incluyen en el código, en el repositorio ni en variables enviadas al navegador.

Las variables de entorno de Vercel y Render (opción A) están incluidas en sus planes y no tienen tarifa por secreto, pero no ofrecen auditoría de accesos, versionado ni rotación comparables a las de un baúl especializado, y dejan los valores repartidos entre plataformas. Un baúl autogestionado (opción C) da independencia del proveedor, pero exige desplegar, actualizar y mantener disponible un componente crítico.

Los proveedores de baúl cobran por secreto almacenado o por operaciones, con costos bajos para pocos secretos; la comparación de proveedores y costos está en ADR-35. Los cambios de clave pueden hacerse sustituyendo el valor y volviendo a desplegar el servicio que la usa, sin necesidad de rotación automática en el alcance actual.

Si una credencial aparece en un commit, log, captura o documento sin control, se considera expuesta y se reemplaza. El acceso a la consola del baúl y a los paneles de las plataformas se limita a quienes deban administrar la aplicación.
