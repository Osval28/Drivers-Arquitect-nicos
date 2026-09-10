# Restricciones de Negocio
| # | Tipo | Restricción de negocio |
|---:|:---|:---|
| 1 | Legal | La plataforma debe respetar la ley 527 de 1999, que define y reglamenta el uso de los mensajes de datos, el comercio electrónico y las firmas digitales |
| 2 | Legal | La plataforma debe respetar la ley 1581 de 2012 de protección de datos, que establece el régimen general de hábeas data |
| 3 | Legal | Las compras realizadas en la plataforma deben respetar la Ley 1480 de 2011, que establece en el capítulo VI la protección al consumidor de comercio electrónico y el decreto 587 de 2016 que reglamenta el derecho a la reversión del pago para compras hechas por internet, PSE, call centers o tiendas virtuales usando tarjetas de crédito, débito u otros medios electrónicos |
| 4 | Legal | La plataforma debe respetar el artículo 91 de la ley 633 del 2000, que establece la obligación del registro mercantil y de reportes de información de las transacciones a la DIAN para cualquier sitio de comercio electrónico de origen colombiano |
| 5 | Legal | La plataforma debe respetar los artículos 1955 a 1958 del Código Civil, que establece el contrato de permuta |
| 6 | Legal | La plataforma debe respetar el artículo 1850, que establece el comportamiento adecuado de intercambios con encime |
| 7 | Legal | La plataforma debe publicar sus términos y condiciones antes del lanzamiento, incluyendo reglas de uso, publicaciones permitidas, responsabilidades, cancelaciones, reportes y consecuencias por incumplimiento. |
| 8 | Presupuesto | Las inversiones iniciales en licencias, plataformas y servicios deben respetar el presupuesto de inversión aprobado y evitar compromisos a largo plazo antes de validar comercialmente el producto. |
| 9 | Humano | La salida a producción queda condicionada a que el negocio disponga de capacidad operativa suficiente para atención al cliente, moderación, reclamaciones y gestión de incidentes comerciales. |
| 10 | Proceso | El modelo de monetización aún debe ser validado comercialmente para determinar si las fuentes de ingreso propuestas tendrán suficiente aceptación entre los usuarios para sostener progresivamente la operación de la plataforma. |
| 11 | Proceso | La operación de las fuentes de ingreso asociadas a transacciones depende de que uno o más proveedores de pago acepten comercial y contractualmente el modelo de operación requerido |
| 12 | Proceso | La viabilidad de la salida pública depende de alcanzar una masa crítica inicial de compradores, vendedores y publicaciones activas dentro del mercado seleccionado para el lanzamiento. |

## Detalle

### RN-01 — Legal

**Restricción de negocio:** La plataforma debe respetar la ley 527 de 1999, que define y reglamenta el uso de los mensajes de datos, el comercio electrónico y las firmas digitales

**Justificación:** Nuestra plataforma intermedia compras e intercambios entre terceros, por lo que si un acuerdo entre 2 usuarios de la plataforma no se cumpliese o no fuese válido se perdería gran parte de la propuesta de la confianza y la seguridad del dinero como propuesta de generación de valor

**Plan de acción:** Mantener los acuerdos y compras entre usuarios inmutables luego de una confirmación o rechazo con su respectivo registro de quién realizó la propuesta de intercambio/aceptación/rechazo con fecha y hora, de forma que después se pueda consultar toda esta información en caso de presentarse una disputa

### RN-02 — Legal

**Restricción de negocio:** La plataforma debe respetar la ley 1581 de 2012 de protección de datos, que establece el régimen general de hábeas data

**Justificación:** DAZMA recopila y utiliza información de personas para operar. El incumplimiento de las obligaciones sobre autorización, finalidad, seguridad y derechos de los titulares puede producir sanciones y afectar gravemente la confianza en la plataforma

**Plan de acción:** Informar al usuario en el momento de la creación de su cuenta que está aceptando el consentimiento de que se use su información y que esta será usada con fines legales,establecer una política de tratamiento de datos, permitirle actualizar sus datos en cualquier momento, usar técnicas de cifrado en base de datos para reducir su vulnerabilidad, además, si el usuario quisiera borrar su cuenta se debería pedir una confirmación donde se le explica que esta acción es irreversible y que se perderán sus datos pero quedarán registradas las transacciones realizadas

### RN-03 — Legal

**Restricción de negocio:** Las compras realizadas en la plataforma deben respetar la Ley 1480 de 2011, que establece en el capítulo VI la protección al consumidor de comercio electrónico y el decreto 587 de 2016 que reglamenta el derecho a la reversión del pago para compras hechas por internet, PSE, call centers o tiendas virtuales usando tarjetas de crédito, débito u otros medios electrónicos

**Justificación:** Nuestro marketplace protege a todos los usuarios y su dinero como una parte fundamental de cualquier operación, por lo que debe tener lineamientos claros que permitan que un usuario en casos específicos como operación fraudulenta o no solicitada o producto defectuoso/distinto al solicitado reciba el dinero de vuelta en su medio de pago

**Plan de acción:** Establecer integraciones con pasarelas de pago seguras que permitan reversar el pago cuando nuestro sistema reciba la respectiva petición y un agente de soporte se contacte con los usuarios implicados

### RN-04 — Legal

**Restricción de negocio:** La plataforma debe respetar el artículo 91 de la ley 633 del 2000, que establece la obligación del registro mercantil y de reportes de información de las transacciones a la DIAN para cualquier sitio de comercio electrónico de origen colombiano

**Justificación:** El funcionamiento de DAZMA sin que esta esté inscrita en el Registro Mercantil o la compra o intercambio de productos sin informarle a la DIAN podría significar multas y sanciones que dificulten o directamente acaben con la operación de la plataforma

**Plan de acción:** Inscribirse en el registro mercantil en menos de 1 mes (Como establece la norma) después de que la plataforma salga a producción para empezar a operar de forma segura, además, mantener los registros de compras inmutables para garantizar su veracidad de forma tal que si la DIAN pidiese información relevante de estos se pueda generar un reporte oportunamente

### RN-05 — Legal

**Restricción de negocio:** La plataforma debe respetar los artículos 1955 a 1958 del Código Civil, que establece el contrato de permuta

**Justificación:** El funcionamiento de los intercambios directos sin respetar estas reglas expone a DAZMA a que un acuerdo se pueda declarar inválido, o a que una disputa por el estado del producto quede sin marco legal claro, ya que el artículo 1958 remite a las reglas de compraventa para resolver ese tipo de conflictos.

**Plan de acción:** Realizar una verificación de edad al momento del registro. Las disputas derivadas del intercambio directo como estado del producto, incumplimiento, desacuerdo entre las partes se atienden a través del sistema de reportes y el soporte, que registra la reclamación, la evidencia aportada y la resolución, dejando trazabilidad de cada caso conforme a lo exigido por la ley

### RN-06 — Legal

**Restricción de negocio:** La plataforma debe respetar el artículo 1850, que establece el comportamiento adecuado de intercambios con encime

**Justificación:** Es importante para la plataforma poder clasificar las negociaciones para determinar qué norma dicta el comportamiento adecuado; en este caso eso depende de la proporción entre el valor del objeto entregado y el encime. Sin esto bien clasificado no se sabría qué obligaciones legales le aplicarían a cada caso

**Plan de acción:** Al momento de acordar un intercambio con encime, el sistema compara el valor declarado del objeto entregado por cada parte contra el monto del encime en efectivo. Si el objeto vale más que el dinero, la operación se clasifica como permuta; si el dinero vale más que el objeto, se clasifica como venta. Esa clasificación queda registrada junto con la negociación y determina si aplican las obligaciones de consumidor asociadas a la venta (derecho de retracto, reversión de pago) o el régimen de permuta. El valor de referencia del objeto se toma del monto declarado por el usuario al momento de negociar

### RN-07 — Legal

**Restricción de negocio:** La plataforma debe publicar sus términos y condiciones antes del lanzamiento, incluyendo reglas de uso, publicaciones permitidas, responsabilidades, cancelaciones, reportes y consecuencias por incumplimiento.

**Justificación:** Los términos y condiciones son los que le exigen a los usuarios las reglas del negocio y delimitan la responsabilidad de la plataforma. Sin establecer eso, no se puede sustentar la suspensión de cuentas, el retiro de publicaciones, la retención o reversión del dinero y queda expuesta a reclamaciones por decisiones que no tienen soporte contractual.

**Plan de acción:** Redactar con asesoría jurídica los términos y condiciones y la política de tratamiento de datos; exigir consentimiento expreso en el momento del registro; conservar la versión aceptada por cada usuario junto con la fecha y definir el procedimiento de notificación y aceptación si llegase a cambiar

### RN-08 — Presupuesto

**Restricción de negocio:** Las inversiones iniciales en licencias, plataformas y servicios deben respetar el presupuesto de inversión aprobado y evitar compromisos a largo plazo antes de validar comercialmente el producto.

**Justificación:** Una inversión inicial excesiva puede consumir capital antes de comprobar que existe adopción suficiente del mercado. Además, tener contratos de largo plazo puede reducir la capacidad del negocio para cambiar de estrategia rápidamente en caso de que la demanda suba y el servicio actual no pueda soportar una mayor cantidad de usuarios

**Plan de acción:** Aprobar inversiones por etapas, priorizar servicios escalables y realizar pilotos antes de asumir compromisos financieros de largo plazo.

### RN-09 — Humano

**Restricción de negocio:** La salida a producción queda condicionada a que el negocio disponga de capacidad operativa suficiente para atención al cliente, moderación, reclamaciones y gestión de incidentes comerciales.

**Justificación:** Un marketplace en producción genera situaciones que requieren intervención humana. Lanzar sin personal, responsabilidades y procesos de atención definidos puede aumentar tiempos de respuesta, conflictos entre usuarios y daño reputacional.

**Plan de acción:** Conseguir un equipo pequeño que pueda cumplir con un número de peticiones básico de usuarios en una etapa temprana. El equipo podrá crecer conforme la plataforma lo haga

### RN-10 — Proceso

**Restricción de negocio:** El modelo de monetización aún debe ser validado comercialmente para determinar si las fuentes de ingreso propuestas tendrán suficiente aceptación entre los usuarios para sostener progresivamente la operación de la plataforma.

**Justificación:** El sostenimiento de la plataforma en el tiempo depende de la adopción del modelo de monetización por parte de los usuarios, por ahora se tienen algunas maneras de monetizar que si el cliente decide que no son las adecuadas se deben cambiar, lo que podría retrasar la fecha de entrega del producto final

**Plan de acción:** Hablar con el cliente sobre estrategias que le permitan tener madurez sobre el modelo de monetización, como hacer encuestas o entrevistas para identificar si hay suficientes personas dispuestas a pagar por el servicio

### RN-11 — Proceso

**Restricción de negocio:** La operación de las fuentes de ingreso asociadas a transacciones depende de que uno o más proveedores de pago acepten comercial y contractualmente el modelo de operación requerido

**Justificación:** Parte del modelo de negocio depende de pagos realizados dentro de la plataforma. La aprobación, condiciones comerciales, límites y capacidades disponibles dependen de proveedores externos sobre los cuales no se tiene control. Si ningún proveedor permite operar bajo las condiciones requeridas por el negocio, podría ser necesario modificar el modelo de monetización o la forma en que se gestionan las transacciones.

**Plan de acción:** Evaluar anticipadamente varios proveedores de pago, consultar sus condiciones comerciales y contractuales, validar que puedan soportar el modelo requerido y mantener más de una alternativa disponible antes de comprometer el modelo de negocio con un único proveedor.

### RN-12 — Proceso

**Restricción de negocio:** La viabilidad de la salida pública depende de alcanzar una masa crítica inicial de compradores, vendedores y publicaciones activas dentro del mercado seleccionado para el lanzamiento.

**Justificación:** Los compradores necesitan encontrar una oferta suficiente para percibir valor y los vendedores necesitan encontrar compradores para tener incentivos para publicar. Lanzar con una cantidad insuficiente de participantes puede producir una baja percepción de utilidad, poca recurrencia y pérdida de los primeros usuarios antes de que el marketplace logre consolidarse.

**Plan de acción:** Definir junto con el Product Owner los indicadores mínimos de preparación comercial, como usuarios registrados, vendedores activos, publicaciones disponibles y concentración de oferta; realizar campañas o pilotos previos de captación y evaluar estos indicadores antes de autorizar el lanzamiento público.
