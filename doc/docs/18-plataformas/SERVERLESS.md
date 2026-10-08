# Serverless

> **Clasificación:** CONDICIONAL

## Activar si

Se utilizan funciones o plataformas de ejecución administrada disparadas por eventos/solicitudes.

## Decisiones

- modelo de ejecución;
- trigger;
- timeout;
- memoria/CPU;
- concurrencia;
- cold start;
- límites/cuotas;
- retries;
- idempotencia;
- DLQ;
- networking;
- secretos;
- almacenamiento;
- observabilidad;
- coste.

## Riesgos

- acoplamiento al proveedor;
- límites de ejecución;
- tormentas de concurrencia;
- reintentos automáticos;
- depuración distribuida;
- costes impredecibles por volumen.

## Regla

Usar serverless cuando su modelo operativo y de costes encaje con la carga, no por ausencia de servidores visibles.
