# Enrutamiento frontend

> **Clasificación:** CONDICIONAL

## Propósito

Definir navegación, URLs, protección visual y carga de páginas.

## Decisiones

- routing cliente/servidor;
- rutas públicas/privadas;
- parámetros;
- query params;
- deep links;
- 404;
- redirecciones;
- layouts;
- lazy loading;
- prefetch;
- guards.

## Regla de seguridad

Una ruta protegida en frontend mejora UX, pero no sustituye autorización en backend/servicio confiable.

## Compatibilidad

Si existe SSR/SSG, coordinar rutas con `RENDERIZADO_ENTREGA.md`.

## Testing

Cubrir:

- navegación;
- rutas inválidas;
- permisos visuales;
- parámetros;
- refresh directo;
- back/forward.
