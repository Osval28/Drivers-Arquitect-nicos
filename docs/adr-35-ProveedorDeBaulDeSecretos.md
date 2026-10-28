# ADR-35: Selección del proveedor del baúl de secretos

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-SEG-0008
- ESC-CAL-INT-0002

## Contexto

ADR-25 decidió guardar las credenciales de producción en un baúl de secretos administrado y dedicado. Falta escoger el proveedor que lo ofrecerá. DAZMA tendrá pocos secretos (aproximadamente uno por servicio: base de datos, identidad, almacenamiento de archivos, observabilidad, notificaciones y caché) y un backend desplegado en una plataforma distinta a la del baúl.

## Factores de decisión

- Debe permitir limitar quién puede leer o modificar cada secreto, porque cada servicio debe acceder solo a sus propias credenciales
- Debe registrar quién accedió a cada secreto, porque ante una sospecha de exposición el equipo necesita saber qué se leyó y cuándo
- El costo debe ser proporcional a la cantidad esperada de secretos (alrededor de seis) y no exigir un mínimo mensual, porque el proyecto evita compromisos elevados antes de validar el negocio
- Debe poder consumirse desde un backend Java con Spring Boot mediante una biblioteca o API documentada, porque el backend se despliega fuera del proveedor del baúl
- Debe reducir la cantidad de proveedores de nube con cuenta y facturación propias, porque cada cuenta adicional implica administrar accesos, pagos y revisiones de seguridad por separado

## Opciones

- **A.** AWS Secrets Manager: baúl administrado de AWS con permisos por secreto mediante IAM, versionado, auditoría de accesos y rotación. Cobra USD 0,40 por secreto al mes más USD 0,05 por cada 10.000 llamadas a la API.
- **B.** Google Cloud Secret Manager: baúl administrado de Google con permisos por secreto, versionado y auditoría. Cobra alrededor de USD 0,06 por versión activa al mes y USD 0,03 por cada 10.000 accesos, con las primeras seis versiones y los primeros 10.000 accesos gratis cada mes.
- **C.** Azure Key Vault: baúl administrado de Microsoft con permisos por secreto y auditoría. Cobra por cada 10.000 operaciones y requiere una suscripción de Azure.
- **D.** Infisical Cloud: servicio especializado en gestión de secretos fuera de las grandes nubes. Su plan gratuito admite 5 identidades (personas e integraciones) y 3 proyectos; el plan Pro cuesta USD 18 por identidad al mes.

## Decisión

Se adopta AWS Secrets Manager como proveedor del baúl de secretos, porque cumple los factores de permisos por secreto, auditoría de accesos y consumo desde Spring Boot, y porque concentra los secretos en el mismo proveedor de nube que usa el ADR-34 (Amazon API Gateway), lo que evita administrar una segunda cuenta. El costo es mayor que el de Google Cloud Secret Manager para esta cantidad de secretos.

## Consecuencias

**Ventajas:** una sola cuenta de nube para el API Gateway (ADR-34) y los secretos, permisos por secreto, auditoría de accesos, versionado y rotación disponible si se necesita más adelante. Los parámetros que no son secretos se leen de AWS Systems Manager Parameter Store en la misma cuenta.

**Desventajas:** cuesta unos USD 2,40 al mes con seis secretos, más que la opción de Google, y el costo crece con cada secreto. Agrega una dependencia de AWS. La aplicación necesita una credencial propia para leer el baúl, que pasa a ser el secreto más sensible.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige AWS Secrets Manager. Los tres baúles de las grandes nubes cumplen los factores de permisos por secreto y auditoría, así que esos factores no los separan. Lo que los diferencia es el costo y la cuenta de nube que exigen.

Por costo, Google Cloud Secret Manager es la opción más barata para esta escala: con una versión activa por secreto, seis secretos caben dentro de las seis versiones gratuitas mensuales. AWS cuesta unos USD 2,40 al mes por seis secretos, más USD 0,05 por cada 10.000 llamadas. La diferencia es de pocos dólares, pero obligaría a abrir y administrar una cuenta de Google Cloud con facturación propia, además de la cuenta de AWS del ADR-34. Por eso se prefiere AWS aun pagando más, ya que el ADR-34 usa Amazon API Gateway en la misma cuenta.

Azure Key Vault cobra por operaciones y requiere una suscripción de Azure, una cuenta adicional que ningún otro servicio del proyecto usaría. Infisical Cloud no pertenece a una gran nube y su plan gratuito cuenta como identidad tanto a cada persona como a cada integración; pasar al plan Pro costaría USD 18 por identidad al mes, un costo fijo mayor que el de los demás para este volumen de secretos.

Los precios se consultaron en las páginas públicas de cada proveedor en octubre de 2026 y deben revisarse antes de contratar.
