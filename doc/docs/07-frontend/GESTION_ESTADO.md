# Gestión del estado

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Decidir dónde vive cada tipo de estado y evitar stores globales innecesarios.

## Decisión J-AXON

| Tipo | Estrategia inicial |
|---|---|
| Server state | **TanStack Query** |
| Estado local UI | React local state |
| Filtros/paginación compartibles | URL/search params |
| Estado global cliente pequeño | React Context solo cuando aporte |
| Estado de autenticación oficial | backend + cookie HttpOnly server-side |
| Persistencia cliente | solo preferencias no sensibles si existe necesidad |

No Redux/Zustand inicialmente.

## TanStack Query

Gestiona fetching, cache, stale/fresh, invalidación, refetch, cancelación, loading/error y mutations. Los tiempos de `staleTime` se definen por feature/semántica, no globalmente.

- GET: 1 retry transitorio inicial configurable;
- mutations: sin retry automático por defecto;
- SSE: invalida/refresca queries afectadas; no sustituye estado oficial con payload arbitrario;
- sesión 401: limpiar server-state sensible en memoria y llevar a login.

## URL state

Filtros, orden y paginación que deban sobrevivir refresh/back/forward o ser compartibles se representan en URL cuando tenga sentido.

## Persistencia

No persistir credenciales, secretos ni datos sensibles completos en localStorage/sessionStorage/IndexedDB sin nueva decisión de seguridad.

## Riesgos a vigilar

- duplicación;
- estado divergente;
- race conditions;
- stale data;
- acoplamiento global;
- prefetch excesivo.
