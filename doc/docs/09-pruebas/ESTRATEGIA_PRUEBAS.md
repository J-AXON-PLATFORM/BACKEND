# Estrategia de pruebas

> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla dinámica

## Propósito

Definir una estrategia de pruebas basada en riesgos y arquitectura, no una receta fija.

## Capas disponibles

- **Análisis estático:** compilación, tipos, lint, validación de schemas y configuración.
- **Unitarias:** reglas y unidades aislables.
- **Componente:** módulo/componente a través de su interfaz pública.
- **Integración:** colaboración con DB, filesystem, broker, framework o servicios controlados.
- **Contrato:** compatibilidad entre consumidor y proveedor.
- **Sistema:** conjunto desplegado/integrado desde una frontera externa.
- **E2E:** recorridos críticos desde la perspectiva del actor/cliente.
- **Rendimiento:** cuando existan NFR de capacidad o latencia.
- **Seguridad:** según amenazas y superficie.
- **Accesibilidad:** cuando exista interfaz de usuario.
- **Resiliencia/recuperación:** cuando disponibilidad y continuidad lo requieran.

No todos los proyectos requieren todas las capas.

## Principios

1. Aplicar TDD al comportamiento ejecutable cuando sea viable.
2. Validar infraestructura/configuración con mecanismos apropiados, no tests unitarios artificiales.
3. Probar contratos en sus fronteras, no solo mediante E2E.
4. Reservar E2E para journeys críticos y riesgos de integración completa.
5. No perseguir porcentajes de cobertura sin significado.
6. Evitar duplicar exactamente el mismo caso en cada capa sin valor adicional.
7. La estrategia debe cubrir el sistema completo según `VALIDACION_SISTEMA_COMPLETO.md`.

## Selección por riesgo

Por cada requisito/flujo significativo, indicar qué nivel ofrece la evidencia más barata y confiable, y dónde se necesita evidencia adicional por integración.
