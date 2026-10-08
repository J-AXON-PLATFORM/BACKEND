# Reglas de frontend para IA

> **Clasificación:** CONDICIONAL

## Antes de modificar

Leer flujo, aceptación, arquitectura frontend, contrato y documentos activados de accesibilidad/rendimiento.

## Reglas

- modelar carga, vacío, éxito, error y permisos;
- no tratar guards visuales como autorización de seguridad;
- no duplicar reglas centrales de negocio sin justificación;
- seleccionar estado local/compartido/server-state conscientemente;
- preservar accesibilidad;
- preservar responsive;
- considerar errores de red y respuestas obsoletas;
- medir antes de optimizar bundles/renderizado;
- no asumir que mocks demuestran integración;
- actualizar E2E solo para journeys críticos;
- evitar capturar PII en telemetría.
