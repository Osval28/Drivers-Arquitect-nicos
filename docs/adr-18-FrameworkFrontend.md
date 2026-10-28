# ADR-18: Framework y modelo de renderizado para la aplicación web

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-USA-0006
- ESC-CAL-REN-0010
- ESC-CAL-ESC-0003

## Contexto

La interfaz del sistema debe mostrar publicaciones y también permitir acciones privadas como negociar, revisar propuestas y confirmar operaciones. Se necesita elegir una base frontend que permita construir esas experiencias con buen rendimiento y mantenimiento razonable.

## Factores de decisión

- La tecnología seleccionada debe facilitar la indexabilidad de las publicaciones para aumentar la probabilidad de que un usuario haga click y use la plataforma porque todavía no es una plataforma reconocida
- Se tomará en cuenta la compatibilidad nativa con SSR (Server Side Rendering) y SSG (Static Site Generation) porque la plataforma necesita ganar confiabilidad de usuarios nuevos dando un buen rendimiento
- Se valorará que la tecnología escogida sea un framework completo, o sea que no se tengan que escoger otras librerías para tareas que tengamos que hacer como formularios, rutas, etc porque reduce la dependencia hacia múltiples herramientas que pueden quedarse sin soporte

## Opciones

- **A.** React SPA con Vite: opción simple y rápida para una aplicación cliente. Funciona bien para áreas privadas, pero exige resolver por aparte renderizado público, SEO y optimización inicial si las publicaciones deben ser visibles y compartibles
- **B.** Next.js con React y TypeScript: permite combinar páginas públicas y privadas, renderizado en servidor o cliente según la pantalla, buen ecosistema y despliegue moderno
- **C.** Angular: framework completo y estructurado. Aporta orden, pero puede ser más pesado si el equipo no necesita todo su modelo
- **D.** Nuxt/Vue: alternativa sólida si el equipo prefiere Vue, con capacidades similares de renderizado híbrido

## Decisión

Se adopta Next.js con React y TypeScript para la aplicación web de DAZMA. La decisión se basa en que el producto combina contenido público de marketplace con funcionalidades privadas de negociación y operación. Next.js permite decidir por pantalla si conviene renderizado de servidor, generación estática o renderizado en cliente, manteniendo React como base común. Esto ayuda a cuidar rendimiento, experiencia de usuario e integración con los servicios necesarios de la plataforma sin separar el frontend en tecnologías distintas.

## Consecuencias

**Ventajas:** permite construir publicaciones con mejor rendimiento inicial, mantener secciones privadas interactivas, usar TypeScript y aprovechar un ecosistema amplio de React. También facilita organizar rutas, formularios, llamadas a API e integración con autenticación

**Desventajas:** exige entender qué código corre en servidor y qué código corre en navegador. Un mal uso puede agregar complejidad innecesaria o exponer datos que deberían permanecer en backend. Para evitarlo, se deben definir reglas claras sobre consumo de API, manejo de sesión, variables de entorno y componentes de servidor/cliente

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige Next.js con React y TypeScript para la aplicación web de DAZMA. Las publicaciones públicas necesitan HTML y metadatos que puedan ser interpretados y compartidos correctamente, mientras que las áreas privadas requieren interacción en el navegador. Next.js permite resolver ambos tipos de pantalla dentro de una misma aplicación mediante renderizado en servidor y componentes de cliente, e incluye en el framework el enrutamiento, la generación de metadatos, el manejo de páginas y soporte integrado de TypeScript.

Las páginas públicas que dependen de información vigente de publicaciones se producirán con datos actuales mediante renderizado en servidor. Las secciones privadas y sus interacciones usarán componentes de cliente cuando lo necesiten. La generación estática se reservará para contenido que sea realmente fijo y no dependa del estado del negocio.

React con Vite funciona bien para una SPA privada, pero no resuelve por sí solo el renderizado público y la generación de metadatos que exigen los factores; sería necesario añadir y mantener otra solución. Angular ofrece un marco más completo y Nuxt ofrece capacidades híbridas equivalentes sobre Vue, pero ambos cambian la base de componentes y el ecosistema. Next.js satisface el requisito público y privado manteniendo React como una sola base y reduce las decisiones separadas sobre rutas y renderizado.
