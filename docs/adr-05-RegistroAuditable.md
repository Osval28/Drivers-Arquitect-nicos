# ADR-05: Registro auditable de eventos críticos del negocio

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-AUD-0008
- Relacionado: ESC-CAL-CON-0001

## Contexto

En una negociación pueden existir desacuerdos o reclamaciones. DAZMA necesita conservar un registro entendible de los eventos importantes para saber qué ocurrió, quién participó y en qué momento, sin depender solo de mensajes técnicos del sistema.

## Factores de decisión

- El sistema debe guardar evidencia clara de las acciones importantes del negocio: quién hizo algo, cuándo lo hizo, sobre qué operación y con qué resultado
- Esa información debe permitir reconstruir una negociación o resolver una reclamación sin depender únicamente de mensajes técnicos difíciles de interpretar

## Opciones

- **A.** Utilizar únicamente logs técnicos: no requiere otro mecanismo, pero no garantiza que todos los eventos de negocio necesarios existan o conserven una estructura estable
- **B.** Audit trail de negocio append-only: almacena eventos críticos estructurados con actor, fecha/hora, acción, resultado y correlation ID
- **C.** Event Sourcing completo: ofrece historial por diseño, pero convierte los eventos en la fuente de verdad de todo el dominio y aumenta significativamente la complejidad

## Decisión

Se adopta un audit trail de negocio append-only separado de los logs técnicos. Cada evento crítico registrará identificador de actor, fecha/hora, acción, resultado, operación afectada y correlation ID. El acceso estará restringido y se evitará registrar contraseñas, tokens u otros secretos. Event Sourcing sólo se reconsiderará si aparecen drivers adicionales que justifiquen convertir el historial en la fuente de verdad del dominio.

## Consecuencias

**Ventajas:** facilita reclamaciones, soporte y reconstrucción de incidentes con una semántica estable

**Desventajas:** aumenta el volumen de información almacenada y obliga a definir políticas de retención, acceso y privacidad. Un diseño deficiente puede registrar datos sensibles de más. La sincronización de tiempo y el catálogo de eventos críticos deben ser consistentes para que la reconstrucción tenga valor real

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige tener un registro de auditoría del negocio separado de los logs técnicos. Por cada evento crítico se guarda quién lo hizo (identificador del actor), cuándo, qué acción fue, sobre qué operación, con qué resultado y un identificador de correlación que permite unir los pasos de una misma negociación. El registro es de solo adición: si hay que corregir algo se agrega una entrada nueva y la anterior no se reemplaza.

Los logs técnicos sirven para diagnosticar excepciones, tiempos de respuesta y fallos de infraestructura, pero su formato puede cambiar, están pensados para desarrolladores y se rotan según lo que necesite la operación, así que no garantizan que una reclamación se pueda reconstruir con conceptos del negocio. Con un registro propio siempre están los datos que responden quién hizo qué, cuándo, sobre cuál operación y con qué resultado.

No se adopta Event Sourcing completo porque, aunque permitiría reconstruir el estado a partir de los eventos, obligaría a diseñar versiones, proyecciones y consultas alrededor de ellos. Lo que se necesita es que haya evidencia estable para reconstruir operaciones y resolver reclamaciones, para eso es suficiente usar un audit trail donde solo se pueda agregar.

El registro no va a tener contraseñas, tokens, secretos ni datos completos de medios de pago, y solo podrán consultarlo las personas y procesos que necesiten investigar una operación.

No hay que pagar una licencia aparte. El costo está en el almacenamiento, los respaldos y las consultas (depende de cuántos eventos se guarden y por cuánto tiempo), y en el desarrollo para registrar los eventos correctos en cada operación y probar que no se puedan modificar desde los flujos normales. Es más costoso que tener solo logs técnicos, pero da una evidencia estructurada que esos logs no garantizan; Event Sourcing costaría bastante más en diseño, mantenimiento y operación porque afectaría todo el modelo del dominio.
