# Reversión y roll-forward

> **Clasificación:** CONDICIONAL

## Propósito

Definir recuperación de un despliegue defectuoso.

## Decisión

Para cada tipo de cambio indicar:

- rollback posible;
- tiempo;
- datos afectados;
- compatibilidad;
- migraciones;
- cachés;
- eventos;
- contratos.

## Roll-forward

Preferible cuando revertir datos/esquemas es más peligroso que desplegar una corrección.

## Migraciones

No asumir que una migración destructiva puede revertirse.

Relacionar con estrategia `expand-contract` cuando se requiera compatibilidad durante despliegue.

## Verificación

Después de revertir/corregir:

- health;
- smoke;
- métricas;
- errores;
- integridad de datos;
- jobs/eventos.
