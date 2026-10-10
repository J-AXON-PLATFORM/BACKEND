# Contratos ejecutables de J-AXON

Esta carpeta es la fuente canónica de los contratos externos de J-AXON.

- `openapi/1.0/openapi.yaml`: contrato REST inicial de la primera vertical.
- `agent/1.0/`: contratos JSON Schema Draft 2020-12 para Agent.

Los contratos usan versión `MAJOR.MINOR`, independiente de la versión del software. Un cambio incompatible requiere un nuevo major. Un minor nuevo debe aceptar las instancias válidas del minor anterior y coexistir con él durante la transición. Se mantiene la versión actual y el minor anterior del mismo major cuando exista.

Los identificadores bajo `https://schemas.j-axon.invalid/` no son endpoints. Los validadores deben resolverlos únicamente contra recursos locales. `backend` conserva la fuente canónica; las copias en consumidores se sincronizan y verifican mediante el manifest SHA-256.

Este bloque define contratos, no controladores, persistencia, transporte SFTP ni conformidad de runtime.
