# Matriz de activación documental

> **Clasificación:** OBLIGATORIO  
> **Instancia:** J-AXON | CKP-JAXON-003 | 2026-09-29  
> **Estado:** revisada y aprobada para descubrimiento inicial.

## Principio

El framework se activa por capacidades, riesgos y decisiones; no por carpetas existentes. Todos los archivos físicos se conservan aunque un área no esté activa.

## Salida de activación de J-AXON

**Perfil documental:** `ESTANDAR`.

**Perfiles técnicos activos:**

- `BACKEND_API`
- `FRONTEND_WEB`
- `BATCH_WORKERS`
- `MONOLITO_MODULAR`
- `SISTEMA_DISTRIBUIDO`
- `ON_PREMISE`
- `PRODUCCION_BAJA_CRITICIDAD`

**No activados actualmente como perfil:** `MOVIL`, `ESCRITORIO`, `CLI`, `DATOS_ETL`, `MONOLITO`, `MICROSERVICIOS`, `ORIENTADO_EVENTOS`, `SERVERLESS`, `LOCAL_ONLY`, `CLOUD`, `HIBRIDO`, `MULTICLOUD`, `SIN_PRODUCCION`, `PRODUCCION_24X7`, `REGULADO`, `ALTA_CRITICIDAD`.

El Agent en un PC no implica una aplicación de escritorio con UI. Usar eventos internos no convierte por sí solo toda la arquitectura en orientada a eventos.

## Documentación condicional activada

- HLD y ADR por perfil `ESTANDAR`.
- Arquitectura distribuida: varias unidades y comunicación remota.
- Resiliencia + pruebas de resiliencia: fallos parciales posibles.
- Rendimiento/capacidad + pruebas de rendimiento: escala aprobada y necesidad de medición.
- Persistencia, gobierno y arquitectura de datos.
- Pruebas de migraciones por Flyway.
- Accesibilidad como política de calidad aprobada.
- Pruebas de componente cuando aporten cobertura distinta.
- Pruebas de sistema por integración browser/backend/DB/SFTP/Agent.
- Seguridad de dependencias/cadena de suministro por artefactos y dependencias de terceros.
- CI/CD y estrategias de despliegue.
- Observabilidad, guía operativa y recuperación/desastre; RTO/RPO aún sin valores.

## Condicionales no activados por defecto

No se activan solo por moda/presencia de plantilla: microservicios, broker, CQRS, event sourcing, cache distribuida, service mesh, Kubernetes, multi-región, multicloud, microfrontends, data lake, autoscaling o una arquitectura cloud específica. Feature flags/circuit breakers se usan selectivamente solo si el caso lo exige.

## Reglas generales

1. `OBLIGATORIO` se mantiene siempre.
2. `CONDICIONAL` solo se completa cuando existe su disparador.
3. `OPCIONAL` necesita razón concreta.
4. Si una condición deja de existir, conservar historia y marcar no activa.
5. Si algo está `POR_DECIDIR`, no asumir activación.
6. Tecnología concreta no es un disparador si el problema no existe.

## Decisiones pendientes que pueden cambiar activación

Proveedor/cloud futuro, regulación, criticidad 24x7, multi-tenancy SaaS compartido, infraestructura final, SLO/SLA y requisitos contractuales del cliente.
