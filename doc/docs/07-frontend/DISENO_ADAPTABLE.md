# Diseño adaptable

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Decisión J-AXON

La web es responsive y desktop es la experiencia operativa principal. No existe app móvil nativa v1.

- breakpoints basados en contenido, no marcas/dispositivos concretos;
- tablas complejas pueden transformarse a listas/cards o vistas simplificadas en viewports estrechos;
- preservar operación, jerarquía y legibilidad, no solo eliminar scroll horizontal;
- considerar touch targets, zoom, contenido largo y orientación.

## Browser support

Dos versiones estables mayores más recientes de Chrome, Edge, Firefox y Safari. Internet Explorer y navegadores fuera de soporte del fabricante quedan fuera.

## Validación

Playwright principal en Chromium; flujos críticos seleccionados en Chromium, Firefox y WebKit antes de releases relevantes. Probar viewport pequeño/mediano/grande, zoom y teclado.
