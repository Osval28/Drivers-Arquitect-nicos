# ADR-20: Estilo de API y contrato entre frontend, backend e integraciones

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-INT-0002
- Relacionado: ESC-CAL-REN-0010

## Contexto

El frontend, el backend y algunas integraciones deben comunicarse mediante contratos claros. DAZMA necesita definir el estilo de API para que las operaciones del negocio sean entendibles, comprobables y estables a medida que el sistema crece.

## Factores de decisión

- La API debe expresar operaciones del negocio de forma clara: consultar, crear, actualizar, confirmar o cancelar recursos
- Debe manejar autenticación, autorización, errores, paginación, validaciones y solicitudes repetidas de manera entendible para frontend y backend
- Debe poder documentarse con un contrato que permita probar endpoints y evitar cambios incompatibles sin control
- Debe funcionar bien con proveedores externos que envían webhooks o requieren llamadas HTTP
- La solución debe ser suficiente para el alcance actual de DAZMA sin agregar una capa de consulta o comunicación más compleja de lo necesario

## Opciones

- **A.** REST/JSON + OpenAPI: usa recursos y operaciones HTTP entendibles, devuelve JSON y permite documentar contratos con OpenAPI.
- **B.** GraphQL: permite al cliente pedir exactamente los campos que necesita, pero agrega complejidad de esquema, autorización por campo, caché y operación.
- **C.** API RPC ad-hoc: rápida para crear endpoints sueltos, pero tiende a contratos inconsistentes y difíciles de mantener.
- **D.** gRPC: contratos binarios con Protocol Buffers sobre HTTP/2. Es eficiente entre servicios internos, pero los navegadores y los proveedores externos que envían webhooks no lo consumen de forma directa y exige herramientas adicionales de generación de código.

## Decisión

Se adopta REST/JSON con OpenAPI como estilo principal de API para DAZMA. Las operaciones del negocio se expondrán como endpoints HTTP documentados, con respuestas JSON, códigos de estado consistentes, validaciones claras y contratos versionables. OpenAPI servirá para documentar y probar la comunicación entre frontend y backend. Los eventos enviados por proveedores externos se recibirán mediante endpoints HTTP específicos y validados en backend.

## Consecuencias

**Ventajas:** es fácil de entender, probar, documentar e integrar con frontend y proveedores externos. Encaja bien con un backend centrado en reglas de negocio y con operaciones como consultar, crear, confirmar o cancelar recursos.

**Desventajas:** si los endpoints se diseñan mal puede aparecer exceso o falta de datos en algunas consultas. También exige disciplina para mantener OpenAPI sincronizado con la implementación, definir errores estándar, versionar cambios incompatibles y no mezclar reglas de negocio en el frontend.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige REST sobre HTTP con mensajes JSON y un contrato OpenAPI como estilo principal de comunicación entre frontend y backend. Las rutas representarán recursos y acciones de negocio reconocibles, y utilizarán de forma consistente los métodos y códigos de estado HTTP. El contrato describirá parámetros, cuerpos, respuestas y errores para que ambas partes puedan conocer y probar la comunicación sin depender de explicaciones separadas.

La API tendrá una estructura común para errores de validación y de negocio, reglas de paginación en listados, autenticación y comprobación de autorización en el backend. Las operaciones cuyo efecto no debe repetirse, especialmente las relacionadas con pagos o cambios críticos de estado, aceptarán un identificador que permita reconocer solicitudes duplicadas. Los webhooks de proveedores tendrán endpoints específicos, validarán autenticidad y contenido y responderán según el resultado del procesamiento.

GraphQL permite seleccionar campos con precisión, pero añade un esquema de consulta, resolvers y controles de autorización y complejidad que los factores actuales no necesitan. Un RPC definido endpoint por endpoint puede construirse con rapidez, pero dificulta mantener convenciones, documentación y errores coherentes. REST/JSON ofrece el contrato suficiente para las operaciones descritas y coincide con las integraciones externas basadas en HTTP. gRPC ofrece contratos binarios eficientes entre servicios internos, pero DAZMA es un monolito modular sin comunicación entre servicios que lo justifique, y el frontend y los webhooks de proveedores usan HTTP con JSON.

OpenAPI es un estándar abierto, independiente del lenguaje, publicado bajo Apache 2.0; usar la especificación no genera un cobro por solicitud ni una licencia mensual. Las herramientas comerciales que puedan alojar documentación, generar clientes o gobernar APIs tienen precios propios, pero no se escogió ninguna, por lo que no suman costo. El documento OpenAPI deberá mantenerse sincronizado con el comportamiento real. No reemplaza la implementación de autorización, validación, idempotencia ni seguridad de webhooks; solo hace explícito y comprobable el contrato.
