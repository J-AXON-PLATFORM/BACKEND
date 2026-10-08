# Gestión del backlog

> **Clasificación:** OBLIGATORIO  
> **Instancia:** J-AXON | CKP-JAXON-003 | 2026-09-29  
> **Estado:** backlog inicial aprobado; publicación remota no autorizada.

## Método

- Scrum + Kanban/GitHub Projects.
- Sprint inicial: 2 semanas.
- Jerarquía: `Épica -> Funcionalidad -> Historia de Usuario -> Tarea`.
- Tipos adicionales: Bug/Error, Spike, Deuda Técnica.
- Story Points: `1,2,3,5,8,13`; historia 13 normalmente se divide o se precede de Spike.
- Flujo: `Backlog -> Ready -> In Progress -> In Review -> QA/Validation -> Done`.
- IA puede generar propuestas; modo inicial `SOLO_PROPUESTA`; no crear Issues/Projects sin autorización.

## Épicas

| ID | Épica |
|---|---|
| EP-01 | Identidad y Acceso |
| EP-02 | Inventario y Activos |
| EP-03 | Collector y Agents |
| EP-04 | Descubrimiento de Red |
| EP-05 | Soporte y Tickets |
| EP-06 | IA + RAG |
| EP-07 | Automatización y Notificaciones |
| EP-08 | Auditoría y Reportes |
| EP-09 | Plataforma y Entornos |
| EP-10 | Calidad y Seguridad |
| EP-11 | Operación y Entrega |

## Historias funcionales iniciales

### EP-01 Identidad
`HU-IAM-001` Login; `HU-IAM-002` Logout; `HU-IAM-003` usuarios; `HU-IAM-004` roles/permisos; `HU-IAM-005` identidad Agent; `HU-IAM-006` revocación Agent.

### EP-02 Inventario
`HU-INV-001` alta manual; `002` ficha; `003` actualización administrativa; `004` responsable/ubicación; `005` estado; `006` configuración técnica; `007` historial; `008` incorporación desde Discovery.

### EP-03 Collector
`HU-COL-001` registro/vínculo Agent; `002` generar snapshot; `003` entregar SFTP; `004` validar/procesar; `005` estado Agent; `006` crear tarea; `007` recoger/validar tarea; `008` respuesta; `009` ciclo de vida; `010` recuperar pendientes.

### EP-04 Discovery
`HU-DIS-001` rango autorizado; `002` solicitar discovery; `003` ejecutar en equipo designado; `004` consultar detectados; `005` revisar; `006` solicitar incorporación.

### EP-05 Soporte
`HU-SUP-001` crear; `002` asociar activo; `003` consultar; `004` clasificar/priorizar; `005` asignar; `006` estado; `007` conversación; `008` adjunto; `009` historial; `010` cerrar.

### EP-06 IA/RAG
`HU-AI-001` fuente; `002` indexar; `003` consulta autorizada; `004` fuentes; `005` asistir ticket; `006` reindexar/eliminar; `007` degradación proveedor.

### EP-07 Automatización
`HU-AUT-001` regla; `002` ejecución por evento; `003` notificación; `004` SSE; `005` solicitud a módulo; `006` n8n controlado.

### EP-08 Auditoría/Reportes
`HU-AUD-001` evento auditable; `002` consultar auditoría; `003` reportar activos; `004` Agents; `005` tickets.

## Trabajo técnico inicial

### EP-09 Plataforma
`TEC-PLT-001..007`: estructura backend, PostgreSQL/schemas, Flyway, entornos, contenedores, Compose, configuración/secretos.

### EP-10 Calidad
`TEC-QLT-001..010`: CI, TDD/unit, Testcontainers, OpenAPI, JSON Schema, Playwright, pytest, seguridad, resiliencia y rendimiento.

### EP-11 Operación
`TEC-OPS-001..010`: health/readiness, logs/correlación, métricas, trazas, backup, restore test, instalación, upgrade, releases, observabilidad Agent/SFTP.

## Conteo

Backlog inicial: **11 épicas y 85 elementos** (58 historias funcionales + 27 elementos técnicos). En la conversación se dijo “alrededor de 70”; aquí se corrige el conteo sin añadir/eliminar elementos.

## Orden recomendado

- OLA 0: Plataforma + Calidad base.
- OLA 1: Identidad + Inventario.
- OLA 2: Collector + SFTP + Agents.
- OLA 3: Soporte.
- OLA 4: Discovery.
- OLA 5: IA/RAG.
- OLA 6: Automatización + Auditoría/Reportes.
- OLA 7: hardening integral + release v1.

Primera vertical: login -> crear/consultar activo -> registrar Agent -> recibir/validar snapshot -> visualizar información técnica.

Las tareas se desglosan durante refinamiento; no crear tareas artificiales por capa si no aportan valor.
