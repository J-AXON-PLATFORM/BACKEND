# Chequeos de salud

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Distinguir proceso vivo, listo y funcional sin provocar reinicios en cascada.

## Liveness

Responde si el proceso está vivo. No falla solo porque IA, n8n, SFTP u observabilidad estén temporalmente degradados.

## Readiness

Indica si el backend puede atender tráfico core de forma segura. PostgreSQL sí afecta readiness del core; IA/n8n normalmente no.

## Startup

Puede usarse durante arranque/migraciones/inicialización cuando aporte.

## Seguridad

No exponer detalles internos o secretos en endpoints públicos.

## Alertas

Readiness core fallando durante >5 min en ventana operativa es condición inicial de alerta relevante.

## Pruebas

Verificar arranque, caída/recuperación de DB y caída de dependencias degradables sin reinicio en cascada.
