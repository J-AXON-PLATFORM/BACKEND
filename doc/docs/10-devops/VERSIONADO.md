# Versionado

> **Clasificación:** CONDICIONAL

## Propósito

Definir cómo se identifican versiones de producto, artefactos y contratos.

## Producto

Seleccionar esquema según necesidad:

- SemVer;
- CalVer;
- versión interna;
- otro.

No usar SemVer mecánicamente si el producto no tiene semántica de API compatible con ese modelo.

## Artefactos

Relacionar versión con:

- commit;
- build;
- imagen;
- SBOM;
- release;
- entorno.

Preferir artefactos inmutables.

## Contratos

Definir:

- breaking change;
- compatibilidad;
- deprecación;
- ventana de soporte;
- coexistencia de versiones.

## Pre-releases

Si aplica:

- alpha;
- beta;
- rc;
- snapshots.

## Base de datos

No confundir versión del producto con número de migración.

## Regla

La versión debe permitir identificar exactamente qué software está ejecutándose y qué compatibilidad promete.
