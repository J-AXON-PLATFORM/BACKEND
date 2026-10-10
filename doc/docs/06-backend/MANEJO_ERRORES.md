# Manejo de errores backend

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- contrato de error

REST usa RFC 9457 (`application/problem+json`). Campos estándar + extensiones J-AXON `code`, `correlationId` y, para validación múltiple, `errors[]` con puntero/código/mensaje. Los clientes programan contra `code`, nunca contra el texto de `detail`. En Production no se exponen stack traces, SQL, rutas internas ni secretos.

AC-JAXON-059 materializa este modelo en `doc/contracts/openapi/1.0/openapi.yaml` mediante `Problem` y `FieldError`; el contrato existe, pero sus handlers todavía no están implementados.
<!-- JAXON-CKP003:END -->

## Categorías

- validación;
- autenticación;
- autorización;
- no encontrado;
- conflicto;
- rate limit;
- dependencia;
- timeout;
- interno.

## Contrato

Definir:

- código/status;
- identificador estable;
- mensaje seguro;
- detalles permitidos;
- correlation ID.

## Logs

Registrar causa técnica internamente sin exponer secretos al cliente.

## Retries

No convertir errores internos en reintentos indiscriminados. Coordinar con `RESILIENCIA.md`.
