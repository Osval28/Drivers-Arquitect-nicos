# Prueba de concepto · ADR-15 Notification gateway (Resend)

**Objetivo.** Comprobar contra la API real de Resend lo que el ADR-15 promete y lo que el gateway del backend
(`ResendNotificationGateway`) da por hecho: que la clave de idempotencia evita correos duplicados en los reintentos,
que se puede saber si un correo fue entregado o rebotó sin confundir "enviado" con "entregado", que los errores se
clasifican bien para Retry y Circuit Breaker (ADR-16) y que los webhooks firmados se pueden validar.

**Requisitos relacionados:** RF-30 y RF-31.

**Tipo:** contra el servicio real. Usa `delivered@resend.dev` y `bounced@resend.dev`, direcciones de prueba de
Resend que simulan la entrega y el rebote sin enviar correo a nadie. Consume unos 4 correos de la cuota diaria.
C7 no necesita credenciales.

## Casos de prueba

| Caso | Qué se prueba | Factor o decisión del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Envío con `Idempotency-Key` | Canal de correo | 200 con `id` |
| C2 | Reintento con la misma clave | Reintentos sin duplicados | Devuelve el mismo `id` |
| C3 | Misma clave y otro contenido | Reintentos coherentes | 409 |
| C4 | Consulta de estado | Saber si fue entregado | `last_event` llega a `delivered` |
| C5 | Rebote | Saber si fue rechazado | `last_event` llega a `bounced`, nunca a `delivered` |
| C6 | Errores 4xx | Retry y Circuit Breaker solo ante fallas transitorias | Destinatario inválido y credencial inválida responden 4xx y la regla del gateway no los reintenta |
| C7 | Firma de webhooks | Eventos firmados | Firma de referencia de Svix aceptada; cuerpo alterado, marca de tiempo vieja y otro secreto rechazados |
| C8 | Webhooks reales | Eventos de entrega | Llegan `email.delivered` y `email.bounced` firmados; un reenvío del mismo evento se procesa una vez |

C8 es opcional porque Resend tiene que poder llegar al equipo. Para activarlo:

1. Exponer el puerto local (por defecto 8089) con un túnel, por ejemplo `cloudflared tunnel --url http://localhost:8089`.
2. Registrar la URL pública en *Resend → Webhooks* con los eventos `email.delivered` y `email.bounced`.
3. Copiar el *signing secret* del webhook en `RESEND_WEBHOOK_SECRET` y poner `RESEND_WEBHOOK_LISTEN=true`.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `ResendPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → ResendPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java ResendPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `RESEND_API_KEY` | Sí | Clave de API con permiso de envío |
| `RESEND_FROM` | Sí | Remitente de un dominio verificado, por ejemplo `DAZMA <no-reply@notificaciones.dazma.store>` |
| `RESEND_WEBHOOK_LISTEN` | No | `true` para C8 |
| `RESEND_WEBHOOK_SECRET` | Para C8 | Signing secret del webhook (`whsec_...`) |
| `RESEND_WEBHOOK_PORT` | No | Puerto del receptor. Por defecto `8089` |
| `RESEND_WEBHOOK_WAIT_SECONDS` | No | Espera de C8. Por defecto `90` |
| `RESEND_STATUS_WAIT_SECONDS` | No | Espera de C4 y C5. Por defecto `60` |
| `RESEND_TEST_DELIVERED` | No | Por defecto `delivered@resend.dev` |
| `RESEND_TEST_BOUNCED` | No | Por defecto `bounced@resend.dev` |
| `RESEND_API_URL` | No | Por defecto `https://api.resend.com` |
| `RESEND_PAUSE_MS` | No | Pausa entre llamadas para respetar el límite por segundo. Por defecto `700` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- La regla de reintento de C6 es la misma de `ResendNotificationGateway.isRetryable`: solo 429, 5xx o sin respuesta.
- La verificación de firma sigue el esquema de Svix que usa Resend: HMAC-SHA256 sobre `id.timestamp.cuerpo`, con
  tolerancia de 5 minutos. C7 usa el ejemplo publicado por Svix, así que su resultado no depende de la cuenta.
- La recepción de webhooks todavía no existe en el backend (ADR-15); C8 deja probado el mecanismo que se implementará.

## Resultados

Pendiente de ejecución con la cuenta de Resend. Pegar aquí el resumen del archivo generado en `resultados/`.
