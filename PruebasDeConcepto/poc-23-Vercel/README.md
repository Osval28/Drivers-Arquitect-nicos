# Prueba de concepto · ADR-23 PaaS del frontend (Vercel)

**Objetivo.** Comprobar sobre el frontend publicado y su proyecto en Vercel los factores del ADR-23: dominio propio
con conexión segura, páginas públicas servidas desde la CDN con buen tiempo de respuesta, vistas previas por cambio,
configuración por ambiente y que el navegador no reciba secretos ni reglas sensibles.

**Escenarios relacionados:** ESC-CAL-DIS-0006, ESC-CAL-ESC-0003, ESC-CAL-CON-0010, ESC-CAL-CON-0011 y, en C4,
ESC-CAL-SEG-0008.

**Tipo:** contra el servicio real, solo lecturas. C1–C5 usan la URL pública; C6–C8 la API de Vercel.

## Casos de prueba

| Caso | Qué se prueba | Factor del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Página principal | Dominio propio y conexión segura | 200 por HTTPS, servida por Vercel, con HSTS |
| C2 | `http://` | Conexión segura | Redirige a `https://` |
| C3 | Páginas públicas repetidas | Rendimiento de las páginas públicas del marketplace | 200, repeticiones con `x-vercel-cache` HIT/STALE/PRERENDER y p95 ≤ umbral |
| C4 | HTML y JavaScript del navegador | No exponer secretos en el navegador | 0 coincidencias de llaves privadas, claves de AWS/Resend, secretos de webhook, URLs con credenciales o nombres de variables de servidor |
| C5 | URL del backend en producción | URLs configuradas por ambiente | Contiene la URL esperada y no la de otro ambiente |
| C6 | Despliegues del proyecto | Vistas previas por cambio | Al menos un `preview` y un `production` en estado READY |
| C7 | Variables del proyecto (solo nombres) | Configuración por ambiente | Variables distintas por ambiente; ninguna `NEXT_PUBLIC_` con nombre de secreto |
| C8 | Registros del último despliegue | Revisar despliegues y errores | Hay líneas de registro de construcción |

La clave web de Firebase (`AIza...`) aparece en el JavaScript de cualquier app de Firebase y es pública por diseño;
C4 la menciona pero no la cuenta como hallazgo. Los valores encontrados nunca se imprimen, solo el tipo y el archivo.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `VercelFrontendPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → VercelFrontendPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java VercelFrontendPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `VERCEL_APP_URL` | Para C1–C5 | URL pública del frontend, por ejemplo `https://app.dazma.store` |
| `VERCEL_PUBLIC_PATHS` | No | Páginas públicas separadas por coma. Por defecto `/` |
| `VERCEL_REPETITIONS` | No | Repeticiones por página en C3. Por defecto `10` |
| `VERCEL_MAX_P95_MS` | No | Umbral de C3. Por defecto `1500` |
| `VERCEL_MAX_SCRIPTS` | No | Máximo de JavaScript revisados en C4. Por defecto `60` |
| `VERCEL_EXPECTED_API_URL` | Para C5 | URL de la API que debe usar producción |
| `VERCEL_FORBIDDEN_API_URL` | No | URL de otro ambiente que no debe aparecer en producción |
| `VERCEL_TOKEN` | Para C6–C8 | Token de acceso de Vercel con lectura del proyecto |
| `VERCEL_PROJECT_ID` | Para C6–C8 | Identificador del proyecto (`prj_...`) o su nombre |
| `VERCEL_TEAM_ID` | No | Equipo, si el proyecto pertenece a uno |
| `VERCEL_API_URL` | No | Por defecto `https://api.vercel.com` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Resultados

Pendiente de ejecución contra el frontend publicado. Pegar aquí el resumen del archivo generado en `resultados/`.
