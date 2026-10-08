# Política TDD


> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Aplicar TDD a comportamiento ejecutable sin forzar unit tests artificiales.


## Para comportamiento ejecutable

```text
RED -> GREEN -> REFACTOR
```

1. Definir comportamiento esperado.
2. Escribir una prueba que falle por la razón correcta.
3. Implementar lo mínimo para pasarla.
4. Refactorizar sin romper comportamiento.

## Para infraestructura/configuración/documentación

Usar validaciones equivalentes: schema validation, config test, image build, container healthcheck, smoke test, link check, lint, policy check, etc.

Principio común: **ningún cambio sin evidencia de verificación apropiada**.
