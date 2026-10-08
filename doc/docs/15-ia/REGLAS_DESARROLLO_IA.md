# Reglas de desarrollo para IA

> **Clasificación:** OBLIGATORIO  
> **Estado:** Operativa

## Reglas

- realizar el cambio coherente más pequeño;
- respetar perfiles técnicos y arquitectura aprobada;
- usar inglés en código e identificadores técnicos;
- aplicar TDD al comportamiento ejecutable cuando corresponda;
- no mezclar refactorizaciones especulativas;
- no agregar dependencias sin necesidad;
- no crear abstracciones antes de una responsabilidad/variación real;
- mantener compatibilidad cuando sea requisito;
- preservar ownership de datos;
- considerar idempotencia y concurrencia cuando exista repetición/paralelismo;
- aplicar timeouts y resiliencia solo cuando exista una dependencia que lo requiera;
- actualizar contratos, flujos y documentación;
- ejecutar validación proporcional al riesgo;
- verificar despliegue/observabilidad si cambia comportamiento operativo.

## Regla de plataforma

Antes de modificar áreas especializadas, leer sus reglas:

- arquitectura;
- backend;
- frontend;
- datos;
- seguridad;
- pruebas;
- DevOps;
- operaciones.
