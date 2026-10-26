# Matriz de trazabilidad de requisitos

> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla dinámica

## Propósito

Relacionar requisitos con backlog, flujos, contratos, decisiones, código, pruebas y releases.

| Requisito | HU | Flujo | Contrato | Criterios de aceptación | ADR/RFC | Pruebas | PR/Versión | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-SUP-004 / subconjunto RF-SUP-006 | OLA3-SUP-005a | FL-SUP-001 | OpenAPI 4.2 | CA OLA3-SUP-005a | AC-JAXON-077 | SupportIntegrationTest, SupportMigrationIntegrationTest, ExecutableContractsTest, Vitest y Playwright Chromium | FRONTEND#18 y BACKEND#42 integrados/verificados | VERIFICADO |
| RF-SUP-006 / subconjunto terminal y RF-SUP-009 / historial terminal | OLA3-SUP-006a | FL-SUP-001 | OpenAPI 4.3 | CA OLA3-SUP-006a + TEC-QLT-007a | AC-JAXON-078 | SupportIntegrationTest, SupportMigrationIntegrationTest, ExecutableContractsTest, Vitest, Playwright y clasificadores CI | FRONTEND#19 y BACKEND#43 integrados/verificados | VERIFICADO |
| RF-DIS-001 / AuthorizedScope mínimo | OLA4-DIS-001a | FL-DIS-001 | OpenAPI 4.4 | CA OLA4-DIS-001a + TEC-QLT-008a | AC-JAXON-079 | AuthorizedScopeIntegrationTest/NormalizerTest, ExecutableContractsTest, Vitest, Playwright y clasificadores/caché | FRONTEND#21 y BACKEND#44 integrados/verificados | VERIFICADO |
| RF-DIS-001 / DiscoveryExecutor pre-registro e identidad | OLA4-DIS-002a | FL-DIS-001 | OpenAPI 4.5 | CA OLA4-DIS-002a | AC-JAXON-080 | DiscoveryExecutorIntegrationTest, ExecutableContractsTest, pytest/Ruff, Vitest/RTL y Playwright multirepo | AUTOMATION-COLLECTOR#9, FRONTEND#22 y BACKEND#45 integrados/verificados | VERIFICADO |
| RF-DIS-003 / activación y canal SFTP Discovery | OLA4-DIS-003a | FL-DIS-001 | OpenAPI 5.0 | CA OLA4-DIS-003a | AC-JAXON-081 | DiscoveryExecutorIntegrationTest, DiscoverySftpGoClientIntegrationTest, ExecutableContractsTest, pytest/Ruff, Vitest/RTL y Playwright multirepo | AUTOMATION-COLLECTOR#10, FRONTEND#23 y BACKEND#46 integrados/verificados | VERIFICADO |
| RF-DIS-002 / creación y despacho mínimo de job | OLA4-DIS-004a | FL-DIS-001 | OpenAPI 5.1 + Discovery 1.0 | CA OLA4-DIS-004a | AC-JAXON-082 | DiscoveryJobContractTest, DiscoveryJobIntegrationTest, DiscoverySftpGoClientIntegrationTest, ExecutableContractsTest, pytest/Ruff, Vitest/RTL y Playwright multirepo | AUTOMATION-COLLECTOR#11 y FRONTEND#24 integrados; backend verificado localmente y PR pendiente | IMPLEMENTADO_LOCAL |

La implementación concreta puede usar una tabla, herramienta de gestión o enlaces automáticos; no duplicar manualmente información si ya existe una fuente canónica confiable.
