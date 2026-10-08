# Arquitectura de backend

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- decisiones vigentes

- Java 25 LTS, Spring Boot 4.1.x, Maven 3.9.x + Wrapper, Spring Modulith 2.1.x.
- Monolito modular con ocho módulos como frontera primaria: `inventory`, `discovery`, `collector`, `support`, `ai`, `identity`, `automation`, `audit_reporting`.
- Evitar carpetas globales `controller/service/repository/entity/dto` que mezclen ownership. Capas internas por módulo solo cuando aporten valor.
- API web REST/HTTPS/OpenAPI 3.1.2; contratos Agent JSON Schema 2020-12 por SFTP.
- Sesiones humanas server-side en PostgreSQL/`identity`; autorización backend default-deny.
- Collector conserva observaciones; Inventory conserva ficha oficial. Sin escrituras cross-module directas.
<!-- JAXON-CKP003:END -->

## Propósito

Definir módulos, capas, dependencias, concurrencia y fronteras del backend.

## Estructura

Documentar:

- módulos/dominos;
- interfaces;
- aplicación;
- dominio;
- infraestructura;
- dependencias externas.

No imponer arquitectura hexagonal, clean, layered o DDD si el problema no la requiere.

## Dependencias

Definir dirección permitida y límites.

## Concurrencia

Según runtime:

- threads;
- event loop;
- async;
- pools;
- bloqueo;
- límites.

## Persistencia

Mantener ownership y transacciones explícitos.

## Integraciones

Relacionar con contratos, resiliencia y observabilidad.

## Seguridad

Aplicar authn/authz en fronteras confiables.

## Calidad

Enlazar pruebas unitarias, integración, contrato y sistema.
