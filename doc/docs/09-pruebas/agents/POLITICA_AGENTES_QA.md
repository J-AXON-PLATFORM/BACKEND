# Política de agentes QA


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Permitir agentes de testing sin dejar que oculten defectos.


## Permitido
- Explorar aplicación.
- Generar test plans.
- Generar tests.
- Ejecutar tests.
- Analizar fallos.
- Proponer reparaciones.
- Crear reportes de defectos.

## Prohibido sin aprobación
- Eliminar tests fallidos.
- Debilitar assertions.
- Agregar skips silenciosos.
- Cambiar requisitos/criterios de aceptación.
- Reclasificar un bug de producto como bug de test sin evidencia.

## Clasificación de fallo
`PRODUCT_BUG | TEST_BUG | ENVIRONMENT | FLAKY | UNKNOWN`
