# ADR-04: Alertas en momentos de alto consumo para mitigar a tiempo los fallos del sistema antes de una caída general

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0010
- ESC-CAL-CON-0011

## Contexto

DAZMA debe poder detectar señales de saturación antes de que una falla afecte a los usuarios. Si aumentan los errores, la lentitud o el consumo de recursos, el equipo necesita recibir alertas claras para actuar a tiempo.

## Factores de decisión

- El costo de monitoreo y de capacidad adicional debe ser proporcional al volumen esperado
- Las alertas deben llegar a un canal externo al componente que presenta la saturación, con umbrales y responsables definidos
- La estrategia seleccionada debe facilitar la detección de ataques DoS

## Opciones

- **A.** Monitoreo proactivo + Circuit Breaker + autoescalado + rate limiting: se detectan anomalías antes de que el sistema falle, se corta el contacto con servicios caídos, se añade capacidad cuando la demanda legítima crece y se limita el ritmo de las solicitudes abusivas.
- **B.** Rate limiting + load shedding, sin autoescalado: el sistema sacrifica peticiones de menor prioridad para protegerse cuando se acerca al umbral.
- **C.** La opción A más load shedding como último resguardo: si el autoescalado y el rate limiting no alcanzan, se rechaza el trabajo de menor prioridad.
- **D.** Solo alertas a operaciones, sin mitigación automática: el sistema notifica al equipo humano y este decide y ejecuta la acción manualmente.

## Decisión

Se adopta monitoreo proactivo con alertas, Circuit Breaker, autoescalado con un máximo de instancias definido y rate limiting (opción A). El monitoreo detecta la saturación, el Circuit Breaker evita insistir sobre servicios caídos y los fallos en cascada, el autoescalado agrega capacidad ante demanda legítima y el rate limiting limita las solicitudes repetidas o abusivas.

## Consecuencias

**Ventajas:** reduce el consumo de recursos porque evita llamadas a servicios que ya están caídos, evita fallos en cascada (que colapse toda la aplicación porque falló solamente un servicio), agrega capacidad ante demanda legítima y limita el impacto de solicitudes abusivas.

**Desventajas:** hacer pruebas se vuelve más complejo, porque hay que provocar varias respuestas de error y probar cada una. Se necesita definir umbrales de tiempo y cantidad; si se definen mal, el Circuit Breaker puede abrirse demasiado pronto o demorar en reaccionar, y el rate limiting puede rechazar a usuarios legítimos. El autoescalado aumenta la factura mientras existan instancias adicionales.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige la opción A: monitoreo proactivo, Circuit Breaker, autoescalado y rate limiting.

Un circuit breaker evita insistir sobre una dependencia que está fallando, pero no impide que el propio backend agote CPU, memoria o conexiones por exceso de solicitudes, ni identifica por sí mismo un ataque de denegación de servicio. Por esa razón se combina con las otras tres técnicas.

Las métricas permiten detectar consumo sostenido de CPU y memoria, aumento de latencia, porcentaje de errores y acumulación de trabajo. El autoescalado agrega capacidad ante demanda legítima, con un mínimo y un máximo para que un error de software o tráfico hostil no se convierta en crecimiento ilimitado de la factura. El rate limiting controla cuántas solicitudes admite la plataforma por origen y ruta. Se aplica en el borde, con el WAF (ADR-14) y el API Gateway (ADR-34).

El load shedding (opciones B y C) obliga a clasificar qué solicitudes son de menor prioridad y a mantener esa clasificación. Con rate limiting y autoescalado se cubren los factores de decisión sin ese costo adicional, y puede reconsiderarse si las pruebas de carga muestran que no bastan.

Esta estrategia también ayuda a detectar ataques DoS, pero no los elimina. El equipo podrá observar incrementos anormales por ruta u origen, solicitudes bloqueadas, errores y consumo del backend. El rate limiting reduce el impacto de solicitudes repetidas; ante un ataque volumétrico que sature la red se necesita además la protección del WAF (ADR-14). Aumentar instancias sin filtrar tráfico hostil puede elevar el costo sin recuperar la disponibilidad.

Los porcentajes de CPU, los tiempos y las cantidades de solicitudes se derivan de pruebas de carga y de los tiempos máximos de los escenarios de calidad, porque un umbral sin esos datos puede generar alertas innecesarias o hacer que se reaccione tarde. Cada alerta indica el servicio, la condición, el responsable y la actuación correspondiente, y llega por un medio que sigue disponible aunque el proceso saturado deje de responder.

El costo de esta estrategia se compone del volumen de métricas y logs y su retención (ADR-29), el canal de alertas y las instancias adicionales activadas (ADR-24). Autoescalar sin un máximo puede crecer sin control; operar sin autoescalamiento reduce la factura de cómputo pero acepta más rechazos; y depender solo de una reacción humana prolonga el incidente.
