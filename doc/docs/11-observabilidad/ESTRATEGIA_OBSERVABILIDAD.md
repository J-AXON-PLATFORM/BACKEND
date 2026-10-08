# Estrategia de observabilidad

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Permitir responder qué está ocurriendo, por qué y qué capacidades están afectadas, sin convertir observabilidad en dependencia crítica del core.

## Stack inicial on-premise

- **Prometheus** para métricas;
- **Loki** para logs;
- **Tempo** para trazas;
- **Grafana** para dashboards/consulta;
- sin HA inicial obligatoria;
- PostgreSQL de negocio no se usa como backend de telemetría.

La aplicación produce estándares/desacoplamiento: Micrometer/Prometheus-compatible metrics, OpenTelemetry y logs JSON estructurados.

## Señales

- logs;
- métricas;
- trazas;
- health;
- alertas;
- eventos/estado operativo relevantes.

## Dashboards iniciales

1. J-AXON Overview;
2. Collector / Agents;
3. Backend / PostgreSQL;
4. Integrations (IA, n8n, SFTPGo, DocumentStorage);
5. Operations (backups, disk, restore/deployment/version, alertas).

## Correlación

- `correlationId` operativo end-to-end;
- `traceId` para tracing;
- `taskId`, `jobId`, `executionId` siguen siendo identidades de negocio/flujo separadas;
- no convertir IDs de alta cardinalidad en labels de Prometheus.

## Retención

- Production: logs 30 d, métricas 90 d, trazas 7 d;
- Development: 7 d;
- Test: efímero;
- QA: 14 d;
- Local: sin centralización obligatoria;
- auditoría de negocio/seguridad permanece separada con retención propia de 5 años.

## Sampling

- errores: 100 % de trazas cuando sea técnicamente posible/seguro;
- operaciones normales: 10 % inicial, configurable;
- no registrar contenido sensible indiscriminadamente.

## Regla de fallo

Grafana/Prometheus/Loki/Tempo caídos degradan diagnóstico/alertas pero no deben tumbar Identity, Inventory, Collector o Support.

## Privacidad y coste

- redactar secretos;
- minimizar PII;
- controlar acceso;
- limitar cardinalidad;
- usar sampling/retención/niveles de log;
- no guardar telemetría ilimitada.
