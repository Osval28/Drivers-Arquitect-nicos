## 1. Requisitos funcionales

**Resumen:** 47 requisitos; 16 impactan al negocio (34.04%) y 2 están marcados como reto tecnológico (4.26%).

| ID | Descripción | ¿Impacta al negocio? | ¿Es un reto tecnológico? | ¿Por qué? |
|---|---|:---:|:---:|---|
| RF-01 | El sistema debe permitir registrar usuarios con nombre, apellido, correo y contraseña | No | No | Es genérico, es un problema ya resuelto |
| RF-02 | El sistema debe permitir iniciar sesión con usuario/correo y contraseña al sistema | No | No | Es genérico, es un problema ya resuelto |
| RF-03 | El sistema debe permitir cerrar sesión | No | No | Es genérico, es un problema ya resuelto |
| RF-04 | El sistema debe permitir recuperar la contraseña en caso de que el usuario la haya olvidado | No | No | Es genérico, es un problema ya resuelto |
| RF-05 | El sistema debe permitirle a un usuario consultar su propia información | No | No | Es genérico, es un problema ya resuelto |
| RF-06 | El sistema condiciona las funcionalidades de acuerdo a su rol y datos de usuario | Sí | No | Porque afecta directamente a la reputación de la plataforma y qué tanto confían los usuarios en la plataforma |
| RF-07 | El sistema debe permitirle al usuario eliminar su cuenta | No | No | Es genérico, es un problema ya resuelto |
| RF-08 | El sistema debe permitirle al usuario actualizar sus datos | No | No | Es genérico, es un problema ya resuelto |
| RF-09 | El sistema debe permitir crear publicaciones con producto, tipo (Compra/intercambio), nombre, descripción, fotos, ciudad e información de contacto | Sí | No | Es nuestra propuesta de valor principal |
| RF-10 | El sistema debe permitir editar cualquier apartado de una publicación creada | No | No | Es una funcionalidad genérica |
| RF-11 | El sistema debe permitir visualizar las publicaciones | No | No | Es una funcionalidad genérica |
| RF-12 | El sistema debe permitir eliminar publicaciones | No | No | CRUD genérico, sin valor diferencial ni dificultad técnica. |
| RF-13 | El sistema debe permitir filtrar las publicaciones por parámetros como ciudad, precio o categoría | Sí | No | Filtrar por ciudad es importante para la idea de negocio |
| RF-14 | El sistema debe permitir agregar productos a una lista de favoritos | No | No | Es una funcionalidad genérica |
| RF-15 | El sistema debe permitir realizar propuestas de intercambio con producto, valor, encime y lugar | Sí | No | Es nuestro motor principal de generación de valor |
| RF-16 | El sistema debe permitir realizar contraofertas a un intercambio propuesto | Sí | No | Es parte del posible flujo de los intercambios |
| RF-17 | El sistema debe verificar que un usuario no se haga una propuesta de intercambio o se compre a sí mismo | No | No | Es una verificación básica |
| RF-18 | El sistema debe marcar una publicación como no disponible luego de que se finalice una compra o intercambio de la misma | No | No | Es algo genérico |
| RF-19 | El sistema debe permitir aceptar o rechazar propuestas de intercambio | Sí | No | Porque permite fomalizar el intercambio, que es parte fundamental del negocio |
| RF-20 | El sistema debe permitir comprarle un producto directamente al vendedor | Sí | No | Es la función principal de uno de nuestros segmentos |
| RF-21 | El sistema debe permitir pagar el encime de un intercambio a través de una pasarela | Sí | No | Aquí se garantiza que el dinero pase por la plataforma, asegurando la monetización de la misma |
| RF-22 | El sistema debe retener el dinero de una compra o intercambio hasta la confirmación de entrega por parte de ambos miembros | Sí | Sí | Es una función de alto impacto porque protege el dinero mientras la operación no ha finalizado. Además, plantea un reto técnico por la coordinación de estados, confirmaciones de ambas partes e integración con un proveedor de pagos cuya capacidad de retención debe validarse. |
| RF-23 | El sistema debe liberar el dinero hacia el vendedor/persona con el objeto de mayor valor en un intercambio luego de la confirmación de entrega por parte de ambos miembros | Sí | Sí | Es crítica para el cierre financiero de la operación. Requiere garantizar que la liberación ocurra una sola vez, al destinatario correcto y únicamente cuando se cumplan las condiciones de finalización definidas. |
| RF-24 | El sistema debe calcular la comisión del valor de la compra o encime de un intercambio | No | No | Es lógica de las comisiones que se hayan tomado de forma matemática |
| RF-25 | El sistema debe permitir suscribirse a uno de los planes premium que ofrece la plataforma pagando a través de una pasarela de pago | No | No | Es un patrón freemium estándar, ya está establecido |
| RF-26 | El sistema debe permitir crear nuevos planes de suscripción | No | No | Es un CRUD simple desde la parte administrativa |
| RF-27 | El sistema debe permitir cancelar su suscripción a un plan premium | No | No | Es una funcionalidad de gestión de cuenta genérica |
| RF-28 | El sistema debe permitir pagar una insignia de verificación por medio de una pasarela de pago | No | No | No se necesita implementar una pasarela de pago, solo integrarla |
| RF-29 | El sistema debe priorizar en los resultados de búsqueda y listados las publicaciones de usuarios con un plan premium activo. | No | No | Es una regla de ordenamiento que puede seguir algoritmos ya creados y probados |
| RF-30 | El sistema debe notificarle al usuario por medio de correo electrónico que hay nuevos mensajes en un chat | No | No | Es un patrón estándar de notificaciones, hay soluciones ya creadas y no es nuestro diferencial competidor |
| RF-31 | El sistema debe notificarle al usuario por medio de correo electrónico propuestas de intercambio o compra | No | No | Es un patrón estándar de notificaciones, hay soluciones ya creadas y no es nuestro diferencial competidor |
| RF-32 | El sistema debe permitir iniciar un chat con el dueño de una publicación para aclarar detalles de la negociación | No | No | Es una funcionalidad básica no inherente a la generación de valor del proyecto, además de que es un problema ya solucionado y probado ampliamente |
| RF-33 | El sistema debe permitir enviar mensajes en un chat | No | No | Es una funcionalidad básica no inherente a la generación de valor del proyecto, además de que es un problema ya solucionado y probado ampliamente |
| RF-34 | El sistema debe permitir calificar a un usuario luego de un intercambio o compra en una escala numérica de 1 a 5 | Sí | No | Es una funcionalidad que aumenta la cantidad de datos para la reputación de un usuario, lo que es importante para cumplir con la promesa de confianza de la plataforma |
| RF-35 | El sistema debe permitir al usuario acceder a su historial de compras e intercambios finalizados o en curso con el estado en que se encuentre | No | No | Es una funcionalidad básica que no genera valor al negocio y no tiene una gran complejidad de implementación |
| RF-36 | El sistema debe permitir acceder al historial de intercambios de cualquier usuario | Sí | No | Es una funcionalidad importante para cumplir con la promesa de confianza del proyecto |
| RF-37 | El sistema debe permitir consultar la reputación de un usuario | Sí | No | Es la manera en que un usuario puede tener o no confianza en realizar una compra/intercambio con otro |
| RF-38 | El sistema debe permitir reportar publicaciones con contenido inapropiado o engañoso | No | No | Es una moderación genérica presente en cualquier plataforma |
| RF-39 | El sistema debe permitir reportar usuarios por incumplir las normas de la plataforma | No | No | Mismo caso, funcionalidad de moderación genérica. |
| RF-40 | El sistema debe permitir visualizar los reportes a los administradores | No | No | Es una herramienta administrativa interna, consulta simple. |
| RF-41 | El sistema debe permitir a los administradores marcar al usuario responsable de una publicación o comportamiento inapropiado en chat con un strike. | No | No | Es una moderación genérica y sin mucha complejidad técnica |
| RF-42 | El sistema debe permitir a cada parte de un intercambio o compra confirmar que la entrega presencial fue completada exitosamente. | Sí | No | Es crítica porque conecta la operación digital con la entrega presencial. La doble confirmación afecta el estado final de la operación y puede habilitar acciones económicas posteriores, por lo que requiere controlar concurrencia y transiciones válidas. |
| RF-43 | El sistema debe permitir cancelar una propuesta de intercambio o compra antes de su confirmación final. | Sí | No | Es crítica porque define un camino principal del ciclo de vida de una negociación. Debe establecerse en qué estados puede cancelarse y qué efectos produce sobre confirmaciones, pagos retenidos y demás elementos relacionados. |
| RF-44 | El sistema debe reembolsar el dinero retenido de una compra o intercambio si este se cancela antes de la confirmación de entrega. | Sí | No | Es crítica porque define qué ocurre con el dinero cuando una operación se cancela. Requiere mantener coherencia entre el estado interno y la operación externa de reembolso o reversión, y validar que el proveedor de pagos seleccionado soporte el flujo necesario. |
| RF-45 | El sistema debe permitir consultar la lista de publicaciones marcadas como favoritas por el usuario. | No | No | Es una funcionalidad genérica, parte de un CRUD, no aporta valor al negocio |
| RF-46 | El sistema debe permitir eliminar una publicación de la lista de favoritos del usuario. | No | No | Es una funcionalidad genérica, parte de un CRUD, no aporta valor al negocio |
| RF-47 | El sistema debe permitir a los administradores cerrar la cuenta de un usuario responsable de una publicación o comportamiento inapropiado en chat. | No | No | Es una accción administrativa estándar que no aporta valor de negocio |

## 2. Funcionalidades críticas / significativas

| Código | Funcionalidad crítica | Justificación |
|---|---|---|
| RF-15 | El sistema debe permitir realizar propuestas de intercambio con producto, valor, encime y lugar | Es crítica porque materializa el dominio de negocio de nuestro proyecto: La negociación de intercambios. Permite iniciar una operación especificando los productos que serán negociados y constituye el punto de partida del flujo principal de generación de valor de la plataforma. |
| RF-16 | El sistema debe permitir realizar contraofertas a un intercambio propuesto | Es crítica porque forma parte directamente del mecanismo de negociación que diferencia al proyecto de un marketplace convencional. Permitir contraofertas hace posible que el intercambio sea dinámico y no se limite a una propuesta de compra de un solo lado. |
| RF-20 | El sistema debe permitir comprarle un producto directamente al vendedor | Es crítica porque, aunque la compra directa es un segmento secundario del negocio, reutiliza el mecanismo económico mediante el cual se obtienen ingresos. La comisión se calcula sobre el dinero que pasa por la plataforma, por lo que esta funcionalidad condiciona capacidades relacionadas con pagos, comisiones y ciclo de vida de la operación. |
| RF-22 | El sistema debe retener el dinero de una compra o intercambio hasta la confirmación de entrega por parte de ambos miembros | Es crítica por su impacto en la confianza y por el riesgo económico asociado. La retención depende del estado de la operación, de la confirmación de ambas partes y de una integración externa cuya capacidad real debe validarse antes de adoptar un proveedor. |
| RF-23 | El sistema debe liberar el dinero hacia el vendedor/persona con el objeto de mayor valor en un intercambio luego de la confirmación de entrega por parte de ambos miembros | Es crítica porque determina el resultado financiero de un intercambio con diferencia monetaria. El sistema debe identificar quién debe recibir los fondos y garantizar que la liberación ocurra únicamente después de cumplirse la finalización de la operación |
| RF-42 | El sistema debe permitir a cada parte de un intercambio o compra confirmar que la entrega presencial fue completada exitosamente. | Es crítica porque conecta la negociación digital con el evento físico que permite finalizarla. La confirmación de ambas partes condiciona el estado final y puede habilitar la liberación de fondos, por lo que debe manejarse correctamente la concurrencia entre confirmaciones. |
| RF-43 | El sistema debe permitir cancelar una propuesta de intercambio o compra antes de su confirmación final. | Es crítica porque define uno de los caminos principales de la máquina de estados de una operación. Debe determinarse cuándo una cancelación es válida y qué consecuencias produce sobre pagos, confirmaciones y estados relacionados. |
| RF-44 | El sistema debe reembolsar el dinero retenido de una compra o intercambio si este se cancela antes de la confirmación de entrega. | Es crítica porque establece qué pasa con el dinero cuando una operación no llega a completarse. Forma parte de la garantía financiera ofrecida por la plataforma y debe coordinarse con el estado de la operación y con la pasarela de pagos para evitar que una cancelación deje el dinero en el lugar incorrecto |
| RF-19 | El sistema debe permitir aceptar o rechazar propuestas de intercambio | Es crítica porque formaliza el resultado de la negociación. La aceptación o rechazo determina si una propuesta se convierte en una operación acordada o finaliza sin intercambio entre dichos usuarios. |
| RF-34 | El sistema debe permitir calificar a un usuario luego de un intercambio o compra en una escala numérica de 1 a 5 | Es una funcionalidad que aumenta la cantidad de datos para la reputación de un usuario, lo que es importante para cumplir con la promesa de confianza de la plataforma, y asi los usuarios puedan tener confinza a la hora de comprar |
| RF-36 | El sistema debe permitir acceder al historial de intercambios de cualquier usuario | Es una funcionalidad importante para cumplir con la promesa de confianza del proyecto, y a su vez, permite ver la confiabilidad de la persona con la cual realizará el intercambio o, en su defecto, a quien le comprara algun producto |
| RF-37 | El sistema debe permitir consultar la reputación de un usuario | Porque facilita que un usuario tenga o no confianza en realizar una compra/intercambio con otro, lo cual le da seguridad en la plataforma. Así puede ver si este usuario es confiable, es bueno y cómo le ha ido en sus intercambios anteriores |
| RF-09 | El sistema debe permitir crear publicaciones con producto, tipo (Compra/intercambio), nombre, descripción, fotos y ciudad | Es significativa porque las publicaciones son el punto de entrada del marketplace y permiten representar la oferta disponible para compra o intercambio. Sin este elemento no puede iniciarse el flujo comercial principal ni materializarse la propuesta de valor |
| RF-13 | El sistema debe permitir filtrar las publicaciones por parámetros como ciudad, precio o categoría | Los filtros ayudan a los compradores a ver articulos en especifico, permitiendo buscarlos por lugar de venta, precio, categorias y mas filtros que ayudan a facilitar las busquedas |
| RF-06 | El sistema debe darle permisos a los usuarios basado en el rol que tiene (Admin, comprador/vendedor) | Es significativa porque asegura que solamente los usuarios implicados en una negociación (Compra/intercambio) puedan tener acceso a sus datos y control de sus operaciones |

## 3. Atributos de calidad

| Atributo de Calidad | Descripción | Implícito | Explícito | ¿Por qué? |
|---|---|:---:|:---:|---|
| Seguridad | Protección contra accesos no autorizados, ejecución indebida de operaciones críticas y exposición de información sensible o secretos. | X |  | Se considera implícito en los requisitos de cuentas, negociaciones y pagos: los RF describen qué operaciones existen, pero no especifican por sí solos controles como mínimo privilegio, deny by default o protección de secretos. |
| Confiabilidad | Conservación de estados válidos ante fallas, idempotencia de operaciones críticas, aislamiento de fallos y prevención de efectos duplicados. | X |  | Es implícito porque los flujos de negociación y pagos sólo son correctos si no pierden, duplican o corrompen información ante fallas, reintentos o concurrencia. |
| Capacidad para ser auditado | Posibilidad de reconstruir cronológicamente eventos críticos de una negociación u operación, identificando actor, momento, acción y resultado. | X |  | Es implícito porque las reclamaciones y operaciones económicas requieren evidencia verificable, aunque los requisitos funcionales no solicitan explícitamente un módulo de auditoría. |
| Disponibilidad | Capacidad de mantener accesibles los flujos críticos y reducir la indisponibilidad durante cambios planificados o periodos de operación. | X |  | Es implícito porque compras, negociaciones, confirmaciones y operaciones críticas deben poder utilizarse cuando corresponde, aunque los RF no fijan un porcentaje de disponibilidad. |
| Interoperabilidad | Capacidad de DAZMA para integrarse con servicios externos mediante contratos y protocolos definidos sin acoplar las reglas centrales a un proveedor concreto. |  | X | Es explícito porque varios requisitos requieren integración con servicios externos, especialmente la pasarela de pagos, y el proyecto contempla sustituir o incorporar proveedores mediante interfaces conocidas. |
| Rendimiento | Tiempo de respuesta y latencia percibida en operaciones relevantes, especialmente la comunicación dentro de una negociación. |  | X | Es explícito porque la plataforma debe ser fluida a la hora de establecer comunicación entre los 2 actores de una negociación para que se sienta rápida |
| Usabilidad UX | Comprensión clara de propuestas, contraofertas, estados y acciones importantes para que los usuarios puedan operar sin ambigüedad. |  | X | Es explícito porque el usuario debe comprender con claridad qué entrega y qué recibe al decidir sobre una propuesta o contraoferta, dado que un flujo técnicamente correcto puede fallar si el usuario interpreta mal la operación, lo cual es sumamente importante para la seguridad que ofrece la plataforma |
| Escalabilidad | Capacidad de absorber crecimiento y picos de demanda sin una caída general ni reconstruir las funcionalidades centrales. | X |  | Es implícito porque el comportamiento esperado de la plataforma ante picos de tráfico y crecimiento de solicitudes es algo importante para cualquier e-commerce |
| Capacidad | Volumen de usuarios, negociaciones, conversaciones, publicaciones y recursos que la plataforma puede soportar dentro de límites operativos definidos. | X |  | Es implícito porque todo sistema tiene límites de volumen y concurrencia, pero el negocio todavía debe definir los objetivos cuantitativos para cada etapa de crecimiento. |
| Accesibilidad | Posibilidad de que las funciones principales sean utilizables por personas con diferentes capacidades y mediante tecnologías de asistencia. | X |  | Se mantiene como atributo del proyecto, aunque no quedó entre los escenarios finales priorizados. Los requisitos funcionales actuales no establecen medidas concretas de accesibilidad. |
| Portabilidad | Capacidad de ejecutar y configurar la solución en entornos soportados sin modificar las reglas centrales del negocio ni depender innecesariamente de componentes propietarios. | X |  | Se mantiene como atributo del proyecto, aunque su prioridad es baja. Los RF no exigen un entorno específico, por lo que conviene evitar dependencias innecesarias mientras se toman las decisiones tecnológicas. |

### 3.1 Priorización Trade-off

| Prioridad | Atributo de calidad |
|---:|---|
| 11 | Seguridad |
| 10 | Confiabilidad |
| 9 | Capacidad para ser auditado |
| 8 | Disponibilidad |
| 7 | Interoperabilidad |
| 6 | Rendimiento |
| 5 | Usabilidad UX |
| 4 | Escalabilidad |
| 3 | Capacidad |
| 2 | Accesibilidad |
| 1 | Portabilidad |

### 3.2 Priorización por stakeholders

| Atributo de calidad | Comprador | Vendedor | Administrador | Total de votos | Ponderador global |
|---|---:|---:|---:|---:|---:|
| Seguridad | 11 | 11 | 10 | 32 | 16.16% |
| Confiabilidad | 10 | 10 | 9 | 29 | 14.65% |
| Capacidad para ser auditado | 6 | 9 | 11 | 26 | 13.13% |
| Disponibilidad | 9 | 8 | 7 | 24 | 12.12% |
| Usabilidad UX | 8 | 5 | 5 | 18 | 9.09% |
| Escalabilidad | 2 | 4 | 4 | 10 | 5.05% |
| Accesibilidad | 4 | 2 | 2 | 8 | 4.04% |
| Rendimiento | 7 | 6 | 6 | 19 | 9.60% |
| Interoperabilidad | 5 | 7 | 8 | 20 | 10.10% |
| Capacidad | 3 | 3 | 3 | 9 | 4.55% |
| Portabilidad | 1 | 1 | 1 | 3 | 1.52% |
| **Total** | **66** | **66** | **66** | **198** | **100.00%** |

## 4. Lluvia de escenarios — Ronda 1

> **Dot Voting:** Se asignó un 25% del total de ideas a cada actor, lo que da 24.75 ideas, quedando finalmente 25 puntos a cada actor. Cada actor puede dar un máximo de 2 puntos a una idea que le parezca más importante, siendo 2 puntos un punto de importancia máxima

Se registraron **99 escenarios**, agrupados en **45 características**. La votación acumuló **25 puntos de Comprador, 25 de Vendedor y 25 de Administrador, para un total de 75 puntos**.

### 4.1 Seguridad

#### CAR-SEG-0001 — El sistema debe gestionar de forma segura las credenciales y el ciclo de vida de las sesiones de sus usuarios

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-SEG-0001 | Quiero que, al olvidar la contraseña, pueda reestablecerla | 0 | 0 | 0 | 0 |
| ESC-CAL-SEG-0002 | Si un usuario deja su sesión abierta durante un periodo prolongado de inactividad, el sistema debe evitar que otra persona pueda continuar utilizando indefinidamente esa sesión | 1 | 1 | 1 | 3 |
| ESC-CAL-SEG-0003 | Cuando un usuario cambie su contraseña, las sesiones que representen un riesgo deben poder invalidarse para evitar accesos con credenciales anteriores | 0 | 0 | 0 | 0 |

#### CAR-SEG-0002 — El sistema debe garantizar que cada usuario solo acceda y opere sobre los recursos y operaciones que le corresponden

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-SEG-0004 | El sistema debe impedir que usuarios no autorizados accedan, consulten o modifiquen información y recursos que no les pertenecen. | 2 | 2 | 2 | 6 |
| ESC-CAL-SEG-0005 | Un usuario no puede aceptar, cancelar o confirmar operaciones en las que no participa | 0 | 0 | 0 | 0 |
| ESC-CAL-SEG-0006 | Un usuario que intente consultar directamente mediante una URL un recurso administrativo sin permisos no debe poder acceder a su información | 0 | 0 | 0 | 0 |

#### CAR-SEG-0003 — El sistema debe proteger las operaciones económicas frente a uso indebido, repetición y exposición de información secreta

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-SEG-0007 | Yo como comprador quiero que mi dinero se retenga en la aplicación hasta confirmar que el encuentro fue exitoso | 1 | 1 | 1 | 3 |
| ESC-CAL-SEG-0008 | Las operaciones críticas relacionadas con cuentas, negociaciones, pagos y acciones administrativas deben ejecutarse únicamente con identidad y permisos válidos, sin exponer información secreta. | 2 | 1 | 2 | 5 |
| ESC-CAL-SEG-0009 | Las operaciones económicas requieren que el usuario se encuentre correctamente autenticado y autorizado | 0 | 0 | 0 | 0 |

#### CAR-SEG-0004 — El sistema debe validar el contenido cargado por los usuarios antes de incorporarlo a la plataforma

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-SEG-0010 | Los archivos o imágenes cargados por los usuarios deben validarse para evitar que contenido malicioso sea utilizado para comprometer la plataforma | 0 | 0 | 0 | 0 |

### 4.2 Confiabilidad

#### CAR-CON-0001 — El sistema debe preservar la integridad de la información crítica del negocio ante fallas e interrupciones

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CON-0001 | Si ocurre una falla mientras se procesa una negociación o una operación económica, el sistema debe conservar un único estado válido sin cambios parciales ni contradictorios. | 2 | 2 | 1 | 5 |
| ESC-CAL-CON-0002 | Las fallas temporales no deben provocar pérdida, duplicación o corrupción de información crítica del negocio | 0 | 0 | 0 | 0 |
| ESC-CAL-CON-0003 | Si una confirmación de pago llega mientras el usuario pierde conexión, el resultado final de la operación debe conservarse correctamente, aunque el usuario no alcance a visualizarlo en ese instante | 0 | 0 | 0 | 0 |

#### CAR-CON-0002 — El sistema debe garantizar que una misma operación crítica produzca un único efecto, sin importar cuántas veces se solicite

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CON-0004 | El sistema debe conservar estados coherentes y evitar que una misma operación crítica se ejecute más de una vez | 0 | 0 | 0 | 0 |
| ESC-CAL-CON-0005 | La recepción repetida de una misma notificación válida de pago debe producir un único efecto económico y un único cambio válido de estado. | 2 | 2 | 1 | 5 |
| ESC-CAL-CON-0006 | Si un usuario presiona varias veces el botón de confirmar una operación debido a una conexión lenta, debe producirse un único resultado válido | 0 | 0 | 0 | 0 |

#### CAR-CON-0003 — El sistema debe mantener la coherencia de la máquina de estados de la negociación frente a eventos concurrentes o tardíos

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CON-0007 | Una cancelación válida conserva coherencia entre el estado de la negociación y las operaciones económicas relacionadas | 0 | 0 | 0 | 0 |
| ESC-CAL-CON-0008 | Si una negociación ya fue cancelada, una respuesta retrasada de otro proceso no debe devolverla accidentalmente a un estado anterior | 0 | 0 | 0 | 0 |
| ESC-CAL-CON-0009 | Cuando dos usuarios actúan casi simultáneamente sobre una misma negociación, el sistema debe conservar un único estado válido y reconocible | 0 | 0 | 0 | 0 |

#### CAR-CON-0004 — El sistema debe detectar condiciones de degradación antes de que provoquen una falla

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CON-0010 | Si la plataforma presenta señales de saturación o degradación que puedan provocar una falla, deben detectarse y mitigarse antes de que el servicio se interrumpa. | 2 | 1 | 2 | 5 |

#### CAR-CON-0005 — El sistema debe seguir prestando sus funciones principales aunque un servicio secundario o externo se encuentre fuera de servicio

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CON-0011 | Si un componente o servicio secundario o externo presenta una falla, esta debe quedar contenida y los flujos críticos que no dependan de él deben continuar operando. | 1 | 1 | 2 | 4 |
| ESC-CAL-CON-0012 | La plataforma continúa permitiendo consultar publicaciones, aunque temporalmente falle el servicio de pagos | 0 | 0 | 0 | 0 |
| ESC-CAL-CON-0013 | Si una funcionalidad secundaria presenta una falla, el usuario debe poder continuar utilizando las demás funciones que no dependan directamente de ella | 0 | 0 | 0 | 0 |

#### CAR-CON-0006 — El sistema debe recuperarse de interrupciones breves conservando el último estado válido de la operación

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CON-0014 | Después de una interrupción breve de conexión, el usuario puede continuar una negociación desde el último estado confirmado | 0 | 0 | 0 | 0 |

### 4.3 Capacidad para ser auditado

#### CAR-AUD-0001 — El sistema debe registrar quien hizo y el momento de cada acción crítica realizada sobre la plataforma

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-AUD-0001 | Puede conocerse quién realizó una acción crítica sobre una negociación | 0 | 0 | 0 | 0 |
| ESC-CAL-AUD-0002 | Puede conocerse cuándo ocurrió cada cambio importante en una operación | 0 | 0 | 0 | 0 |

#### CAR-AUD-0002 — El sistema debe conservar registros suficientes para investigar incidentes, protegidos contra alteración y sin almacenar secretos innecesarios

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-AUD-0003 | Cuando un administrador aplique una acción sobre una cuenta o publicación reportada, debe quedar evidencia de quién realizó la acción y sobre qué elemento | 0 | 0 | 0 | 0 |
| ESC-CAL-AUD-0004 | Los registros conservan información suficiente para investigar incidentes sin almacenar innecesariamente contraseñas o secretos. | 0 | 0 | 0 | 0 |

#### CAR-AUD-0003 — El sistema debe permitir seguir el rastro de las operaciones económicas y vincularlas con la negociación que las originó

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-AUD-0005 | Los registros utilizados como evidencia deben protegerse contra modificaciones indebidas y evitar almacenar secretos innecesarios | 0 | 0 | 0 | 0 |
| ESC-CAL-AUD-0006 | Puede identificarse el resultado de las operaciones económicas relevantes realizadas por la plataforma | 0 | 0 | 0 | 0 |
| ESC-CAL-AUD-0007 | Los cambios relevantes de una operación económica deben poder relacionarse con la negociación que los originó | 0 | 0 | 0 | 0 |

#### CAR-AUD-0004 — El sistema debe permitir reconstruir cronológicamente lo ocurrido en una operación para atender reclamaciones y diagnosticar fallas

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-AUD-0008 | Ante una reclamación, un usuario autorizado debe poder reconstruir cronológicamente los eventos críticos ocurridos durante una negociación y sus operaciones relacionadas. | 1 | 2 | 2 | 5 |
| ESC-CAL-AUD-0009 | Cuando una operación falle, debe ser posible identificar posteriormente en qué etapa ocurrió el problema sin depender únicamente de la descripción del usuario | 0 | 0 | 0 | 0 |

### 4.4 Disponibilidad

#### CAR-DIS-0002 — El sistema debe evitar que una falla deje al usuario bloqueado o le haga perder el avance de su negociación

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-DIS-0005 | Si un servicio crítico deja de responder, la plataforma informa la falta de servicio sin dejar al usuario en una pantalla bloqueada indefinidamente | 1 | 1 | 1 | 3 |

#### CAR-DIS-0003 — El sistema debe poder evolucionar, mantenerse y recuperarse afectando lo menos posible la operación de los usuarios

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-DIS-0006 | Durante despliegues de nuevas versiones o mantenimientos planificados, la indisponibilidad de los flujos críticos de DAZMA debe mantenerse dentro del máximo definido. | 1 | 1 | 2 | 4 |
| ESC-CAL-DIS-0007 | Durante mantenimientos planificados, los periodos de indisponibilidad deben reducirse de manera que afecten lo menos posible las operaciones activas en los horarios menos concurridos | 0 | 0 | 0 | 0 |

### 4.5 Usabilidad UX

#### CAR-USA-0001 — La interfaz debe emplear un lenguaje y una presentación visual comprensibles para usuarios con distintos niveles de experiencia digital

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-USA-0001 | Los colores de la interfaz deben facilitarle la búsqueda de opciones al usuario y el tamaño de la letra suficientemente grande para que público de todas las edades acceda cómodamente a la información | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0002 | La interfaz debe usar palabras fáciles de entender para los usuarios | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0003 | No se debe saturar la pantalla de información irrelevante que le quite visibilidad a lo verdaderamente útil | 0 | 0 | 0 | 0 |

#### CAR-USA-0002 — La interfaz debe mantener siempre visible el estado actual de las operaciones del usuario sin exigirle memorizar lo ocurrido

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-USA-0004 | Cuando un usuario complete correctamente una operación, la interfaz debe indicarle inmediatamente que la acción fue realizada con éxito | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0005 | El usuario debe poder identificar fácilmente en qué estado se encuentra una negociación sin tener que recordar las acciones realizadas anteriormente | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0006 | Antes de decidir sobre una propuesta o contraoferta, el usuario debe comprender claramente qué entrega, qué recibe y si existe dinero adicional involucrado. | 2 | 2 | 1 | 5 |
| ESC-CAL-USA-0007 | Cuando existan filtros de búsqueda activos, el usuario debe poder reconocer cuáles están aplicados sin tener que abrir nuevamente la opción | 0 | 0 | 0 | 0 |

#### CAR-USA-0003 — La interfaz debe prevenir errores del usuario y facilitar su recuperación sin pérdida de trabajo

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-USA-0008 | La interfaz debe prevenir errores y ayudar al usuario a recuperarse con salidas fáciles cuando una acción no pueda completarse o quiera deshacer operaciones sencillas | 0 | 0 | 0 | 0 |

#### CAR-USA-0004 — La interfaz debe ser consistente entre pantallas y minimizar los recorridos necesarios para las acciones frecuentes

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-USA-0009 | Los mensajes de error deben ser suficientemente claros para los usuarios, con descripciones que realmente hagan entender al usuario qué provocó el error | 1 | 1 | 1 | 3 |
| ESC-CAL-USA-0010 | Las opciones que realizan la misma acción deben conservar nombres, iconos y movimientos coherentes entre las diferentes pantallas | 0 | 0 | 0 | 0 |

#### CAR-USA-0005 — El sistema debe acompañar al usuario antes de que tome decisiones importantes y ofrecerle ayuda accesible

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-USA-0011 | Las acciones utilizadas frecuentemente deben encontrarse cerca del contexto donde el usuario las necesita, evitando recorridos innecesarios por múltiples pantallas | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0012 | Los flujos principales deben ser comprensibles para usuarios con distintos niveles de experiencia digital y mostrar con claridad qué está ocurriendo | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0013 | Quiero que sea fácil para el usuario encontrar guías sobre cómo hacer operaciones en la plataforma | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0014 | Antes de confirmar una compra, intercambio o acción importante, el usuario debe comprender claramente qué ocurrirá después de confirmar | 1 | 1 | 0 | 2 |

### 4.6 Escalabilidad

#### CAR-ESC-0001 — La plataforma debe poder crecer en usuarios y cobertura geográfica sin reconstruir ni duplicar sus funcionalidades centrales

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ESC-0001 | Si la cantidad de usuarios activos aumenta varias veces respecto a la etapa inicial, la plataforma debe poder crecer sin necesidad de reconstruir sus funcionalidades principales | 0 | 0 | 0 | 0 |

#### CAR-ESC-0002 — La plataforma debe absorber picos de demanda sin interrumpir el servicio

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ESC-0002 | La expansión desde el mercado inicial hacia nuevas ciudades o regiones debe poder realizarse sin crear una instalación independiente de DAZMA para cada ubicación | 0 | 0 | 0 | 0 |
| ESC-CAL-ESC-0003 | Ante un incremento temporal importante de usuarios y solicitudes, DAZMA debe absorber la carga o degradarse de forma controlada sin una caída general. | 1 | 1 | 1 | 3 |

#### CAR-ESC-0003 — La plataforma debe sostener el crecimiento continuo del volumen de datos históricos sin degradar su capacidad de atender nuevos usuarios

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ESC-0004 | El crecimiento continuo del número de publicaciones no debe hacer que la plataforma pierda progresivamente su capacidad para atender nuevos usuarios | 0 | 0 | 0 | 0 |

#### CAR-ESC-0004 — La plataforma debe seguir entregando la información operativa que el negocio requiere a medida que crece el volumen de operaciones

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ESC-0005 | El incremento del historial de chats y negociaciones a lo largo de los años no debe impedir que DAZMA continúe incorporando nuevos usuarios y operaciones | 0 | 0 | 0 | 0 |
| ESC-CAL-ESC-0006 | Quiero que en caso de necesitar registros de cualquier operación realizada este se genere en menos de 10s | 0 | 0 | 0 | 0 |

### 4.7 Accesibilidad

#### CAR-ACC-0001 — La plataforma debe ofrecer soporte de accesibilidad para personas con discapacidades cognitivas, motoras o visuales

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ACC-0001 | Quiero que el sistema tenga soporte de accesibilidad para personas con discapacidades cognitivas, motoras o visuales | 0 | 0 | 0 | 0 |

#### CAR-ACC-0002 — Las funciones principales deben poder operarse sin depender del mouse ni de una precisión motora elevada

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ACC-0002 | Todas las acciones principales de DAZMA deben poder realizarse mediante teclado sin depender exclusivamente del mouse | 0 | 0 | 0 | 0 |

#### CAR-ACC-0003 — La interfaz debe ser interpretable por tecnologías de asistencia como los lectores de pantalla

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ACC-0003 | Los elementos interactivos deben ser suficientemente distinguibles y utilizables por personas con dificultades motoras que requieren mayor precisión o dispositivos alternativos | 0 | 0 | 0 | 0 |
| ESC-CAL-ACC-0004 | Los campos de formularios deben disponer de etiquetas que puedan ser interpretadas por tecnologías de asistencia | 0 | 0 | 0 | 0 |

#### CAR-ACC-0004 — La información debe ser perceptible visualmente sin depender de un único canal ni de una configuración de pantalla específica

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-ACC-0005 | Los botones e imágenes que cumplen una función deben proporcionar información comprensible para usuarios que utilicen lectores de pantalla | 0 | 0 | 0 | 0 |
| ESC-CAL-ACC-0006 | Los mensajes de error deben poder ser percibidos por usuarios que dependan de tecnologías de asistencia | 0 | 0 | 0 | 0 |
| ESC-CAL-ACC-0007 | La información importante no debe comunicarse únicamente mediante colores | 0 | 0 | 0 | 0 |
| ESC-CAL-ACC-0008 | Al aumentar considerablemente el tamaño del texto o el zoom del navegador, las funciones esenciales deben continuar siendo visibles y utilizables | 0 | 0 | 0 | 0 |
| ESC-CAL-ACC-0009 | La interfaz debe mantener suficiente contraste entre texto, controles y fondos para facilitar su lectura | 0 | 0 | 0 | 0 |

### 4.8 Rendimiento

#### CAR-REN-0001 — El sistema debe conservar tiempos de respuesta aceptables en las operaciones principales, incluso al aumentar la carga de usuarios

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-REN-0001 | Quiero que si el número de usuarios diarios sube mucho la plataforma siga demorando menos de 3s al realizar cualquier operación | 1 | 1 | 0 | 2 |
| ESC-CAL-REN-0002 | Las acciones de aceptar, rechazar o responder una propuesta reflejan de inmediato la nueva interfaz | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0003 | Durante periodos de alta actividad, iniciar sesión y acceder a la pantalla principal debe conservar tiempos de respuesta aceptables | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0004 | Quiero que los usuarios no tengan congelamientos en pantallas que tengan muchas imágenes o contenido | 1 | 1 | 0 | 2 |
| ESC-CAL-REN-0005 | La consulta del detalle de una publicación responde rápidamente incluso cuando contiene varias imágenes | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0006 | Al cargar el listado principal de publicaciones, el usuario debe comenzar a visualizar contenido sin esperar a que todas las imágenes terminen de cargarse completamente | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0007 | La aplicación de filtros sobre un conjunto grande de publicaciones debe actualizar los resultados sin producir bloqueos prolongados de la interfaz | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0008 | Consultar las negociaciones activas de un usuario debe mantenerse rápido, aunque este acumule un historial considerable de operaciones anteriores | 0 | 0 | 0 | 0 |

#### CAR-REN-0004 — La comunicación durante una negociación debe percibirse como inmediata entre las partes

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-REN-0009 | Cargar la información principal de un chat no debe retrasarse significativamente debido a la existencia de mensajes antiguos | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0010 | Los mensajes enviados durante una negociación deben estar disponibles para la otra parte en un máximo de 2 segundos bajo la carga objetivo. | 2 | 2 | 1 | 5 |

#### CAR-REN-0005 — Las cargas generadas por funciones administrativas no deben afectar el rendimiento de la operación normal de los usuarios

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-REN-0011 | Las funciones administrativas de consulta no deben generar cargas tan pesadas que ralenticen las operaciones normales de compradores y vendedores | 0 | 0 | 0 | 0 |

### 4.9 Interoperabilidad

#### CAR-INT-0001 — La plataforma debe poder incorporar nuevos proveedores externos apoyándose en protocolos estándares y con bajo impacto sobre el resto del sistema

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-INT-0001 | Quiero que el sistema se pueda integrar fácilmente con nuevos servicios de pago que vayan saliendo en el tiempo | 0 | 0 | 0 | 0 |

#### CAR-INT-0002 — La lógica central del negocio no debe depender de la implementación ni de la versión específica de un proveedor externo

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-INT-0002 | DAZMA debe poder intercambiar la información requerida con proveedores externos mediante contratos o protocolos soportados sin acoplar las reglas centrales del negocio a una implementación concreta. | 0 | 1 | 2 | 3 |
| ESC-CAL-INT-0003 | Incorporar posteriormente un nuevo proveedor de notificaciones no debe requerir modificar las reglas centrales de publicaciones, compras o intercambios | 0 | 0 | 0 | 0 |
| ESC-CAL-INT-0004 | La lógica de negociación no debe depender de alguna implementación específica de una pasarela de pago | 0 | 0 | 0 | 0 |
| ESC-CAL-INT-0005 | Si cambia el formato o versión de una integración externa, el impacto debe quedar limitado principalmente al mecanismo encargado de comunicarse con dicho proveedor | 0 | 0 | 0 | 0 |

#### CAR-INT-0003 — La plataforma debe procesar de forma confiable los mensajes que recibe de proveedores externos, incluidos duplicados, errores y fallas temporales

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-INT-0006 | Si un proveedor externo envía dos veces la misma notificación de una operación, la plataforma debe reconocerla correctamente sin producir dos efectos de negocio | 0 | 0 | 0 | 0 |

#### CAR-INT-0004 — La información intercambiada con servicios externos debe poder relacionarse correctamente con la operación interna que le corresponde

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-INT-0007 | Si un proveedor externo devuelve un error, la plataforma debe poder interpretar dicho resultado y mantener un estado interno coherente de la operación | 0 | 0 | 0 | 0 |
| ESC-CAL-INT-0008 | La falla temporal del servicio utilizado para enviar correos electrónicos no debe provocar la pérdida definitiva de las notificaciones que faltan por entregarse | 0 | 0 | 0 | 0 |
| ESC-CAL-INT-0009 | La información intercambiada entre los servicios externos y la plataforma deben poder relacionarse claramente con la operación interna de DAZMA | 0 | 0 | 0 | 0 |

### 4.10 Capacidad

#### CAR-CAP-0001 — La plataforma debe soportar el volumen de usuarios, negociaciones y conversaciones concurrentes definido por el negocio para cada etapa

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CAP-0001 | La plataforma soporta el volumen esperado de negociaciones concurrentes | 0 | 0 | 0 | 0 |

#### CAR-CAP-0002 — La plataforma debe soportar el volumen previsto de publicaciones e imágenes almacenadas sin agotar sus recursos

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CAP-0002 | DAZMA debe poder soportar el volumen máximo de usuarios concurrentes que el negocio determine para cada etapa de crecimiento | 0 | 0 | 2 | 2 |
| ESC-CAL-CAP-0003 | El chat debe poder atender el volumen de conversaciones simultáneas esperado durante los periodos de mayor utilización | 0 | 0 | 0 | 0 |
| ESC-CAL-CAP-0004 | El sistema soporta el volumen objetivo de publicaciones activas sin dejar de operar correctamente | 0 | 0 | 0 | 0 |

#### CAR-CAP-0003 — El sistema debe detectar la proximidad a sus límites operativos antes de alcanzar un punto de saturación

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CAP-0005 | La plataforma debe poder almacenar el volumen de publicaciones e imágenes previsto sin impedir nuevas publicaciones por agotamientos inesperados de recursos | 0 | 0 | 0 | 0 |
| ESC-CAL-CAP-0006 | El sistema debe identificar cuando se aproxima a ciertos limites operativos antes de llegar a un punto de saturación | 0 | 0 | 0 | 0 |

#### CAR-CAP-0004 — La capacidad de la plataforma debe poder verificarse y ampliarse de forma controlada antes de un crecimiento previsto

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-CAP-0007 | El volumen de operaciones económicas simultaneas no debe superar la capacidad del sistema sin que exista alguna forma de detectarlo | 0 | 0 | 0 | 0 |
| ESC-CAL-CAP-0008 | El sistema debe activar automáticamente más servidores para recibir más peticiones en picos de tráfico | 0 | 0 | 0 | 0 |
| ESC-CAL-CAP-0009 | Antes de ampliar considerablemente la cantidad de usuarios, se debe poder comprobar mediante pruebas cuál es el volumen concurrente que la plataforma puede soportar | 0 | 0 | 0 | 0 |

### 4.11 Portabilidad

#### CAR-POR-0001 — La plataforma debe ser accesible desde cualquier dispositivo con navegador web, sin requerir componentes propios instalados

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-POR-0001 | Quiero que una persona desde cualquier dispositivo de TI con un navegador web pueda acceder a la página sin limitaciones significativas | 0 | 0 | 0 | 0 |

#### CAR-POR-0002 — El comportamiento funcional de la plataforma debe conservarse en cualquier entorno soportado, variando únicamente su configuración

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-POR-0002 | La plataforma no debe depender de extensiones propias instaladas en el navegador del usuario para ejecutar sus funcionalidades principales | 0 | 0 | 0 | 0 |
| ESC-CAL-POR-0003 | La configuración especifica de cada ambiente debe poder cambiar sin requerir modificar el comportamiento funcional de la plataforma | 0 | 0 | 0 | 0 |
| ESC-CAL-POR-0004 | Las funcionalidades principales deben conservar el mismo comportamiento independientemente del entorno en que se encuentre ejecutándose la plataforma, siempre y cuando este cumpla con las condiciones soportadas | 0 | 0 | 0 | 0 |

#### CAR-POR-0003 — Los servicios de infraestructura deben poder sustituirse sin modificar las reglas de publicaciones, negociación o reputación

| Código escenario | Descripción del escenario | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-POR-0005 | Cambiar determinados servicios de infraestructura no obliga a modificar directamente las reglas de publicaciones, negociación o reputación | 0 | 0 | 0 | 0 |

## 5. Resultado de Ronda 1

| Código escenario | Atributo | Comprador | Vendedor | Administrador | Total |
|---|---|---:|---:|---:|---:|
| ESC-CAL-SEG-0002 | Seguridad | 1 | 1 | 1 | 3 |
| ESC-CAL-SEG-0004 | Seguridad | 2 | 2 | 2 | 6 |
| ESC-CAL-SEG-0007 | Seguridad | 1 | 1 | 1 | 3 |
| ESC-CAL-SEG-0008 | Seguridad | 2 | 1 | 2 | 5 |
| ESC-CAL-CON-0001 | Confiabilidad | 2 | 2 | 1 | 5 |
| ESC-CAL-CON-0005 | Confiabilidad | 2 | 2 | 1 | 5 |
| ESC-CAL-CON-0010 | Confiabilidad | 2 | 1 | 2 | 5 |
| ESC-CAL-CON-0011 | Confiabilidad | 1 | 1 | 2 | 4 |
| ESC-CAL-AUD-0008 | Capacidad para ser auditado | 1 | 2 | 2 | 5 |
| ESC-CAL-DIS-0005 | Disponibilidad | 1 | 1 | 1 | 3 |
| ESC-CAL-DIS-0006 | Disponibilidad | 1 | 1 | 2 | 4 |
| ESC-CAL-USA-0006 | Usabilidad UX | 2 | 2 | 1 | 5 |
| ESC-CAL-USA-0009 | Usabilidad UX | 1 | 1 | 1 | 3 |
| ESC-CAL-USA-0014 | Usabilidad UX | 1 | 1 | 0 | 2 |
| ESC-CAL-ESC-0003 | Escalabilidad | 1 | 1 | 1 | 3 |
| ESC-CAL-REN-0001 | Rendimiento | 1 | 1 | 0 | 2 |
| ESC-CAL-REN-0004 | Rendimiento | 1 | 1 | 0 | 2 |
| ESC-CAL-REN-0010 | Rendimiento | 2 | 2 | 1 | 5 |
| ESC-CAL-INT-0002 | Interoperabilidad | 0 | 1 | 2 | 3 |
| ESC-CAL-CAP-0002 | Capacidad | 0 | 0 | 2 | 2 |

### 5.1 Especificación de los escenarios que pasan a Ronda 2

#### ESC-CAL-SEG-0002

| Campo | Definición |
|---|---|
| Nombre | Si un usuario deja su sesión abierta durante un periodo prolongado de inactividad, el sistema debe evitar que otra persona pueda continuar utilizando indefinidamente esa sesión |
| Fuente del estímulo | Usuario con una sesión autenticada. |
| Estímulo | Permanece inactivo durante un periodo superior al umbral de inactividad configurado. |
| Ambiente | Operación normal con una sesión iniciada. |
| Artefacto | Gestión del ciclo de vida de sesiones. |
| Respuesta | El sistema detecta la inactividad, invalida la sesión y exige una nueva autenticación para continuar. |
| Métrica | El 100% de las sesiones evaluadas se invalida al superar el umbral configurado y no permite continuar sin una nueva autenticación. |

#### ESC-CAL-SEG-0004

| Campo | Definición |
|---|---|
| Nombre | El sistema debe impedir que usuarios no autorizados accedan, consulten o modifiquen información y recursos que no les pertenecen. |
| Fuente del estímulo | Usuario autenticado sin autorización suficiente. |
| Estímulo | Intenta consultar, modificar o ejecutar una acción sobre un recurso ajeno o para el cual no tiene permiso. |
| Ambiente | Operación normal, con una identidad válida pero sin autorización sobre el recurso solicitado. |
| Artefacto | Controles de autorización y recursos protegidos de DAZMA. |
| Respuesta | El sistema valida permisos y propiedad del recurso, rechaza la operación y no expone ni modifica información protegida. |
| Métrica | El 100% de las solicitudes no autorizadas evaluadas son rechazadas, con 0 modificaciones y 0 exposición de información protegida. |

#### ESC-CAL-SEG-0007

| Campo | Definición |
|---|---|
| Nombre | Yo como comprador quiero que mi dinero se retenga en la aplicación hasta confirmar que el encuentro fue exitoso |
| Fuente del estímulo | Comprador que realiza un pago asociado a una compra o a un encime. |
| Estímulo | Completa el pago de una operación que requiere retención hasta la confirmación definida para la negociación. |
| Ambiente | Operación normal de una negociación aceptada. |
| Artefacto | Flujo de retención, liberación y reversión del pago. |
| Respuesta | La operación conserva el dinero retenido hasta que se cumplan las condiciones válidas de liberación o reversión definidas por el negocio. |
| Métrica | En el 100% de los casos el dinero no se libera antes de la condición válida de liberación y se aplica la reversión cuando corresponde. |

#### ESC-CAL-SEG-0008

| Campo | Definición |
|---|---|
| Nombre | Las operaciones críticas relacionadas con cuentas, negociaciones, pagos y acciones administrativas deben ejecutarse únicamente con identidad y permisos válidos, sin exponer información secreta. |
| Fuente del estímulo | Usuario, cliente o actor que intenta ejecutar una operación crítica. |
| Estímulo | Solicita una operación crítica con credenciales inválidas, permisos insuficientes o una petición manipulada. |
| Ambiente | Operación normal o intento de uso indebido de una función crítica. |
| Artefacto | Frontera de seguridad de las operaciones críticas y manejo de secretos. |
| Respuesta | DAZMA valida autenticación, autorización y datos de entrada; rechaza la operación cuando no cumple las políticas y evita exponer credenciales, tokens o secretos. |
| Métrica | El 100% de las solicitudes críticas no autorizadas son rechazadas y se exponen 0 credenciales, tokens o secretos en las respuestas y registros evaluados.|

#### ESC-CAL-CON-0001

| Campo | Definición |
|---|---|
| Nombre | Si ocurre una falla mientras se procesa una negociación o una operación económica, el sistema debe conservar un único estado válido sin cambios parciales ni contradictorios. |
| Fuente del estímulo | Falla de software, infraestructura o conectividad durante una operación crítica. |
| Estímulo | Se interrumpe el procesamiento mientras cambia el estado de una negociación o de una operación económica. |
| Ambiente | Operación degradada o con una falla inducida durante el procesamiento. |
| Artefacto | Gestión de estado de negociaciones y operaciones económicas. |
| Respuesta | El sistema confirma completamente un estado válido o conserva/revierte al último estado consistente, evitando cambios parciales. |
| Métrica | El 100% de los casos evaluados termina en uno de los estados válidos definidos, sin cambios parciales ni duplicados. |

#### ESC-CAL-CON-0005

| Campo | Definición |
|---|---|
| Nombre | La recepción repetida de una misma notificación válida de pago debe producir un único efecto económico y un único cambio válido de estado. |
| Fuente del estímulo | Proveedor externo de pagos. |
| Estímulo | Envía dos o más veces la misma notificación correspondiente a una única operación. |
| Ambiente | Operación normal de la integración, incluyendo reintentos del proveedor. |
| Artefacto | Procesamiento de eventos y notificaciones de pago. |
| Respuesta | DAZMA reconoce que el evento ya fue procesado y evita repetir el efecto económico o el cambio de estado. |
| Métrica | Al reenviar N veces el mismo evento identificado, se produce exactamente 1 cobro y 1 transición válida asociada a la operación. |

#### ESC-CAL-CON-0010

| Campo | Definición |
|---|---|
| Nombre | Si la plataforma presenta señales de saturación o degradación que puedan provocar una falla, deben detectarse y mitigarse antes de que el servicio se interrumpa. |
| Fuente del estímulo | Sistema e infraestructura de operación. |
| Estímulo | Uno o varios recursos críticos superan el umbral preventivo definido y continúan acercándose a su límite operativo. |
| Ambiente | Operación normal o periodos de alta demanda. |
| Artefacto | Plataforma DAZMA y recursos que soportan su operación. |
| Respuesta | El sistema detecta la condición de riesgo, genera una alerta y ejecuta o habilita las acciones de mitigación previstas antes de que la degradación provoque una interrupción. |
| Métrica | Al menos el 95% de las condiciones de saturación definidas son detectadas y mitigadas antes de provocar una indisponibilidad del servicio |

#### ESC-CAL-CON-0011

| Campo | Definición |
|---|---|
| Nombre | Si un componente o servicio secundario o externo presenta una falla, esta debe quedar contenida y los flujos críticos que no dependan de él deben continuar operando. |
| Fuente del estímulo | Componente interno o servicio externo que presenta una falla. |
| Estímulo | El componente deja de responder o falla durante la operación. |
| Ambiente | Operación normal con una falla parcial. |
| Artefacto | Componentes afectados y flujos críticos independientes de ellos. |
| Respuesta | DAZMA contiene la falla, evita su propagación y mantiene operativos los flujos críticos que no dependan del componente afectado. |
| Métrica | El 100% de las fallas parciales definidas no provoca una caída general y los flujos críticos no dependientes permanecen operativos.|

#### ESC-CAL-AUD-0008

| Campo | Definición |
|---|---|
| Nombre | Ante una reclamación, un usuario autorizado debe poder reconstruir cronológicamente los eventos críticos ocurridos durante una negociación y sus operaciones relacionadas. |
| Fuente del estímulo | Administrador o usuario autorizado para investigar una reclamación. |
| Estímulo | Solicita reconstruir una negociación que registró múltiples cambios de estado y operaciones relacionadas. |
| Ambiente | Después de que la operación ocurrió, durante una reclamación, disputa o investigación. |
| Artefacto | Historial de negociación y registros de auditoría. |
| Respuesta | El sistema presenta una secuencia cronológica con actor, fecha y hora, acción, resultado y relación con la operación correspondiente. |
| Métrica | El 100% de los eventos críticos aparece correctamente ordenado y relacionado, y ninguno puede modificarse mediante las funciones normales de administración. |

#### ESC-CAL-DIS-0005

| Campo | Definición |
|---|---|
| Nombre | Si un servicio crítico deja de responder, la plataforma informa la falta de servicio sin dejar al usuario en una pantalla bloqueada indefinidamente |
| Fuente del estímulo | Servicio crítico interno o externo. |
| Estímulo | No responde dentro del tiempo esperado. |
| Ambiente | Operación normal con indisponibilidad parcial. |
| Artefacto | Flujo que depende del servicio y su interacción con el usuario. |
| Respuesta | El sistema finaliza la espera de forma controlada, informa la indisponibilidad y conserva un estado válido de la operación. |
| Métrica | El 100% de los timeouts simulados termina con una respuesta controlada, sin espera indefinida ni pérdida o corrupción del estado. |

#### ESC-CAL-DIS-0006

| Campo | Definición |
|---|---|
| Nombre | Durante despliegues de nuevas versiones o mantenimientos planificados, la indisponibilidad de los flujos críticos de DAZMA debe mantenerse dentro del máximo definido. |
| Fuente del estímulo | Equipo de desarrollo u operaciones. |
| Estímulo | Realiza un despliegue, actualización o mantenimiento planificado sobre la plataforma. |
| Ambiente | Producción, dentro de una ventana de cambio planificada. |
| Artefacto | Flujos críticos de DAZMA. |
| Respuesta | El cambio se realiza manteniendo los flujos críticos accesibles o limitando su interrupción al máximo permitido. |
| Métrica | El tiempo total de indisponibilidad de los flujos críticos durante la ventana planificada debe ser ≤ X, valor que debe definirse y validarse antes de producción. |

#### ESC-CAL-USA-0006

| Campo | Definición |
|---|---|
| Nombre | Antes de decidir sobre una propuesta o contraoferta, el usuario debe comprender claramente qué entrega, qué recibe y si existe dinero adicional involucrado. |
| Fuente del estímulo | Comprador o vendedor participante de una negociación. |
| Estímulo | Consulta o prepara una propuesta o contraoferta para decidir si continuar con la negociación. |
| Ambiente | Negociación activa, incluyendo usuarios con poca experiencia previa en la plataforma. |
| Artefacto | Interfaz de propuestas y contraofertas. |
| Respuesta | DAZMA diferencia visual y textualmente lo que entrega cada parte, lo que recibe, el dinero adicional involucrado y el estado actual de la propuesta. |
| Métrica | Al menos el 90% de los participantes identifica correctamente qué entrega, qué recibe y si existe dinero adicional, sin asistencia externa. |

#### ESC-CAL-USA-0009

| Campo | Definición |
|---|---|
| Nombre | Los mensajes de error deben ser suficientemente claros para los usuarios, con descripciones que realmente hagan entender al usuario qué provocó el error |
| Fuente del estímulo | Usuario de la plataforma. |
| Estímulo | Comete un error de entrada o ejecuta una acción que no puede completarse. |
| Ambiente | Uso normal de formularios y flujos interactivos. |
| Artefacto | Interfaz en la que se produce el error. |
| Respuesta | El sistema explica el problema en lenguaje comprensible, identifica qué debe corregirse y orienta al usuario sobre cómo continuar. |
| Métrica | Al menos el 90% de los participantes identifica la causa del error y logra corregirlo sin asistencia externa. |

#### ESC-CAL-USA-0014

| Campo | Definición |
|---|---|
| Nombre | Antes de confirmar una compra, intercambio o acción importante, el usuario debe comprender claramente qué ocurrirá después de confirmar |
| Fuente del estímulo | Usuario que está a punto de confirmar una acción de alto impacto. |
| Estímulo | Intenta confirmar una compra, intercambio, cancelación, entrega u otra acción crítica. |
| Ambiente | Operación normal de la plataforma. |
| Artefacto | Interfaz de confirmación de acciones importantes. |
| Respuesta | El sistema resume la acción y sus consecuencias antes de ejecutarla y permite al usuario cancelar si detecta un error. |
| Métrica | Al menos el 90% de los participantes identifica correctamente la acción y su consecuencia antes de confirmarla. |

#### ESC-CAL-ESC-0003

| Campo | Definición |
|---|---|
| Nombre | Ante un incremento temporal importante de usuarios y solicitudes, DAZMA debe absorber la carga o degradarse de forma controlada sin una caída general. |
| Fuente del estímulo | Conjunto elevado de usuarios concurrentes. |
| Estímulo | La cantidad de solicitudes aumenta rápidamente hasta el volumen pico objetivo definido para la etapa de operación. |
| Ambiente | Periodo de demanda significativamente superior a la operación habitual. |
| Artefacto | Plataforma DAZMA y recursos que atienden las solicitudes. |
| Respuesta | La plataforma utiliza capacidad adicional disponible o aplica mecanismos de control de carga, manteniendo operativos los flujos críticos sin una caída general. |
| Métrica | En el volumen pico objetivo se presentan 0 caídas generales y los flujos críticos(operaciones más importantes de la plataforma) permanecen dentro de los límites de error y tiempo acordados |

#### ESC-CAL-REN-0001

| Campo | Definición |
|---|---|
| Nombre | Quiero que si el número de usuarios diarios sube mucho la plataforma siga demorando menos de 3s al realizar cualquier operación |
| Fuente del estímulo | Usuarios concurrentes. |
| Estímulo | Aumentan considerablemente las solicitudes sobre las operaciones principales a la plataforma |
| Ambiente | Periodo de alta carga dentro del volumen objetivo. |
| Artefacto | Operaciones principales de la plataforma. |
| Respuesta | El sistema procesa las solicitudes manteniendo el tiempo de respuesta establecido para las operaciones principales evaluadas. |
| Métrica | Las operaciones principales evaluadas responden en un tiempo menor o igual a 3 segundos bajo la carga objetivo definida. |

#### ESC-CAL-REN-0004

| Campo | Definición |
|---|---|
| Nombre | Quiero que los usuarios no tengan congelamientos en pantallas que tengan muchas imágenes o contenido |
| Fuente del estímulo | Usuario de la plataforma. |
| Estímulo | Accede a una pantalla con una cantidad elevada de imágenes o contenido. |
| Ambiente | Operación normal |
| Artefacto | Interfaz de listado y detalle de publicaciones. |
| Respuesta | El sistema presenta el contenido sin bloquear la interacción y mantiene disponible la navegación mientras se completa la carga. |
| Métrica | La interfaz permanece interactiva y las acciones evaluadas responden dentro del umbral establecido, sin bloqueos prolongados.|

#### ESC-CAL-REN-0010

| Campo | Definición |
|---|---|
| Nombre | Los mensajes enviados durante una negociación deben estar disponibles para la otra parte en un máximo de 2 segundos bajo la carga objetivo. |
| Fuente del estímulo | Comprador o vendedor participante de una negociación. |
| Estímulo | Envía un mensaje dentro del chat de una negociación activa. |
| Ambiente | Operación normal |
| Artefacto | Componente de mensajería o chat de DAZMA. |
| Respuesta | El sistema recibe, conserva y entrega el mensaje al destinatario sin bloquear el resto de la negociación. |
| Métrica | El tiempo extremo a extremo para que el mensaje esté disponible para la otra parte es ≤ 2 segundos. |

#### ESC-CAL-INT-0002

| Campo | Definición |
|---|---|
| Nombre | DAZMA debe poder intercambiar la información requerida con proveedores externos mediante contratos o protocolos soportados sin acoplar las reglas centrales del negocio a una implementación concreta. |
| Fuente del estímulo | Equipo de integración o proveedor externo compatible. |
| Estímulo | Se incorpora o actualiza una integración necesaria para pagos, autenticación u otro servicio externo. |
| Ambiente | Evolución normal de la plataforma con un proveedor que cumple los contratos o protocolos soportados. |
| Artefacto | Capa de integración entre DAZMA y servicios externos. |
| Respuesta | DAZMA intercambia la información requerida mediante contratos o protocolos definidos y mantiene las reglas centrales desacopladas de la implementación concreta del proveedor. |
| Métrica | El 98% de las operaciones de integración definidas para el proveedor soportado intercambia y mapea correctamente la información sin requerir modificaciones en las reglas centrales del dominio. |

#### ESC-CAL-CAP-0002

| Campo | Definición |
|---|---|
| Nombre | DAZMA debe poder soportar el volumen máximo de usuarios concurrentes que el negocio determine para cada etapa de crecimiento |
| Fuente del estímulo | Conjunto de usuarios concurrentes. |
| Estímulo | La concurrencia alcanza el volumen máximo que el negocio defina para una etapa de crecimiento. |
| Ambiente | Operación normal con alta demanda|
| Artefacto | Plataforma DAZMA y sus flujos críticos. |
| Respuesta | La plataforma continúa atendiendo el volumen objetivo sin una caída general ni agotamiento no controlado de sus recursos. |
| Métrica | DAZMA soporta el volumen concurrente de usuarios definido para la etapa y mantiene los flujos críticos dentro de los límites de error y tiempo acordados. |

## 6. Ronda 2

> **Dot Voting:** Se asignó un 33% del total de ideas a cada actor, lo que da 6.66 ideas a cada uno, quedando finalmente 7 puntos para cada actor, donde este puede dar un máximo de 2 puntos en caso de importancia máxima en cierta idea


| Código escenario | Comprador | Vendedor | Administrador | Total |
|---|---:|---:|---:|---:|
| ESC-CAL-SEG-0002 | 0 | 0 | 0 | 0 |
| ESC-CAL-SEG-0004 | 1 | 1 | 0 | 2 |
| ESC-CAL-SEG-0007 | 0 | 0 | 0 | 0 |
| ESC-CAL-SEG-0008 | 1 | 0 | 1 | 2 |
| ESC-CAL-CON-0001 | 1 | 1 | 0 | 2 |
| ESC-CAL-CON-0005 | 1 | 2 | 0 | 3 |
| ESC-CAL-CON-0010 | 0 | 0 | 2 | 2 |
| ESC-CAL-CON-0011 | 0 | 1 | 1 | 2 |
| ESC-CAL-AUD-0008 | 0 | 0 | 1 | 1 |
| ESC-CAL-DIS-0005 | 0 | 0 | 0 | 0 |
| ESC-CAL-DIS-0006 | 0 | 0 | 1 | 1 |
| ESC-CAL-USA-0006 | 1 | 1 | 0 | 2 |
| ESC-CAL-USA-0009 | 0 | 0 | 0 | 0 |
| ESC-CAL-USA-0015 | 0 | 0 | 0 | 0 |
| ESC-CAL-ESC-0003 | 1 | 0 | 0 | 1 |
| ESC-CAL-REN-0001 | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0004 | 0 | 0 | 0 | 0 |
| ESC-CAL-REN-0010 | 1 | 1 | 0 | 2 |
| ESC-CAL-INT-0002 | 0 | 0 | 1 | 1 |
| ESC-CAL-CAP-0002 | 0 | 0 | 0 | 0 |

**Total de votos de Ronda 2:** 7 Comprador + 7 Vendedor + 7 Administrador = **21 puntos**.

### 6.1 Escenarios seleccionados en Ronda 2

Los **12 escenarios con al menos un punto** coinciden con los 12 registros de la hoja **Escenarios de Calidad finales**.

| Código escenario | Total Ronda 2 |
|---|---:|
| ESC-CAL-SEG-0004 | 2 |
| ESC-CAL-SEG-0008 | 2 |
| ESC-CAL-CON-0001 | 2 |
| ESC-CAL-CON-0005 | 3 |
| ESC-CAL-CON-0010 | 2 |
| ESC-CAL-CON-0011 | 2 |
| ESC-CAL-AUD-0008 | 1 |
| ESC-CAL-DIS-0006 | 1 |
| ESC-CAL-USA-0006 | 2 |
| ESC-CAL-ESC-0003 | 1 |
| ESC-CAL-REN-0010 | 2 |
| ESC-CAL-INT-0002 | 1 |

## 7. Escenarios de calidad finales

### ESC-CAL-SEG-0004
El sistema debe impedir que usuarios no autorizados accedan, consulten o modifiquen información y recursos que no les pertenecen.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-SEG-0004 |
| Nombre | El sistema debe impedir que usuarios no autorizados accedan, consulten o modifiquen información y recursos que no les pertenecen. |
| Quality Attribute | Seguridad |
| Priority | Alta |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Usuario autenticado sin autorización suficiente. |
| Estímulo | Intentar consultar, modificar o ejecutar una acción sobre un recurso ajeno o para el cual no tiene permiso. |
| Ambiente | Operación normal |
| Artefacto | Módulo de autorización |
| Respuesta | El sistema valida permisos y propiedad del recurso, rechaza la operación y no expone ni modifica información protegida. |
| Métrica | El 100% de las solicitudes no autorizadas evaluadas son rechazadas, con 0 modificaciones y 0 exposición de información protegida. |
| Business Rationale | Sin este escenario podrían producirse accesos indebidos, modificaciones sobre publicaciones o negociaciones ajenas, fuga de información y escalamiento de privilegios. |
| Architectural Tactics | Autorización del lado del servidor; principio de mínimo privilegio; deny-by-default; RBAC/ABAC o validación de propiedad; políticas de autorización centralizadas; pruebas de control de acceso. |
| Assumptions & Risks | Se asume que los roles, permisos y reglas de propiedad estarán definidos y que todas las operaciones protegidas pasarán por el mecanismo de autorización. |

### ESC-CAL-SEG-0008
 Las operaciones críticas relacionadas con cuentas, negociaciones, pagos y acciones administrativas deben ejecutarse únicamente con identidad y permisos válidos, sin exponer información secreta.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-SEG-0008 |
| Nombre | Las operaciones críticas relacionadas con cuentas, negociaciones, pagos y acciones administrativas deben ejecutarse únicamente con identidad y permisos válidos, sin exponer información secreta. |
| Quality Attribute | Seguridad |
| Priority | Alta |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Un usuario |
| Estímulo | Solicitar una operación crítica con credenciales inválidas, permisos insuficientes o una petición manipulada. |
| Ambiente | Operación normal |
| Artefacto | Módulo de autorización |
| Respuesta | El sistema valida autenticación, autorización y datos de entrada; rechaza la operación cuando no cumple las políticas y no expone credenciales, tokens o secretos. |
| Métrica | El 100% de las solicitudes críticas no autorizadas son rechazadas y se exponen 0 credenciales, tokens o secretos en las respuestas y registros evaluados. |
| Business Rationale | Sin este escenario un actor podría ejecutar acciones de alto impacto sin permiso o aprovechar información secreta expuesta para comprometer cuentas, negociaciones o pagos. |
| Architectural Tactics | Autenticación robusta; autorización centralizada; gestión segura de secretos; validación de entradas; cifrado en tránsito; sanitización de logs; controles OWASP; auditoría de operaciones críticas. |
| Assumptions & Risks | Se asume que existe un catálogo de operaciones críticas y de información secreta y que las integraciones externas permiten mecanismos de autenticación seguros. |

### ESC-CAL-CON-0001 
 Si ocurre una falla mientras se procesa una negociación o una operación económica, el sistema debe conservar un único estado válido sin cambios parciales ni contradictorios.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-CON-0001 |
| Nombre | Si ocurre una falla mientras se procesa una negociación o una operación económica, el sistema debe conservar un único estado válido sin cambios parciales ni contradictorios. |
| Quality Attribute | Confiabilidad |
| Priority | Alta |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Sistema |
| Estímulo | Interrumpir el procesamiento mientras cambia el estado de una negociación o de una operación económica. |
| Ambiente | Operación con problemas |
| Artefacto | Sistema completo |
| Respuesta | El sistema confirma completamente un estado válido o conserva/revierte al último estado consistente, evitando cambios parciales. |
| Métrica | El 95% de los casos evaluados termina en uno de los estados válidos definidos, sin cambios parciales ni duplicados. |
| Business Rationale | Sin este escenario una interrupción podría dejar negociaciones o movimientos económicos en estados ambiguos, producir inconsistencias y requerir correcciones manuales. |
| Architectural Tactics | Transacciones atómicas dentro de límites transaccionales locales; máquina de estados explícita; rollback ante fallos previos al commit; operaciones compensatorias para efectos ya confirmados en procesos distribuidos; persistencia durable de estados críticos y recuperación desde el último estado válido confirmado. |
| Assumptions & Risks | Se asume que pueden definirse formalmente las operaciones críticas y los estados válidos de cada negociación. |

### ESC-CAL-CON-0005
 La recepción repetida de una misma notificación válida de pago debe producir un único efecto económico y un único cambio válido de estado.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-CON-0005 |
| Nombre | La recepción repetida de una misma notificación válida de pago debe producir un único efecto económico y un único cambio válido de estado. |
| Quality Attribute | Confiabilidad |
| Priority | Alta |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Proveedor externo de pagos |
| Estímulo | Enviar dos o más veces la misma notificación correspondiente a una única operación. |
| Ambiente | Operación normal |
| Artefacto | Modulo de notificaciones y pagos |
| Respuesta | El sistema reconoce que el evento ya fue procesado y no repite el efecto económico o el cambio de estado. |
| Métrica | Al reenviar N veces el mismo evento identificado, se produce exactamente 1 efecto económico y 1 transición válida asociada a la operación. |
| Business Rationale | Sin este escenario un webhook repetido podría duplicar efectos económicos, estados o registros y generar pérdidas, reclamaciones y conciliaciones manuales. |
| Architectural Tactics | Idempotency keys para operaciones repetibles; identificadores únicos para eventos externos; registro persistente de eventos procesados; restricciones de unicidad para evitar duplicados concurrentes; procesamiento transaccional del evento y sus efectos locales; patrón Inbox para recepción confiable de eventos externos cuando la integración lo requiera. |
| Assumptions & Risks | Se asume que el proveedor entrega un identificador estable o que puede construirse una clave idempotente confiable y que los reintentos del proveedor son posibles. |

### ESC-CAL-CON-0010 
Si la plataforma presenta señales de saturación o degradación que puedan provocar una falla, deben detectarse y mitigarse antes de que el servicio se interrumpa.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-CON-0010 |
| Nombre | Si la plataforma presenta señales de saturación o degradación que puedan provocar una falla, deben detectarse y mitigarse antes de que el servicio se interrumpa. |
| Quality Attribute | Confiabilidad |
| Priority | Alta |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Muchos usuarios utilizando la plataforma |
| Estímulo | Uno o varios recursos críticos superan el umbral preventivo definido y continúan acercándose a su límite operativo. |
| Ambiente | Operación en momentos con alta demanda. |
| Artefacto | Sistema completo |
| Respuesta | El sistema detecta la condición de riesgo, genera una alerta y ejecuta o habilita las acciones de mitigación previstas antes de que la degradación provoque una interrupción. |
| Métrica | El 95% de las condiciones de saturación definidas son detectadas y mitigadas antes de provocar una indisponibilidad del servicio. |
| Business Rationale | Sin este escenario DAZMA podría alcanzar niveles críticos de utilización sin advertencia y el equipo técnico reaccionaría después de la caída, aumentando el impacto sobre usuarios y operaciones activas. |
| Architectural Tactics | Monitoreo proactivo de latencia, errores, saturación y salud de dependencias; métricas y dashboards para identificar tendencias; umbrales preventivos calibrados mediante pruebas; alertas accionables; Health Checks para detectar componentes degradados; Circuit Breaker para aislar dependencias fallidas; Rate Limiting para controlar consumo excesivo; Load Shedding para preservar flujos críticos bajo saturación; autoescalado cuando las métricas y la infraestructura demuestren que aumentar capacidad resuelve el cuello de botella; y mitigación automatizada únicamente para acciones seguras y previamente validadas. |
| Assumptions & Risks | Se asume que las fallas relevantes presentan señales observables, que los umbrales pueden calibrarse, que existe tiempo suficiente para actuar y que el objetivo del 95% es alcanzable; todo esto debe validarse. |

### ESC-CAL-CON-0011 
Si un componente o servicio secundario o externo presenta una falla, esta debe quedar contenida y los flujos críticos que no dependan de él deben continuar operando.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-CON-0011 |
| Nombre | Si un componente o servicio secundario o externo presenta una falla, esta debe quedar contenida y los flujos críticos que no dependan de él deben continuar operando. |
| Quality Attribute | Confiabilidad |
| Priority | Media |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Sistema |
| Estímulo | El componente deja de responder o falla durante la operación. |
| Ambiente | Operación normal con una falla parcial. |
| Artefacto | Sistema completo |
| Respuesta | El sistema contiene la falla, evita su propagación y mantiene operativos los flujos críticos que no dependan del componente afectado. |
| Métrica | El 95% de las fallas parciales definidas no provoca una caída general y los flujos críticos no dependientes permanecen operativos. |
| Business Rationale | Sin este escenario una falla secundaria podría propagarse y derribar funciones que técnicamente podrían continuar disponibles. |
| Architectural Tactics | Bulkhead para separar recursos y limitar la propagación de saturación; Circuit Breaker y timeouts para evitar dependencia prolongada de servicios degradados; aislamiento lógico y de recursos entre capacidades; graceful degradation para mantener operativos los flujos críticos cuando fallen funciones secundarias; colas para diferir tareas no críticas; Health Checks para detectar componentes no saludables; y fallbacks únicamente cuando exista una respuesta alternativa funcionalmente segura y consistente. |
| Assumptions & Risks | Se asume que las dependencias entre componentes están identificadas, que existe suficiente desacoplamiento y que se conocen con claridad los flujos que realmente pueden funcionar de manera independiente. |

### ESC-CAL-AUD-0008 
Ante una reclamación, un usuario autorizado debe poder reconstruir cronológicamente los eventos críticos ocurridos durante una negociación y sus operaciones relacionadas.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-AUD-0008 |
| Nombre | Ante una reclamación, un usuario autorizado debe poder reconstruir cronológicamente los eventos críticos ocurridos durante una negociación y sus operaciones relacionadas. |
| Quality Attribute | Capacidad para ser auditado |
| Priority | Alta |
| Difficulty / Risk | Media-Alta |
| Status | Final |
| Fuente del estímulo | Administrador |
| Estímulo | Solicita reconstruir una negociación que registró múltiples cambios de estado y operaciones relacionadas. |
| Ambiente | Operación normal |
| Artefacto | Historial de negociación y registros de auditoría. |
| Respuesta | El sistema presenta una secuencia cronológica con actor, fecha y hora, acción, resultado y relación con la operación correspondiente. |
| Métrica | El 95% de los eventos críticos aparece correctamente ordenado y relacionado, y ninguno puede modificarse mediante las funciones normales de administración. |
| Business Rationale | Sin este escenario las reclamaciones dependerían de versiones de los usuarios o de registros incompletos, dificultando determinar responsabilidades y resolver disputas. |
| Architectural Tactics | Audit trail de negocio append-only para eventos críticos; identificadores de correlación para relacionar acciones de un mismo flujo; timestamps consistentes y sincronizados; esquema común de eventos auditables; almacenamiento con acceso restringido y mínimo privilegio; centralización de logs técnicos para análisis transversal; y políticas diferenciadas de retención según el tipo y sensibilidad de la información. |
| Assumptions & Risks | Se asume que se define qué eventos son críticos, que los relojes de los componentes están sincronizados, que existe una política de retención y que se conocen los roles autorizados para consultar la evidencia. |

### ESC-CAL-DIS-0006 
Durante despliegues de nuevas versiones o mantenimientos planificados, la indisponibilidad de los flujos críticos de DAZMA debe mantenerse dentro del máximo definido.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-DIS-0006 |
| Nombre | Durante despliegues de nuevas versiones o mantenimientos planificados, la indisponibilidad de los flujos críticos de DAZMA debe mantenerse dentro del máximo definido. |
| Quality Attribute | Disponibilidad |
| Priority | Media |
| Difficulty / Risk | Media-Alta |
| Status | Final |
| Fuente del estímulo | Equipo de desarrollo |
| Estímulo | Realiza un despliegue, actualización o mantenimiento planificado sobre la plataforma. |
| Ambiente | Operación normal con lanzamiento de nuevas funciones |
| Artefacto | Sistema completo |
| Respuesta | El cambio se realiza manteniendo los flujos críticos accesibles o limitando su interrupción al máximo permitido. |
| Métrica | El tiempo total de indisponibilidad de los flujos críticos durante la ventana planificada debe ser ≤ X, valor que debe definirse y validarse antes de producción. |
| Business Rationale | Sin este escenario cada despliegue o mantenimiento podría provocar interrupciones extensas aunque no exista una falla inesperada. |
| Architectural Tactics | Rolling Deployment como estrategia inicial de despliegue gradual; Blue/Green o Canary cuando el riesgo del cambio o el objetivo de disponibilidad lo justifiquen; balanceo de carga para retirar e incorporar instancias sin interrumpir el servicio; Health/Readiness Checks antes de recibir tráfico; rollback verificable de versiones; migraciones de datos compatibles con versiones coexistentes; y ventanas de mantenimiento planificadas cuando una operación no pueda realizarse sin interrupción |
| Assumptions & Risks | Se asume que la arquitectura e infraestructura seleccionadas soportarán despliegues graduales y que el valor máximo X de indisponibilidad todavía debe ser definido. |

### ESC-CAL-USA-0006 
Antes de decidir sobre una propuesta o contraoferta, el usuario debe comprender claramente qué entrega, qué recibe y si existe dinero adicional involucrado.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-USA-0006 |
| Nombre | Antes de decidir sobre una propuesta o contraoferta, el usuario debe comprender claramente qué entrega, qué recibe y si existe dinero adicional involucrado. |
| Quality Attribute | Usabilidad UX |
| Priority | Media |
| Difficulty / Risk | Media-Alta |
| Status | Final |
| Fuente del estímulo | Usuario participante de una negociación. |
| Estímulo | Consulta o prepara una propuesta o contraoferta para continuar con la negociación. |
| Ambiente | Operación normal |
| Artefacto | Interfaz de propuestas y contraofertas. |
| Respuesta | El sistema diferencia visual y textualmente lo que entrega cada parte, lo que recibe, el dinero adicional involucrado y el estado actual de la propuesta. |
| Métrica | Al menos el 90% de los participantes identifica correctamente qué entrega, qué recibe y si existe dinero adicional, sin asistencia externa. |
| Business Rationale | Sin este escenario un usuario podría aceptar una operación diferente a la que cree estar negociando, generando disputas, cancelaciones y pérdida de confianza. |
| Architectural Tactics | Jerarquía visual de la información crítica; agrupación por proximidad de elementos relacionados; etiquetas explícitas para diferenciar lo que el usuario entrega, recibe y paga; resumen de la propuesta antes de decisiones relevantes; consistencia terminológica y visual entre pantallas; aplicación de heurísticas de Nielsen y principios de percepción/diseño visual apoyados en Kolenda; y pruebas iterativas de usabilidad con usuarios representativos. |
| Assumptions & Risks | Se asume que el 90% es una meta inicial que debe validarse, que se dispone de usuarios representativos y que la terminología de intercambio y encime será comprendida o explicada. |

### ESC-CAL-ESC-0003
 Ante un incremento temporal importante de usuarios y solicitudes, DAZMA debe absorber la carga o degradarse de forma controlada sin una caída general.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-ESC-0003 |
| Nombre | Ante un incremento temporal importante de usuarios y solicitudes, DAZMA debe absorber la carga o degradarse de forma controlada sin una caída general. |
| Quality Attribute | Escalabilidad |
| Priority | Media |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Conjunto elevado de usuarios durante un momento específico |
| Estímulo | La cantidad de solicitudes aumenta rápidamente hasta el volumen pico objetivo definido para la etapa de operación. |
| Ambiente | Operación normal con alta demanda |
| Artefacto | Sistema completo |
| Respuesta | La plataforma utiliza capacidad adicional disponible o aplica mecanismos de control de carga, manteniendo operativos los flujos críticos sin una caída general. |
| Métrica | En un volumen pico objetivo se presentan 0 caídas y los flujos críticos permanecen funcionales dentro del tiempo de "colapso" |
| Business Rationale | Sin este escenario un evento de alta demanda podría derribar la plataforma completa e impedir operaciones precisamente cuando existe mayor oportunidad de uso. |
| Architectural Tactics | Escalamiento horizontal de componentes que puedan replicarse; balanceo de carga para distribuir solicitudes entre instancias; diseño stateless donde el estado de sesión no requiera residir localmente; autoescalado condicionado a métricas de saturación y evidencia de pruebas; caché selectiva para lecturas repetitivas que toleren consistencia temporal; colas para desacoplar procesamiento asíncrono y absorber picos; backpressure para evitar acumulación ilimitada de trabajo; Rate Limiting para controlar consumo excesivo por cliente; y pruebas de carga y estrés para establecer capacidad, cuellos de botella y comportamiento bajo saturación. |
| Assumptions & Risks | Se asume que el volumen pico objetivo será definido, que la infraestructura permitirá ampliar capacidad y que la base de datos y proveedores externos no constituirán cuellos de botella imposibles de escalar. |

### ESC-CAL-REN-0010
 Los mensajes enviados durante una negociación deben estar disponibles para la otra parte en un máximo de 2 segundos bajo la carga objetivo.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-REN-0010 |
| Nombre | Los mensajes enviados durante una negociación deben estar disponibles para la otra parte en un máximo de 2 segundos bajo la carga objetivo. |
| Quality Attribute | Rendimiento |
| Priority | Baja |
| Difficulty / Risk | Media-Alta |
| Status | Final |
| Fuente del estímulo | Usuario participante de una negociación. |
| Estímulo | Envía un mensaje dentro del chat de una negociación activa. |
| Ambiente | Operación normal |
| Artefacto | Modulo de mensajería |
| Respuesta | El sistema entrega el mensaje al destinatario en el tiempo estimado |
| Métrica | El tiempo máximo para que el mensaje esté disponible para la otra parte es de 2 segundos. |
| Business Rationale | Sin este escenario los retrasos prolongados volverían la negociación frustrante, fomentarían reenvíos y podrían producir malentendidos o abandono de conversaciones. |
| Architectural Tactics | Canal de comunicación persistente para funcionalidades en tiempo cercano al real, como el chat; procesamiento asíncrono de tareas secundarias; colas o broker cuando se requiera desacoplamiento, buffering o fan-out; persistencia durable con índices diseñados según las consultas del historial; paginación del chat —preferiblemente basada en cursor cuando el volumen lo justifique; y monitoreo de latencia extremo a extremo con percentiles bajo la carga objetivo. |
| Assumptions & Risks | Se asume que el objetivo de 2 segundos se mantiene como requisito aceptado, que la carga objetivo será definida y que la red del usuario puede introducir latencia fuera del control directo de DAZMA. |

### ESC-CAL-INT-0002
 DAZMA debe poder intercambiar la información requerida con proveedores externos mediante contratos o protocolos soportados sin acoplar las reglas centrales del negocio a una implementación concreta.

| Campo | Contenido |
|---|---|
| Código Escenario | ESC-CAL-INT-0002 |
| Nombre | DAZMA debe poder intercambiar la información requerida con proveedores externos mediante contratos o protocolos soportados sin acoplar las reglas centrales del negocio a una implementación concreta. |
| Quality Attribute | Interoperabilidad |
| Priority | Media |
| Difficulty / Risk | Alta |
| Status | Final |
| Fuente del estímulo | Proveedor externo de pagos. |
| Estímulo | Se incorpora o actualiza una integración necesaria para pagos, autenticación u otro servicio externo. |
| Ambiente | Operación normal |
| Artefacto | Capa de integración entre DAZMA y servicios externos. |
| Respuesta | DAZMA intercambia la información requerida mediante contratos o protocolos definidos y mantiene las reglas centrales desacopladas de la implementación concreta del proveedor. |
| Métrica | El 95% de las operaciones de integración definidas para el proveedor soportado intercambia y mapea correctamente la información sin requerir modificaciones en las reglas centrales del dominio. |
| Business Rationale | Sin este escenario cada nuevo proveedor podría obligar a modificar lógica central de negocio, aumentando el acoplamiento, el costo de evolución y el riesgo de errores. |
| Architectural Tactics | Adapters para encapsular la comunicación con proveedores externos; Anti-Corruption Layer para traducir modelos, estados y errores externos sin contaminar el dominio; contratos de API explícitos y estrategia de versionado para cambios incompatibles; modelo canónico interno basado en los conceptos propios de DAZMA; Contract Testing y pruebas de integración para verificar la compatibilidad con proveedores; e identificadores de correlación para seguir una operación a través de múltiples sistemas. |
| Assumptions & Risks | Se asume que los proveedores exponen contratos suficientemente documentados, que existe un sandbox y que sus protocolos son compatibles con los mecanismos de integración seleccionados. |
