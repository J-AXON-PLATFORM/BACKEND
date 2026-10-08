# Entornos

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- cinco entornos canónicos

| Entorno | Propósito | Datos/persistencia | Entrega |
|---|---|---|---|
| Local | desarrollo individual | ficticios/recreables | Compose principalmente para infraestructura; backend/frontend pueden correr nativos |
| Development | integración de componentes | separados de otros entornos | imágenes generadas por CI |
| Test | pruebas automáticas | efímeros/desechables | Testcontainers/CI, no servidor permanente |
| QA | validación/demo estable | sintéticos/preparados | mismo artefacto promovido |
| Production | operación real | reales/aislados | mismo digest validado, aprobación explícita |

Preproduction física separada no es obligatoria. `develop` es rama Git de integración/candidato, **no** el entorno Development. Misma build/artefacto se promueve con configuración y secretos externos separados; no copiar datos productivos a QA sin sanitización.
<!-- JAXON-CKP003:END -->

## Propósito

Definir diferencias legítimas entre local, dev, test, QA, staging y producción.

## Por entorno

```text
Nombre:
Propósito:
Usuarios:
Datos:
Acceso:
Configuración:
Secretos:
Dependencias:
Despliegue:
Observabilidad:
Persistencia:
Destrucción/recreación:
```

## Principios

- minimizar diferencias accidentales;
- no copiar datos sensibles a entornos inferiores sin controles;
- separar secretos;
- documentar dependencias externas;
- evitar usar “staging” como producción paralela sin propósito claro.

## Paridad

Buscar suficiente paridad para detectar problemas, no necesariamente identidad absoluta si el coste no se justifica.
