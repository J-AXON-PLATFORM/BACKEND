# Postmortem de incidente

> **Clasificación:** CONDICIONAL  
> **Activar si:** un incidente tuvo impacto significativo o reveló un riesgo sistémico.

## Principio

Buscar aprendizaje y cambios del sistema, no culpables.

## Plantilla

```text
ID:
Fecha:
Severidad:
Impacto:
Duración:
Servicios/usuarios afectados:
Detección:
Línea de tiempo:
Causa técnica:
Factores contribuyentes:
Qué funcionó:
Qué falló:
Dónde faltó observabilidad:
Acciones inmediatas:
Acciones preventivas:
Responsables:
Fechas objetivo:
Riesgo residual:
```

## Causa

Distinguir:

- trigger;
- causa técnica;
- condiciones latentes;
- controles ausentes;
- decisiones previas.

Evitar respuestas superficiales como “error humano” sin analizar por qué el sistema permitió que el error causara impacto.

## Acciones

Cada acción debe ser:

- específica;
- asignada;
- trazable;
- priorizada;
- verificable.

No generar decenas de acciones sin impacto real.
