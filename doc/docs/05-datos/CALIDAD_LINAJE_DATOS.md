# Calidad y linaje de datos

> **Clasificación:** CONDICIONAL  
> **Activar si:** hay ETL/ELT, reporting, analítica, integración de múltiples fuentes o calidad de datos crítica.

## Dimensiones de calidad

Seleccionar las relevantes:

- completitud;
- validez;
- exactitud;
- unicidad;
- consistencia;
- oportunidad;
- integridad referencial.

## Reglas

Por dato crítico:

```text
Dato:
Fuente:
Propietario:
Regla de calidad:
Umbral:
Dónde se valida:
Qué ocurre si falla:
Métrica:
```

## Linaje

Registrar:

- fuente;
- transformaciones;
- destinos;
- contratos;
- responsables;
- versión de esquema;
- uso sensible.

## Schema evolution

Definir:

- compatibilidad backward/forward;
- deprecación;
- migración;
- consumidores;
- validación.

## Pipelines

Para ETL/ELT:

- idempotencia;
- checkpoints;
- reejecución;
- datos tardíos;
- duplicados;
- errores parciales;
- reconciliación;
- observabilidad.

## Evidencia

La calidad debe ser medible cuando afecte decisiones de negocio o sistemas downstream.
