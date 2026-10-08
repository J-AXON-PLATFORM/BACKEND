# Renderizado y entrega frontend

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Decisión J-AXON

Next.js App Router con **enfoque híbrido ligero**:

- Server Components por defecto para layouts/composición/contenido sin interacción;
- Client Components para features interactivas y server-state autenticado;
- datos de negocio autenticados normalmente browser -> REST -> Spring Boot;
- no usar Route Handlers/SSR como proxy/BFF general sin necesidad concreta;
- Spring Boot sigue siendo autoridad de negocio y autorización.

## SEO

J-AXON v1 es aplicación interna/LAN/autenticada: SEO no es requisito. Configurar no-indexación para evitar crawling accidental; metadata básica de title/favicon sigue permitida.

## Caché

TanStack Query gestiona caché cliente por semántica de feature. No introducir Redis ni caché server-side frontend preventivamente.

## Criterios de cambio

SSR/SSG adicional solo si una ruta concreta demuestra necesidad de HTML inicial, contenido público, caché server-side u otra ventaja medible.
