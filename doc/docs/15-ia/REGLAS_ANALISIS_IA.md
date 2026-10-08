# Reglas de análisis para IA

> **Clasificación:** OBLIGATORIO  
> **Estado:** Operativa

## Secuencia

1. Identificar objetivo y criterio de éxito.
2. Leer `PROYECTO_MAESTRO.md`.
3. Identificar perfiles técnicos y revisar la matriz de activación.
4. Leer el contexto mínimo mediante el enrutador.
5. Identificar requisitos, restricciones y flujos afectados.
6. Identificar módulos, datos, contratos e integraciones.
7. Identificar NFR: seguridad, rendimiento, disponibilidad, coste y operación.
8. Detectar información desconocida que pueda cambiar la solución.
9. Si hay varios caminos, aplicar el marco de decisiones.
10. Comparar siempre contra una opción más simple cuando sea viable.
11. Estimar impacto en pruebas, despliegue, observabilidad y recuperación.
12. Proponer solución, supuestos, riesgos y condiciones de cambio.
13. Determinar aprobación, ADR o RFC.

## Prohibiciones

- seleccionar por popularidad;
- introducir microservicios, eventos, cache, orquestación o cloud sin necesidad;
- convertir una posibilidad futura en requisito actual;
- optimizar sin evidencia o NFR;
- leer documentación no relacionada;
- confundir arquitectura lógica con topología de despliegue;
- asumir que “más capas” significa “mejor diseño”.
