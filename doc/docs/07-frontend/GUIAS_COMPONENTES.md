# Guías de componentes

> **Clasificación:** CONDICIONAL

## Propósito

Construir componentes reutilizables sin crear abstracciones prematuras.

## Criterios

Un componente debería tener:

- responsabilidad clara;
- API explícita;
- estados definidos;
- accesibilidad;
- comportamiento responsive;
- errores manejables;
- pruebas proporcionales al riesgo.

## Reutilización

No abstraer porque dos bloques “se parecen”.

Abstraer cuando existe:

- comportamiento compartido estable;
- semántica común;
- beneficio de mantenimiento;
- contrato claro.

## Estados

Documentar o implementar según aplique:

- default;
- hover/focus;
- disabled;
- loading;
- empty;
- error;
- success.

## Diseño

Enlazar con `SISTEMA_DISENO.md` para tokens, componentes base y convenciones.
