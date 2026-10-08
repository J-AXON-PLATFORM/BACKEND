# Trazas distribuidas

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Seguir operaciones entre browser/backend/DB/integraciones cuando aporte diagnóstico.

## Estándar

OpenTelemetry para instrumentación. Tempo es backend inicial on-premise de Production/QA, sin acoplar contratos de negocio al proveedor.

## Correlación

- trace/span IDs técnicos;
- correlationId operativo separado;
- IDs de negocio (`taskId`, `jobId`, `executionId`) no se sustituyen por trace IDs.

## Sampling inicial

- operaciones normales: 10 %;
- errores: 100 % cuando sea posible;
- configurable temporalmente para incidentes.

## Retención

- Production: 7 días;
- QA: 14 días como política general de telemetría del entorno, salvo ajuste de capacidad;
- Development: 7 días;
- Test efímero.

## Privacidad

No capturar bodies, documentos, secretos o datos sensibles por defecto. Propagar contexto compatible sin convertirlo en contrato de negocio.
