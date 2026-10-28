# ADR-34: Selección del API Gateway

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- Relacionado: ESC-CAL-CON-0010

## Contexto

El sistema debe estar abierto a crecer sin afectar el funcionamiento en el futuro, por lo que integrar un API Gateway facilitaría migrar a nuevas versiones o formatos del sistema, como una app móvil.

## Factores de decisión

- Debe permitir publicar versiones de la API sin obligar a actualizar inmediatamente todos los clientes
- Debe funcionar con servicios alojados fuera de la infraestructura del proveedor, sin exigir que todo el sistema se despliegue en la misma nube
- Debe permitir describir y administrar las rutas mediante el contrato OpenAPI utilizado por el proyecto
- En el costo se considerarán las solicitudes, la transferencia de información, las métricas, los dominios y otros servicios necesarios para utilizarlo en producción

## Opciones

- **A.** Amazon API Gateway: servicio administrado con pago por solicitudes. Permite rutas, versiones, límites, validación de tokens, métricas y conexión con cualquier backend HTTP accesible. Reduce la administración, pero crea dependencia de AWS y puede generar cargos adicionales por transferencia y observabilidad.
- **B.** Azure API Management: servicio administrado con políticas, control de solicitudes, analítica y portal para APIs. Cuenta con modalidad por consumo y planes de capacidad, pero sus mayores ventajas aparecen cuando la infraestructura utiliza el ecosistema de Azure.
- **C.** Kong Gateway: plataforma independiente del proveedor de nube, con rutas, autenticación, límites y extensiones. Puede contratarse administrada o instalarse en infraestructura propia. La opción administrada comienza en USD 25 mensuales más consumo, mientras que las alternativas con mayores garantías tienen un costo considerablemente superior.
- **D.** NGINX / NGINX Plus: proxy de alto rendimiento que puede realizar rutas, balanceo y limitación de solicitudes. Su versión base no tiene costo de licencia, pero el equipo debe encargarse de configuración, actualización, disponibilidad, monitoreo y recuperación. Las funciones comerciales requieren NGINX Plus.

## Decisión

Se adopta Amazon API Gateway en modalidad HTTP API como punto de entrada de las APIs públicas de DAZMA.

## Consecuencias

**Ventajas:** se obtiene un punto de entrada estable y administrado, con escalamiento automático, control de solicitudes, rutas versionadas, métricas y registros. Los clientes no necesitan conocer la ubicación interna del backend. El modelo por solicitud evita pagar una instancia permanente del gateway y tiene un costo menor que las alternativas administradas comparadas.

**Desventajas:** DAZMA incorpora dependencia de AWS para la entrada de la API, aunque el backend se encuentre en otra plataforma. Cada solicitud recorre un componente adicional, por lo que hay un pequeño aumento de latencia. Pueden aparecer costos separados por transferencia de datos, métricas y almacenamiento de logs. El gateway se convierte en un componente crítico: una configuración incorrecta puede bloquear solicitudes válidas o dejar rutas sin protección. Además, limita cada solicitud a 10 MB y cada integración a 30 segundos, sin posibilidad de ampliarlos.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige utilizar Amazon API Gateway en modalidad HTTP API como punto de entrada de las APIs públicas de DAZMA. Es un servicio completamente administrado, puede enviar solicitudes a cualquier backend HTTP accesible y no exige que el backend esté alojado en AWS. Esto permite obtener un gateway administrado sin operar servidores adicionales y sin trasladar el backend.

La elección responde principalmente a la relación entre costo y operación. AWS publica un precio desde aproximadamente 1 USD por cada millón de solicitudes HTTP para los primeros 300 millones mensuales, según la región. Por ejemplo, diez millones de solicitudes representarían aproximadamente 10 USD por el gateway. Kong Konnect comienza en 25 USD mensuales más consumo y su alternativa dedicada comienza en 500 USD mensuales más transferencia. NGINX evita parte del costo de licencia, pero traslada al equipo la responsabilidad de mantener disponible el punto por el que pasa toda la API.

Azure API Management ofrece capacidades comparables, pero sus planes y herramientas aportan mayor beneficio cuando la infraestructura utiliza Azure. En DAZMA introduciría otra plataforma administrada con un modelo de planes más amplio, sin una ventaja suficiente sobre el modelo por solicitud de AWS.

La documentación de cuotas de Amazon API Gateway fija para HTTP API un tamaño máximo de 10 MB por solicitud y un tiempo máximo de 30 segundos por integración, y ninguno de los dos puede ampliarse. Por eso los archivos no pasan por el gateway (ADR-32) y las operaciones largas se resuelven de forma diferida (ADR-21).
