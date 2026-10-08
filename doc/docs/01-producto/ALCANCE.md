# Alcance

> **Clasificación:** OBLIGATORIO  
> **Instancia:** J-AXON | CKP-JAXON-005 | 2026-09-29  
> **Estado:** alcance v1 inicial aprobado; detalles finos pueden refinarse sin ampliar silenciosamente el scope.

## Objetivo de v1

J-AXON v1 permitirá administrar activos tecnológicos de una organización, recibir información técnica mediante Agents, descubrir equipos de red de forma autorizada, gestionar soporte asociado a esos activos y ofrecer asistencia IA/RAG con trazabilidad y control de acceso.

## Dentro del alcance

- MOD-06 Identidad: cuentas locales, login/logout, usuarios, roles/permisos, sesiones e identidades técnicas de Agents.
- MOD-01 Inventario: alta/consulta/actualización permitida, responsable, ubicación, estado, configuración aceptada e historial.
- MOD-03 Collector: registro/vínculo de Agent, snapshots SFTP, validación, estado y tareas controladas con respuestas.
- MOD-02 Discovery: rangos autorizados, trabajos, dispositivos detectados, revisión e incorporación autorizada.
- MOD-04 Soporte: ciclo básico completo de ticket, activo asociado, asignación, conversación, adjuntos e historial.
- MOD-05 IA+RAG: fuentes autorizadas, indexación pgvector, consulta con referencias y asistencia a soporte.
- MOD-07 Automatización: reglas/notificaciones básicas, SSE e integración n8n controlada.
- MOD-08 Auditoría/Reportes: eventos relevantes y reportes esenciales.

Los ocho módulos forman parte de v1, pero no todos tienen la misma profundidad.

## Fuera de v1 inicial

- microservicios, Kubernetes, HA activa-activa, zero-downtime obligatorio;
- SaaS multi-tenant compartido;
- app móvil o app de escritorio para usuarios;
- SSO/AD/LDAP empresarial;
- IA autónoma, shell/comandos/scripts remotos arbitrarios y tareas Agent mutantes (reboot/instalación/configuración) en v1;
- despliegue masivo avanzado de Agents;
- BPM/workflows complejos, BI/report builder avanzado, PDF/XLSX/reportes programados como capacidad base;
- telemetría de altísima frecuencia;
- exposición pública a Internet por defecto;
- SLA comercial rígido antes de mediciones.

## Criterio funcional de éxito

Un QA limpio ejecuta los flujos E2E aprobados: identidad, activo, Agent/snapshot, tarea SFTP, descubrimiento, ticket, RAG autorizado y auditoría, con datos de prueba y sin depender del legado. Además se exigen tests críticos verdes, contratos validados, migraciones reproducibles, instalación documentada y controles de seguridad aplicables.

## Criterios para cambiar el alcance

Desde el Lote 54 el alcance aprobado constituye la **baseline v1**. Todo cambio material debe analizar impacto, alternativas y riesgos, y requerir aprobación explícita. Una propuesta, ejemplo, capacidad del legado o presencia de un archivo en la plantilla no amplía el alcance por sí sola.
