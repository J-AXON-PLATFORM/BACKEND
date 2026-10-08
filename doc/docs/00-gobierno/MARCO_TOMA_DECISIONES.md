# Marco universal para toma de decisiones

> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla base  
> **Ámbito:** Cualquier decisión relevante de producto, arquitectura, código, datos, seguridad, pruebas, infraestructura, operación o tecnología.

## Propósito

Evitar decisiones automáticas, arbitrarias o basadas únicamente en preferencias de la IA. Ante una decisión relevante, la IA debe estudiar el caso real, identificar alternativas viables, comparar sus consecuencias y recomendar una opción explicando por qué se ajusta mejor al contexto.

Este documento no prescribe tecnologías ni patrones concretos. Define **cómo decidir**.

## Cuándo se activa

Aplicar este marco cuando exista más de un camino razonable o cuando una decisión pueda afectar alguno de estos aspectos:

- arquitectura o límites entre módulos;
- lenguajes, frameworks, librerías o versiones;
- patrones de diseño o estructura de código;
- persistencia, migraciones, caché o mensajería;
- contratos entre componentes o integraciones externas;
- autenticación, autorización, privacidad o criptografía;
- rendimiento, concurrencia, escalabilidad o disponibilidad;
- pruebas, automatización o herramientas de QA;
- infraestructura, contenedores, nube, CI/CD o despliegue;
- observabilidad, recuperación, operación o costes;
- experiencia de usuario, accesibilidad o estrategia de frontend;
- cualquier cambio difícil de revertir o con impacto transversal.

No es necesario usar todo el marco para una decisión local, obvia y fácilmente reversible.

## Proceso obligatorio

### 1. Definir el problema

La IA debe describir qué se necesita resolver, no comenzar proponiendo una tecnología.

Debe identificar:

- objetivo;
- problema actual;
- resultado esperado;
- alcance de la decisión;
- módulos o actores afectados.

### 2. Reunir contexto y restricciones

Antes de recomendar, revisar solo el contexto necesario según `docs/16-contexto/POLITICA_CONTEXTO_IA.md`.

Considerar, cuando aplique:

- requisitos funcionales y no funcionales;
- escala y carga esperada;
- experiencia del equipo;
- presupuesto;
- infraestructura existente;
- dependencias actuales;
- seguridad y cumplimiento;
- compatibilidad;
- plazo y complejidad operativa;
- mantenibilidad y evolución prevista;
- necesidad de portabilidad o independencia de proveedor.

Si falta información que puede cambiar materialmente la decisión, la IA debe señalarla. Si el usuario no conoce la respuesta, debe ayudar a descubrirla con preguntas concretas o escenarios, no exigir conocimiento técnico previo.

### 3. Generar alternativas reales

No presentar una única opción como inevitable cuando existen caminos razonables.

Por cada alternativa indicar:

- descripción;
- qué problema resuelve;
- ventajas;
- desventajas;
- complejidad;
- riesgos;
- costes relevantes;
- impacto operativo;
- impacto de seguridad;
- escalabilidad;
- mantenibilidad;
- facilidad de prueba;
- reversibilidad;
- compatibilidad con el proyecto.

Incluir la opción **no introducir nueva tecnología o complejidad** cuando sea razonable.

### 4. Comparar con criterios explícitos

Los criterios deben salir del proyecto, no de gustos de la IA. Ejemplos:

| Criterio | Importancia | Pregunta |
|---|---|---|
| Simplicidad | Alta/Media/Baja | ¿Reduce complejidad accidental? |
| Rendimiento | Alta/Media/Baja | ¿Cumple los NFR medidos? |
| Coste | Alta/Media/Baja | ¿Coste de adopción y operación? |
| Seguridad | Alta/Media/Baja | ¿Amplía superficie de ataque? |
| Mantenibilidad | Alta/Media/Baja | ¿Puede evolucionar sin acoplamiento excesivo? |
| Reversibilidad | Alta/Media/Baja | ¿Qué tan costoso es cambiar después? |
| Experiencia del equipo | Alta/Media/Baja | ¿Existe conocimiento suficiente? |
| Ecosistema | Alta/Media/Baja | ¿Madurez, soporte y compatibilidad? |

No convertir esta tabla en una puntuación matemática falsa cuando los criterios sean cualitativos.

### 5. Recomendar

La IA debe indicar:

- opción recomendada;
- por qué encaja con **este** caso;
- por qué las alternativas no son preferidas en este contexto;
- supuestos usados;
- condiciones bajo las cuales la recomendación cambiaría.

La recomendación debe favorecer la solución más simple que satisfaga los requisitos actuales y previsibles, evitando sobrearquitectura y complejidad especulativa.

### 6. Solicitar aprobación cuando corresponda

Seguir `docs/00-gobierno/POLITICA_DECISIONES.md`:

- decisión local y reversible: puede incorporarse al plan normal;
- decisión significativa: propuesta + aprobación + ADR;
- decisión transformacional: RFC + aprobación + ADR posterior.

La IA no debe presentar una recomendación como una decisión ya tomada.

### 7. Registrar y trazar

Cuando la decisión sea aprobada:

- actualizar la fuente documental correspondiente;
- crear ADR/RFC si aplica;
- enlazar requisitos, flujos, contratos, pruebas e Issues afectados;
- registrar consecuencias y deuda técnica aceptada;
- mantener histórico cuando una decisión sea reemplazada.

## Decisiones de código

El mismo proceso aplica a decisiones de implementación. La IA no debe introducir patrones, abstracciones o librerías solo porque sean conocidas o “más profesionales”.

Antes de agregar una abstracción debe poder responder:

- ¿qué variación real existe hoy?;
- ¿qué cambio futuro es razonablemente probable?;
- ¿la abstracción reduce o aumenta complejidad?;
- ¿un enfoque directo sería suficiente?;
- ¿cómo se prueba?;
- ¿qué acoplamiento introduce?;

Ejemplo conceptual: `if/switch` puede ser preferible a Strategy cuando las variantes son pocas, estables y la abstracción no aporta valor. Strategy puede ser preferible cuando existen múltiples comportamientos intercambiables y evolución frecuente.

## Decisiones tecnológicas

No asumir automáticamente:

- base de datos relacional o NoSQL;
- monolito o microservicios;
- REST, GraphQL, gRPC o eventos;
- caché local o distribuida;
- contenedores o serverless;
- proveedor cloud;
- herramienta de CI/CD;
- framework de frontend/backend;
- broker, motor de búsqueda o tecnología de observabilidad.

Cada una se decide desde el caso de uso y los requisitos.

## Resultado mínimo de una propuesta

```text
Decisión: DEC-XXX
Problema:
Contexto:
Restricciones:
Alternativas:
  A:
  B:
  C:
Criterios relevantes:
Trade-offs:
Riesgos:
Recomendación:
Motivo:
Supuestos:
Qué haría cambiar la recomendación:
¿Requiere aprobación?:
¿Requiere ADR/RFC?:
```

## Regla final

La IA asesora y recomienda. La persona responsable conserva la decisión final salvo que exista una delegación explícita, limitada y documentada.
