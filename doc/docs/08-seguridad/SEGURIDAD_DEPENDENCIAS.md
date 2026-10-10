# Seguridad de dependencias

> **Clasificación:** CONDICIONAL

## Propósito

Reducir riesgo de librerías, paquetes, imágenes y componentes externos.

## Selección

Evaluar:

- procedencia;
- mantenimiento;
- comunidad/soporte;
- licencias;
- vulnerabilidades;
- frecuencia de releases;
- necesidad real.

## Control

- lockfiles;
- versiones;
- actualización;
- scanning;
- SBOM;
- advisories;
- dependencias transitivas.

## Estado implementado en OLA 0

- Dependency Graph y Dependabot alerts quedan habilitados en `backend`, `frontend` y `automation-collector` sobre `develop`.
- Los tres `.github/dependabot.yml` deshabilitan PR de version updates mediante `open-pull-requests-limit: 0` y agrupan exclusivamente security updates por ecosistema, sin `target-branch` ni auto-merge.
- Backend cubre Maven, GitHub Actions y Docker; frontend cubre npm, GitHub Actions y Docker; automation-collector cubre uv y GitHub Actions.
- Frontend conserva sus dos auditorías bloqueantes y la excepción cerrada existente; Python conserva `uv sync --locked`, `uv lock --check` y `pip-audit`; backend conserva Trivy sobre sus imágenes runtime.
- La revisión inicial de Dependabot presenta cero alertas en backend y automation-collector y únicamente la excepción frontend ya aprobada `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687`, transitiva de desarrollo y sin versión corregida.
- Un security update debe pasar el CI completo y revisión humana. Dependabot no autoriza cambios silenciosos de manifiestos o lockfiles.

## Vulnerabilidades

Definir:

- severidad;
- tiempo de remediación;
- excepciones;
- responsable;
- compensating controls.

## Dependencias abandonadas

Planificar reemplazo o asumir explícitamente el riesgo.

## Relación

Complementar con `CADENA_SUMINISTRO_SOFTWARE.md` para CI, artefactos y contenedores.
