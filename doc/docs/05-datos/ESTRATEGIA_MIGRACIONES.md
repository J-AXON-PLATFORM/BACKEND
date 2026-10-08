# Estrategia de migraciones

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-003 | 2026-09-29.


<!-- JAXON-CKP003:START -->
## Refinamiento aprobado CKP-JAXON-003

- Flyway usa inicialmente la versión administrada por Spring Boot 4.1.x.
- Un único `flyway_schema_history` para el backend completo; scripts organizados por módulo/propietario + bootstrap técnico común.
- Naming: `VYYYYMMDDHHMMSS__module_description.sql`.
- Bootstrap habilita capacidades técnicas necesarias y crea los ocho schemas; no crea tablas ficticias.
- Migración aplicada no se edita: corrección mediante nueva migración/roll-forward.
- Local/Development/Test/QA pueden migrar al arrancar; Production ejecuta migración como paso explícito de deployment/upgrade.
- Separar conceptualmente credencial de migración con DDL de credencial runtime de mínimo privilegio.
<!-- JAXON-CKP003:END -->

## Acuerdo J-AXON

- Flyway desde `backend` con secuencia coordinada para los schemas del monolito modular.
- Base nueva para reconstrucción; no autoriza borrar ni importar legado.
- Pruebas de integración aplican Flyway sobre PostgreSQL real/Testcontainers.
- Probar creación desde DB vacía y upgrades relevantes.
- Migraciones destructivas/irreversibles en producción requieren plan explícito de preservación/transformación y posibilidad de recuperación.
- Backup antes de upgrades con riesgo de datos.
- Estrategia rollback/roll-forward se define por release; no prometer rollback trivial de cambios destructivos.

Scripts concretos se crearán durante implementación autorizada; este checkpoint no ejecuta migraciones.

---
> **Clasificación:** CONDICIONAL

## Propósito

Evolucionar esquemas/datos de forma reproducible y compatible.

## Definir

- herramienta;
- orden;
- ownership;
- inmutabilidad;
- entornos;
- validación;
- backup;
- rollback/forward-only.

## Cambios compatibles

En despliegues sin downtime considerar:

1. expandir esquema;
2. desplegar código compatible;
3. migrar/backfill;
4. cambiar lecturas/escrituras;
5. retirar columnas/contratos obsoletos.

## Datos grandes

Para backfills:

- batches;
- locks;
- duración;
- checkpoints;
- retry;
- observabilidad;
- impacto de carga.

## Pruebas

Validar migración desde versiones soportadas con datos representativos.

## Destructivas

Requieren aprobación, backup/restore verificado y plan de compatibilidad.
