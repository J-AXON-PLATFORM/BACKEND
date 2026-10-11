# Authentication


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.



<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- autenticación humana v1

- Cuentas locales inicialmente; SSO/federación futuro.
- Sesión opaca server-side persistida en PostgreSQL bajo `identity`; no JWT Access/Refresh para humanos v1.
- Cookie `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, no persistente; `__Host-` cuando la topología lo permita.
- CSRF activo en operaciones mutables.
- TTL: 30 min inactividad + 12 h absoluto. Logout, disable, cambio de contraseña o roles revocan sesiones.
- Argon2id base 19 MiB/t=2/p=1 ajustable por benchmark.
- Password: mínimo 15 caracteres, aceptar largas (al menos 128), sin composición artificial, con blocklist local.
- Sin autorregistro. Bootstrap inicial seguro; reset v1 administrativo. MFA no obligatorio v1.
- Protección login base: 5 fallos/15 min con backoff/rate limit configurable y mensajes genéricos.
- Identidad Agent está completamente separada de identidad humana.
<!-- JAXON-CKP003:END -->

## Estado ejecutable -- OLA1-IAM-001a

- `POST /api/v1/auth/login` y `GET /api/v1/auth/me` implementan el contrato OpenAPI 1.0 sin cambios semánticos.
- El token de sesión CSPRNG de 256 bits se conserva únicamente en la cookie `__Host-JAXON_SESSION`; PostgreSQL persiste su SHA-256.
- El CSRF CSPRNG queda asociado a la sesión y el frontend lo conserva solo en memoria.
- El bootstrap está desactivado por defecto y crea exactamente `SYSTEM_ADMIN` + `IT_OPERATOR` en un store vacío.
- Los intentos se serializan por clave hash y aplican cinco fallos permitidos en 15 minutos; el sexto se limita.
- `HUMAN_LOGIN_SUCCEEDED` y `HUMAN_LOGIN_FAILED` se capturan de forma durable en el outbox de Identity conforme a `AC-JAXON-060`.
- La evidencia automatizada cubre PostgreSQL/Flyway real, Argon2id, sesión, inactividad/expiración absoluta, cookie, CSRF, default deny, errores genéricos, rate limit y journey Playwright Chromium.

## Propósito

Definir mecanismo de identidad y ciclo de sesión

- Mechanism
- Credentials/tokens
- Expiration
- Rotation/revocation
- Recovery
- MFA if applicable
