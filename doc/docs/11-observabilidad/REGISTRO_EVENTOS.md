# Registro de eventos y logs

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Producir registros útiles, estructurados y seguros.

## Formato

Preferir JSON estructurado con, cuando aplique:

- timestamp UTC;
- level;
- module/component;
- version/environment;
- correlationId/traceId;
- operation/event;
- result;
- durationMs;
- error code estable.

## No registrar

- passwords;
- session cookies/tokens;
- SSH private keys;
- HMAC secrets;
- backup keys;
- documentos completos;
- prompts/contexto sensible completos salvo caso expresamente aprobado y minimizado.

## Retención

- Production 30 días;
- Development 7 días;
- Test efímero;
- QA 14 días;
- Local sin centralización obligatoria.

Logs técnicos no sustituyen auditoría. Acciones que requieran 5 años deben existir en `audit_reporting`.
