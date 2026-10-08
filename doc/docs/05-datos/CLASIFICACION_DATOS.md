# Clasificación de datos

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Clasificar datos según sensibilidad y controles necesarios.

## Categorías de referencia

`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED` son una taxonomía inicial de referencia. La adaptación normativa/organizacional concreta sigue `POR_DECIDIR`.

## Reglas J-AXON ya aprobadas

- secretos, credenciales, claves privadas y material criptográfico nunca se tratan como datos ordinarios de aplicación;
- Production y QA no dependen de `.env` plano como almacén permanente de secretos;
- todos los backups de Production se cifran en reposo porque mezclan categorías de distinta sensibilidad;
- logs minimizan PII y nunca registran passwords, cookies, tokens, SSH private keys, HMAC secrets o backup keys;
- documentos para IA/RAG conservan clasificación/autorización de su fuente;
- **datos clasificados como sensibles no se envían a un proveedor externo de IA hasta aprobar explícitamente proveedor, tratamiento y configuración**;
- la autorización se evalúa antes/durante retrieval, independientemente de que exista embedding;
- adjuntos Support se protegen según permiso del ticket y clasificación aplicable.

## Por dataset/documento

Registrar cuando corresponda:

```text
Owner:
Clasificación:
Base legal/purpose:
Quién puede leer:
Quién puede modificar:
Puede salir de la LAN:
Puede enviarse a proveedor IA:
Retención:
Cifrado adicional:
Auditoría:
```
