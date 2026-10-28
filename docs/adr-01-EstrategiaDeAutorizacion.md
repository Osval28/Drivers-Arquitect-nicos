# ADR-01: Estrategia de autorización para recursos y operaciones críticas

**Estado:** Accepted

## Escenario(s) de calidad asociado(s)

- ESC-CAL-SEG-0004
- ESC-CAL-SEG-0008

## Contexto

DAZMA manejará publicaciones, propuestas, negociaciones, pagos y acciones administrativas. Un usuario puede estar autenticado y aun así no tener derecho a consultar o modificar un recurso específico. Por eso se necesita definir cómo se controlará el acceso a cada operación importante.

## Factores de decisión

- El método no necesita soportar una lógica de negocio con más validaciones distintas al rol y si el usuario es dueño del recurso porque el tamaño del proyecto no amerita todavía el soporte de muchos roles y permisos distintos
- El método debe poder distinguir entre usuarios porque, sin esto los recursos se exponen a que los modifique cualquiera que tenga el mismo rol del dueño del recurso

## Opciones

- **A.** RBAC únicamente: roles como comprador, vendedor y administrador determinan todos los permisos. Es simple, pero no distingue que dos usuarios con el mismo rol pueden tener acceso a recursos diferentes.
- **B.** RBAC + validación de ownership/participación: el rol concede permisos generales y DAZMA verifica si el usuario es dueño, participante o actor autorizado sobre el recurso concreto.
- **C.** ABAC / Policy Engine externo: decisiones basadas en atributos y políticas centralizadas. Es más expresivo, pero introduce mayor complejidad y una dependencia adicional.

## Decisión

Se adopta RBAC para permisos generales, complementado con validaciones server-side de propiedad, participación y contexto. Se aplicarán mínimo privilegio y deny by default. La autenticación puede apoyarse en un Identity Provider estándar, pero las reglas de autorización del negocio permanecerán en DAZMA. Un Policy Engine externo sólo se reconsiderará si las reglas llegan a ser suficientemente complejas para justificarlo.

## Consecuencias

**Ventajas:** reduce el riesgo de acceso a recursos ajenos, mantiene las reglas de autorización cerca del dominio y facilita administrar permisos generales mediante roles.

**Desventajas:** RBAC por sí solo no es suficiente; se deben diseñar y mantener reglas de ownership/participación. Aumenta la cantidad de pruebas de seguridad necesarias y cualquier nueva operación crítica debe incorporarse a la matriz de autorización. Si la lógica se dispersa entre módulos, puede volverse inconsistente, por lo que conviene centralizar políticas comunes sin sacar del dominio las reglas específicas.

## Spike: ¿Cuál es la mejor opción y por qué?

Se elige combinar permisos generales por rol con una comprobación de propiedad o participación sobre cada recurso. El rol indicará qué clases de acciones puede intentar una persona: por ejemplo, publicar, participar en una negociación o realizar una acción administrativa. Antes de ejecutar la operación, el backend comprobará además si esa persona es dueña del recurso o participa en la negociación correspondiente. Si alguna de las dos condiciones no se cumple, la solicitud será rechazada.

Esta opción cumple con los dos factores de decisión sin agregar un mecanismo que DAZMA todavía no necesita. Usar únicamente RBAC permitiría distinguir perfiles generales, pero no impediría que dos personas con el mismo rol accedieran a los recursos de la otra. Un vendedor, por ejemplo, no debe poder editar cualquier publicación solo porque comparte el rol de vendedor con su propietario. La comprobación adicional sobre el recurso resuelve esa diferencia sin obligar a crear roles artificiales como “vendedor de la publicación 125” o permisos individuales para cada registro.

No se selecciona un motor externo de políticas basado en atributos. Esa opción puede representar reglas más numerosas y variables, pero para el alcance descrito obligaría a mantener otro servicio o componente, trasladar atributos hacia él, aprender su forma de expresar políticas y diagnosticar decisiones tomadas fuera de la lógica principal. Los factores solo exigen distinguir el rol y la relación del usuario con el recurso, por lo que ese costo de implementación, pruebas y operación no aporta una capacidad necesaria.

Las validaciones se ejecutarán en el backend para cada operación protegida. Ocultar botones o rutas en el frontend mejora la experiencia, pero no constituye una autorización porque una solicitud puede enviarse directamente a la API. El backend recibirá la identidad autenticada, consultará el rol y comprobará la propiedad o participación antes de leer, modificar o ejecutar la acción solicitada. Cuando falte una regla o información suficiente, el acceso se negará en lugar de asumirse como permitido.
