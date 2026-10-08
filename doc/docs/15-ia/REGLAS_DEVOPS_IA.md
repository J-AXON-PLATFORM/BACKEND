# Reglas DevOps para IA

> **Clasificación:** CONDICIONAL

## Reglas

Antes de modificar pipelines o infraestructura:

- leer estrategia DevOps, entornos y despliegue;
- no asumir proveedor;
- no introducir contenedores/orquestación/IaC sin justificar;
- preservar secretos;
- evaluar reversión;
- evaluar migraciones;
- mantener artefactos/versiones trazables;
- aplicar mínimo privilegio;
- no saltar quality gates para obtener un pipeline verde;
- verificar health y observabilidad después de desplegar;
- exigir aprobación para cambios destructivos o de producción según política.
