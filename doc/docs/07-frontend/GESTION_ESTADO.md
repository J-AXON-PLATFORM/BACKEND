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

En `OLA2-SSE-001a`, una única `EventSource` con credenciales sigue al activo seleccionado. Solo un `ASSET_TECHNICAL_STATE_UPDATED` válido para ese activo invalida exactamente `['collector', userId, 'asset-technical-state', assetId]`; cambiar de activo o desmontar el workspace cierra la conexión. Un fallo SSE no elimina el último estado REST ni deshabilita la actualización manual.

En `OLA2-STA-001a`, la consulta Agent usa `['users', userId, 'agents', 'agent', agentId]`. La respuesta se valida estrictamente en runtime antes de entrar en caché: estado administrativo, estado operativo y evidencia UTC se muestran por separado; una respuesta incompleta o incoherente falla cerrada sin presentar `UNKNOWN`, `ONLINE` u `OFFLINE` inventado.

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
