# ADR-08: Selección del proveedor externo de chat

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-REN-0010
- ESC-CAL-ESC-0003
- ESC-CAL-SEG-0004
- ESC-CAL-INT-0002

## Contexto

El chat será una capacidad integrada mediante un proveedor externo. DAZMA necesita seleccionar una opción que permita conversaciones privadas, buen manejo de participantes, historial y costos adecuados para el crecimiento esperado.

## Factores de decisión

- El proveedor de chat debe permitir crear conversaciones privadas ligadas a publicaciones o negociaciones
- El proveedor de chat debe manejar nativamente tantas de estas como sea posible: Participantes, historial, reconexión y estados de mensajes para no tener que construir el 100% de la lógica desde 0
- Su precio debe evaluarse con base en conexiones simultáneas, cantidad de mensajes, tiempo de conservación del historial y funciones incluidas, porque esos son los elementos que realmente pueden aumentar el costo

## Opciones

- **A.** Ably Chat: SDK y UI Kit React, transporte e historial administrados y cobro por consumo (desde USD 29 al mes más consumo).
- **B.** Stream Chat: Es un producto de chat más integrado, con gestión de usuarios/canales y herramientas de moderación, tiene un plan gratuito pero el paso a un plan comercial es bastante grande (desde USD 399 al mes con facturación anual)
- **C.** Sendbird Chat: SDK/UI Kit y funciones de administración; un plan base comercial tiene un costo elevado para un equipo pequeño y condiciones según plan (desde USD 349 al mes con pago anual)

## Decisión

Se adopta Ably Chat porque dispone de componentes React, tiene un precio comercial más bajo que las alternativas (USD 29 al mes más consumo, frente a USD 349 o más), publica baja latencia y garantía de entrega de mensajes, e integra deduplicación automática.

## Consecuencias

**Ventajas:** Delega el transporte y reutiliza interfaz de chat sin operar WebSocket propio.

**Desventajas:** Hay que construir la membresía, y los reportes, el archivo y los avisos requieren trabajo propio. El costo depende también de conexiones y canales activos; un mensaje de usuario no equivale siempre a una unidad facturada. Stream puede convenir más si sus funciones integradas ahorran ese desarrollo. Deben comprobarse retención y límites efectivos de la cuenta.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Ably Chat. Cada conversación es una sala vinculada a una publicación o negociación y el backend solo autoriza a sus participantes. Ably pone el transporte en tiempo real, la reconexión, la presencia, los indicadores de escritura y la consulta del historial según la retención contratada. DAZMA guarda la relación entre negociación, sala y miembros, porque esas son reglas del negocio y no deben depender de que el usuario conozca el identificador de una sala.

La decisión se sustenta en el costo completo y no únicamente en la existencia de una cuenta gratuita. Ably Free incluye 200 conexiones simultáneas, 200 canales simultáneos, 500 mensajes por segundo, seis millones de unidades de mensaje al mes y un día máximo de historial. El proveedor lo presenta para construir y experimentar, con soporte de mejor esfuerzo y con un historial limitado a un día.

El plan Standard cuesta 29 USD al mes más consumo. Esa cuota no incluye mensajes: sube los topes a 10.000 conexiones simultáneas, 10.000 canales simultáneos y 2.500 mensajes por segundo, permite hasta 30 días de historial y agrega soporte por correo. Los mensajes se cobran aparte, a 2,50 USD por millón, y los minutos de conexión y de canal a 1 USD por millón cada uno.

Una unidad de mensaje no siempre es un mensaje escrito por una persona: enviar genera una unidad de entrada y cada suscriptor que lo recibe genera una unidad de salida. Consultar el historial cuenta una unidad por cada mensaje recuperado, y la presencia, los indicadores de escritura y las reacciones también generan unidades. Por ejemplo, un mensaje publicado en una sala con dos clientes suscritos, que ambos reciben, puede costar una entrada y dos salidas. Por eso la estimación se hace con la cantidad de participantes, el tiempo conectado, las consultas de historial y las funciones activas, no solo con los mensajes que escriben los usuarios.

Hay que tener cuidado con la retención. En Standard, la retención de los mensajes guardados va de un mínimo de 72 horas a un máximo de 30 días (en Free es de 24 horas); guardar más del mínimo puede costar más y hay que acordarlo. La documentación también dice que Ably no ofrece una API para borrar mensajes guardados: permanecen durante todo el periodo configurado y, para borrar alguno, hay que solicitarlo a Ably. Entonces Ably solo sirve si lo que DAZMA necesita de historial cabe en esas condiciones o si se contrata formalmente la retención necesaria; Ably no guarda las conversaciones indefinidamente.

Stream Chat trae más funciones integradas de administración y moderación. Su plan Build incluye 1.000 usuarios activos mensuales y 100 conexiones simultáneas gratis. El plan Start cuesta USD 399 al mes con facturación anual o USD 499 mensual para 10.000 usuarios activos y 500 conexiones, con sobrecostos de USD 0,09 por usuario adicional y USD 0,99 por conexión simultánea adicional. Sendbird tiene Starter 5K a USD 349 al mes con pago anual o USD 399 mensual, y Pro 5K a USD 499 anualizado o USD 599 mensual, con precio según usuarios activos y conexiones máximas. Son productos de chat más completos, pero su costo fijo en producción es mucho más alto.
