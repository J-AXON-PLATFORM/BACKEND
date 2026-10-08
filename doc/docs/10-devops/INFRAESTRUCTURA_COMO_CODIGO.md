# Infraestructura como código

> **Clasificación:** CONDICIONAL  
> **Activar si:** la infraestructura debe ser repetible, revisable o reproducible y la gestión manual deja de ser suficiente.


## Instancia J-AXON -- decisión Lote 44

No se adopta todavía Terraform/OpenTofu/Pulumi ni otra herramienta IaC porque hardware, virtualización y posible proveedor futuro no están definidos. Esto no autoriza instalaciones artesanales: desde v1 deben quedar versionados y revisables Docker Compose, configuración no secreta, reverse-proxy config, scripts de deployment, backup/restore, migraciones y health checks.

Cuando la infraestructura requiera múltiples hosts, aprovisionamiento repetible, cloud o varias instalaciones gestionadas, reevaluar IaC con evidencia real. Secretos nunca entran al repositorio/estado IaC en texto plano.

## Propósito

Gestionar infraestructura mediante cambios versionados y revisables sin imponer herramienta.

## Decisión

Comparar según contexto:

- Terraform/OpenTofu;
- Pulumi;
- CloudFormation;
- Bicep;
- herramientas del proveedor;
- configuración declarativa de plataforma;
- gestión manual para casos realmente simples.

## Estructura

Definir:

- repositorio;
- módulos;
- entornos;
- variables;
- secretos;
- naming;
- ownership.

## Estado

Si la herramienta usa estado:

- backend;
- locking;
- cifrado;
- permisos;
- backup;
- recuperación;
- acceso de CI.

## Flujo

```text
Cambio
→ validación
→ plan
→ revisión
→ aprobación
→ apply
→ verificación
```

## Drift

Definir:

- detección;
- frecuencia;
- respuesta;
- cambios manuales permitidos;
- reconciliación.

## Seguridad

- mínimo privilegio;
- secretos fuera del código;
- análisis estático;
- políticas;
- revisión;
- separación de entornos.

## Testing

Considerar:

- formato;
- validate;
- lint;
- policy checks;
- unit tests de módulos si aportan valor;
- plan review;
- pruebas en sandbox.

## Destrucción

Para recursos destructivos:

- protecciones;
- aprobación;
- backups;
- retención;
- procedimiento de recuperación.
