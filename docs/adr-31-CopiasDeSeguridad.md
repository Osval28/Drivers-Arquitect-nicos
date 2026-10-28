# ADR-31: Copias de seguridad, restauración y recuperación ante desastres

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-DIS-0006
- ESC-CAL-AUD-0008

## Contexto

La información de usuarios, negociaciones, pagos, auditoría y archivos puede perderse o quedar dañada por una falla, un error humano o una acción no deseada. Sin una forma comprobada de recuperar esos datos, una interrupción podría convertirse en una pérdida permanente o impedir la continuidad de las operaciones.

## Factores de decisión

- Las copias deben incluir la información necesaria para recuperar las operaciones críticas.
- Se debe definir cuánta información puede perderse y en cuánto tiempo debe restablecerse el servicio.
- Las copias deben estar protegidas y separadas del acceso normal de la aplicación.
- La restauración debe probarse periódicamente; una copia no verificada no garantiza recuperación.
- El costo debe corresponder al volumen y a la importancia de los datos.

## Opciones

- **A.** Copias manuales ocasionales: tienen bajo costo, pero dependen de una persona y no ofrecen tiempos de recuperación confiables.
- **B.** Copias automáticas administradas por cada proveedor: reducen la operación y aprovechan las capacidades de PostgreSQL y del almacenamiento de objetos.
- **C.** Plataforma externa completa de recuperación: ofrece mayor independencia y control, pero aumenta costo y operación desde la etapa inicial.

## Decisión

Se adopta el uso de copias automáticas administradas para PostgreSQL y reglas de retención, claves inmutables y borrado diferido para los archivos. La política de recuperación define el máximo de información que puede perderse, el tiempo máximo para restablecer el servicio, la retención, el acceso restringido y un mecanismo separado del proceso normal de la aplicación. La restauración forma parte de la aceptación de la plataforma y se repite con una periodicidad definida; una copia que no puede restaurarse no se considera válida.

## Consecuencias

**Ventajas:** reduce el riesgo de pérdida permanente y permite comprobar que la plataforma puede recuperarse.

**Desventajas:** aumenta el costo de almacenamiento y exige realizar pruebas de restauración. Las capacidades concretas dependerán del plan de cada servicio (PostgreSQL en Render y Cloudflare R2); si no cumplen los tiempos definidos, deberá añadirse una copia independiente.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige utilizar las copias automáticas administradas de PostgreSQL en Render y reglas de protección sobre los archivos en Cloudflare R2. Los planes de pago de Render Postgres incluyen recuperación a un punto en el tiempo: se puede volver al estado de la base en cualquier momento de los últimos 3 días en el espacio de trabajo Hobby, o de los últimos 7 días en Pro o superior. La recuperación crea una nueva instancia con los datos de ese momento, que se valida antes de conectar el backend. El plan gratuito no ofrece esta capacidad, lo que refuerza que no debe usarse en producción.

La política se apoya en dos valores: la cantidad máxima de información que puede perderse y el tiempo máximo de recuperación. La recuperación a un punto en el tiempo reduce la pérdida a pocos minutos, pero el tiempo real de recuperación incluye crear la instancia, cambiar las variables de conexión del backend y comprobar que las negociaciones y los pagos quedaron coherentes. Ese tiempo no se conoce hasta que se mida en una prueba de restauración. Por esa razón, la prueba forma parte de la aceptación de la plataforma y se repite periódicamente.

Las copias manuales ocasionales dependen de una persona y no ofrecen tiempos confiables, y una plataforma externa completa añade costo y operación desde el inicio. Sin embargo, la copia administrada tiene un límite que conviene reconocer: vive en el mismo proveedor y en la misma cuenta que la base. Protege contra fallas y errores recientes, pero no contra la pérdida de acceso a la cuenta ni contra un error descubierto después de la ventana de recuperación. Ese límite se acepta en esta etapa; si las pruebas de restauración muestran que la copia administrada no cumple lo definido, se añadirá una copia independiente.

Cloudflare R2 no ofrece versionado de objetos, de modo que una eliminación o sobrescritura hecha por la aplicación es permanente. Por eso los archivos se tratarán como inmutables: cada carga tendrá una clave única y nunca se reemplazará un objeto existente. Las eliminaciones se marcarán primero en PostgreSQL y el objeto se borrará físicamente después de un plazo definido, lo que deja una ventana de recuperación. R2 también ofrece bloqueos de contenedor que impiden borrar o sobrescribir objetos durante un período, útiles para la evidencia de disputas.

En costo, la recuperación a un punto en el tiempo está incluida en las bases de pago de Render, cuyo almacenamiento cuesta USD 0,30 por GB al mes. Actualmente R2 cobra USD 0,015 por GB al mes, con 10 GB mensuales gratuitos y sin cobro por transferencia de salida. Cada prueba de restauración genera además el costo de una instancia temporal mientras se valida. Si los tiempos medidos no cumplen los valores definidos, se deberá cambiar de plan o añadir otro mecanismo de recuperación.
