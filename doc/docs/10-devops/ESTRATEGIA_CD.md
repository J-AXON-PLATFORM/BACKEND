# Estrategia de entrega y despliegue continuo

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- CD aprobado

`build once -> Development -> QA -> Production` con el **mismo digest**. No reconstruir al promover o etiquetar release.

- Merge/push válido a `develop`: construye/publica artefacto inmutable para Development.
- QA: promoción controlada del mismo artefacto.
- Production: aprobación explícita y mismo digest validado; nunca compila desde source durante deployment.
- Flyway en Production es paso explícito y trazable previo al arranque de la nueva versión; Local/Development/Test/QA pueden migrar automáticamente.
- Releases trazables a commit, tests, artefactos/digests, contratos, migraciones, SBOM y versión.
<!-- JAXON-CKP003:END -->

## Propósito

Definir cómo un artefacto validado llega a cada entorno.

## Modalidad

- manual;
- semiautomática;
- automática.

La automatización de despliegue a producción depende del riesgo, madurez y controles.

## Promoción

Definir:

- artefacto;
- entorno origen/destino;
- aprobación;
- criterios;
- secretos/configuración;
- smoke tests;
- observabilidad.

## Producción

Relacionar con `ESTRATEGIAS_DESPLIEGUE.md`.

## Fallo

Definir:

- detección;
- pausa;
- rollback;
- roll-forward;
- reversión de tráfico;
- datos/migraciones;
- comunicación.

## Auditoría

Registrar quién/qué desplegó, versión, fecha, entorno y resultado cuando el contexto lo requiera.
