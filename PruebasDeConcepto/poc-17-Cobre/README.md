# Prueba de concepto · ADR-17 Infraestructura de pagos (Cobre, adaptador simulado)

**Objetivo.** Validar las obligaciones que el ADR-17 pone sobre el backend de DAZMA al integrarse con Cobre:
relacionar cada recaudo con su negociación, confirmar cada movimiento, consultar el estado cuando la respuesta es
incierta, no repetir instrucciones, atender devoluciones y conciliar.

**Escenarios relacionados:** ESC-CAL-CON-0001, ESC-CAL-CON-0005, ESC-CAL-INT-0002 y ESC-CAL-SEG-0008.

**Tipo:** local, sin credenciales. Cobre y Skandia no publican documentación técnica de la integración y todavía no
hay contrato, así que, como dice el ADR, la validación se hace con un adaptador simulado. La prueba levanta en
`127.0.0.1` un proveedor simulado por HTTP y el endpoint de webhooks de DAZMA.

## Qué es real y qué es simulado

| Parte | En la prueba |
|---|---|
| Contrato interno de pagos (`PaymentGateway`, ADR-09) | Real: es el contrato que usaría el módulo de pagos |
| Servicio de pagos (estados, idempotencia por referencia, Inbox, registro de solo adición, conciliación) | Real |
| Adaptador hacia el proveedor (traducción de API, estados y webhooks; verificación de firma) | Real, apuntando al simulador |
| Proveedor | Simulado. No copia la API de Cobre: reproduce lo que el ADR obliga a manejar (confirmación asíncrona firmada, reenvíos, desorden, respuestas tardías, caídas). No deduplica instrucciones, para que la protección dependa de DAZMA |

Cuando exista el sandbox de Cobre, lo que cambia es el adaptador; el servicio y los casos se mantienen.

## Casos de prueba

| Caso | Qué se prueba | Factor o escenario | Criterio de éxito |
|---|---|---|---|
| C1 | Recaudo por Bre-B, PSE, Botón Bancolombia y Botón Nequi | Recaudos desde bancos y billeteras; relación con la negociación | 4/4 confirmados y ligados a su negociación |
| C2 | Webhook con firma alterada | ESC-CAL-SEG-0008 | 401, sin cambio de estado ni de registro |
| C3 | Mismo webhook N veces | ESC-CAL-CON-0005 | 1 registro y 1 transición; el Inbox identifica los N-1 duplicados |
| C4 | Webhook viejo después de la confirmación | Estado verificable | Sigue CONFIRMADO |
| C5 | Respuesta que no llega a tiempo | Evitar instrucciones repetidas | Consulta por referencia y 1 sola instrucción en el proveedor |
| C6 | Desembolso exitoso y rechazado | Desembolsos con confirmación del resultado | CONFIRMADO y FALLIDO con el motivo |
| C7 | Devolución | Devoluciones con confirmación | Devolución mayor al recaudo rechazada antes del proveedor; devolución válida confirmada |
| C8 | Conciliación | Facilitar la conciliación (ADR-30) | 0 diferencias si todo cuadra; diferencia de valor y movimiento desconocido reportados sin corregirse solos |
| C9 | Proveedor caído | ESC-CAL-CON-0011, ESC-CAL-CON-0001 | Error controlado, estado NO_ENVIADO, y al recuperarse 1 sola instrucción |
| C10 | Otro adaptador con otro vocabulario | ESC-CAL-INT-0002 | Mismos resultados de negocio sin tocar el servicio |

## Verificación de la propia prueba

Para comprobar que los casos detectan fallas y no pasan por diseño, se ejecutó la prueba quitando una protección
a la vez. Cada versión alterada hizo fallar el caso esperado:

| Protección quitada | Caso que falla |
|---|---|
| Inbox de notificaciones | C3 |
| Máquina de estados (estado final no retrocede) | C4 |
| Consulta de estado ante respuesta incierta | C5 |
| Verificación de firma del webhook | C2 |

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `CobrePaymentsPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → CobrePaymentsPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java CobrePaymentsPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

No necesita credenciales. Todas son opcionales:

| Variable | Descripción |
|---|---|
| `PAYMENTS_CLIENT_TIMEOUT_MS` | Tiempo máximo que el adaptador espera al proveedor. Por defecto `800`; el simulador tarda 2.500 ms en C5 |
| `PAYMENTS_DUPLICATE_DELIVERIES` | Veces que se reenvía la misma notificación en C3. Por defecto `5` |
| `POC_RESULTS_DIR` | Carpeta de resultados. Por defecto `resultados` |

El secreto de firma de los webhooks se genera aleatoriamente en cada ejecución.

## Resultados

Ejecución local del 2026-10-28 con Java 21.0.12.1 (archivo [`resultados/resultado-20261028-115529.md`](resultados/resultado-20261028-115529.md)): **10 pasaron, 0 fallaron, 0 omitidos**.

| Caso | Resultado | Evidencia |
|---|---|---|
| C1 Recaudo por cada canal habilitado | **PASÓ** | 4/4 canales. Bre-B → neg-2aea0d10-recaudo CONFIRMADO; PSE → neg-769be4b3-recaudo CONFIRMADO; Botón Bancolombia → neg-0bd1c7bb-recaudo CONFIRMADO; Botón Nequi → neg-0a3410a8-recaudo CONFIRMADO |
| C2 Confirmación con firma inválida | **PASÓ** | HTTP 401; el pago sigue PENDIENTE y el registro financiero no cambió |
| C3 Notificación repetida | **PASÓ** | 5 entregas del mismo evento → 1 registro, 1 transición; el Inbox identificó 4 duplicados por su event_id |
| C4 Notificaciones fuera de orden | **PASÓ** | Confirmado y luego notificación vieja de PENDIENTE: sigue CONFIRMADO |
| C5 Respuesta incierta del proveedor | **PASÓ** | Tiempo de espera agotado → consulta por referencia → movimiento existente adoptado. Instrucciones en el proveedor: 1. Consultas de estado: 1 |
| C6 Desembolso con resultado verificable | **PASÓ** | Exitoso → CONFIRMADO; rechazado → FALLIDO (cuenta_destino_inexistente) |
| C7 Devolución al comprador | **PASÓ** | Devolución de 250.000 sobre 200.000 rechazada localmente; devolución de 200.000 CONFIRMADA |
| C8 Conciliación | **PASÓ** | Primera conciliación: 0 diferencias en 14 movimientos. Con diferencias: movimiento desconocido sin-referencia-dazma por 55000; valor distinto en neg-58a8cc20-recaudo: DAZMA 75000, proveedor 70000 |
| C9 Proveedor no disponible | **PASÓ** | Con el proveedor caído: error controlado (ProviderUnavailable) y estado NO_ENVIADO. Recuperado: 1 instrucción aunque se pidió 2 veces |
| C10 Cambio de proveedor sin tocar las reglas | **PASÓ** | Mismos resultados con ambos adaptadores: [recaudo=CONFIRMADO, vendedor=CONFIRMADO, comision=FALLIDO] |
