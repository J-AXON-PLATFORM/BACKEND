# Pruebas de integración

> **Clasificación:** CONDICIONAL

## Propósito

Verificar colaboración real con infraestructura, framework o componentes externos controlables.

## Posibles objetivos

- base de datos;
- broker;
- filesystem;
- framework;
- cache;
- servicio local;
- sandbox de tercero.

## Diseño

Definir:

- frontera;
- dependencias reales;
- datos;
- aislamiento;
- cleanup;
- determinismo;
- tiempo máximo.

## Regla

No sustituir una integración crítica por mocks en todas las capas si existe riesgo de incompatibilidad real.

## Entornos efímeros

Usarlos cuando mejoren reproducibilidad sin introducir coste excesivo.
