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

## Estado ejecutable inicial -- AC-JAXON-059

- Backend parsea OpenAPI 3.1.2, comprueba rutas/`operationId`/RFC 9457 y valida fixtures externos contra schemas de componentes.
- Backend valida los cuatro JSON Schemas Agent contra Draft 2020-12, resuelve `$ref` locales, prueba fixtures positivos/negativos y verifica manifest SHA-256.
- `automation-collector` repite los fixtures con `jsonschema`/`referencing`, impide resolución remota y prueba sincronización e integridad.
- `oasdiff` 1.33.0 valida la primera incorporación y compara breaking changes cuando exista baseline.
- Estas pruebas verifican contratos, no conformidad de controladores, SFTP o comportamiento E2E todavía inexistentes.

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
