# Estrategia DevOps

> **Clasificación:** OBLIGATORIO como decisión; puede concluir `NO_APLICA`.

## Propósito

Definir cómo un cambio pasa de código a un entorno verificable y operable.

## Estado

- CI: `[ SI | NO | POR_DECIDIR ]`
- Proveedor CI:
- CD: `[ SI | NO | POR_DECIDIR ]`
- Proveedor CD:
- Entornos:
- Registro de artefactos:
- Contenedores:
- Orquestación:
- IaC:
- Gestión de secretos:
- Estrategia de despliegue:

## Flujo de cambio

```text
Código
→ validación local
→ revisión
→ CI
→ artefacto inmutable
→ promoción
→ despliegue
→ smoke/health
→ observabilidad
→ rollback/roll-forward si falla
```

Adaptar si el proyecto es simple.

## Principios

- automatizar lo repetible cuando compense;
- mismo artefacto promovido entre entornos cuando sea viable;
- configuración separada del binario;
- secretos fuera del repositorio;
- evidencia de calidad antes de producción;
- cambios reversibles o recuperables;
- infraestructura reproducible cuando el riesgo lo justifique.

## No implica

DevOps no significa obligatoriamente:

- Docker;
- Kubernetes;
- GitHub Actions;
- cloud;
- Terraform;
- despliegue continuo automático.

Cada herramienta se decide por necesidad.
