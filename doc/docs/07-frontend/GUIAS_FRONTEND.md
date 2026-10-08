# Guías de frontend

> **Clasificación:** CONDICIONAL

## Propósito

Definir reglas generales de implementación sin casarlas con un framework concreto.

## Principios

- componentes con responsabilidades claras;
- lógica de negocio central no duplicada accidentalmente;
- contratos de API explícitos;
- estados de carga, vacío, error y éxito;
- accesibilidad desde diseño;
- responsive según dispositivos reales;
- rendimiento medido;
- errores observables;
- validación en cliente para UX, no como única barrera de seguridad.

## Dependencias

Antes de agregar una librería:

- problema;
- alternativa nativa/simple;
- tamaño/coste;
- mantenimiento;
- seguridad;
- compatibilidad;
- lock-in.

## Formularios

Definir:

- validación;
- errores por campo;
- errores globales;
- envío duplicado;
- loading;
- accesibilidad;
- preservación de datos;
- server validation.

## Asincronía

Manejar:

- cancelación;
- race conditions;
- respuestas obsoletas;
- reintentos;
- offline cuando aplique.

## Testing

Seleccionar:

- unitario;
- componente;
- accesibilidad;
- visual;
- integración;
- E2E crítico.

## Evidencia

Las reglas concretas del proyecto deben quedar aquí o enlazadas desde este documento.
