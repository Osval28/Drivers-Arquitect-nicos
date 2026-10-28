# Prueba de concepto · ADR-35 Proveedor del baúl de secretos (AWS Secrets Manager)

**Objetivo.** Comprobar en la cuenta real de AWS los factores del ADR-35 con la misma credencial de IAM que el
backend tiene en Render: que lee sus seis secretos y que cada uno trae lo que espera `application-prod.properties`,
que esa credencial no puede leer ni enumerar otros secretos, que los parámetros no secretos están en Parameter Store
y que cada lectura queda registrada para auditoría.

**Escenarios relacionados:** ESC-CAL-SEG-0008 y ESC-CAL-INT-0002.

**Tipo:** contra el servicio real, solo con operaciones de lectura. No crea, modifica ni borra secretos. Los
valores nunca se imprimen ni se guardan; el resultado solo lista los nombres de las claves de cada secreto.

## Casos de prueba

| Caso | Qué se prueba | Factor del ADR | Criterio de éxito |
|---|---|---|---|
| C1 | `sts:GetCallerIdentity` | Credencial propia de la aplicación | Se identifica el usuario o rol (cuenta enmascarada) |
| C2 | `GetSecretValue` de los 6 secretos | Consumo desde el backend | 6/6 leídos, con todas las claves esperadas y sin valores vacíos; se reporta el tiempo total de lectura al arrancar |
| C3 | Leer un secreto fuera de `dazma/prod/` | Permisos por secreto | `AccessDeniedException` |
| C4 | `ListSecrets` | Cada servicio accede solo a lo suyo | `AccessDeniedException` |
| C5 | `GetParametersByPath /dazma/prod/` | Parámetros no secretos en Parameter Store | Al menos un parámetro |
| C6 | `cloudtrail:LookupEvents` | Registro de quién accedió a cada secreto | Lecturas de `dazma/...` hechas por la identidad del backend |

Claves esperadas por secreto (tomadas de `application-prod.properties`, configurables con `SECRETS_EXPECTED`):

| Secreto | Claves |
|---|---|
| `dazma/prod/database` | `username`, `password`, `host`, `port`, `dbname` |
| `dazma/prod/firebase` | `serviceAccount` |
| `dazma/prod/r2` | `accessKeyId`, `secretAccessKey` |
| `dazma/prod/observability` | `otlpEndpoint`, `otlpAuthorization` |
| `dazma/prod/resend` | `apiKey` |
| `dazma/prod/cache` | `url` |

C6 usa una segunda credencial con permiso de auditoría, porque la del backend no debe poder consultar CloudTrail.
CloudTrail tarda hasta unos 15 minutos en mostrar los eventos: si C6 falla justo después de C2, se repite la
ejecución más tarde. La firma de las peticiones (Signature Version 4) se validó con los vectores de prueba de AWS.

## Cómo ejecutarla

Solo se necesita JDK 21 o superior. La prueba es un único archivo Java sin Maven ni librerías externas.
Ningún valor sensible está escrito en el código: todo se lee de variables de entorno.

**IntelliJ IDEA**

1. *File → Open* y seleccionar esta carpeta (`ProofOfConcept`).
2. Clic derecho sobre la carpeta → *Mark Directory as → Sources Root* y elegir un JDK 21+ en *Project Structure*.
3. Abrir `SecretsManagerPoc.java` y pulsar la flecha verde junto a `main`. Se crea la configuración de ejecución.
4. *Run → Edit Configurations… → SecretsManagerPoc*:
   - *Working directory*: esta carpeta.
   - *Environment variables*: abrir el editor y pegar las líneas de `.env.example` con sus valores.
5. Ejecutar de nuevo.

**VS Code** (Extension Pack for Java): abrir esta carpeta, pulsar *Run* sobre `main` y agregar las variables en
`launch.json`, dentro de `"env": { ... }`.

**Terminal**: definir las variables y ejecutar `java SecretsManagerPoc.java` desde esta carpeta.

Cada ejecución imprime los casos en consola y guarda `resultados/resultado-AAAAMMDD-HHMMSS.md`.
Ese archivo es la evidencia que se sube al repositorio. Si falta una variable, el caso que la necesita
queda como **OMITIDO** con el nombre de la variable; nunca se marca como aprobado.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `AWS_REGION` | Sí | Región de los secretos |
| `AWS_ACCESS_KEY_ID` | Sí | La misma clave de IAM que usa el backend en Render |
| `AWS_SECRET_ACCESS_KEY` | Sí | Su secreto |
| `AWS_SESSION_TOKEN` | No | Solo si se usan credenciales temporales |
| `AUDIT_AWS_ACCESS_KEY_ID` | Para C6 | Credencial con `cloudtrail:LookupEvents` |
| `AUDIT_AWS_SECRET_ACCESS_KEY` | Para C6 | Su secreto |
| `AUDIT_AWS_SESSION_TOKEN` | No | Solo si es temporal |
| `SECRETS_AUDIT_SINCE_MINUTES` | No | Ventana de búsqueda en CloudTrail. Por defecto `120` |
| `SECRETS_FORBIDDEN_ID` | No | Secreto fuera del alcance para C3. Por defecto `dazma/otro-servicio/poc-no-autorizado` |
| `SECRETS_EXPECTED` | No | Secretos y claves esperadas, con el formato `id=clave1,clave2;id2=...` |
| `PARAMETER_STORE_PATH` | No | Ruta de los parámetros. Por defecto `/dazma/prod/` |
| `AWS_ENDPOINT_URL` | No | Reemplaza los endpoints públicos de AWS, igual que en los SDK (por ejemplo, para un entorno local) |
| `POC_RESULTS_DIR` | No | Carpeta de resultados. Por defecto `resultados` |

## Notas

- C3 distingue dos respuestas: `AccessDeniedException` significa que la política limita la lectura a los secretos
  del backend; `ResourceNotFoundException` significa que la política deja intentar leer fuera de `dazma/prod/`.
- Costo: con seis secretos el ADR estima unos USD 2,40 al mes. Cada arranque del backend hace seis lecturas;
  C2 deja el tiempo que suman, que es lo que tarda de más el arranque por leer el baúl.

## Resultados

Pendiente de ejecución con las credenciales de AWS. Pegar aquí el resumen del archivo generado en `resultados/`.
