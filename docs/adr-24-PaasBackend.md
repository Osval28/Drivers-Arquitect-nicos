# ADR-24: Plataforma como servicio (PaaS) para el despliegue del backend de la plataforma

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-DIS-0006
- ESC-CAL-ESC-0003
- ESC-CAL-CON-0010

## Contexto

DAZMA necesita desplegar un backend persistente encargado de las reglas principales del negocio: publicaciones, usuarios, autorizaciones, negociaciones, pagos, auditoría, notificaciones e integraciones externas. Este backend debe permanecer disponible, exponer endpoints HTTP, conectarse a la base de datos, manejar configuración sensible, recibir eventos de proveedores externos y permitir revisar logs y estado de funcionamiento. Por eso la plataforma del backend debe evaluarse según su capacidad para operar servicios persistentes, no solo por facilidad de despliegue.

## Factores de decisión

- La plataforma debe ejecutar un servicio backend persistente con endpoints HTTP disponibles para el frontend y para integraciones externas
- Debe permitir configurar variables y secretos por ambiente, revisar logs, conectar una URL segura y comprobar si el servicio está funcionando correctamente
- Debe soportar despliegues ordenados, reinicio de servicios, escalamiento controlado y recuperación ante fallos
- Debe integrarse con una base de datos administrada o externa sin agregar operación innecesaria
- Debe permitir operar el backend sin administrar manualmente servidores, certificados, reinicios, parches o configuración de infraestructura básica
- La solución debe permitir mantener el backend portable, con configuración separada del código y sin depender innecesariamente de funciones exclusivas de la plataforma

## Opciones

- **A.** Render Web Services: permite ejecutar servicios persistentes, configurar variables y secretos, revisar logs, usar health checks, dominios, TLS, despliegues automáticos y conexión con base de datos administrada
- **B.** Railway Services: permite desplegar backend y base de datos con una experiencia de desarrollo sencilla y cobro por uso. Es una alternativa válida, pero requiere revisar con cuidado costos, límites, recursos disponibles y comportamiento bajo carga
- **C.** Fly.io Machines: ofrece mayor control sobre máquinas, regiones y ejecución, pero exige más criterio operativo para redes, persistencia, escalamiento y diagnóstico
- **D.** Heroku: PaaS tradicional para aplicaciones backend, con despliegue sencillo y buen ecosistema, pero su costo puede ser menos favorable según recursos, base de datos y necesidades de producción
- **E.** Máquina virtual administrada por el equipo: da control amplio sobre el servidor, pero aumenta mantenimiento, parches, seguridad, certificados, monitoreo, recuperación y operación manual

## Decisión

Se adopta Render para desplegar el backend de DAZMA. La decisión se basa en que permite ejecutar servicios persistentes con variables, secretos, logs, revisión de funcionamiento, dominio o URL segura, despliegues automáticos y escalamiento controlado. Render encaja con un backend que debe estar activo, responder solicitudes del frontend, recibir eventos de proveedores externos y conectarse a la base de datos sin que el equipo administre servidores manualmente.

## Consecuencias

**Ventajas:** permite operar el backend como servicio persistente sin administrar directamente servidores, certificados o reinicios manuales. Facilita logs, configuración por ambiente, revisión de funcionamiento y conexión con base de datos administrada o externa. También permite mantener backend y procesos en segundo plano en una misma plataforma si se decide ejecutar los workers allí.

**Desventajas:** DAZMA dependerá de los límites, precios y forma de configuración de Render. El costo puede crecer con memoria, CPU, transferencia, logs, base de datos y respaldos. Para reducir dependencia, el backend debe mantenerse portable, con configuración externa y sin usar funciones propietarias innecesarias

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige desplegar el backend persistente de DAZMA en Render. Render ofrece servicios web y workers, despliegues automáticos desde el repositorio o desde una imagen publicada, HTTPS, variables y archivos secretos, revisión de funcionamiento, logs, reinicio de procesos y conexión con bases de datos. Estas capacidades permiten ejecutar Spring Boot y recibir solicitudes del frontend y eventos de proveedores sin administrar directamente un servidor.

El plan gratuito no sirve para producción. Render indica expresamente que sus instancias gratuitas son para exploración y proyectos personales. Un servicio gratuito se detiene después de 15 minutos sin tráfico, puede tardar alrededor de un minuto en reactivarse, comparte 750 horas gratuitas mensuales por espacio de trabajo y no permite escalamiento a varias instancias. Las bases gratuitas expiran después de 30 días y no incluyen respaldos administrados.

Los servicios de pago comienzan en USD 7 mensuales por 512 MB. El siguiente tamaño ofrece 1 CPU y 2 GB por USD 25 mensuales. La tarifa de USD 7 es un mínimo comercial, pero no demuestra por sí sola que 512 MB sean suficientes para la JVM, la aplicación y sus conexiones. Render cobra además USD 25 mensuales por el espacio de trabajo Pro, orientado a equipos con aplicaciones de producción. Una instancia de 1 CPU y 2 GB más ese espacio de trabajo suman USD 50 mensuales, antes de worker, base de datos, almacenamiento, transferencia e impuestos.

Cada worker se factura como otra instancia. El espacio de trabajo Pro incluye 25 GB mensuales de transferencia saliente y 1.000 minutos de construcción; el exceso cuesta USD 0,15 por GB y USD 5 por cada 1.000 minutos de construcción. Los discos persistentes cuestan USD 0,25 por GB al mes y el almacenamiento administrado de PostgreSQL USD 0,30 por GB al mes.

Railway Pro tiene un mínimo de USD 20 mensuales, aplicado al consumo de recursos, y después cobra por CPU, memoria, almacenamiento y salida realmente utilizados. Fly.io puede ofrecer máquinas pequeñas por montos inferiores, pero exige más decisiones sobre regiones, redes y operación. Heroku ofrece Basic por USD 7 al mes con 512 MB y Standard-1X por USD 25 con 512 MB. Render presenta una combinación clara de precio por tamaño, servicios web, workers y herramientas operativas; aun así, la capacidad real debe comprobarse mediante mediciones de memoria y carga, porque una tabla de precios no garantiza el rendimiento.
