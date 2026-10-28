# ADR-23: Plataforma como servicio (PaaS) para el despliegue del frontend de la plataforma

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-DIS-0006
- ESC-CAL-ESC-0003
- ESC-CAL-CON-0010
- ESC-CAL-CON-0011

## Contexto

DAZMA necesita desplegar la aplicación web que usarán los usuarios para consultar publicaciones, iniciar sesión, negociar, revisar propuestas, pagar y confirmar operaciones. Esta parte del sistema tiene necesidades diferentes al backend: debe cargar rápido, permitir despliegues frecuentes, conectarse a una dirección propia del proyecto, usar conexión segura y trabajar bien con el framework frontend elegido.

## Factores de decisión

- La plataforma debe integrarse bien con el framework elegido para la aplicación web
- Debe permitir despliegues automáticos, conexión segura, dominio propio, vistas previas de cambios y configuración por ambiente
- Debe favorecer buen rendimiento para las páginas públicas del marketplace y una navegación estable en las secciones privadas
- Debe permitir conectar el frontend con la API del backend sin exponer secretos, claves privadas ni reglas sensibles en el navegador
- Debe facilitar revisar despliegues, errores y comportamiento de la aplicación web
- El costo debe evaluarse considerando plan, transferencia, ancho de banda, uso de funciones, límites del proyecto, colaboración y crecimiento esperado

## Opciones

- **A.** Vercel: plataforma orientada al despliegue de aplicaciones frontend modernas. Tiene integración directa con Next.js, despliegues automáticos, vistas previas por cambio, CDN, HTTPS, dominio propio y buen soporte para aplicaciones web
- **B.** Netlify: alternativa fuerte para frontend web, con despliegues automáticos, CDN, dominio propio y funciones. Puede ser adecuada para aplicaciones frontend, aunque su integración con Next.js no es tan directa como la de Vercel
- **C.** Render Static Sites: permite alojar frontend en la misma plataforma donde podrían estar backend y procesos en segundo plano. Simplifica operación, pero no está tan especializada en Next.js como Vercel
- **D.** Railway: permite desplegar aplicaciones y servicios en una sola plataforma, con cobro por uso. Es flexible, pero se debe revisar con cuidado su conveniencia para un frontend público con tráfico variable
- **E.** Almacenamiento/CDN configurado manualmente: ofrece control, pero exige más configuración de despliegue, certificados, invalidación de caché, dominio y operación

## Decisión

Se adopta Vercel para desplegar el frontend de DAZMA. La decisión se basa en su integración directa con Next.js, despliegues automáticos, vistas previas por cambio, manejo de dominio propio, conexión segura y distribución global de contenido. Vercel encaja con una aplicación web que combina páginas públicas del marketplace y secciones privadas, porque reduce la operación propia del frontend y permite publicar cambios de interfaz de forma ordenada. El frontend consumirá la API del backend mediante URLs configuradas por ambiente y no almacenará secretos ni reglas sensibles.

## Consecuencias

**Ventajas:** permite desplegar el frontend en una plataforma especializada en aplicaciones web modernas, facilita vistas previas, dominio propio, conexión segura y buen rendimiento para páginas públicas. También separa claramente la operación del frontend frente al backend y los procesos en segundo plano.

**Desventajas:** agrega una plataforma adicional si backend y procesos en segundo plano se despliegan en otro lugar. Se deben coordinar variables públicas, URLs por ambiente, dominios, permisos y comunicación con el backend. Además, algunas capacidades específicas de la plataforma pueden generar dependencia si se usan sin necesidad.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige desplegar el frontend de DAZMA en Vercel. Vercel mantiene Next.js y ofrece despliegue sin configuración especial para sus funciones principales, integración con repositorios Git, una dirección de vista previa por cambio, separación entre ambientes, dominio propio, HTTPS y acceso a logs de construcción y ejecución. Estas capacidades responden directamente a los factores de integración con el framework, revisión de cambios, configuración por ambiente y operación del frontend.

Las demás plataformas también pueden ejecutar Next.js. Netlify declara soporte para las funciones principales de Next.js mediante OpenNext y constituye una alternativa técnicamente válida. Render puede ejecutarlo como sitio estático o como servicio Node.js, y Railway puede alojarlo como otro servicio general. Vercel reduce el riesgo de incompatibilidad entre el framework y la plataforma porque la misma empresa mantiene Next.js y su infraestructura interpreta directamente su forma de construir y ejecutar la aplicación.

El plan Hobby de Vercel cuesta USD 0, pero sus condiciones lo limitan a uso personal y no comercial. El plan Pro cuesta USD 20 mensuales, incluye un miembro con capacidad de desplegar, USD 20 de crédito mensual de infraestructura, 1 TB de transferencia de datos y un millón de solicitudes de entrega de contenido antes de aplicar crédito y cobro por consumo. Cada miembro adicional con permiso para desplegar cuesta USD 20 mensuales. Enterprise se cotiza de manera personalizada.

Netlify cobra actualmente USD 20 mensuales por su plan Pro base, con miembros ilimitados y 3.000 créditos mensuales, por lo que puede resultar más económico para un equipo con varios desarrolladores que necesiten desplegar. Vercel conserva la ventaja de integración directa con Next.js; Netlify conserva una ventaja real en costo de colaboración. La factura de Vercel deberá vigilar transferencia, ejecución, optimización de imágenes y otros consumos, porque el precio no se limita a la tarifa fija del plan.
