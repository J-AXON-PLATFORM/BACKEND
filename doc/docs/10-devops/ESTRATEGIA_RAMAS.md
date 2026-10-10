# Estrategia de ramas

> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla dinámica

## Propósito

Elegir y documentar un modelo de ramas adecuado al equipo y al proceso de entrega.

Comparar, cuando sea necesario, trunk-based, GitHub Flow, Git Flow u otro modelo mediante `MARCO_TOMA_DECISIONES.md`.

Definir:

- ramas permanentes;
- convención de nombres;
- protección;
- política de merge/rebase;
- Pull Requests;
- releases/hotfixes;
- eliminación de ramas;
- relación con CI/CD.

## Instancia J-AXON

- `main` es la rama permanente de producción; `develop` es la rama permanente de integración y candidata a producción, no un entorno físico.
- `develop` es la rama predeterminada de los tres repositorios oficiales para que Dependency Graph, Dependabot y el flujo normal de Pull Requests observen la integración vigente.
- Cambiar la rama predeterminada no fusiona ni altera `main`, cuya promoción sigue requiriendo el flujo aprobado.
- El trabajo normal nace desde `develop` en ramas cortas `feature/*`, `fix/*`, `refactor/*`, `test/*`, `docs/*`, `chore/*` o `ci/*` y vuelve mediante Pull Request con CI verde.
- Un hotfix nace de `main`, vuelve mediante Pull Request a `main` y después se sincroniza con `develop`.
- No se permiten force-push o eliminación de `main`/`develop`; los rulesets continúan pendientes por las limitaciones del plan privado actual.
