# Supuestos, restricciones y riesgos

> **Clasificación:** OBLIGATORIO  
> **Instancia:** J-AXON | CKP-JAXON-003 | 2026-09-29  
> **Estado:** registro inicial aprobado; se revisará con evidencia durante implementación/operación.

## Supuestos

- Escenario base on-premise/LAN.
- Aproximadamente 350 equipos iniciales y ~30 usuarios web concurrentes típicos; crecimiento de diseño alrededor de 1000 equipos sin prometer capacidad certificada antes de pruebas.
- RAG inicia con miles de documentos y puede crecer.
- IA externa puede depender de Internet; el core local no debe depender de su disponibilidad.

## Restricciones

- Web HTTPS/REST/OpenAPI; Agents SFTP/JSON/JSON Schema.
- Backend central Spring Boot como monolito modular; no microservicios iniciales.
- Base PostgreSQL nueva; no importar/borrar legado automáticamente.
- IA no ejecuta acciones autónomas de negocio.
- Automatización/n8n no posee reglas centrales ni escribe tablas de módulos directamente.
- Documentación completa de `doc` se conserva; decisiones desconocidas siguen `POR_DECIDIR`.

## Escala de clasificación

- Probabilidad: `BAJA | MEDIA | ALTA`.
- Impacto: `BAJO | MEDIO | ALTO`.
- Estado: `ABIERTO | MITIGADO | ACEPTADO | CERRADO`.

Una mitigación diseñada pero no implementada/probada mantiene el riesgo `ABIERTO`.

## Registro inicial

| ID | Riesgo | Prob. | Impacto | Mitigación principal | Responsable | Estado |
|---|---|---|---|---|---|---|
| R-01 | Heredar decisiones/contratos inconsistentes del proyecto antiguo | ALTA | ALTO | Legado solo como evidencia; nuevos contratos aprobados/versionados | Área de arquitectura | ABIERTO |
| R-02 | Crecimiento descontrolado del alcance de v1 | ALTA | ALTO | Alcance aprobado, backlog priorizado y aprobación de cambios | Producto/arquitectura | ABIERTO |
| R-03 | Backend, frontend y Agent evolucionan con contratos incompatibles | MEDIA | ALTO | OpenAPI/JSON Schema versionados, contract tests y compatibilidad | Arquitectura/calidad | ABIERTO |
| R-04 | Agent sin LAN/SFTP no entrega snapshots o recoge tareas | ALTA | MEDIO | Persistencia local, retry limitado, backoff+jitter, reconciliación | Collector/operación | ABIERTO |
| R-05 | Reintentos generan tareas/respuestas duplicadas | MEDIA | ALTO | IDs estables, deduplicación e idempotencia | Collector | ABIERTO |
| R-06 | Compromiso de credenciales de un Agent | MEDIA | ALTO | Clave individual, mínimo privilegio, revocación | Seguridad/Identidad | ABIERTO |
| R-07 | Discovery escanea redes no autorizadas | MEDIA | ALTO | Rangos autorizados, permisos y ejecutores designados | Discovery/seguridad | ABIERTO |
| R-08 | Pérdida/corrupción de PostgreSQL o archivos | MEDIA | ALTO | Backups independientes, restore tests, migraciones controladas | Datos/operación | ABIERTO |
| R-09 | Capacidad insuficiente al crecer de ~350 a ~1000 equipos | MEDIA | ALTO | Load tests, snapshots escalonados, medición/capacity planning | Rendimiento/operación | ABIERTO |
| R-10 | PostgreSQL se convierte en cuello de botella/punto crítico | MEDIA | ALTO | Métricas, optimización y recuperación documentada | Datos/operación | ABIERTO |
| R-11 | Fuga de datos hacia proveedor IA | MEDIA | ALTO | Autorización previa, minimización y política de privacidad | IA/seguridad | ABIERTO |
| R-12 | Respuesta IA incorrecta se trata como hecho/acción | MEDIA | ALTO | IA asistiva, fuentes y validación humana/backend | IA/Soporte | ABIERTO |
| R-13 | Caída del proveedor IA afecta funciones importantes | MEDIA | MEDIO | Degradación controlada; core independiente | IA/operación | ABIERTO |
| R-14 | n8n concentra reglas críticas de negocio | MEDIA | ALTO | Reglas centrales en backend; n8n solo integración/orquestación | Automatización/arquitectura | ABIERTO |
| R-15 | Secretos aparecen en Git, imágenes o logs | MEDIA | ALTO | Secretos externos, scans y sanitización | Seguridad/DevOps | ABIERTO |
| R-16 | Migración/despliegue defectuoso daña producción | MEDIA | ALTO | Flyway, tests, backup, rollback/roll-forward, QA | DevOps/datos | ABIERTO |
| R-17 | Repositorios liberan combinaciones incompatibles | MEDIA | ALTO | Versionado, manifest de release, compatibilidad y pruebas conjuntas | Release/arquitectura | ABIERTO |
| R-18 | Disco se llena por snapshots/logs/archivos/pendientes | MEDIA | ALTO | Retención, límites, métricas y alertas | Operación | ABIERTO |
| R-19 | Falta de observabilidad impide reconstruir incidentes | MEDIA | MEDIO | Logs, métricas, trazas, correlación y auditoría | Operación | ABIERTO |
| R-20 | Infraestructura distinta por cliente complica instalación | MEDIA | MEDIO | Docker/Compose, config externa, instalación reproducible, portabilidad | DevOps/operación | ABIERTO |

Los responsables nominales se asignarán cuando exista estructura formal de equipo/instalación.
