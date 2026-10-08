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

## Estado implementado en backend -- TEC-PLT-003a

El workflow `.github/workflows/backend-ci.yml`, denominado `Backend CI`, implementa la línea base ejecutable actual del backend:

- Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecución manual mediante `workflow_dispatch`;
- job estable `Backend verification` sobre `ubuntu-24.04`, con timeout de 30 minutos;
- Java 25 Temurin y caché Maven administrada por `setup-java`;
- `./mvnw --batch-mode --no-transfer-progress clean verify` como gate único de compilación, pruebas existentes, modularidad e integración;
- PostgreSQL 18.6, pgvector 0.8.6, Flyway y los ocho schemas verificados mediante Testcontainers;
- permisos mínimos `contents: read`, sin secretos, despliegues, publicación de artefactos ni escritura en el repositorio;
- `actions/checkout` y `actions/setup-java` fijadas por SHA completo, con credenciales persistentes deshabilitadas;
- concurrencia por workflow y rama o Pull Request, con cancelación de ejecuciones obsoletas.

Las verificaciones de contratos y SFTP se incorporarán cuando existan artefactos ejecutables aplicables. Los rulesets y protecciones automáticas continúan pendientes porque el plan actual no los aplica sobre repositorios privados: la disciplina de Pull Request y checks verdes se aplica operativamente, pero GitHub todavía no la fuerza técnicamente. `main` y `develop` pueden quedar temporalmente en commits distintos.

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
