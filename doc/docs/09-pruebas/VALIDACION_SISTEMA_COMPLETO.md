# Validación del sistema completo

> **Clasificación:** OBLIGATORIO como estrategia; las capas concretas se activan según el proyecto.  
> **Estado:** Plantilla dinámica

## Propósito

Evitar que cada parte funcione aisladamente mientras el producto falla como conjunto. La estrategia de calidad debe demostrar el comportamiento correcto desde reglas pequeñas hasta recorridos completos, sin duplicar innecesariamente los mismos escenarios en todas las capas.

## Principio

No existe una pirámide rígida válida para todos los proyectos. Seleccionar capas según arquitectura, riesgos, coste y velocidad de feedback.

Posibles capas:

```text
Análisis estático / compilación / lint
              ↓
Pruebas unitarias
              ↓
Pruebas de componente
              ↓
Pruebas de integración
              ↓
Pruebas de contrato
              ↓
Pruebas de sistema
              ↓
Pruebas E2E
              ↓
Pruebas no funcionales / operación
```

No todos los proyectos requieren todas las capas.

## Qué demuestra cada nivel

### Unitarias

Demuestran reglas o unidades aislables con feedback rápido. No prueban que componentes externos colaboren correctamente.

### Componente

Prueban un componente o módulo como unidad mayor, normalmente a través de su interfaz pública, sustituyendo dependencias externas cuando sea conveniente.

### Integración

Prueban colaboración real con infraestructura o componentes: DB, filesystem, broker, framework, proveedor controlado, etc.

### Contrato

Demuestran que consumidor y proveedor siguen hablando el mismo idioma sin necesitar ejecutar todo el sistema.

### Sistema

Prueban el producto desplegado o un conjunto amplio de componentes desde una frontera externa, verificando que la arquitectura integrada funciona.

### E2E

Prueban recorridos de negocio críticos desde el punto de vista de un actor o cliente real. Deben centrarse en journeys de alto valor, no duplicar exhaustivamente tests unitarios.

### No funcionales

Según requisitos:

- rendimiento/capacidad;
- seguridad;
- accesibilidad;
- resiliencia;
- recuperación;
- compatibilidad;
- observabilidad;
- instalación/upgrade;
- migraciones.

## Matriz por flujo

Para cada flujo crítico decidir qué capas lo verifican:

| Flujo | Unit | Integración | Contrato | Sistema | E2E | NFR |
|---|---|---|---|---|---|---|
| FLOW-XXX | | | | | | |

No marcar todas por defecto. Justificar las necesarias.

## Ejemplo conceptual de recorrido

```text
Actor
  ↓
UI/cliente
  ↓
Contrato de entrada
  ↓
Aplicación/API
  ↓
Módulo A
  ↓
Contrato interno
  ↓
Módulo B
  ↓
Persistencia / integración externa
  ↓
Respuesta
  ↓
Resultado observable por el actor
```

La estrategia de pruebas debe cubrir las fronteras relevantes y al menos un recorrido completo para los flujos críticos cuando sea técnicamente aplicable.

## Validación de backend sin frontend

El backend debe poder demostrar sus contratos y reglas independientemente de la UI cuando su arquitectura lo permita. No esperar al E2E visual para descubrir errores de API, persistencia o seguridad.

## Validación de frontend sin backend real

Puede validar componentes, estados y manejo de respuestas mediante dobles/mocks controlados, pero esto no sustituye pruebas de contrato e integración con el backend real.

## Validación de integraciones externas

Evitar depender exclusivamente de servicios de terceros en la suite normal.

Definir según riesgo:

- stubs/fakes;
- sandbox del proveedor;
- contract tests;
- smoke tests controlados;
- pruebas periódicas contra entorno real autorizado.

## Migraciones

Si existen migraciones de datos o schema, validar:

- migración desde una versión soportada;
- compatibilidad durante despliegue si aplica;
- integridad de datos;
- rollback o estrategia forward-only definida;
- performance sobre volumen representativo cuando sea relevante.

## Automatización

La ejecución puede vivir en cualquier CI/CD seleccionado o ejecutarse localmente/manual. El framework no presupone GitHub Actions.

Separar, cuando sea útil:

- suite rápida por cambio/PR;
- suite de integración;
- suite E2E;
- regresión completa;
- pruebas nocturnas/periódicas;
- validaciones de release.

La selección depende del coste de ejecución y del riesgo.

## Criterio de terminado

Para una funcionalidad significativa, antes de `DONE` debe existir evidencia suficiente de que:

1. las reglas internas funcionan;
2. las integraciones afectadas funcionan;
3. los contratos siguen siendo compatibles;
4. los flujos críticos afectados funcionan como sistema;
5. los NFR relevantes siguen cumpliéndose;
6. no se ocultaron fallos deshabilitando o debilitando pruebas.
