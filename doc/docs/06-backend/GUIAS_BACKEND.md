# Guías de backend

> **Clasificación:** CONDICIONAL

## Principios

- responsabilidades claras;
- dependencias explícitas;
- validación en fronteras;
- errores consistentes;
- transacciones delimitadas;
- timeouts en I/O remoto;
- secretos fuera del código;
- logs sin datos sensibles;
- idempotencia donde exista repetición posible.

## APIs

Seguir `GUIAS_API.md` y `CONTRATOS_API.md`.

## Rendimiento

No optimizar por intuición. Medir queries, pools, I/O y dependencias.

## Abstracciones

No introducir repository/service/factory/strategy por ritual. Cada capa debe resolver un problema real.

## Pruebas

Diseñar código para comportamiento verificable sin convertir el diseño en una arquitectura artificialmente desacoplada.
