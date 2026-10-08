# Arquitectura de frontend

> **Clasificación:** CONDICIONAL  
> **Activar si:** existe `FRONTEND_WEB`.  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Decisiones vigentes

- Node.js 24 LTS, npm 11.x, Next.js 16.3.x, React 19.x compatible y TypeScript 5.9.x.
- `src/app/` para routing/layout y `src/features/` para capacidades de usuario/negocio.
- Next.js App Router con estrategia híbrida ligera: Server Components por defecto para layout/composición sin interactividad; Client Components donde se requiere interacción.
- Datos autenticados de negocio se consumen normalmente desde navegador por REST directo a Spring Boot; Next.js no es BFF/segundo backend por defecto.
- TanStack Query es la herramienta inicial de server-state.
- SSE solo avisa/invalida; REST conserva la verdad.
- No Redux/Zustand inicialmente; introducir store global solo con necesidad demostrada.
- `NEXT_PUBLIC_*` solo para información pública; no almacenar secretos ni tokens sensibles en browser storage.
- SEO no es requisito v1 y la aplicación debe ser no-indexable.
- Browser support: dos versiones estables mayores más recientes de Chrome, Edge, Firefox y Safari; IE fuera.
- Responsive con desktop como experiencia operativa principal.
- WCAG 2.2 AA como objetivo de diseño, sin afirmar certificación formal.

## Estado

- server state: TanStack Query;
- local UI: React local state;
- filtros/paginación compartibles: URL;
- global client state pequeño: Context cuando aporte;
- persistencia: solo preferencias no sensibles cuando exista necesidad.

## Datos/API

Centralizar clientes/operaciones API por feature/shared layer; evitar `fetch` disperso sin convención. OpenAPI del backend sigue siendo fuente contractual. Cualquier cliente/tipos generados son derivados.

Queries GET pueden tener 1 retry transitorio inicial. Mutations no se reintentan automáticamente por defecto salvo garantía explícita de idempotencia.

Ante 401 por sesión expirada, limpiar server-state sensible en memoria y regresar al login; no inventar renovación JWT porque la sesión humana es server-side opaca.

## Estados de pantalla

Cada pantalla remota contempla cuando corresponda: loading, success, empty, error y permission denied. Errores REST usan `application/problem+json` y pueden mostrar `code`/`correlationId` de forma segura para soporte.

## Seguridad

Autorización real siempre en backend. UI solo mejora UX. No persistir passwords, cookies/session material, claves, documentos sensibles completos ni secretos en Local/Session Storage.

## Calidad

Relacionar con accesibilidad, responsive, performance, errores, observabilidad y testing cross-browser.
