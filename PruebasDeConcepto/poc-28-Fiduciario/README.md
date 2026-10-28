# Prueba de concepto · ADR-28 Modelo fiduciario (Skandia + Cobre, adaptador simulado)

**Objetivo.** Validar las reglas que el ADR-28 deja en manos de DAZMA: los recursos de cada negociación quedan
separados de la cuenta operativa, la comisión se calcula una sola vez, la liberación genera dos instrucciones
independientes, solo se reintenta lo pendiente, una cancelación devuelve todo sin comisión, un desacuerdo bloquea
los movimientos y cada negociación deja una línea de tiempo que no se puede modificar.

**Escenarios relacionados:** ESC-CAL-CON-0001, ESC-CAL-CON-0005, ESC-CAL-AUD-0008, ESC-CAL-INT-0002 y
ESC-CAL-SEG-0008 (y ESC-CAL-SEG-0007: el dinero no se libera antes de la condición).

**Tipo:** local, sin credenciales. Skandia no publica documentación técnica de su API y no hay contrato, así que la
fiducia se representa con el adaptador simulado que el ADR establece para la línea base.

## Qué es real y qué es simulado

| Parte | En la prueba |
|---|---|
| Reglas de DAZMA: máquina de estados, distribución, instrucciones con referencia única, reintentos, registro de solo adición | Real |
| Contrato interno hacia la fiducia (`FiduciaryGateway`, ADR-09) | Real |
| Fiducia (Skandia) y ejecución de movimientos (Cobre) | Simulado con un libro de cuentas: encargo por negociación, cuenta operativa de DAZMA, cuentas de comprador y vendedor. No deduplica referencias y permite inyectar fallas y respuestas perdidas |

La condición de liberación usada es la del contexto del proyecto: ambas partes confirman la entrega. La condición
definitiva la fija el contrato con la fiduciaria.

## Casos de prueba

| Caso | Qué se prueba | Factor o escenario | Criterio de éxito |
|---|---|---|---|
| C1 | Recaudo en el encargo | Recursos separados de la cuenta operativa | Encargo con el valor; cuenta operativa en 0 |
| C2 | Distribución | Comisión calculada una vez | 10.000 valores sin pérdida por redondeo; repetir el registro no recalcula |
| C3 | Condición de liberación | ESC-CAL-SEG-0007 | Con una confirmación se rechaza; con dos se libera |
| C4 | Dos instrucciones | Pago al vendedor y comisión separados | Referencias `-vendedor` y `-comision` confirmadas; encargo en 0 |
| C5 | Falla parcial | ESC-CAL-CON-0001 | Solo se reintenta el pago pendiente; cada movimiento se ejecuta una vez |
| C6 | Liberación pedida 5 veces, con una respuesta perdida | Evitar instrucciones duplicadas | Exactamente 2 movimientos ejecutados |
| C7 | Cancelación antes de liberar | Devolución sin comisión | Comprador recupera todo; comisión 0 |
| C8 | Desacuerdo | Manejo de desacuerdos | Nada se mueve mientras está abierto; luego solo la salida decidida |
| C9 | Línea de tiempo | ESC-CAL-AUD-0008 | Eventos en orden, ligados a la negociación; el registro rechaza modificaciones |
| C10 | 300 negociaciones aleatorias con fallas | Todos | 0 violaciones de conservación del dinero, estados finales válidos, duplicados y liberaciones sin condición |

C10 usa una semilla que se imprime en el resultado. Con `POC_SEED` se repite exactamente la misma simulación.

## Verificación de la propia prueba

Se ejecutó la prueba quitando una regla a la vez para comprobar que los casos la detectan:

| Regla quitada | Casos que fallan |
|---|---|
| No volver a ejecutar instrucciones confirmadas | C5, C6, C10 |
| Exigir las dos confirmaciones de entrega | C3, C10 |
| Consultar antes de reintentar una respuesta incierta | C6, C10 |
| Bloquear la devolución durante un desacuerdo | C8 |
| Devolver sin cobrar comisión | C7, C8, C10 |

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `FiduciaryModelPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → FiduciaryModelPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java FiduciaryModelPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

No necesita credenciales. Todas son opcionales:

| Variable | Descripción |
|---|---|
| `DAZMA_COMMISSION_RATE` | Comisión usada en la prueba. Por defecto `0.05`. Es un parámetro de prueba; el porcentaje real no está definido en los ADR |
| `POC_SEED` | Semilla de C10 para repetir una simulación |
| `FIDUCIARY_RANDOM_NEGOTIATIONS` | Negociaciones de C10. Por defecto `300` |
| `POC_RESULTS_DIR` | Carpeta de resultados. Por defecto `resultados` |

## Resultados

Ejecución local del 2026-10-28 con Java 21.0.12.1 (archivo [`resultados/resultado-20261028-115534.md`](resultados/resultado-20261028-115534.md)): **10 pasaron, 0 fallaron, 0 omitidos**.

Semilla de C10: `-5119620344524993935` (con `POC_SEED` se repite la misma simulación).

| Caso | Resultado | Evidencia |
|---|---|---|
| C1 Recursos separados de la cuenta operativa | **PASÓ** | Encargo neg-1 = 420.000; cuenta operativa DAZMA = 0 |
| C2 Comisión calculada una sola vez y sin pérdidas | **PASÓ** | 10.000/10.000 valores sin pérdidas; 99.999 → comisión 5000 + neto 94999; repetir el registro no recalcula ni duplica el recaudo |
| C3 No se libera antes de la condición | **PASÓ** | 1 confirmación → rechazada, encargo intacto; 2 confirmaciones → LIBERADA |
| C4 Liberación en dos instrucciones independientes | **PASÓ** | neg-4-vendedor 950000 CONFIRMADA; neg-4-comision 50000 CONFIRMADA; encargo = 0 |
| C5 Falla parcial de la liberación | **PASÓ** | Primer intento: comisión CONFIRMADA, vendedor FALLIDO, negociación LIBERANDO. Reintento: solo vendedor; ejecuciones comisión=1, vendedor=1 |
| C6 Instrucción repetida | **PASÓ** | 5 solicitudes (una con respuesta perdida) → 2 movimientos ejecutados; consultas de estado: 1 |
| C7 Cancelación antes de liberar | **PASÓ** | Devolución de 250.000 CONFIRMADA; comisión 0; liberar después de devolver se rechaza |
| C8 Desacuerdo | **PASÓ** | Liberar y devolver bloqueados durante el desacuerdo; resuelto a favor del comprador → solo devolución |
| C9 Línea de tiempo para reclamaciones | **PASÓ** | 9 eventos en orden: 1. RECAUDO_CONFIRMADO neg-9-recaudo \| 2. DISTRIBUCION comisión=32000 neto=608000 \| 3. ENTREGA_CONFIRMADA_VENDEDOR \| 4. ENTREGA_CONFIRMADA_COMPRADOR \| 5. LIBERACION_AUTORIZADA \| 6. INSTRUCCION_FALLIDA neg-9-vendedor \| 7. INSTRUCCION_CONFIRMADA neg-9-comision \| 8. INSTRUCCION_CONFIRMADA neg-9-vendedor \| 9. NEGOCIACION_LIBERADA |
| C10 Simulación aleatoria con fallas | **PASÓ** | 300 negociaciones, 0 violaciones. cancelación → DEVUELTA: 56; desacuerdo → DEVUELTA: 54; desacuerdo → LIBERADA: 55; liberación → LIBERADA: 135. Fallas inyectadas: 322, respuestas inciertas: 53. Comisiones en la cuenta operativa: 23441848 |
