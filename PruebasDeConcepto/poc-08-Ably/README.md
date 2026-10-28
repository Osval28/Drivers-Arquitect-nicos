# Prueba de concepto · ADR-08 Proveedor de chat (Ably)

**Objetivo.** Comprobar contra una cuenta real de Ably lo que el ADR-08 delega en el proveedor y lo que DAZMA debe
controlar: cada conversación es una sala ligada a una negociación, el backend solo autoriza a sus participantes,
y Ably se encarga del transporte en tiempo real, el historial, la deduplicación, la presencia y la reconexión.

**Escenarios relacionados:** ESC-CAL-REN-0010 (mensaje disponible para la otra parte en ≤ 2 s), ESC-CAL-SEG-0004,
ESC-CAL-ESC-0003 y ESC-CAL-INT-0002.

**Tipo:** contra el servicio real. Crea dos salas con nombre aleatorio (`dazma-poc:negociacion-xxxx-a` y `-b`),
envía unos 30 mensajes y no deja nada configurado. Consume pocas unidades de mensaje del plan.

## Cómo se modela la negociación

La prueba hace el papel del backend: con la clave de Ably firma un token JWT para cada participante. El token
solo da permisos (`publish`, `subscribe`, `history`, `presence`) sobre la sala de su negociación y fija su
`clientId`. El comprador y el vendedor usan ese token, nunca la clave. Un tercer usuario recibe un token de otra
negociación para comprobar el aislamiento.

## Casos de prueba

| Caso | Qué se prueba | Factor o escenario | Criterio de éxito |
|---|---|---|---|
| C1 | Publicar en la sala propia | Conversaciones privadas ligadas a una negociación | HTTP 201 con el token del participante |
| C2 | Aislamiento entre negociaciones | ESC-CAL-SEG-0004 | 3 de 3 intentos sobre una sala ajena rechazados |
| C3 | Identidad del remitente | Participantes gestionados por el proveedor | Publicar con el `clientId` de otro se rechaza; el historial muestra el remitente real |
| C4 | Historial | Historial nativo | Los mensajes vuelven completos y en orden |
| C5 | Deduplicación | Deduplicación automática | Dos envíos con el mismo `id` dejan un solo mensaje |
| C6 | Latencia extremo a extremo | ESC-CAL-REN-0010 | Todos los mensajes llegan por WebSocket en ≤ 2000 ms |
| C7 | Presencia | Presencia nativa | El vendedor conectado aparece presente para el comprador |
| C8 | Reconexión | Reconexión nativa | Los 3 mensajes enviados durante la desconexión se recuperan al reconectar |

C6 mide desde que el comprador envía el mensaje hasta que el vendedor lo recibe por WebSocket, en el mismo equipo,
así que no depende de relojes sincronizados. La red del equipo donde se ejecuta influye en el resultado; conviene
anotarla junto al resultado.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `AblyChatPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → AblyChatPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java AblyChatPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `ABLY_API_KEY` | Sí | Clave de la app (`appId.keyId:secreto`) con permisos sobre `dazma-poc:*` |
| `ABLY_MAX_LATENCY_MS` | No | Umbral de C6. Por defecto `2000`, el del escenario REN-0010 |
| `ABLY_LATENCY_MESSAGES` | No | Mensajes medidos en C6. Por defecto `20` |
| `ABLY_CHANNEL_PREFIX` | No | Prefijo de las salas. Por defecto `dazma-poc` |
| `ABLY_CHANNEL_SUFFIX` | No | Sufijo de las salas. Ably Chat usa `::$chat` en el canal de cada sala |
| `ABLY_REST_URL` | No | Por defecto `https://rest.ably.io` |
| `ABLY_REALTIME_URL` | No | Por defecto `wss://realtime.ably.io` |
| `ABLY_PROTOCOL_VERSION` | No | Versión del protocolo realtime. Por defecto `2` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- La prueba usa la API REST y el protocolo realtime JSON directamente para no depender de librerías. Las salas de
  Ably Chat funcionan sobre estos mismos canales, así que permisos, historial y entrega se comportan igual; los
  componentes React del UI Kit pertenecen al frontend y no se prueban aquí.
- La clave de Ably nunca sale del "backend": solo se envían tokens firmados con vigencia de una hora.
- Retención: el historial que se consulta es el del plan de la cuenta (24 h en Free, 72 h por defecto en Standard).

## Resultados

Pendiente de ejecución con la cuenta de Ably del proyecto. Pegar aquí el resumen del archivo generado en `resultados/`.
