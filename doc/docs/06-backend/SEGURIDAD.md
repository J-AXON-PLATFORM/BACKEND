# Seguridad backend

> **Clasificación:** CONDICIONAL

## Propósito

Traducir políticas de `docs/08-seguridad/` a controles concretos del backend.

## Fronteras

Por endpoint/handler sensible definir:

- autenticación;
- autorización;
- validación;
- rate limit si aplica;
- auditoría;
- datos sensibles.

## Autorización

La autorización debe ejecutarse en una frontera confiable y considerar propiedad/alcance del recurso, no solo la visibilidad de la UI.

## Datos

- minimizar exposición;
- cifrar según clasificación;
- no incluir secretos en respuestas;
- controlar serialización;
- validar uploads.

## Sesiones/tokens

Documentar expiración, revocación, almacenamiento, rotación y protección contra replay según mecanismo.

## Integraciones

Usar mínimo privilegio y secretos gestionados.

## Errores/logs

No filtrar stack traces, claves, tokens ni detalles internos.

## Pruebas

Cubrir abuso de permisos, entradas no confiables y casos negativos relevantes.
