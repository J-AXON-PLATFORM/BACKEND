# Pruebas de regresión

> **Clasificación:** CONDICIONAL

## Propósito

Volver a comprobar comportamiento existente en proporción al impacto del cambio.

## Niveles

### Smoke

Capacidades mínimas que indican que el sistema está operativo.

### Regresión dirigida

Escenarios relacionados con módulos, contratos y riesgos afectados.

### Regresión completa

Suite amplia para versiones o ventanas periódicas cuando el coste lo justifique.

## Selección basada en impacto

Usar trazabilidad para identificar:

- requisitos afectados;
- flujos;
- contratos;
- módulos;
- integraciones;
- NFR.

## Defectos

Todo bug relevante debería agregar una prueba en la capa adecuada si es reproducible y aporta protección futura.

## Regla

La regresión no debe ser una lista fija ajena a los cambios; debe combinar un núcleo estable con cobertura dirigida.
