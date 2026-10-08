# Flujo de trabajo de la IA

> **Clasificación:** OBLIGATORIO  
> **Estado:** Operativo

## Propósito

Estandarizar el ciclo de trabajo de cualquier agente o asistente.

```mermaid
flowchart LR
    A[Solicitud] --> B[Leer Proyecto Maestro]
    B --> C[Activar contexto necesario]
    C --> D[Analizar]
    D --> E{¿Falta contexto material?}
    E -->|Sí| F[Consultar solo lo necesario]
    E -->|No| G[Comparar alternativas si aplica]
    F --> G
    G --> H[Recomendación]
    H --> I{¿Aprobación requerida?}
    I -->|Sí| J[Solicitar/obtener aprobación]
    I -->|No| K[Plan]
    J --> K
    K --> L[Implementar]
    L --> M[Probar]
    M --> N[Actualizar contratos/documentación]
    N --> O[Verificar sistema afectado]
    O --> P[Entregar evidencia]
```

## 1. Activación de contexto

Antes de analizar:

1. leer `PROYECTO_MAESTRO.md`;
2. identificar perfiles técnicos relevantes;
3. consultar `MATRIZ_ACTIVACION.md`;
4. usar `ENRUTADOR_DOCUMENTACION.md`;
5. cargar solo módulo, flujo, requisito y políticas capaces de cambiar el resultado.

## 2. Modo análisis

Permitido:

- inspeccionar;
- reconstruir comportamiento;
- detectar riesgos;
- comparar opciones;
- estimar impacto;
- preparar ADR/RFC;
- proponer plan.

No ejecutar cambios materiales sin autorización cuando la política la exija.

## 3. Modo decisión

Si existen alternativas:

- aplicar `MARCO_TOMA_DECISIONES.md`;
- incluir opción simple;
- considerar operación y reversibilidad;
- evitar seleccionar por moda;
- registrar decisión aprobada.

## 4. Modo ejecución

1. cambio mínimo coherente;
2. TDD o validación equivalente;
3. contratos;
4. pruebas necesarias;
5. regresión;
6. documentación;
7. trazabilidad;
8. verificación operacional si aplica.

## 5. Modo cierre

Entregar:

- qué cambió;
- decisión/ADR/RFC;
- contratos afectados;
- pruebas;
- documentación;
- riesgos;
- pendientes;
- evidencia de aceptación.

Si se modificó documentación estructural, ejecutar `tools/validate_docs.py`.


## Regla J-AXON para lotes y alcance

Las decisiones relacionadas pueden agruparse en lotes coherentes. El usuario puede aprobar el lote completo o parcialmente. Cada elemento no aprobado conserva su estado. El paso “Implementar” del diagrama solo se ejecuta cuando existe autorización de implementación; aprobar diseño no basta.
