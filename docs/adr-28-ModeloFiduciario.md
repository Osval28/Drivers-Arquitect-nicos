# ADR-28: Modelo fiduciario para administrar y liberar pagos

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-AUD-0008
- ESC-CAL-INT-0002
- ESC-CAL-SEG-0008

## Contexto

En las negociaciones de DAZMA, el comprador necesita saber que el dinero no será entregado antes de que se cumplan las condiciones acordadas, y el vendedor necesita comprobar que los recursos fueron recibidos y podrán pagarse cuando la operación termine correctamente. Los recursos deben mantenerse separados del dinero utilizado para operar DAZMA y deben existir reglas claras para liberarlos, devolverlos o conservarlos mientras se resuelve un desacuerdo.

## Factores de decisión

- Los recursos de cada negociación deben permanecer separados de las cuentas operativas de DAZMA
- Cada recaudo, liberación y devolución debe poder relacionarse con la negociación y las personas correspondientes
- La entidad debe estar autorizada para administrar recursos de terceros en Colombia
- El contrato debe definir condiciones de liberación, plazos de respuesta, cancelaciones, devoluciones y manejo de desacuerdos
- La solución debe integrarse con el backend y la infraestructura de pagos, mantener trazabilidad y evitar instrucciones duplicadas
- El costo debe incluir estructuración, administración fiduciaria, movimientos, devoluciones, soporte y requisitos mínimos de operación

## Opciones

- **A.** Skandia Fidu as a Service: solución fiduciaria para empresas y plataformas digitales, con integración documentada con Cobre para recaudos, conciliación y dispersiones
- **B.** Fiduciaria Bancolombia: ofrece negocios de administración y pagos, con condiciones e integración definidas mediante contratación empresarial
- **C.** Alianza Fiduciaria: ofrece administración de recursos y pagos conforme a instrucciones contractuales, con integración y costos sujetos a estructuración
- **D.** Administración directa por DAZMA: daría mayor control operativo, pero mezclaría funciones financieras con la operación de la plataforma y no proporcionaría un patrimonio administrado por una fiduciaria

## Decisión

Se adopta un modelo de fiducia de administración y pagos mediante Skandia Fidu as a Service. Los recursos pagados por los compradores permanecerán separados de las cuentas operativas de DAZMA y cada movimiento se relacionará con una negociación.

DAZMA registrará el pago recibido y calculará una sola vez la comisión y el valor neto correspondiente al vendedor. Este cálculo no moverá el dinero; quedará registrado como la distribución esperada de la venta. Cuando se cumplan las condiciones definidas en el contrato, se generarán dos instrucciones relacionadas con la misma negociación: una para pagar al vendedor y otra para pagar la comisión a DAZMA. Skandia administrará los recursos y Cobre ejecutará los movimientos autorizados.

Cada instrucción tendrá una referencia única y un estado independiente. Si un movimiento se completa y el otro falla, solo se reintentará el pendiente. Si la operación se cancela antes de la liberación, se devolverá el valor correspondiente al comprador y no se pagará la comisión. El contrato establecerá las reglas para confirmación, vencimiento de plazos, cancelaciones, devoluciones y desacuerdos. Las comisiones de varias ventas podrán agruparse en un desembolso periódico cuando el contrato, la trazabilidad y los costos lo hagan conveniente.

## Consecuencias

**Ventajas:** separa los recursos de las negociaciones del dinero propio de DAZMA, mejora la confianza entre las partes y deja la administración en una entidad fiduciaria autorizada. La integración con Cobre permite recaudos y dispersiones fiduciarias con conciliación y trazabilidad

**Desventajas:** requiere contratación, vinculación, definición de reglas y una integración que puede tomar varios meses. Skandia no publica una tarifa única; los costos de estructuración, administración y transacciones se acuerdan según el negocio. DAZMA deberá mantener estados claros, evidencia de las confirmaciones y un procedimiento para cancelaciones, devoluciones y desacuerdos. Si los recursos generan rendimientos, el contrato deberá definir su beneficiario, costos y riesgos. Skandia no publica documentación técnica de su API. Cobre no reparte un pago entre varios destinatarios, por eso se generan dos instrucciones independientes.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Skandia Fidu as a Service, integrada con la infraestructura de Cobre, para administrar los recursos de las negociaciones. Skandia será la entidad encargada de mantener los recursos bajo el contrato fiduciario y ejecutar recaudos, liberaciones o devoluciones conforme a las reglas acordadas. DAZMA conservará el estado de cada negociación, las confirmaciones y la evidencia que sustenta cada instrucción.

La integración que Cobre publica con Skandia incluye recaudos y dispersiones fiduciarias mediante API, operación 24/7 a través de Bre-B, transferencias referenciadas y conciliación automática. Estas capacidades corresponden a los factores de separación de recursos, relación de cada movimiento con una negociación, trazabilidad e integración con el backend. Las instrucciones que puedan repetirse deberán llevar identificadores únicos para no producir dos movimientos.

El contrato debe establecer quién puede ordenar una liberación, qué pruebas son válidas, cuánto tarda cada operación, cómo funcionan cancelaciones y devoluciones, y qué ocurre cuando las partes no están de acuerdo. La fiduciaria ejecutará las condiciones contractuales y DAZMA administrará las reglas y evidencias de la negociación; la existencia de una API no sustituye esas definiciones legales y operativas.

Skandia y Cobre no publican un plan gratuito ni una tarifa estándar para esta solución empresarial.
