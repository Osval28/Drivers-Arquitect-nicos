# ADR-30: Conciliación financiera y control de movimientos

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-AUD-0008

## Contexto

En una negociación económica intervienen registros internos, una plataforma de pagos, una entidad fiduciaria y movimientos bancarios. Una operación puede aparecer como completada en una fuente y estar ausente, repetida o registrada con un valor diferente en otra. DAZMA necesita comprobar que todos los movimientos esperados realmente ocurrieron y conservar evidencia cuando exista una diferencia.

## Factores de decisión

- Cada movimiento debe relacionarse con una negociación y una referencia única.
- La solución debe detectar movimientos faltantes, duplicados o con valores diferentes.
- Debe conservar evidencia suficiente para reclamaciones y revisiones.
- Las comisiones de DAZMA deben permanecer separadas de los recursos administrados para compradores y vendedores.
- El proceso debe poder operar con grandes volúmenes sin depender de una revisión manual movimiento por movimiento.

## Opciones

- **A.** Confiar únicamente en los estados informados por los proveedores: tiene menor desarrollo, pero no permite comprobar de forma independiente que el conjunto de movimientos esté completo.
- **B.** Conciliación manual mediante reportes: permite revisar diferencias, pero es lenta y propensa a errores cuando aumenta el volumen.
- **C.** Registro financiero interno y conciliación automática: DAZMA conserva los movimientos esperados, los compara con Cobre, Skandia y los reportes bancarios, y deja las diferencias pendientes de revisión.

## Decisión

Se adopta un registro financiero interno de solo adición. Cada recaudo, devolución, pago al vendedor y comisión tendrá una referencia única, valor esperado, valor confirmado, estado y relación con la negociación. Un proceso periódico comparará los registros de DAZMA con la información recibida de Cobre, Skandia y la cuenta bancaria. Las diferencias no se corregirán silenciosamente: quedarán visibles hasta su investigación y cierre. La cuenta operativa de DAZMA recibirá únicamente recursos propios, como comisiones ya liberadas, y desde ella podrán pagarse los proveedores de la plataforma.

## Consecuencias

**Ventajas:** permite detectar faltantes, duplicados y diferencias de valor; conserva evidencia propia; facilita reclamaciones y cierres financieros.

**Desventajas:** requiere almacenar movimientos, integrar reportes o consultas de los proveedores y crear un proceso de revisión de diferencias. La frecuencia de conciliación y los formatos disponibles deberán acordarse con Cobre, Skandia y la entidad bancaria.

## Spike: ¿Cuál es la mejor opción y por qué?

La opción elegida es que DAZMA mantenga un registro financiero interno de solo adición y lo compare periódicamente, de forma automática, con la información de Cobre, Skandia y la cuenta bancaria. Cada recaudo, reembolso, pago al vendedor y comisión quedará registrado con una referencia única, la negociación a la que pertenece, el valor esperado, el valor confirmado y su estado. Si un movimiento no coincide, se registra la diferencia y queda abierta hasta que alguien la investigue y la cierre. El registro original no se modifica para que la diferencia desaparezca.

Confiar solo en los estados que informan los proveedores no alcanza, porque un reporte solo muestra lo que ese proveedor registró. Si DAZMA esperaba un reembolso y el proveedor nunca lo ejecutó, ese movimiento no aparece en ningún reporte externo; solo se nota cuando se compara contra una lista propia de lo que debía ocurrir. Por eso la conciliación compara en las dos direcciones: busca lo que DAZMA esperaba y no aparece, y lo que el proveedor reporta y DAZMA no esperaba. La conciliación manual sirve para revisar diferencias puntuales, pero revisar movimiento por movimiento deja de ser viable cuando crece el volumen. Con la opción elegida, las personas solo revisan las diferencias.

La referencia única de cada movimiento será la misma que se envía como clave de idempotencia al proveedor. Así, un cobro duplicado aparece como dos movimientos externos con una sola referencia esperada, y se detecta sin reglas adicionales. Las comisiones se registran como un tipo de movimiento aparte y solo pasan a la cuenta operativa de DAZMA después de liberarse, de modo que el dinero de compradores y vendedores nunca se mezcla con los recursos propios. Los valores se guardarán como enteros en pesos o como NUMERIC, nunca como números de punto flotante, porque un error de redondeo también es una diferencia de conciliación.

La conciliación no tiene licencia. Su costo proviene del almacenamiento de los movimientos en PostgreSQL, del proceso periódico, que puede ejecutarse en el worker del ADR-21 sin crear otra instancia, y del desarrollo necesario para leer los reportes de cada proveedor. Todavía no se puede dar un valor mensual porque no conocemos los formatos, los medios de consulta ni la frecuencia con que Cobre, Skandia y el banco entregan su información. Esa frecuencia define también cuánto puede tardar DAZMA en descubrir una diferencia: si un proveedor solo entrega un reporte diario, un faltante puede pasar hasta un día sin detectarse.
