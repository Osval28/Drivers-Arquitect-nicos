# ADR-29: Adopción de herramienta de observabilidad y monitoreo

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0010
- ESC-CAL-CON-0011

## Contexto

Es necesario monitorear tanto los recursos del servidor donde se está corriendo la app, tasa de errores por endpoint y latencia de servicios externos para detectar un rendimiento deficiente del sistema y así generar alertas para que se mitigue el problema.

## Factores de decisión

- La herramienta seleccionada debe soportar el monitoreo de consumo de CPU, memoria RAM, tasa de errores de cada servicio externo
- Se valorará que la herramienta seleccionada requiera la menor complejidad de integración posible, con factores como que tenga un SDK para las herramientas de front y back seleccionadas
- Se valorará que la herramienta seleccionada tenga el menor costo posible porque todavía no se tiene tráfico real, por lo que no se puede justificar gastar tanto en soporte y validación de errores todavía
- La herramienta seleccionada debe soportar generación de alertas automáticas con la superación de un umbral de consumo de recursos, latencia de servicios externos o fallas recurrentes de los mismos, enviadas por un canal externo al servicio afectado (correo, Slack o webhook)
- La instrumentación debe apoyarse en un estándar abierto, como OpenTelemetry, para poder cambiar de proveedor sin reescribirla
- La herramienta debe reunir métricas, logs y trazas en un solo lugar, para correlacionar una falla entre el frontend, el backend y los workers
- La herramienta debe permitir definir métricas propias del negocio, como negociaciones fallidas o pagos pendientes, y mostrarlas en paneles a medida

## Opciones

- **A.** Grafana Cloud: Permite monitorear CPU, RAM, Disco y red, maneja tasa de errores por endpoint (Pero necesita ser implementada por nosotros), maneja fallas de dependencias externas pero no nativamente, tiene alertas configurables para cualquier parámetro, pero tiene una complejidad de integración más alta al tener que crear dashboards y se paga por volumen de telemetría
- **B.** Sentry: No cuenta con monitoreo de recursos directamente (Se tendría que integrar con otro servicio), accede a tasa de errores por endpoint, se capturan las excepciones automáticamente, maneja fallas de dependencias externas automáticamente, tiene un SDK para Spring Boot, Next.js y se paga por volumen de errores
- **C.** Better Stack: Permite monitorear el consumo de CPU, RAM, Disco y red, para manejar tasa de errores por endpoint se tiene que configurar manualmente, tampoco detecta automáticamente las fallas de dependencias externas ni tiene un SDK, se paga por volumen de logs

## Decisión

Se adopta Grafana Cloud como plataforma central de observabilidad. Reunirá métricas de infraestructura y aplicación, registros y trazas del frontend, backend y workers. Los paneles mostrarán CPU, memoria, errores por endpoint, tiempos de respuesta y fallas de dependencias; las alertas se enviarán a un canal externo al servicio afectado. Se elige frente a Sentry porque también cubre recursos de infraestructura y frente a Better Stack por su mayor profundidad en métricas, paneles y alertas, además de apoyarse en OpenTelemetry, reunir las tres señales en un solo lugar y permitir métricas de negocio propias.

## Consecuencias

**Ventajas:** centraliza las señales necesarias para detectar y explicar fallas sin contratar una herramienta distinta para cada componente. El plan Free incluye 10.000 series métricas, 50 GB mensuales de registros, 50 GB de trazas y 14 días de retención; Pro parte de USD 19 al mes más consumo.

**Desventajas:** requiere instrumentación, paneles y alertas bien configuradas. El costo aumenta con el volumen y una mala selección de umbrales puede generar alertas innecesarias.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Grafana Cloud para reunir métricas de CPU y memoria, errores, tiempos de respuesta, registros y trazas del frontend, backend y workers. Las alertas se configurarán con umbrales definidos y se enviarán a correo, Slack o webhook, de modo que sigan siendo visibles aunque el servicio afectado no pueda responder.

Grafana Cloud cubre los siete factores de decisión en una misma plataforma. El backend Java envía información mediante OpenTelemetry, un estándar abierto que permite cambiar de proveedor sin reescribir la instrumentación, y el frontend Next.js dispone del SDK Faro y una guía oficial de integración. La latencia y los errores de proveedores externos deberán medirse desde las llamadas que realiza DAZMA; Grafana no conoce automáticamente que un proveedor falló si la aplicación no envía esa información. También será necesario definir paneles, nombres consistentes y alertas útiles, por lo que su amplitud implica más configuración que una herramienta centrada únicamente en excepciones.

Sentry se centra en errores y trazas de la aplicación y necesita otra herramienta para los recursos del servidor. Grafana reúne métricas, logs y trazas en un solo lugar, lo que permite correlacionar una falla entre el frontend, el backend y los workers, y permite definir métricas propias del negocio, como negociaciones fallidas o pagos pendientes, con paneles a medida. Sus alertas se envían por correo, Slack o webhook, fuera del servicio afectado (ADR-04). Grafana no es la opción de menor complejidad de integración: Sentry se integra con menos configuración y Grafana exige crear paneles y alertas. Esa configuración adicional se acepta a cambio de cubrir los siete factores en una sola plataforma, y su cuota gratuita de logs y trazas es mayor que la de Better Stack.

El plan Free cuesta USD 0, admite hasta 10.000 series métricas activas, 50 GB mensuales de logs, 50 GB mensuales de trazas y conserva esos datos durante 14 días. El plan Pro parte de USD 19 mensuales más consumo, conserva métricas durante 13 meses y logs y trazas durante 30 días. Después de las cuotas incluidas, las métricas comienzan en USD 6,50 por cada 1.000 series y el procesamiento, escritura y retención de logs o trazas se cobra por GB. La interfaz administrada de Application Observability comienza en USD 0,025 por host-hora, aproximadamente USD 18 por host al mes. El volumen enviado debe limitarse a las señales necesarias, porque etiquetas sin control pueden aumentar rápidamente el número de series.

Sentry ofrece una integración muy directa para errores y trazas. Su plan Developer cuesta USD 0 para un usuario e incluye 5.000 errores; Team cuesta USD 26 mensuales e incluye 50.000 errores, 5 GB de logs, 5 GB de métricas de aplicación y 5 millones de spans. Sin embargo, el monitoreo de CPU y memoria del servidor no es su capacidad principal, por lo que no cubre por sí solo todos los factores.

Better Stack ofrece un plan gratuito con 30 GB de métricas, 3 GB de logs, 3 GB de trazas, conservación de tres días y 100.000 excepciones. Su paquete Nano cuesta USD 30 mensuales, o USD 25 mensuales con pago anual, e incluye 40 GB para métricas, logs y trazas. Es una alternativa válida y con mayor cantidad de configuración ya preparada para incidentes, pero Grafana ofrece mayor profundidad para métricas, paneles y alertas del conjunto completo.

La decisión se toma por cobertura integral y estándares de integración, no solo por la existencia de un plan gratuito. El costo dependerá del volumen de telemetría, la retención y las funciones contratadas; esos valores deberán vigilarse como cualquier otro consumo de infraestructura.
