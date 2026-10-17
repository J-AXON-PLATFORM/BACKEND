# Contratos ejecutables de J-AXON

Esta carpeta es la fuente canónica de los contratos externos de J-AXON.

- `openapi/1.0/openapi.yaml`: contrato REST inicial de la primera vertical.
- `openapi/1.1/openapi.yaml`: minor compatible que conserva 1.0 y añade pre-registro, consulta y clave pública Agent.
- `openapi/2.0/openapi.yaml`: major que añade activación Agent y conexión SFTP pública.
- `openapi/2.1/openapi.yaml`: minor compatible que añade el aviso SSE selectivo de estado técnico.
- `agent/1.0/`: contratos JSON Schema Draft 2020-12 para Agent.

Los contratos usan versión `MAJOR.MINOR`, independiente de la versión del software. Un cambio incompatible requiere un nuevo major. Un minor nuevo debe aceptar las instancias válidas del minor anterior y coexistir con él durante la transición. OpenAPI mantiene 2.1 como actual y 2.0 como minor anterior del major vigente.

Los identificadores bajo `https://schemas.j-axon.invalid/` no son endpoints. Los validadores deben resolverlos únicamente contra recursos locales. `backend` conserva la fuente canónica; las copias en consumidores se sincronizan y verifican mediante el manifest SHA-256.

OpenAPI 1.0 permanece inmutable y disponible como minor anterior. OpenAPI 1.1 referencia sus operaciones estables y añade únicamente tres operaciones Agent para sesión humana `IT_OPERATOR`; no define un canal HTTP para el ejecutable Agent.

OpenAPI 2.0 permanece inmutable. OpenAPI 2.1 conserva sus operaciones y añade únicamente el stream best-effort `GET /assets/{assetId}/technical-state/events`; REST sigue siendo la fuente oficial del estado técnico.

Los JSON Schemas Agent 1.0 no cambian con el enrolamiento administrativo. Este bloque contractual no implica SFTP, activación, snapshots ni tareas.
