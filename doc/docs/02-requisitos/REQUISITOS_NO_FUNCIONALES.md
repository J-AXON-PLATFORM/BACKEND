# Requisitos no funcionales

> **Clasificación:** OBLIGATORIO  
> **Instancia:** J-AXON | CKP-JAXON-005 | 2026-09-29  
> **Estado:** atributos críticos y varios objetivos numéricos iniciales aprobados; implementación/validación aún pendientes.

## Rendimiento y capacidad

- Carga inicial: ~350 Agents + 30 usuarios; objetivo ~1000 Agents + 30 sin rediseño, sujeto a pruebas.
- Targets iniciales: API p95 <=500 ms/p99 <=1 s; login p95 <=1 s/p99 <=2 s; operación pesada p95 <=2 s/p99 <=4 s; SSE p95 <=2 s/p99 <=5 s; retrieval vectorial p95 <=500 ms; 5xx <1 % bajo carga normal.
- Headroom orientativo: CPU <70 %, RAM <80 %, sin saturación sostenida de pools/I/O.
- Snapshots escalonados; no concentrarlos todos a la vez.
- No afirmar capacidad certificada de ~1000 Agents antes de prueba representativa.

## Seguridad

- HTTPS web; SFTP/SSH Agents; RBAC backend; cuentas locales iniciales; Argon2id; cookie segura; keypair SSH por Agent; mínimo privilegio; secretos fuera del repo; auditoría sin secretos; threat model STRIDE ligero; controles CI/CD progresivos.

## Resiliencia

- Retries limitados con backoff+jitter; idempotencia; deduplicación; persistencia local acotada; procesamiento atómico de archivos; cuarentena/rechazo; reconciliación; degradación segura de IA/n8n/SSE; no éxito falso si PostgreSQL no persiste.

## Datos

- PostgreSQL por entorno, schema por módulo; ownership; files fuera de tablas; backups y restore tests; retención por categorías; single-tenant por instalación en v1.

## Observabilidad y operación

- Logs estructurados, métricas, trazas/correlación, health liveness/readiness y estado Agent. SLOs iniciales: core >=99,5 %, freshness Agent >=99 % <=8 h, procesamiento snapshot >=99 % <=5 min. RPO <=1 h y RTO <=8 h iniciales; SLA comercial sigue abierto.

## Calidad

- TDD donde aporta; pirámide de pruebas; contratos, integración real, E2E, seguridad, rendimiento, resiliencia, accesibilidad, migraciones y regresión. Release no ignora fallos críticos.

## Portabilidad/mantenibilidad

- Monolito modular; integraciones detrás de adapters; configuración externa; Docker/Compose donde aplique; evitar lock-in del core; mismas builds promovidas entre entornos.

## Valores cuantitativos restantes

Siguen pendientes principalmente hardware Production definitivo, tuning fino de DB/JVM/pools, capacidad certificada, SLA comercial/tiempos contractuales por severidad, thresholds adicionales de circuit breaker/bulkhead/rate limiting y parámetros que solo deban fijarse con evidencia. Los valores ya aprobados en Lotes 40--46 no deben volver a tratarse como abiertos salvo revisión explícita.
