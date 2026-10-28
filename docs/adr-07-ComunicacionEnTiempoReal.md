# ADR-07: Estrategia de comunicación en tiempo real para negociaciones

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-REN-0010
- ESC-CAL-ESC-0003

## Contexto

Las negociaciones requieren comunicación directa entre las partes. DAZMA necesita definir cómo se entregarán los mensajes, cómo se conservará el historial y cómo se mantendrá la privacidad de cada conversación.

## Factores de decisión

- La comunicación debe permitir conversaciones privadas entre las personas relacionadas con una publicación o negociación
- Los mensajes deben llegar en un tiempo menor o igual a 2s, conservar el historial necesario y mostrar al usuario si el mensaje fue enviado, recibido o si ocurrió un problema
- La solución debe cuidar quién puede entrar a cada conversación y evitar que las reglas del negocio dependan del chat

## Opciones

- **A.** Polling periódico: muy simple, pero genera tráfico constante y la latencia depende del intervalo de consulta
- **B.** Server-Sent Events + HTTP para envío: adecuado para push del servidor, pero la bidireccionalidad queda dividida entre mecanismos
- **C.** WebSocket: canal persistente bidireccional con baja latencia; requiere manejar reconexión, conexiones persistentes y escalamiento multi-instancia
- **D.** Servicio realtime externo: reduce operación propia, pero introduce costo, dependencia y posible lock-in

## Decisión

Se adopta un servicio realtime externo porque evita operar un WebSocket propio a escala: manejar reconexiones, mecanismos de pub/sub para que el mensaje le llegue al usuario correcto sin importar la instancia a la que esté conectado y sostener miles de conexiones simultáneas. También reduce el desarrollo que requiere el chat.

## Consecuencias

**Ventajas:** el proveedor opera las conexiones persistentes, la reconexión y la distribución de mensajes entre instancias, y entrega comunicación bidireccional con baja latencia sin polling frecuente. DAZMA evita construir y mantener esa infraestructura.

**Desventajas:** el servicio tiene un costo que crece con el uso y una dependencia comercial y técnica del proveedor. El historial queda limitado por la retención que ofrezca, y el backend debe autorizar a cada usuario antes de dejarlo entrar a una conversación. La red del usuario aporta latencia fuera del control directo de DAZMA, por lo que la métrica debe medirse con condiciones y percentiles definidos.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige integrar un servicio externo de comunicación en tiempo real a través de su SDK o API. El proveedor mantiene las conexiones, entrega los mensajes, recupera la comunicación después de una desconexión y guarda el historial contratado, mientras nuestro backend sigue decidiendo qué usuarios pertenecen a cada conversación, a qué publicación o negociación corresponde y qué reglas de negocio aplican. Ningún pago, aceptación o cancelación va a depender solo de un mensaje del chat.

El polling periódico no se selecciona porque cada cliente consultaría repetidamente aunque no existieran mensajes. Para aproximarse al objetivo de entrega de dos segundos tendría que consultar con un intervalo corto, lo que aumentaría solicitudes, consumo del backend y base de datos. Si se amplía el intervalo para reducir ese tráfico, también aumenta el tiempo que tarda en aparecer un mensaje.

Con Server-Sent Events el servidor puede enviar novedades al navegador, pero el usuario tendría que mandar sus mensajes por HTTP y recibirlos por otro canal. Se puede construir el chat así, pero hay que coordinar dos mecanismos con sus errores y reconexiones. Un WebSocket propio sí es bidireccional, pero DAZMA tendría que operar conexiones persistentes, autenticar cada suscripción, reconectar clientes, repartir eventos entre varias instancias, guardar historial y vigilar la capacidad; son tareas permanentes que no son la función principal del marketplace. Aquí gana el servicio externo porque ahorra ese trabajo de construir y mantener un servicio de chat estandarizado.

El servicio externo quita esa carga operativa, pero trae dependencia comercial y técnica. El backend tiene que autorizar a cada usuario antes de dejarlo entrar a una conversación, y la integración debe manejar estados de envío y error que la interfaz pueda mostrar claramente. Los dos segundos se miden desde que el cliente envía hasta que el otro participante recibe; ese tiempo no depende solo del proveedor, la red del usuario también influye.
