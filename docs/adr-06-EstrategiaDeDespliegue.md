# ADR-06: Estrategia de despliegue con mínima indisponibilidad

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-DIS-0006

## Contexto

La plataforma tendrá cambios y nuevas versiones durante su evolución. Es necesario definir cómo publicar esas actualizaciones reduciendo el impacto sobre los usuarios y evitando que un despliegue defectuoso deje fuera de servicio los flujos críticos.

## Factores de decisión

- La estrategia de despliegue debe permitir actualizar la plataforma pausando el funcionamiento de las funciones críticas durante el menor tiempo posible
- Antes de enviar usuarios a una versión nueva, debe comprobarse que esa versión está funcionando correctamente
- También debe existir una forma práctica de retirar una versión defectuosa y volver a una versión estable, cuidando que los cambios en base de datos y contratos no rompan la operación

## Opciones

- **A.** In-place: se reemplaza directamente la versión activa. Es simple, pero tiene mayor riesgo de interrupción visible y rollback lento
- **B.** Rolling Deployment: reemplaza instancias gradualmente con health/readiness checks. Reduce downtime, aunque durante un periodo pueden convivir versiones
- **C.** Blue/Green: mantiene dos ambientes completos y cambia el tráfico. Facilita rollback, pero duplica recursos temporalmente
- **D.** Canary: expone gradualmente la versión nueva a una parte del tráfico. Reduce el riesgo de cambios grandes, pero exige observabilidad y control de tráfico más avanzados

## Decisión

Se adopta Rolling Deployment, acompañado de health/readiness checks, rollback y migraciones compatibles entre versiones. Blue/Green o Canary se adoptarán únicamente si el objetivo de disponibilidad o el riesgo del cambio demuestra que Rolling no es suficiente.

## Consecuencias

**Ventajas:** disminuye la interrupción y hace el rollback más seguro sin exigir duplicar siempre toda la infraestructura

**Desventajas:** la coexistencia temporal de versiones obliga a mantener compatibilidad de APIs, esquemas y otras configuraciones.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige rolling deployment: la plataforma va agregando instancias de la versión nueva poco a poco, revisa que estén listas para recibir solicitudes y después retira las de la versión anterior. Si las comprobaciones fallan, el despliegue se detiene y se vuelve a la versión estable. Mientras convivan las dos versiones, los cambios de base de datos y de contratos tienen que ser compatibles con ambas.

No se selecciona un reemplazo in-place porque detener o sustituir directamente la única versión activa produce una interrupción que el usuario puede ver y deja menos margen para comprobar la nueva versión antes de atender usuarios. Rolling permite mantener una parte del servicio disponible durante el reemplazo y detectar un fallo antes de retirar toda la versión anterior.

Blue/Green facilita un cambio de tráfico y una reversión rápidos, pero mantiene dos ambientes completos durante la publicación. En términos de cómputo de la aplicación, esto puede acercarse temporalmente al doble de capacidad, además de duplicar configuración y verificaciones. Canary expone la versión nueva a un porcentaje del tráfico y permite comparar resultados, pero exige dirigir tráfico por versión, separar métricas y definir criterios estadísticos de promoción. Por ahora ninguna de esas capacidades es un requisito de DAZMA. Rolling cumple los factores de indisponibilidad mínima, comprobación previa y retirada práctica de una versión defectuosa con menos infraestructura simultánea.

La comprobación verifica que el proceso esté activo y que se encuentre preparado para recibir tráfico. Una instancia nueva no debe atender usuarios mientras no pueda usar las dependencias necesarias para responder correctamente. Al retirar una instancia anterior, deja de recibir solicitudes nuevas y terminará las que ya tiene en curso dentro del tiempo permitido por la plataforma.

La reversión del código no resuelve por sí sola una modificación incompatible de datos. Por eso los cambios del esquema se realizarán de modo que la versión anterior y la nueva puedan operar durante el reemplazo: primero se incorporan estructuras compatibles, después se publica el código y únicamente cuando ninguna versión anterior las necesite se retiran las estructuras antiguas. Esta condición es necesaria para que rolling no interrumpa la operación.
