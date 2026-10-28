# ADR-11: Selección del motor de base de datos relacional para el núcleo transaccional

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-AUD-0008

## Contexto

Después de definir que la persistencia de datos requiere un enfoque relacional, DAZMA necesita escoger un motor de base de datos que soporte las operaciones principales del negocio con confiabilidad, buen costo y facilidad de administración.

## Factores de decisión

- El motor de base de datos debe soportar transacciones ACID porque se necesita garantizar que haya estados sin cambios parciales ni contradictorios
- Debe ofrecer soporte para reglas de integridad, consultas, índices, copias de seguridad y recuperación ante fallas
- El costo de licencia del motor debe ser conocido y bajo, porque el proyecto necesita evaluar cuánto ofrece el motor de base de datos frente a lo que cuesta

## Opciones

- **A.** PostgreSQL: motor relacional con transacciones, control de concurrencia MVCC, restricciones e índices; además ofrece tipos JSON/JSONB para datos semiestructurados sin abandonar el modelo relacional.
- **B.** MySQL con InnoDB: motor ampliamente utilizado, con transacciones ACID, MVCC, bloqueo a nivel de fila y claves foráneas. Puede satisfacer los requisitos transaccionales del núcleo.
- **C.** Oracle Database: solución relacional empresarial con transacciones ACID y herramientas maduras de operación. Su amplitud de capacidades puede resultar mayor que la necesidad actual y su adopción debe evaluarse según el contexto real del proyecto.

## Decisión

Se adopta PostgreSQL como motor de base de datos. La elección se fundamenta en que permite aplicar las tácticas de consistencia e idempotencia requeridas, posee control de concurrencia transaccional y permite combinar estructuras relacionales con JSONB cuando una parte del modelo lo necesite. MySQL/InnoDB permanece como alternativa técnicamente válida. Oracle tendría sentido si futuros condicionantes organizacionales, soporte o infraestructura justifican su adopción.

## Consecuencias

**Ventajas:** permite concretar y medir las tácticas de transacciones, idempotencia, restricciones y concurrencia sobre una tecnología real. PostgreSQL mantiene flexibilidad para datos relacionales y ciertos datos semiestructurados sin introducir inmediatamente otra base de datos.

**Desventajas:** seleccionar un motor implica decisiones de índices, migraciones, administración y operación específicas. La elección no elimina la necesidad de diseñar correctamente las transacciones y estados en la aplicación.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige PostgreSQL como motor relacional del núcleo transaccional de DAZMA. Ofrece transacciones ACID, control de concurrencia mediante MVCC, restricciones de integridad, índices, claves foráneas y capacidades de consulta suficientes para mantener consistentes los estados de publicaciones, negociaciones, pagos y auditoría. Su soporte de JSONB permite conservar algunos datos semiestructurados dentro del mismo motor cuando el modelo lo justifique, sin reemplazar las relaciones y restricciones que protegen las operaciones principales.

MySQL con InnoDB también cumple las necesidades esenciales de transacciones, concurrencia y claves foráneas, por lo que es una alternativa técnicamente válida. PostgreSQL se elige porque reúne esas capacidades con un conjunto amplio de restricciones, tipos de datos e índices dentro de una única edición abierta, sin crear una dependencia de licenciamiento comercial. Oracle tiene herramientas empresariales maduras, pero DAZMA no necesita ninguna capacidad exclusiva de Oracle que justifique pagar su licenciamiento y asumir una administración y soporte más complejos.

PostgreSQL no cobra licencia: su licencia permite usar, copiar, modificar y distribuir el motor sin pago, incluso en software comercial. MySQL Community también es gratuito, de modo que el costo de licencia no es la diferencia decisiva entre esas dos opciones.
