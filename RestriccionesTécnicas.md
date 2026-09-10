# Restricciones Técnicas

## Resumen

| # | Tipo | Categoría | Restricción técnica |
|---:|:---|:---|:---|
| 1 | Propia del proyecto | Prácticas de diseño | El diseño y desarrollo de DAZMA debe propender por seguir los principios SOLID y mantener una clara separación de responsabilidades. |
| 2 | Propia del proyecto | Prácticas de diseño | Se debe propender por el uso de building blocks y componentes reutilizables para resolver capacidades comunes antes de construir soluciones nuevas desde cero. |
| 3 | Propia del proyecto | Prácticas de diseño | El sistema debe propender por una organización modular y de bajo acoplamiento que permita separar componentes de negocio, soporte y componentes transversales. |
| 4 | Propia del proyecto | Marco metodológico | Se debe propender por el uso de un marco de trabajo ágil como Scrum para organizar la evolución del producto mediante backlog priorizado, iteraciones y revisiones frecuentes. |
| 5 | Propia del proyecto | Prácticas DEVOPS | Se debe propender por prácticas DevOps que automaticen la integración, las pruebas, la entrega y, cuando sea apropiado, el despliegue del software. |
| 6 | Propia del proyecto | Prácticas DEVOPS | Todo el código fuente y los artefactos versionables del proyecto deben mantenerse bajo un sistema de control de versiones, con una estrategia de ramas, revisiones y trazabilidad de cambios. |
| 7 | Propia del proyecto | Prácticas DEVOPS | El producto debe contar con mecanismos de observabilidad que permitan registrar y consultar logs, métricas y eventos relevantes para diagnosticar fallos y conocer el comportamiento del sistema. |
| 8 | Propia del proyecto | Patrones de diseño | Se debe inclinar hacia la adopción de los principios de 12-Factor App que resulten aplicables al producto, especialmente en configuración, dependencias, procesos y logs. |
| 9 | Propia del proyecto | Prácticas de código limpio | Se deben aplicar prácticas de Clean Code, evitando Messy Code y Code Smells, acompañadas de revisiones de código y análisis estático. |
| 10 | Propia del proyecto | Patrones de diseño | Se debe propender por el uso de patrones y principios de diseño como GoF, GRASP, DRY y KISS cuando aporten una solución clara al problema, evitando aplicarlos de forma innecesaria. |
| 11 | Propia del proyecto | Prácticas de desarrollo | El desarrollo debe incorporar prácticas de seguridad alineadas con OWASP, incluyendo validación de entradas, control de acceso, gestión segura de secretos y revisión de dependencias. |
| 12 | Propia del proyecto | Prácticas de desarrollo | Las funcionalidades y reglas críticas deben contar con pruebas automatizadas y controles de calidad antes de integrarse a versiones candidatas a producción. |
| 13 | Propia del proyecto | Prácticas de desarrollo | La configuración específica de cada ambiente y los secretos de operación deben mantenerse separados del código fuente. |

## Detalle

### RT-01 — Prácticas de diseño

**Tipo:** Propia del proyecto

**Restricción técnica:** El diseño y desarrollo de DAZMA debe propender por seguir los principios SOLID y mantener una clara separación de responsabilidades.

**Justificación:** DAZMA tendrá módulos con responsabilidades diferentes, como publicaciones, negociación, pagos, reputación y moderación. Aplicar SOLID ayuda a reducir el acoplamiento entre estos módulos y facilita que futuras modificaciones no produzcan efectos inesperados en funcionalidades no relacionadas.

### RT-02 — Prácticas de diseño

**Tipo:** Propia del proyecto

**Restricción técnica:** Se debe propender por el uso de building blocks y componentes reutilizables para resolver capacidades comunes antes de construir soluciones nuevas desde cero.

**Justificación:** Reutilizar componentes ya probados permite concentrar el esfuerzo del equipo en la lógica propia del negocio. En DAZMA esto es especialmente útil para capacidades transversales o de soporte como autenticación, notificaciones, manejo de errores, auditoría e integración con servicios externos.

### RT-03 — Prácticas de diseño

**Tipo:** Propia del proyecto

**Restricción técnica:** El sistema debe propender por una organización modular y de bajo acoplamiento que permita separar componentes de negocio, soporte y componentes transversales.

**Justificación:** La separación modular facilita que funcionalidades propias del marketplace evolucionen de forma independiente y que capacidades comunes puedan reutilizarse. Esto reduce dependencias innecesarias y prepara el producto para crecer sin obligar a rediseñar continuamente todo el sistema.

### RT-04 — Marco metodológico

**Tipo:** Propia del proyecto

**Restricción técnica:** Se debe propender por el uso de un marco de trabajo ágil como Scrum para organizar la evolución del producto mediante backlog priorizado, iteraciones y revisiones frecuentes.

**Justificación:** DAZMA evolucionará a partir de decisiones de negocio y retroalimentación de usuarios. Un marco iterativo permite ajustar prioridades y validar incrementos de producto de forma frecuente. Los compromisos de tiempo y presupuesto deberán definirse posteriormente por el Product Owner.

### RT-05 — Prácticas DEVOPS

**Tipo:** Propia del proyecto

**Restricción técnica:** Se debe propender por prácticas DevOps que automaticen la integración, las pruebas, la entrega y, cuando sea apropiado, el despliegue del software.

**Justificación:** La automatización reduce errores manuales y permite entregar cambios de forma repetible y trazable. A medida que DAZMA crezca, estas prácticas facilitarán mantener estabilidad durante actualizaciones y correcciones sin depender de procesos manuales difíciles de reproducir.

### RT-06 — Prácticas DEVOPS

**Tipo:** Propia del proyecto

**Restricción técnica:** Todo el código fuente y los artefactos versionables del proyecto deben mantenerse bajo un sistema de control de versiones, con una estrategia de ramas, revisiones y trazabilidad de cambios.

**Justificación:** El control de versiones permite trabajar de forma colaborativa, conocer la evolución del producto, revisar modificaciones y recuperar versiones anteriores cuando sea necesario. La herramienta o plataforma concreta podrá seleccionarse posteriormente.

### RT-07 — Prácticas DEVOPS

**Tipo:** Propia del proyecto

**Restricción técnica:** El producto debe contar con mecanismos de observabilidad que permitan registrar y consultar logs, métricas y eventos relevantes para diagnosticar fallos y conocer el comportamiento del sistema.

**Justificación:** Un marketplace en producción requiere detectar problemas sin depender únicamente de reproducirlos en ambientes de desarrollo. La observabilidad permite identificar errores, degradaciones de rendimiento e incidentes operativos, apoyando soporte y mantenimiento.

### RT-08 — Patrones de diseño

**Tipo:** Propia del proyecto

**Restricción técnica:** Se debe inclinar hacia la adopción de los principios de 12-Factor App que resulten aplicables al producto, especialmente en configuración, dependencias, procesos y logs.

**Justificación:** Estos principios ayudan a mantener una aplicación portable y consistente entre ambientes, separando la configuración del código y favoreciendo despliegues reproducibles. Su aplicación no obliga todavía a seleccionar un lenguaje, base de datos o proveedor de nube concreto.

### RT-09 — Prácticas de código limpio

**Tipo:** Propia del proyecto

**Restricción técnica:** Se deben aplicar prácticas de Clean Code, evitando Messy Code y Code Smells, acompañadas de revisiones de código y análisis estático.

**Justificación:** Un producto de larga evolución debe poder ser comprendido y modificado por distintos desarrolladores. Mantener código legible, nombres claros y complejidad controlada reduce errores y facilita mantenimiento, pruebas y incorporación de nuevas funcionalidades.

### RT-10 — Patrones de diseño

**Tipo:** Propia del proyecto

**Restricción técnica:** Se debe propender por el uso de patrones y principios de diseño como GoF, GRASP, DRY y KISS cuando aporten una solución clara al problema, evitando aplicarlos de forma innecesaria.

**Justificación:** Estas prácticas ayudan a distribuir responsabilidades, reducir duplicación y mantener soluciones simples. En DAZMA deben utilizarse como herramientas para resolver problemas reales del diseño y no como una obligación de introducir complejidad adicional.

### RT-11 — Prácticas de desarrollo

**Tipo:** Propia del proyecto

**Restricción técnica:** El desarrollo debe incorporar prácticas de seguridad alineadas con OWASP, incluyendo validación de entradas, control de acceso, gestión segura de secretos y revisión de dependencias.

**Justificación:** DAZMA será una aplicación pública que manejará cuentas de usuario, información personal y operaciones económicas. Incorporar seguridad desde el ciclo de desarrollo reduce el riesgo de vulnerabilidades comunes y evita depender únicamente de controles agregados al final.

### RT-12 — Prácticas de desarrollo

**Tipo:** Propia del proyecto

**Restricción técnica:** Las funcionalidades y reglas críticas deben contar con pruebas automatizadas y controles de calidad antes de integrarse a versiones candidatas a producción.

**Justificación:** Los flujos de negociación, permisos, reputación y operaciones económicas pueden afectar la confianza y el negocio si presentan regresiones. Las pruebas automatizadas permiten verificar repetidamente que los cambios mantienen el comportamiento esperado.

### RT-13 — Prácticas de desarrollo

**Tipo:** Propia del proyecto

**Restricción técnica:** La configuración específica de cada ambiente y los secretos de operación deben mantenerse separados del código fuente.

**Justificación:** Separar configuración y código evita exponer credenciales y facilita ejecutar el mismo producto en distintos ambientes. La solución concreta para administrar secretos o configuraciones se definirá cuando se seleccione la infraestructura de operación.
