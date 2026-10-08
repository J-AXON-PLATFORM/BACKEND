# Estrategia de integración continua

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- CI aprobado

- GitHub Actions independiente en `backend`, `frontend`, `automation-collector`.
- PR a `develop/main`: `fast + integration` obligatorios.
- Backend: compile, unit, Spring Modulith verification, PostgreSQL/Flyway, contratos y SFTP cuando aplique.
- Frontend: `npm ci`, lint, typecheck, Vitest/RTL, `next build`.
- Python: `uv sync --locked`, Ruff, pytest, contratos/SFTP y packaging cuando aplique.
- Cambios cross-repo: validar combinación explícita de versiones/contratos antes de release.
- Actions externas fijadas por SHA completo y permisos mínimos; Dependabot por PR; CodeQL/GHAS cuando esté disponible.
- PRs no reciben secretos productivos. Cancelar runs obsoletos del mismo PR cuando no sean deployments/releases.
<!-- JAXON-CKP003:END -->

## Propósito

Detectar problemas temprano en cada cambio integrable.

## Disparadores

Definir:

- push;
- pull request;
- merge;
- tag;
- manual;
- programado.

## Etapas

Seleccionar según proyecto:

- formato/lint;
- compilación;
- unitarias;
- integración;
- contrato;
- seguridad;
- build;
- SBOM;
- scan;
- empaquetado.

## Quality gates

Los gates deben corresponder a riesgos reales.

Definir qué falla el pipeline y qué produce solo advertencia.

## Caché

Cachear dependencias/builds sin permitir artefactos corruptos u obsoletos.

## Secretos y permisos

- mínimo privilegio;
- tokens de corta duración si posible;
- no imprimir secretos;
- revisar actions/plugins externos.

## Artefactos

Definir:

- naming;
- versión;
- digest/checksum;
- retención;
- registry;
- promoción.

## Rendimiento

Mantener una suite rápida en PR y separar validaciones costosas si mejora feedback sin perder seguridad.
