# ADR-10: Selección del paradigma de la base de datos: Relacional o no relacional

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-CON-0001
- ESC-CAL-CON-0005
- ESC-CAL-AUD-0008

## Contexto

El núcleo de DAZMA se basa en información relacionada: usuarios, publicaciones, propuestas, negociaciones, pagos e historial. Se necesita decidir qué tipo de modelo de datos permite guardar y consultar esa información de forma consistente.

## Factores de decisión

- El modelo de datos debe representar correctamente las relaciones entre usuarios, publicaciones, propuestas, confirmaciones, pagos e historial
- Debe proteger la integridad de la información para evitar datos sueltos, estados inválidos o relaciones que no deberían existir
- También debe permitir consultas frecuentes del negocio, como buscar operaciones por usuario, publicación, estado o historial, sin dificultar el crecimiento futuro del sistema

## Opciones

- **A.** SQL: Funcionan muy bien para sistemas relacionales donde las entidades siempre tienen la misma estructura, diseñados para consultas y transacciones complejas, lenguaje de consulta estándar, sobresalen cuando se necesitan integridad de datos y comportamiento transaccional. Sin embargo, el escalamiento horizontal exige más diseño (réplicas, particionamiento o servicios administrados) y el procesamiento de datos a gran escala y alta velocidad puede ser costoso
- **B.** NoSQL: Tienen esquemas más flexibles como documentos, clave-valor, columna ancha, series de tiempo y búsqueda vectorial; diseñado para modelos de datos en evolución que no tienen una estructura fija, soporta con facilidad el escalamiento horizontal a través de funcionar con nodos, lo cual es muy útil para repartir la carga y así mantener el rendimiento, optimizado para lectura y escritura a gran escala y alta velocidad. Sin embargo, no tiene un lenguaje universal que funcione para todos los motores, tiene consistencia eventual (lo que puede significar datos desactualizados en algunas réplicas), muchos motores no tienen soporte para transacciones ACID, tienen ecosistemas más pequeños en comparación con las principales alternativas SQL

## Decisión

SQL porque es la decisión que más se ajusta al caso de negocio; la plataforma necesita consistencia, soporte para transacciones ACID para que las negociaciones tengan un estado válido siempre, ya que, o se hace una transacción completa o no se hace nada, eliminando la posibilidad de que haya estados inconsistentes. Además, las negociaciones, propuestas, usuarios, contraofertas y pagos que son varias de las entidades más importantes del sistema, tienen una estructura fija.

## Consecuencias

**Ventajas:** Hay relaciones con un esquema fijo que permite realizar consultas complejas con mayor facilidad, tiene un fuerte soporte para transacciones ACID, consistencia (No eventual), lenguaje estándar en cualquier motor

**Desventajas:** Sacrifica flexibilidad en cómo se guardan los datos y facilidad para procesar a gran escala con alta velocidad; la tolerancia a fallos y la escalabilidad horizontal exigen más diseño (réplicas, particionamiento o servicios administrados)

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige utilizar un modelo relacional y SQL para el núcleo de DAZMA. Usuarios, publicaciones, propuestas, participantes, negociaciones, estados, pagos e historial mantienen relaciones identificables y requieren reglas que impidan referencias inexistentes o combinaciones inválidas. Las claves, relaciones y restricciones se declararán en la base, y las operaciones que modifican varios registros relacionados podrán confirmarse o deshacerse como una unidad.

La elección sale del tipo de información y de consultas que tiene DAZMA, no de que las bases NoSQL no tengan transacciones o consistencia: motores documentales actuales como MongoDB soportan transacciones ACID entre varios documentos. Sin embargo, su ventaja aparece cuando la información se consulta y se modifica naturalmente como documentos completos o cuando el esquema cambia seguido, y en DAZMA las consultas por usuario, publicación, negociación, estado, fecha y pago pasan por relaciones que el modelo relacional expresa directamente.

Una base de datos orientada a documentos podría guardar información relacionada dentro de un mismo documento, pero tendría que duplicar ciertos datos o actualizar varios documentos cuando una entidad participe en distintos procesos. Una base clave-valor es adecuada cuando se conoce la clave de acceso y el valor puede tratarse como una unidad, pero no cubre con la misma claridad las relaciones y consultas del marketplace. Usar varios motores especializados aumentaría credenciales, respaldos, monitoreo y conocimiento operativo cuando todavía no hay una necesidad que lo justifique.

SQL permite utilizar claves foráneas para impedir registros huérfanos, restricciones únicas para evitar duplicados e índices para las búsquedas frecuentes. Los atributos realmente variables pueden almacenarse en estructuras semiestructuradas cuando el motor lo permita, sin convertir todo el núcleo en documentos. También existen réplicas, particionamiento y servicios relacionales administrados; por lo que el escalamiento horizontal es posible con más diseño.
