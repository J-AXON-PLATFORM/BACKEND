# Prácticas SRE

> **Clasificación:** CONDICIONAL  
> **Activar si:** existe operación 24x7, alta criticidad o SLO formales.

## Propósito

Aplicar prácticas de confiabilidad sin asumir la existencia de un equipo SRE dedicado.

## SLI/SLO

Definir indicadores medibles y objetivos vinculados a experiencia de usuario.

## Error budget

Si se utiliza:

- ventana;
- presupuesto;
- consumo;
- acciones cuando se agota;
- relación con releases.

No usar error budgets si no existen SLO medibles.

## Toil

Identificar trabajo operativo:

- manual;
- repetitivo;
- automatizable;
- reactivo;
- de bajo valor duradero.

Priorizar automatización cuando el coste acumulado la justifique.

## On-call

Si aplica:

- rotación;
- cobertura;
- severidades;
- escalamiento;
- accesos;
- handoff;
- fatiga;
- simulacros.

## Capacidad

Relacionar demanda, saturación y headroom con `RENDIMIENTO_CAPACIDAD.md`.

## Drills

Ejercitar:

- restauración;
- failover;
- pérdida de dependencia;
- credenciales;
- incidentes críticos.

## Regla

SRE es una disciplina de confiabilidad, no una colección obligatoria de herramientas.
