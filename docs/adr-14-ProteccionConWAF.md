# ADR-14: Protección de la aplicación web mediante WAF

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-SEG-0008
- ESC-CAL-CON-0010
- ESC-CAL-SEG-0004

## Contexto

Los endpoints públicos del sistema estarán expuestos a tráfico de internet. Además de las validaciones internas, se necesita una capa de protección que ayude a reducir solicitudes abusivas o maliciosas antes de que lleguen a la aplicación.

## Factores de decisión

- Esa capa debe ayudar a bloquear abuso, tráfico sospechoso o intentos comunes de ataque sin complicar el acceso normal de usuarios y proveedores externos
- También debe permitir revisar qué se bloqueó, ajustar reglas y controlar costos, porque una protección mal configurada puede afectar usuarios reales

## Opciones

- **A.** Cloudflare WAF: protege aplicaciones y API mediante reglas administradas, controles de solicitudes y protección DDoS
- **B.** AWS WAF: ofrece reglas administradas y personalizadas, con buena integración con CloudFront, API Gateway y otros servicios de AWS
- **C.** Google Cloud Armor: protege aplicaciones mediante la red global de Google, reglas administradas y límites de solicitudes
- **D.** Azure WAF con Front Door: proporciona reglas administradas y protección global desde la infraestructura de Microsoft. Su capacidad completa requiere Azure Front Door Premium
- **E.** Akamai App & API Protector: combina protección web, seguridad de API, mitigación DDoS y control de bots. Está orientado a entornos empresariales y distribuidos; sus precios requieren cotización.

## Decisión

Se adopta Cloudflare WAF como capa de protección de los endpoints públicos por su filtrado administrado de solicitudes web y API y su independencia del alojamiento del backend. El WAF protege los endpoints de la API pública; la aplicación web se protege con los controles de su propia plataforma de despliegue, sin un proxy adicional delante. El origen aceptará tráfico únicamente a través del acceso autorizado para impedir que se eludan las reglas. Las reglas se ajustarán para conservar el tráfico legítimo y los webhooks, manteniendo en la aplicación la autorización y validación de negocio.

## Consecuencias

**Ventajas:** filtra patrones de ataque y facilita reglas comunes en el borde.

**Desventajas:** puede bloquear operaciones válidas y añade dependencia y costo. No sustituye autorización por recurso, validación de entradas ni firma de webhooks. Un WAF de DAZMA no protege directamente la infraestructura del proveedor de chat; revisar sus controles por separado. El frontend queda fuera de las reglas administradas del WAF y depende de los controles de su plataforma.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige utilizar Cloudflare WAF para inspeccionar y filtrar las solicitudes dirigidas a la API pública de DAZMA. Su plan Pro incluye las reglas administradas completas de Cloudflare, el conjunto de reglas OWASP, controles personalizados, limitación de solicitudes, protección contra bots y mitigación DDoS. Estas capacidades permiten bloquear ataques frecuentes, controlar el abuso de rutas sensibles y consultar los eventos que explican por qué una solicitud fue rechazada.

La aplicación web no se coloca detrás del WAF. Un proxy delante de la plataforma de despliegue del frontend duplicaría la caché y le quitaría visibilidad de seguridad, y esas plataformas ya incluyen CDN y mitigación de DDoS.

Cloudflare presenta la mejor relación entre cobertura, costo y facilidad de operación para la arquitectura de DAZMA. AWS WAF ofrece capacidades comparables, pero cobra por listas de control, reglas y solicitudes, además de requerir servicios como CloudFront para proteger un origen externo. Google Cloud Armor obtiene su mayor ventaja cuando la aplicación utiliza los balanceadores de Google Cloud. Azure WAF necesita Azure Front Door Premium para disponer de reglas administradas completas, con un costo base aproximado de USD 330 mensuales más consumo. Akamai App & API Protector ofrece funciones avanzadas de protección de aplicaciones, API y bots, pero requiere cotización y está orientado a operaciones empresariales con mayores necesidades de seguridad y administración.

Cloudflare Pro puede proteger dominios y servicios alojados en diferentes plataformas sin trasladar el backend a una nube determinada. Su precio publicado es de USD 20 mensuales con facturación anual o USD 25 con facturación mensual, lo que proporciona un costo más predecible que las alternativas cobradas por reglas, solicitudes, transferencia o componentes adicionales. El plan gratuito no incluye las reglas administradas completas ni el conjunto OWASP, mientras que Business cuesta USD 200 mensuales con facturación anual o USD 250 mensual y aporta una capacidad superior a la requerida por DAZMA.
