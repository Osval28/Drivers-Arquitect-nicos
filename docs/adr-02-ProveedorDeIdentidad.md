# ADR-02: Adopción de un proveedor de identidad

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-SEG-0004
- ESC-CAL-SEG-0008

## Contexto

Los usuarios de DAZMA deben poder crear cuenta, iniciar sesión, recuperar acceso y confirmar su identidad antes de usar funciones que requieran autorización. Como estas capacidades implican manejo de credenciales y seguridad, el proyecto necesita decidir cómo resolverá la autenticación de forma confiable y manejable.

## Factores de decisión

- La opción escogida debe tener un plan gratuito, porque el proyecto necesita lanzarse a producción con la menor incertidumbre financiera
- Que tenga Multifactor Authentication al menor costo es importante porque la plataforma necesita que los usuarios puedan contar con seguridad en sus operaciones como intercambios, pagos y chats
- Permitir el inicio de sesión con redes sociales facilita la entrada de usuarios a la plataforma, por lo que tener más proveedores de terceros es conveniente para el candidato
- El proveedor debe permitir sacar los hashes de las contraseñas de las cuentas de los usuarios de su plataforma al menor costo posible, para que en caso de que se necesite cambiar de proveedor los usuarios no tengan que restablecer su contraseña masivamente

## Opciones

- **A.** Auth0: proveedor administrado compatible con OAuth 2.0 y OpenID Connect. Incluye autenticación social, MFA según plan y herramientas de administración de usuarios; su costo depende de usuarios activos y funciones habilitadas.
- **B.** Amazon Cognito: proveedor administrado dentro del ecosistema AWS, compatible con OAuth 2.0 y OpenID Connect. Soporta proveedores externos y escalamiento por volumen; su adopción tiene mayor sentido cuando la infraestructura principal se apoya en AWS.
- **C.** Firebase Authentication / Identity Platform: proveedor administrado de Google para registro, inicio de sesión con correo/contraseña y proveedores sociales. Permite emitir tokens que el backend puede validar y tiene costos asociados al uso y a funciones avanzadas de identidad.
- **D.** Keycloak: proveedor open source autoalojado compatible con OpenID Connect. No tiene costo de licencia por usuario, pero exige operación, actualizaciones, seguridad, disponibilidad, respaldos y mantenimiento propio.
- **E.** Microsoft Entra External ID: proveedor de identidad de Microsoft para usuarios externos, compatible con estándares modernos y orientado a organizaciones que ya administran usuarios dentro del ecosistema Microsoft.

## Decisión

Se adopta Firebase Authentication porque ofrece un modelo de costos progresivo y adecuado para una etapa inicial de crecimiento. Con Identity Platform, los métodos de autenticación más comunes cuentan con un nivel sin costo de hasta 50.000 usuarios activos mensuales, permitiendo validar el producto y crecer sin asumir costos elevados desde el inicio. A partir de ese umbral, el cobro es por usuario activo mensual y disminuye por volumen, lo que permite que el costo escale de forma proporcional al uso real. Además, al ser un servicio administrado reduce costos operativos asociados a desplegar, actualizar, asegurar y mantener una infraestructura propia de identidad.

## Consecuencias

**Ventajas:** Permite comenzar con costos bajos y escalar de acuerdo con el número de usuarios activos, evitando invertir desde el inicio en infraestructura propia de identidad. Al ser un servicio administrado, reduce el esfuerzo de operación, mantenimiento, actualización y seguridad.

**Desventajas:** Se aumenta la dependencia del ecosistema de Google y puede aparecer lock-in si se utilizan funciones muy específicas de Firebase/Identity Platform. Aunque el costo inicial es bajo, este puede crecer de forma importante si aumenta considerablemente la cantidad de usuarios activos. También se pierde parte del control que tendría una solución autoalojada como Keycloak, ya que disponibilidad, cambios del servicio y condiciones comerciales dependen del proveedor.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige utilizar Firebase Authentication con Identity Platform como proveedor de identidad de DAZMA y validar desde el backend los tokens emitidos. Firebase resolverá el registro, el inicio de sesión, la verificación de correo, la recuperación de contraseña y el acceso con proveedores sociales. DAZMA conservará en su propio backend el perfil de negocio, los roles y los permisos sobre publicaciones, negociaciones y pagos; Firebase confirmará quién es el usuario, pero no decidirá qué puede hacer dentro de la plataforma.

La elección responde al factor económico con dos escenarios claramente distintos. En el plan Spark, Firebase Authentication con Identity Platform no exige un medio de pago, pero limita a 3.000 usuarios activos por día. Spark también permite hasta 1.000 correos de verificación de dirección por día y solo 150 correos de restablecimiento de contraseña por día.

El plan Blaze, de facturación por uso, exige una cuenta de facturación y permite configurar controles de presupuesto. En Blaze, correo, contraseña y proveedores sociales incluyen 50.000 usuarios activos mensuales sin cargo. Por encima de ese nivel, Google publica USD 0,0055 por cada usuario activo entre 50.000 y 100.000, USD 0,0046 entre 100.000 y un millón, USD 0,0032 entre uno y diez millones y USD 0,0025 a partir de diez millones. Una cuenta que no realiza una operación de autenticación durante el mes no se factura como usuario activo. Blaze eleva además los límites publicados a 100.000 correos de verificación de dirección y 10.000 correos de restablecimiento por día. La cuota gratuita de 50.000 usuarios no significa que toda la cuenta sea incapaz de generar cobros: disponer de facturación habilita el cobro por exceso y por otros servicios consumidos, por lo que se requieren alertas de presupuesto y revisión de uso.

Para autenticación multifactor se priorizará TOTP, es decir, códigos generados por una aplicación autenticadora. Firebase documenta TOTP como parte de Authentication con Identity Platform y exige que el correo del usuario esté verificado. Este método no genera un SMS en cada autenticación. El segundo factor mediante SMS sí se cobra por mensaje: Google publica para Colombia USD 0,01 por SMS y no factura los primeros diez mensajes de cada día. Debido a que el costo y el riesgo de abuso crecen con cada envío, SMS no será el factor predeterminado. El uso de TOTP responde mejor al requisito de MFA al menor costo recurrente.

Firebase admite correo y contraseña, Google, Facebook, Apple, GitHub y otros proveedores, y permite vincular varios métodos a la misma cuenta. Esto cubre el factor de acceso social sin obligar a DAZMA a mantener contraseñas. El backend valida el token con el SDK administrativo, lo cambia por una sesión propia que pueda revocar y usa el identificador del usuario para consultar los roles y datos internos.

En portabilidad, Firebase permite exportar usuarios. La herramienta oficial auth:export incluye las contraseñas que Firebase conserva con su algoritmo scrypt y Google documenta cómo consultar los parámetros del hash. Esto reduce la posibilidad de exigir un restablecimiento masivo, pero no vuelve la migración automática: el proveedor de destino debe admitir ese algoritmo o debe implementarse una transición controlada. Por tanto, se reconoce una dependencia del proveedor, aunque existe una salida documentada para las cuentas con correo y contraseña. Las cuentas sociales dependen además de volver a configurar los proveedores y relacionar correctamente sus identificadores.

Auth0 incluye un plan gratuito de hasta 25.000 usuarios activos mensuales, pero su página comercial ubica Pro MFA en el plan Essentials, publicado desde USD 35 al mes para 500 usuarios activos, y funciones de seguridad superiores en Professional, desde USD 240 al mes para 500 usuarios. Amazon Cognito es competitivo y soporta MFA con aplicaciones autenticadoras y SMS, pero sus planes y ventajas operativas están estrechamente integrados con AWS; además, las cuentas nuevas tienen una estructura de niveles y cobros que debe evaluarse junto con el resto de servicios de AWS. Keycloak no cobra licencia por usuario, pero exige pagar y operar cómputo, base de datos, copias de seguridad, actualizaciones, monitoreo y disponibilidad de un componente crítico. Microsoft Entra External ID es una alternativa válida para identidades externas, con cobro por usuarios activos y complementos, pero tiene mayor afinidad operativa con una solución asentada en Azure. Se escoge Firebase porque es la opción que mejor cumple con los factores de decisión: tiene MFA TOTP sin cobro por mensaje, inicio de sesión con redes sociales, permite exportar los usuarios, sus cuotas son públicas y es un servicio administrado.

Tenemos evidencia práctica dentro del proyecto: se creó un usuario, se inició sesión, se obtuvo un ID token y el backend lo validó desde una solicitud en Postman, devolviendo el identificador y el correo reconocidos. La respuesta mostró emailVerified=false, lo que demostró además que el token transporta el estado de verificación y que el backend puede exigirlo cuando una operación lo necesite.
