# ADR-22: Modelo de ejecución y plataforma de despliegue

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-DIS-0006
- ESC-CAL-ESC-0003
- ESC-CAL-CON-0010
- ESC-CAL-CON-0011

## Contexto

El sistema necesita ser desplegado para producción en una o más plataformas que alojen la aplicación web, el backend y procesos en segundo plano, además de facilitar despliegues de nuevas versiones, configuración, logs, recuperación y crecimiento.

## Factores de decisión

- La plataforma o plataformas de despliegue deben permitir ejecutar la aplicación web, el backend y trabajos en segundo plano de forma estable
- Debe facilitar configuración por ambiente, revisión de salud del servicio, logs, escalamiento y recuperación cuando algo falle
- El costo debe verse completo: cómputo, base de datos, almacenamiento, transferencia, monitoreo, respaldos y capacidad necesaria durante actualizaciones

## Opciones

- **A.** PaaS / plataforma administrada de contenedores: Gestiona el despliegue, health checks, escalado horizontal, balanceo de carga y certificados sin que tengamos que administrar servidores, pero también se depende de los costos del proveedor
- **B.** Kubernetes administrado: Da mayor control sobre aspectos de la red y almacenamiento, pero esto requiere una complejidad de implementación mayor
- **C.** Máquinas virtuales administradas por el equipo: Es la opción con mayor control sobre todos los aspectos, pero también la que tiene mayor complejidad porque tendríamos que configurar cada característica manualmente

## Decisión

Se adopta una o más plataformas administradas para servicios web o contenedores, capaces de ejecutar la aplicación web, el backend y los procesos en segundo plano con variables por ambiente, logs, revisión de salud, dominios y escalamiento controlado. La aplicación web y el backend pueden desplegarse en plataformas distintas. Este modelo ofrece una relación adecuada entre capacidad de producción, costo operativo y facilidad de manejo. No se adopta una plataforma de orquestación compleja como decisión base porque exige una operación mayor para beneficios que no se necesitan en este punto de la arquitectura.

## Consecuencias

**Ventajas:** reduce carga de operación, permite desplegar servicios y procesos en segundo plano de forma ordenada, facilita logs, configuración, revisión de salud y escalamiento sin administrar toda la infraestructura manualmente. Encaja con una aplicación web, un backend, base de datos y procesos diferibles

**Desventajas:** genera dependencia de la plataforma elegida, sus límites, precios y forma de configurar servicios. El costo puede crecer con recursos, transferencia, almacenamiento y bases de datos administradas. Para reducir esa dependencia, las aplicaciones deben empaquetarse de forma portable, usar configuración externa y evitar funciones propietarias que no sean necesarias

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige usar una o más plataformas administradas para ejecutar la aplicación web, el backend y los procesos en segundo plano; la aplicación web y el backend pueden ir en plataformas distintas. Cada plataforma deberá encargarse de la puesta en marcha de los procesos, certificados, reinicios, revisión de funcionamiento, logs y asignación de capacidad, mientras DAZMA mantiene sus aplicaciones portables y su configuración separada del código.

Este modelo ofrece una relación adecuada entre capacidad de producción y carga operativa. Kubernetes administrado aporta más control sobre redes, nodos, almacenamiento y distribución de cargas, pero exige administrar clústeres, versiones, permisos, despliegues, balanceadores, monitoreo y recuperación. Como referencia, Amazon EKS y Google GKE publican una tarifa de USD 0,10 por clúster-hora, aproximadamente USD 73 mensuales, antes de cobrar nodos, almacenamiento, balanceadores, direcciones, transferencia y monitoreo. Esa tarifa no representa por sí sola una aplicación funcionando.

Una máquina virtual puede mostrar una factura básica menor, pero trasladaría al equipo los parches del sistema operativo, certificados, configuración del servidor, reinicios, monitoreo, respaldos y recuperación. Ese trabajo también forma parte del costo operativo aunque no aparezca como una línea en la factura.

La PaaS no siempre es la opción más barata, depende del volumen. Su precio crece con cada instancia, worker, base de datos, almacenamiento y transferencia. Se escoge porque cubre lo que necesita DAZMA con menos infraestructura que mantener. El costo real se calculará sumando todos los procesos y recursos necesarios, no se puede tomar una instancia gratuita o aislada como el costo de producción.
