# Casos de uso

> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla dinámica

## Propósito

Describir interacciones actor-sistema cuando una Historia de Usuario no aporta suficiente detalle.

### UC-001 — <Título>

- Actor principal:
- Actores secundarios:
- Precondiciones:
- Disparador:
- Flujo principal:
- Flujos alternativos:
- Errores/excepciones:
- Postcondiciones:
- Requisitos relacionados:
- Flujos relacionados:

### UC-SUP-005 -- Triaje operativo del ticket asignado

- Actor principal: `SUPPORT_TECHNICIAN` actualmente asignado.
- Precondiciones: sesión válida, CSRF y versión vigente del ticket no terminal.
- Flujo principal: cambia prioridad o ejecuta una transición permitida; Support incrementa la versión y persiste el evento durable en la misma transacción; frontend reconsulta el estado.
- Flujos alternativos: repetir el valor vigente con la versión actual devuelve el ticket sin mutación ni evento.
- Errores/excepciones: versión obsoleta o transición inválida produce `409`; actor no asignado no obtiene autoridad funcional.
- Postcondiciones: prioridad/estado oficial y outbox quedan consistentes.
- Requisitos relacionados: `RF-SUP-004`, `RF-SUP-006`.
- Flujos relacionados: `FL-SUP-001`.
