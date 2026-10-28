# ADR-15: Selección de un notification gateway

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- Sin escenario de calidad final asociado
- Relacionado: RF-30
- Relacionado: RF-31

## Contexto

El sistema deberá avisar a los usuarios sobre eventos importantes, como cambios en negociaciones, estados de operaciones o novedades relevantes. Se necesita definir qué servicio y canales se usarán para enviar esas notificaciones de forma confiable.

## Factores de decisión

- La solución debe cubrir los canales de notificación requeridos, al menos correo y, si aplica, SMS
- Debe permitir saber si una notificación fue enviada, entregada o rechazada, para asegurar que efectivamente los mensajes están llegando a su destinatario
- El costo debe evaluarse por cantidad de mensajes, límites del plan, destinatarios y comportamiento cuando se supere la cuota

## Opciones

- **A.** Twilio: Plataforma de comunicaciones más grande del mercado (SMS, voz, WhatsApp en más de 190 países); correo vía su filial SendGrid
- **B.** SendGrid: El servicio de correo transaccional más usado históricamente. Tiene una documentación extensa pero no tiene un plan gratuito, solo una prueba de 60 días.
- **C.** Amazon SES/SNS: Son servicios de AWS, SES para correo, SNS para push/SMS. El costo por envío más bajo del mercado, pero toda cuenta nueva arranca en modo sandbox y necesita aprobación manual de AWS antes de poder enviar a destinatarios reales.
- **D.** Resend: Tiene 3,000 correos gratis al mes (con un máximo de 100 al día), es fácil de integrar y funciona con usuarios reales desde el primer momento, pero es un proveedor relativamente nuevo y con un ecosistema más pequeño.

## Decisión

Se adopta Resend porque cuenta con un plan gratuito de hasta 3000 correos/mes con un máximo de 100 al día, lo cual cubre las fases iniciales, ya que es muy posible que en fases iniciales del proyecto el número de usuarios sea mucho menor, permite mandar correos a usuarios reales sin pasar por una verificación que puede tomar algunos días y maneja webhooks de eventos de entrega para analizar el tráfico de los correos a los usuarios.

## Consecuencias

**Ventajas:** Nivel gratuito de 3000 correos/mes (máximo 100 al día), maneja webhooks de eventos de entrega incluso en el plan gratis, no incluye complejidad innecesaria para una fase inicial del proyecto y en caso de escalar tampoco tiene un problema de lock in

**Desventajas:** Tiene un ecosistema más pequeño, el tope diario del plan gratuito puede retrasar notificaciones y puede cobrar el excedente cuando se activa esa opción, lo cual es un riesgo de facturación

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Resend para el correo transaccional de DAZMA porque tiene API y SMTP, autenticación de dominio y webhooks para todos los tipos de evento, así que se pueden registrar envíos, entregas, rebotes y fallos sin montar una infraestructura propia de correo. Con esto se cubre el canal requerido y se sabe qué pasó con cada notificación.

El plan Free de Resend cuesta USD 0 e incluye 3.000 correos al mes con un límite de 100 al día, tres dominios, un endpoint de webhook y 30 días de retención de datos. Ese límite diario puede cortar notificaciones aunque quede cuota mensual, así que no sirve como referencia para una operación con envíos continuos. El plan Pro cuesta USD 20 al mes por 50.000 correos, sin límite diario, diez dominios, cinco endpoints de webhook y 30 días de retención. Los correos adicionales cuestan USD 0,90 por cada mil si se activa el cobro por excedentes. Scale cuesta USD 90 al mes por 100.000 correos con el mismo excedente de USD 0,90 por mil, y Enterprise se cotiza y es el plan que tiene un SLA de disponibilidad de 99,99 %.

Amazon SES es más barato por correo (USD 0,10 por mil correos salientes en su modalidad a la carta, más USD 0,12 por GB de adjuntos), pero para tener los estados de entrega y el mismo flujo normalmente hay que combinarlo con otros servicios de AWS y su configuración de cuenta. Resend cuesta más por mensaje, pero tiene la API, los webhooks, los dominios y el diagnóstico en un solo servicio. SendGrid es una alternativa madura y Twilio cubre SMS y otros canales, pero hoy no hay un canal SMS obligatorio, así que una plataforma multicanal no aporta nada al correo transaccional.

La integración guarda el identificador de cada envío y procesa los eventos firmados del proveedor para actualizar su resultado, sin confundir "enviado" con "entregado". El dominio de envío se configura con las comprobaciones que pide el proveedor, y el uso se vigila para no pasar la cuota sin saber cuánto se va a cobrar; el excedente automático solo se activa con un límite de presupuesto acordado.
