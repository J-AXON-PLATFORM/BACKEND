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

## Estado implementado en backend -- TEC-PLT-003a y TEC-QLT-003a

El workflow `.github/workflows/backend-ci.yml`, denominado `Backend CI`, implementa la línea base ejecutable actual del backend:

- Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecución manual mediante `workflow_dispatch`;
- job estable `Backend verification` sobre `ubuntu-24.04`, con timeout de 30 minutos;
- Java 25 Temurin y caché Maven administrada por `setup-java`;
- build y scan de una fixture local endurecida PostgreSQL 18.6 + pgvector 0.8.6 antes de las pruebas;
- `./mvnw --batch-mode --no-transfer-progress clean verify` para compilación, cinco pruebas, modularidad, health/probes e integración Testcontainers/Flyway con los ocho schemas;
- build multi-stage del backend, smoke no-root/read-only con PostgreSQL real y scan Trivy bloqueante HIGH/CRITICAL;
- permisos mínimos `contents: read`, sin secretos, despliegues, publicación de artefactos ni escritura en el repositorio;
- `actions/checkout` y `actions/setup-java` fijadas por SHA completo, con credenciales persistentes deshabilitadas;
- concurrencia por workflow y rama o Pull Request, con cancelación de ejecuciones obsoletas.

La ampliación se integró mediante `BACKEND#5`, squash `90c550714b861beb7d6388cf571744efcda4af8d`; los runs `37879426717` de PR y `37879749721` post-merge finalizaron correctamente. Las verificaciones de contratos y SFTP se incorporarán cuando existan artefactos ejecutables aplicables. Los rulesets y protecciones automáticas continúan pendientes porque el plan actual no los aplica sobre repositorios privados. `main` y `develop` pueden quedar temporalmente en commits distintos.

## Estado implementado en frontend -- TEC-QLT-001a y TEC-QLT-003a

El workflow `.github/workflows/frontend-ci.yml`, denominado `Frontend CI`, implementa la línea base ejecutable actual del frontend:

- Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecución manual mediante `workflow_dispatch`;
- job estable `Frontend verification` sobre `ubuntu-24.04`, con timeout de 20 minutos;
- Node.js 24.21.0 y npm 11.19.0 desde `.nvmrc`, con caché npm ligada a `package-lock.json`;
- pasos separados para `npm ci`, lint, typecheck, Vitest/RTL, Next.js build, auditoría de producción y auditoría completa;
- build standalone, smoke no-root/read-only que confirma ausencia de gestores JavaScript y scan Trivy bloqueante HIGH/CRITICAL;
- `NEXT_TELEMETRY_DISABLED=1`, permisos `contents: read`, sin secretos, artefactos, publicación o despliegue;
- `actions/checkout@v7.0.1` y `actions/setup-node@v7.0.0` fijadas por SHA completo, con credenciales persistentes deshabilitadas;
- concurrencia por workflow y rama o Pull Request, con cancelación de ejecuciones obsoletas.

La activación fue integrada mediante el Pull Request frontend `#1`, squash `0a34d6dadacc95351544996235faf6bcb0b21b65`. El run de PR `37849248403` y el run post-merge sobre `develop` `37849379045` finalizaron correctamente.

La ampliación de contenedor se integró mediante `FRONTEND#2`, squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` de PR y `37880065517` post-merge finalizaron correctamente. Las imágenes permanecieron locales a CI y no fueron publicadas.

La auditoría de producción es bloqueante y está limpia. La auditoría completa usa `scripts/verify-npm-audit.mjs`, que acepta temporalmente solo `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, dependencia transitiva exclusivamente de desarrollo introducida por `eslint-config-next -> @next/eslint-plugin-next -> fast-glob -> micromatch`. No existe versión corregida al 2026-10-08. El control falla ante cualquier otra vulnerabilidad, cambio de versión/cadena/alcance, presencia en producción o error de auditoría. ESLint 9.39.5 permanece como riesgo EOL temporal porque la combinación aprobada con ESLint 10.12.0 falló de forma ejecutable; ambos pendientes deben revisarse cuando la cadena oficial publique compatibilidad y correcciones.

## Estado implementado en automation-collector -- TEC-QLT-002a

El workflow `.github/workflows/python-ci.yml`, denominado `Python CI`, implementa la línea base ejecutable actual de Python:

- Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecución manual mediante `workflow_dispatch`;
- job estable `Python verification` sobre `ubuntu-24.04`, con timeout de 20 minutos;
- Python 3.14.8 desde `.python-version` y uv 0.12.24 fijado también mediante `tool.uv.required-version`;
- pasos separados para sincronización `uv sync --locked`, `uv lock --check`, Ruff lint, Ruff format check, seis pruebas pytest, build, pip-audit y ejecución de ambos entrypoints;
- permisos `contents: read`, sin secretos, publicación, artefactos o despliegue;
- `actions/checkout@v7.0.1`, `actions/setup-python@v7.0.0` y `astral-sh/setup-uv@v10.2.0` fijadas por SHA completo, con credenciales persistentes deshabilitadas;
- concurrencia por workflow y rama o Pull Request, con cancelación de ejecuciones obsoletas.

La activación fue integrada mediante el Pull Request `AUTOMATION-COLLECTOR#1`, squash `a040c5fba34fae22903088b82191e8355deaab26`. El run de PR `37852626079` y el run post-merge `37852683236` finalizaron correctamente. La auditoría no detectó vulnerabilidades conocidas. Las dependencias runtime permanecen vacías; contratos JSON Schema, SFTP, Paramiko y PyInstaller quedan pendientes hasta incrementos funcionales aplicables.

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
