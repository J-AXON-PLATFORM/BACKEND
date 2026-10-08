# Pruebas de resiliencia

> **Clasificación:** CONDICIONAL

## Activar si

Existen mecanismos de resiliencia o requisitos de disponibilidad relevantes.

## Escenarios

Según arquitectura:

- timeout;
- latencia alta;
- error temporal;
- conexión rechazada;
- dependencia caída;
- saturación;
- retry;
- duplicados;
- pérdida de broker;
- DLQ;
- recuperación;
- failover.

## Validar

- integridad;
- degradación;
- ausencia de cascadas;
- límites de reintentos;
- idempotencia;
- observabilidad;
- recuperación.

## Chaos engineering

Solo introducir experimentos de caos cuando el sistema, entorno y equipo tengan madurez suficiente y exista una hipótesis concreta.

No causar riesgo innecesario en producción.
