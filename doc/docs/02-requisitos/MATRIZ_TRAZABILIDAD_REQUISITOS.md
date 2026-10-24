# Matriz de trazabilidad de requisitos

> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla dinámica

## Propósito

Relacionar requisitos con backlog, flujos, contratos, decisiones, código, pruebas y releases.

| Requisito | HU | Flujo | Contrato | Criterios de aceptación | ADR/RFC | Pruebas | PR/Versión | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-SUP-004 / subconjunto RF-SUP-006 | OLA3-SUP-005a | FL-SUP-001 | OpenAPI 4.2 | CA OLA3-SUP-005a | AC-JAXON-077 | SupportIntegrationTest, SupportMigrationIntegrationTest, ExecutableContractsTest, Vitest y Playwright Chromium | FRONTEND#18 y BACKEND#42 integrados/verificados | VERIFICADO |
| RF-SUP-006 / subconjunto terminal y RF-SUP-009 / historial terminal | OLA3-SUP-006a | FL-SUP-001 | OpenAPI 4.3 | CA OLA3-SUP-006a + TEC-QLT-007a | AC-JAXON-078 | SupportIntegrationTest, SupportMigrationIntegrationTest, ExecutableContractsTest, Vitest, Playwright y clasificadores CI | FRONTEND#19 y BACKEND#43 integrados/verificados | VERIFICADO |
| RF-DIS-001 / AuthorizedScope mínimo | OLA4-DIS-001a | FL-DIS-001 | OpenAPI 4.4 | CA OLA4-DIS-001a + TEC-QLT-008a | AC-JAXON-079 | AuthorizedScopeIntegrationTest/NormalizerTest, ExecutableContractsTest, Vitest, Playwright y clasificadores/caché | FRONTEND#21 integrado/verificado; backend pendiente | INTEGRACION_CONTROLADA |

La implementación concreta puede usar una tabla, herramienta de gestión o enlaces automáticos; no duplicar manualmente información si ya existe una fuente canónica confiable.
