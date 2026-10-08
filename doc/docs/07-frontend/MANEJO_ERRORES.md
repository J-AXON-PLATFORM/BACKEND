# Manejo de errores frontend

> **Clasificación:** CONDICIONAL

## Propósito

Definir una experiencia consistente ante fallos locales, de red y del servidor.

## Categorías

- validación;
- autenticación;
- autorización;
- conflicto;
- no encontrado;
- rate limit;
- red;
- timeout;
- error inesperado.

## UX

Para cada categoría definir:

- mensaje;
- acción posible;
- retry;
- preservación de datos;
- navegación;
- soporte/código de referencia.

No mostrar stack traces, secretos o detalles internos.

## Observabilidad

Registrar datos suficientes para correlacionar el fallo sin capturar PII innecesaria.

## Error boundaries

Usarlos cuando el framework los permita y el aislamiento de fallos aporte valor.
