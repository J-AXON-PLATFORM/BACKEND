# Observabilidad frontend

> **Clasificación:** CONDICIONAL

## Propósito

Detectar problemas que solo ocurren en el navegador/dispositivo del usuario.

## Señales

Según necesidad:

- errores JavaScript;
- fallos de red;
- performance;
- navegación;
- release/version;
- correlation ID;
- métricas de experiencia.

## Privacidad

No capturar:

- contraseñas;
- tokens;
- datos sensibles;
- contenido de formularios sin justificación.

Definir redacción y muestreo.

## Correlación

Cuando sea posible, relacionar error cliente con request/traza backend sin exponer identificadores sensibles.
