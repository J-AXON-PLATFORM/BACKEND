# Pruebas de migraciones

> **Clasificación:** CONDICIONAL

## Activar si

Se modifican esquemas o datos persistentes.

## Casos

- instalación limpia;
- migración desde versiones soportadas;
- datos nulos/legacy;
- volumen representativo;
- backfill;
- compatibilidad entre versiones;
- restart/retry;
- fallo parcial.

## Validar

- integridad;
- constraints;
- índices;
- duración;
- locks;
- disponibilidad;
- rollback/roll-forward.

## Regla

No declarar una migración segura solo porque funciona sobre una base vacía.
