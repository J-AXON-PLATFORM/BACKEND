# Criterios de calidad


> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Definir puertas de calidad independientes del proveedor de CI/CD.


## Gate antes de merge/release

- [ ] Build/compilación valida.
- [ ] Lint/static checks validos si aplican.
- [ ] Unit tests pasan.
- [ ] Integration tests relevantes pasan.
- [ ] Contratos/API compatibles o breaking change documentado.
- [ ] E2E críticos pasan cuando aplica.
- [ ] Las verificaciones de seguridad definidas pasan.
- [ ] Documentación afectada actualizada.
- [ ] Criterios de aceptación verificados.

El proveedor puede ser GitHub Actions, GitLab CI, Jenkins, Azure DevOps, otro o ejecución manual automatizada local.
