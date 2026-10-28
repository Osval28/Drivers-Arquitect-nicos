# ADR-09: Estrategia de integración con proveedores externos

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-INT-0002
- ESC-CAL-CON-0011
- ESC-CAL-CON-0005

## Contexto

DAZMA dependerá de servicios externos para identidad, pagos, notificaciones, chat y almacenamiento. Como esos proveedores pueden cambiar o fallar, el sistema necesita una forma ordenada de integrarlos sin que el corazón del negocio dependa directamente de cada proveedor.

## Factores de decisión

- DAZMA debe poder integrarse con servicios externos sin que las reglas principales del negocio queden amarradas a la forma particular de trabajar de cada proveedor
- La solución debe traducir pagos, identidad, correo o chat a conceptos propios de DAZMA, para que el sistema pueda cambiar o ajustar proveedores con el menor impacto posible
- La separación debe ser simple y útil. No se busca crear capas innecesarias, sino proteger las partes importantes del negocio frente a cambios externos

## Opciones

- **A.** Usar SDK/modelos del proveedor directamente en el dominio: reduce código inicial, pero genera dependencia a la implementación del proveedor y propaga cambios externos
- **B.** Ports & Adapters + Adapter/Anti-Corruption Layer: Nuestra plataforma define contratos internos y cada proveedor traduce sus modelos/errores hacia ese contrato
- **C.** ESB/iPaaS desde el inicio: centraliza integración y transformación, pero añade una plataforma y complejidad actualmente no justificadas
- **D.** API Gateway con lógica de adaptación de negocio: centraliza entrada, pero puede desplazar reglas del dominio hacia el borde y crear un nuevo punto de acoplamiento

## Decisión

Se adoptan contratos internos para cada tipo de integración externa. El backend de DAZMA trabajará con conceptos propios del negocio, como pago aprobado, notificación enviada, usuario autenticado, archivo disponible o conversación creada. Cada proveedor externo tendrá un adaptador encargado de traducir sus respuestas, errores y eventos hacia esos conceptos internos.

## Consecuencias

**Ventajas:** reduce el acoplamiento con proveedores externos, facilita pruebas con datos simulados y limita el impacto de cambios en APIs, errores o respuestas de terceros. También ayuda a que el negocio de DAZMA conserve sus propios conceptos aunque cambie la tecnología usada para pagos, identidad, notificaciones, chat o almacenamiento

**Desventajas:** agrega código de adaptación y requiere mantener contratos internos claros. No elimina por completo la dependencia económica, contractual o funcional de un proveedor. Además, si se diseñan interfaces demasiado genéricas, pueden perder información importante del proveedor o dificultar el uso de capacidades necesarias

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige definir contratos internos para identidad, pagos, notificaciones, chat y almacenamiento, y crear un adaptador para cada proveedor. Las reglas del negocio trabajarán con conceptos propios de DAZMA y el adaptador convertirá esos conceptos en las solicitudes, respuestas, estados y errores particulares de la API externa. Los SDK del proveedor se mantendrán dentro de esa integración y no se usarán en las reglas centrales.

Esta alternativa responde al factor de cambio con un límite concreto. Si un proveedor modifica un código de error, una estructura de respuesta o una versión del SDK, la traducción se corrige en su adaptador sin obligar a que publicaciones, negociaciones o pagos conozcan ese detalle. También permite probar las reglas del negocio con respuestas simuladas y comprobar de forma separada que el adaptador entiende los casos reales del proveedor.

Usar directamente las clases del SDK reduce mapeos, pero dispersa nombres, estados y excepciones externas por la aplicación. Cambiar de proveedor exigiría entonces localizar y modificar varios módulos. Un ESB o iPaaS puede centralizar transformaciones, pero agrega otra plataforma, licenciamiento, monitoreo y disponibilidad para integraciones que pueden permanecer dentro del backend.

Los contratos no serán interfaces genéricas que oculten diferencias importantes. Si un proveedor dispone de una función necesaria que no existe en los demás, se representará explícitamente. El objetivo es aislar formatos y errores técnicos, no fingir que todos los servicios ofrecen lo mismo ni prometer un reemplazo sin trabajo. La dependencia contractual, económica, de datos y de capacidades continúa existiendo aunque el código esté separado.
