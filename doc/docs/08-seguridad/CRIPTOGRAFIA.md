# Criptografía

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Documentar usos criptográficos y evitar diseños caseros o decisiones ad hoc.

## Regla principal

Usar primitivas y bibliotecas mantenidas y reconocidas. No diseñar algoritmos criptográficos propios.

## Inventario J-AXON aprobado

| Propósito | Primitiva / regla | Notas |
|---|---|---|
| Password hashing humano | Argon2id | base 19 MiB / t=2 / p=1, ajustable por benchmark |
| Identidad Agent/Discovery | SSH Ed25519 | keypair individual; private key local; host key verificada |
| Integridad de archivos | SHA-256 | hash canónico; no sustituye cifrado |
| Callback n8n | HMAC-SHA-256 | firma sobre mensaje/campos contractuales + timestamp; anti-replay 5 min |
| Web TLS | TLS 1.3 preferido; TLS 1.2 permitido | TLS 1.0/1.1 deshabilitados; cert confiable PKI/CA organización |
| SFTP | SSH/SFTP | canal Agent y Discovery Executor separado por identidad |
| Backups Production | cifrado en reposo obligatorio | clave separada del backup; mecanismo/proveedor concreto según instalación |

`OLA4-DIS-002a` genera Ed25519 exclusivamente en el host del Discovery Executor y almacena `executor_ed25519` con modo `0600` dentro de un directorio `0700`; backend recibe solo la representación pública OpenSSH, normaliza los bytes y calcula la huella `SHA256` canónica. La misma clave no puede pertenecer a dos identidades técnicas.

## Separación conceptual

No intercambiar:

- hash de integridad SHA-256;
- HMAC para autenticidad/integridad de mensajes;
- password hashing Argon2id;
- cifrado de backups/datos;
- TLS/SFTP para tránsito;
- firma/autenticación SSH.

## Claves y secretos

- separación por propósito y entorno;
- Development/QA/Production no comparten secretos;
- no existe un `JAXON_MASTER_SECRET` reutilizado para todo;
- permitir rotación independiente;
- secret manager empresarial concreto sigue abierto; v1 puede usar runtime secret files protegidos fuera de Git.

## Nonces/IV/salts

Seguir requisitos de la primitiva. No reutilizar valores que deban ser únicos.

## Migración

Cambios criptográficos significativos requieren estrategia de compatibilidad y, normalmente, ADR.

## Pruebas

Validar interoperabilidad, rotación, replay, errores y datos históricos sin exponer material secreto.
