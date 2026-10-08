# Validación backend

> **Clasificación:** CONDICIONAL

## Propósito

Distinguir formato, invariantes y reglas de negocio.

## Capas

### Frontera

Formato, tipos, campos requeridos, límites de tamaño.

### Aplicación/dominio

Reglas que dependen del caso de uso o estado.

### Base de datos

Constraints como última línea de integridad cuando corresponda.

## Errores

Devolver errores consistentes y no filtrar detalles internos.

## Regla

No confiar en validación del frontend para proteger el sistema.
