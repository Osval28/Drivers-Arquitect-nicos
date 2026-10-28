# ADR-36: Plataforma de integración y entrega continuas (CI/CD)

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-DIS-0006

## Contexto

Tres personas modifican el backend de DAZMA y cada cambio puede afectar pagos, negociaciones o datos de usuarios. Hace falta definir cómo se integran los cambios, qué controles automáticos debe superar una versión antes de llegar a producción y cómo se despliega la versión aprobada, para que un error no llegue a los usuarios y el proceso no dependa de que alguien recuerde ejecutar pasos manuales. El código del proyecto se aloja en GitHub y el backend se despliega en una plataforma administrada (ADR-24).

## Factores de decisión

- Todo cambio debe pasar por compilación, pruebas con cobertura, análisis estático con umbral de calidad y escaneo de vulnerabilidades, porque un control que solo informa no impide que un defecto llegue a producción
- Los controles deben ejecutarse sobre cada solicitud de cambio y mostrar su resultado donde el equipo la revisa, porque el proyecto necesita que el análisis ocurra antes de integrar y no solo en el equipo de cada desarrollador
- El despliegue a producción debe usar la misma versión que superó los controles, una ejecución a la vez y con credenciales separadas del código (ADR-25), porque lo que se probó debe ser lo que se ejecuta
- Debe poder desplegar en la plataforma del backend (ADR-24) sin depender de una función exclusiva de otra herramienta, porque el despliegue debe seguir siendo portable (ADR-22)
- No debe exigir operar un servidor propio de integración, porque el proyecto no tiene personal para mantenerlo
- El costo debe ser proporcional a un equipo de tres personas y a un repositorio pequeño, porque el proyecto evita compromisos elevados antes de validar el negocio

## Opciones

- **A.** GitHub Actions: CI/CD integrado al repositorio de GitHub. Los flujos se versionan con el código, los ejecutores son administrados, los ambientes admiten secretos y reglas propias, y existen acciones reutilizables de terceros. Se cobra por minutos de ejecución sobre una cuota
- **B.** GitLab CI/CD: CI/CD integrado a GitLab, con capacidades comparables. Exigiría alojar el repositorio en GitLab o mantener un espejo, además de duplicar las solicitudes de cambio
- **C.** Jenkins autogestionado: servidor de automatización de código abierto, muy flexible mediante plugins. Exige instalar, actualizar, respaldar y proteger un servidor propio

## Decisión

Se adopta GitHub Actions como plataforma de integración y entrega continuas del backend. Cada cambio ejecuta compilación, pruebas con cobertura, análisis estático con umbral de calidad y escaneo de vulnerabilidades, y el fallo de cualquiera de estos controles bloquea el avance. Solo la rama principal despliega a producción, con la misma versión que superó los controles, una ejecución a la vez y mediante un ambiente protegido con sus propias credenciales.

## Consecuencias

**Ventajas:** los controles quedan versionados con el código y se aplican a cada solicitud de cambio sin depender de la memoria del equipo. No se mantiene un servidor de integración. El despliegue es repetible y deja registro de qué versión se publicó y cuándo. Las credenciales de despliegue viven en la plataforma y no en el repositorio.

**Desventajas:** el repositorio y la integración continua dependen del mismo proveedor. Los minutos de ejecución y el almacenamiento de artefactos tienen cuota, y los controles lentos la consumen. Las acciones de terceros son código externo que corre con acceso al repositorio, por lo que deben fijarse a una versión inmutable y revisarse al actualizarlas. La reversión (ADR-06) requiere volver a desplegar una versión anterior que se haya conservado.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige GitHub Actions. El código ya está en GitHub, por lo que los controles se ejecutan sobre cada solicitud de cambio, el resultado aparece en la misma solicitud y puede exigirse como condición para integrar. Al integrar en la rama principal, la versión se construye una sola vez, se escanea y se publica, y el despliegue toma esa versión exacta sin volver a compilarla. Así, lo que superó las pruebas es lo que se ejecuta en producción. Un ambiente protegido guarda las credenciales de despliegue y una regla de concurrencia impide dos despliegues simultáneos.

GitLab CI/CD ofrece capacidades equivalentes, pero obligaría a mover el repositorio o a mantener un espejo, con solicitudes de cambio duplicadas y una segunda cuenta que administrar. Jenkins es la opción más flexible y no tiene licencia, pero el proyecto debería operar un servidor, actualizar sus plugins, respaldarlo y exponerlo para recibir eventos del repositorio. Esa operación compite con el desarrollo en un equipo de tres personas y repite el trabajo que el ADR-22 busca evitar al usar plataformas administradas.

GitHub Actions cobra por minutos de ejecución. Los ejecutores estándar son gratuitos en repositorios públicos; en repositorios privados con el plan gratuito de GitHub se incluye un crédito mensual de USD 12, equivalente a unos 2.000 minutos de Linux con 2 núcleos a USD 0,006 por minuto, y 500 MB de almacenamiento compartidos entre artefactos y paquetes. GitLab.com incluye 400 minutos mensuales en su plan gratuito, y su plan Premium cuesta USD 29 por usuario al mes con 10.000 minutos. Jenkins no tiene licencia, pero su costo es el servidor y el tiempo de mantenimiento. El costo real en GitHub depende de si el repositorio es público o privado y de cuántas ejecuciones lance el equipo, por lo que conviene reservar el análisis completo para las solicitudes de cambio y la rama principal.
