# Prueba de concepto · ADR-33 Memoria caché (Redis administrado)

**Objetivo.** Comprobar contra el Redis contratado lo que decidió el ADR-33: que guarda varios tipos de datos, que
las entradas vencen a los 10 minutos con un prefijo versionado (`dazma:publications:v1:`), que tiene copia en disco
y control de memoria, que responde rápido desde donde corre el backend, que el patrón cache-aside con invalidación
funciona y que una caída de Redis no tumba la consulta.

**Escenarios relacionados:** ESC-CAL-ESC-0003, ESC-CAL-CON-0010 y ESC-CAL-CON-0011.

**Tipo:** contra el servicio real, con la misma URL que el backend lee del secreto `dazma/prod/cache` (`cache.url`).
Las claves de la prueba usan el prefijo `dazma:poc:publications:v1:<ejecución>:` y se borran al final.

## Casos de prueba

| Caso | Qué se prueba | Factor o decisión del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | Conexión con la URL del backend | Servicio administrado y protegido | PING → PONG; se informa versión y TLS |
| C2 | Texto, hash, lista, conjunto y contador | Varios tipos de datos | 5/5 correctos |
| C3 | `SET ... EX 600` con prefijo versionado | Vencimiento de 10 min | TTL entre 590 y 600 s |
| C4 | Persistencia | Copia en disco ante reinicios | `appendonly=yes` o `save` configurado (o, si CONFIG está restringido, guardados reales en INFO) |
| C5 | Límite y política de memoria | Control de memoria y entradas eliminadas | Política distinta de `noeviction`; se informa `evicted_keys` |
| C6 | 200 lecturas de 2 KB | Tiempo de respuesta | p95 ≤ `REDIS_MAX_P95_MS` |
| C7 | Cache-aside e invalidación | Patrón decidido en el ADR | 1.ª lectura a la base, 2.ª desde Redis, tras invalidar se lee el dato nuevo |
| C8 | Redis inalcanzable | ESC-CAL-CON-0011 | La lectura responde desde la base dentro del tiempo máximo de espera |
| C9 | Backend desplegado (opcional) | Integración real | Tras consultar una publicación existe su clave con TTL ≤ 600 s |
| C10 | Limpieza | | Las claves de la prueba se eliminan |

En C7 y C8 la base de datos se representa con un repositorio en memoria con una latencia configurable, porque la
prueba no usa el driver de PostgreSQL. Lo que se valida es el comportamiento del patrón (cuándo se va a la base,
cuándo se guarda, cuándo se invalida y qué pasa si Redis falla), igual al de `RedisPublicationCacheAdapter`.
C9 sí mide el backend real.

## Verificación de la propia prueba

Se ejecutó contra un Redis 7.0.15 local en tres configuraciones para comprobar que los casos detectan problemas:
con AOF y `allkeys-lru` pasaron todos; sin persistencia falló C4 y con `noeviction` falló C5; con una clave
equivocada falló C1. Esos resultados son de un Redis local, no del servicio contratado.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `RedisCachePoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → RedisCachePoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java RedisCachePoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `REDIS_URL` | Sí | `rediss://usuario:clave@host:puerto` (o `redis://` sin TLS) |
| `REDIS_TIMEOUT_MS` | No | Tiempo máximo de conexión y lectura. Por defecto `500`, como `spring.data.redis.timeout` |
| `REDIS_KEY_PREFIX` | No | Prefijo de las claves de prueba. Por defecto `dazma:poc:publications:v1:` |
| `REDIS_TTL_SECONDS` | No | Vencimiento. Por defecto `600` |
| `REDIS_MAX_P95_MS` | No | Umbral de C6. Por defecto `50` |
| `REDIS_SAMPLES` | No | Lecturas de C6. Por defecto `200` |
| `REDIS_DB_LATENCY_MS` | No | Latencia simulada de la base en C7 y C8. Por defecto `30` |
| `REDIS_UNREACHABLE_URL` | No | Dirección sin Redis para C8. Por defecto `redis://10.255.255.1:6379` |
| `REDIS_BACKEND_PUBLICATION_URL` | Para C9 | URL del detalle de una publicación en el backend desplegado |
| `REDIS_BACKEND_PUBLICATION_ID` | Para C9 | Identificador de esa publicación |
| `REDIS_BACKEND_COOKIE` | No | Cookie de sesión si el endpoint la exige |
| `REDIS_BACKEND_KEY_PREFIX` | No | Prefijo del backend. Por defecto `dazma:publications:v1:` |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- La latencia de C6 depende de la red entre el equipo y Redis. Ejecutada desde un portátil mide la red del
  portátil; el dato representativo es el que se obtiene desde la misma región del backend.
- Algunos proveedores administrados restringen `CONFIG` o `INFO`. En ese caso C4 o C5 quedan OMITIDOS con el
  motivo y hay que confirmar el dato en la consola del proveedor.

## Resultados

Pendiente de ejecución con el Redis contratado. Pegar aquí el resumen del archivo generado en `resultados/`.
