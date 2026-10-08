# Pruebas de contrato

> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla dinámica

## Propósito

Detectar incompatibilidades entre consumidores y proveedores antes de depender de una prueba E2E completa.

Los contratos pueden corresponder a frontend/backend, módulo/módulo, servicio/servicio, productor/consumidor de eventos o sistema/proveedor externo.

## Estrategias posibles

Seleccionar según tecnología y riesgo:

- validación de schema;
- provider verification;
- consumer-driven contract testing;
- pruebas de compatibilidad hacia atrás/adelante;
- validación de eventos y mensajes;
- pruebas contra sandbox de proveedor externo;
- compilación compartida de interfaces/tipos cuando sea apropiado.

No imponer una herramienta concreta.

## Para cada contrato

- ID del contrato;
- consumidor;
- proveedor;
- fuente canónica;
- versión;
- escenarios críticos;
- cambios compatibles/incompatibles;
- estrategia de prueba;
- cuándo se ejecuta;
- responsable del fallo.

## Instancia J-AXON -- verificación mínima de contratos internos

Además de OpenAPI y JSON Schema, los contratos internos del Lote 53 requieren:

- verificación de fronteras/dependencias con Spring Modulith;
- compilación de APIs/tipos públicos compartidos dentro del backend;
- integration tests consumidor/proveedor cuando la regla cruza módulo;
- serialización/deserialización de eventos durables y compatibilidad `MAJOR.MINOR`;
- pruebas de idempotencia ante `eventId` duplicado;
- prueba crash/recovery de cambio de negocio + outbox + dispatch posterior;
- pruebas de autorización para comprobar que Automation/AI/eventos no adquieren autoridad implícita.

No se introduce una plataforma externa de consumer-driven contracts por defecto mientras el backend siga siendo un único artefacto desplegable.

## Regla de fallo

Un contrato roto debe clasificarse antes de modificar tests:

- defecto del proveedor;
- consumidor desactualizado;
- contrato/documentación incorrecta;
- cambio incompatible no gestionado;
- ambiente/datos de prueba;
- indeterminado.

No reducir assertions o modificar el contrato solo para hacer pasar la pipeline.

Consultar `docs/04-arquitectura/CONTRATOS_SISTEMA.md`.
