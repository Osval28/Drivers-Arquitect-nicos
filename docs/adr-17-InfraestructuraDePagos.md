# ADR-17: Infraestructura de pagos para recaudo y desembolso

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-INT-0002
- ESC-CAL-SEG-0008

## Contexto

DAZMA necesita recibir pagos de compradores, identificar a qué negociación pertenece cada recaudo y realizar desembolsos o devoluciones dentro del sistema financiero colombiano. La infraestructura de pagos debe conectarse directamente con el esquema fiduciario, confirmar cada movimiento y evitar que los recursos pasen por las cuentas operativas de DAZMA.

## Factores de decisión

- La solución debe permitir recaudos desde bancos y billeteras de uso común en Colombia
- Cada pago debe quedar relacionado con una negociación y contar con un estado verificable
- Debe permitir desembolsos y devoluciones a bancos o billeteras, con confirmación del resultado
- La integración debe operar durante todo el año, facilitar la conciliación y evitar instrucciones repetidas
- El costo debe considerar recaudos, desembolsos, devoluciones, integración, soporte y condiciones comerciales

## Opciones

- **A.** Cobre: infraestructura empresarial conectada con Skandia para recaudos y dispersiones fiduciarias. Permite operar mediante Bre-B, PSE, Botón Bancolombia y Botón Nequi, con conciliación y trazabilidad
- **B.** Wompi: admite tarjetas, PSE, Nequi y transferencias Bancolombia, pero no presenta una integración fiduciaria documentada
- **C.** ePayco: ofrece pagos y distribución entre receptores, pero su flujo documentado está orientado a dividir pagos y no acredita la integración con el patrimonio fiduciario seleccionado

## Decisión

Se adopta Cobre como infraestructura de pagos de DAZMA. Los compradores podrán pagar mediante los canales bancarios y billeteras habilitados, como Bre-B, PSE, Botón Bancolombia y Botón Nequi. Cobre relacionará cada recaudo con la negociación correspondiente y conectará los movimientos con los recursos administrados por Skandia.

El backend verificará las confirmaciones recibidas, consultará el estado cuando exista incertidumbre y utilizará identificadores únicos para evitar recaudos, desembolsos o devoluciones duplicadas. Los recursos no deberán pasar por las cuentas operativas de DAZMA.

## Consecuencias

**Ventajas:** Cobre publica una integración con Skandia para recaudos y dispersiones fiduciarias. La solución permite operar las 24 horas, recibir pagos desde bancos y billeteras, conciliar movimientos y desembolsar a beneficiarios dentro del sistema financiero colombiano

**Desventajas:** las tarifas se definen mediante cotización empresarial y dependen de los servicios y el volumen contratado. El flujo publicado no confirma pagos con tarjeta, por lo que DAZMA aceptará los canales bancarios y billeteras habilitados. La aplicación deberá conservar estados pendientes, confirmar cada movimiento y atender fallas, devoluciones o diferencias de conciliación. Cobre no reparte un pago entre varios destinatarios, por lo que el pago al vendedor y la comisión se ejecutan como instrucciones separadas (ADR-28). Skandia no publica documentación técnica de su API.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Cobre como infraestructura de recaudos, consultas y desembolsos vinculados al modelo fiduciario de DAZMA. Cobre publica una integración directa mediante API con Skandia Fidu as a Service, aunque Skandia no publica documentación técnica de esa API: se conecta con Bre-B, permite operación continua, utiliza transferencias referenciadas para conciliación y centraliza dispersiones fiduciarias. También publica canales locales como PSE, Botón Bancolombia y Nequi.

DAZMA generará un identificador único para cada instrucción y conservará el estado informado por Cobre. El backend validará las notificaciones, consultará el estado cuando la respuesta sea incierta y no repetirá un desembolso o devolución sin comprobar el movimiento anterior. Los recursos y movimientos deberán relacionarse con la negociación y con la instrucción autorizada en el sistema.

Wompi y ePayco ofrecen métodos de recaudo útiles, pero actualmente no hay documentación que diga que pueden funcionar con el modelo fiduciario. ePayco Split puede dividir pagos, pero el problema es que eso no serviría porque el dinero primero necesita ser retenido antes de sacar la comisión de la plataforma. Se eligió Cobre porque es la única de las opciones evaluadas con una integración fiduciaria publicada, requisito del flujo definido en ADR-28.

Cobre y Skandia no publican una tarifa estándar para este servicio empresarial. El costo se definirá mediante una cotización escrita que separe costo de integración o estructuración, recaudo por canal, desembolso, devolución, conciliación, soporte, mínimos de facturación e impuestos. El contrato deberá fijar como compromisos la disponibilidad, tiempos de respuesta y soporte. El caso publicado por Cobre indica operación 24/7, cobertura del sistema financiero colombiano y una API con más de 99,9 % de disponibilidad, pero son afirmaciones comerciales del proveedor y no sustituyen el SLA contractual de DAZMA.
