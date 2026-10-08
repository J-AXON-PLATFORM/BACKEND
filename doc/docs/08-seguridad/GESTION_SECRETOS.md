# Gestión de secretos

> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla dinámica


<!-- JAXON-CKP004:START -->
## Instancia J-AXON -- reglas aprobadas

- Prefijo de configuración propia: `JAXON_`.
- `.env.example` versionable solo con nombres/valores ficticios; `.env` real únicamente conveniencia Local y fuera de Git.
- QA/Production no usan `.env` plano como almacén permanente de secretos.
- v1 usa archivos protegidos/runtime secrets del host, fuera del repositorio, montados solo en componentes que los necesitan.
- Secretos separados por Local/Development/Test/QA/Production y por propósito: DB runtime, DB migration, n8n HMAC, backup key, TLS private key, SFTPGo, etc.
- No existe un secreto maestro compartido para todo. Rotación independiente por propósito.
- CI secrets separados de runtime y mínimo privilegio; PRs no reciben secretos productivos.
- Frontend prácticamente no tiene secretos; `NEXT_PUBLIC_*` significa público para navegador.
- Vault/OpenBao/secret manager empresarial no es obligatorio v1; se integra cuando escala o infraestructura lo justifique.
- Production usa certificado confiable de PKI/CA de la organización; la private key TLS se protege como secreto.
<!-- JAXON-CKP004:END -->

## Propósito

Evitar secretos en código, repositorios, documentación o logs.

Definir:

- herramienta/proveedor si aplica;
- estrategia para desarrollo local;
- inyección por entorno;
- control de acceso;
- rotación;
- caducidad;
- auditoría;
- respuesta ante filtración;
- separación entre entornos.

Nunca usar secretos reales como ejemplos documentales.
