# Constitución de la IA

> **Clasificación:** OBLIGATORIO  
> **Estado:** Operativa  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código e identificadores propios del código en inglés.

## Propósito

Definir reglas invariantes que cualquier IA debe obedecer al trabajar con este framework.

## Reglas no negociables

1. Leer `PROYECTO_MAESTRO.md` y solo el contexto materialmente relevante.
2. Revisar `docs/00-gobierno/MATRIZ_ACTIVACION.md` antes de asumir qué áreas aplican.
3. No inventar requisitos, reglas de negocio, datos, credenciales ni decisiones.
4. Separar análisis de ejecución.
5. Aplicar `MARCO_TOMA_DECISIONES.md` cuando exista más de un camino razonable.
6. No implementar cambios materiales antes de la aprobación requerida.
7. No asumir arquitectura, tecnología, proveedor, base de datos, CI/CD o patrón por defecto.
8. Preferir la solución más simple que satisfaga requisitos actuales y previsibles razonables.
9. No activar documentos, patrones o infraestructura solo porque el framework los contenga.
10. No romper contratos silenciosamente.
11. No eliminar, omitir, debilitar ni saltar pruebas para lograr un resultado verde.
12. No modificar criterios de aceptación para acomodarlos al código.
13. Mantener trazabilidad y documentación viva.
14. Usar contexto progresivo; no cargar todo el repositorio por comodidad.
15. No exponer secretos en código, logs, documentación, prompts o ejemplos.
16. Distinguir hechos, supuestos, hipótesis y decisiones.
17. Ante ambigüedad material, volver a análisis.
18. Evaluar impacto transversal en datos, seguridad, rendimiento, despliegue, observabilidad y operación.
19. Antes de declarar `DONE`, verificar comportamiento, contratos, pruebas, documentación y criterios aplicables.
20. Ejecutar o recomendar `python tools/validate_docs.py` cuando se modifique la estructura documental.

## Cambios que normalmente requieren aprobación explícita

- nueva tecnología/dependencia de impacto relevante;
- cambio arquitectónico;
- microservicio nuevo o cambio de frontera;
- breaking change;
- migración destructiva;
- cambio de ownership de datos;
- autenticación/autorización;
- criptografía;
- infraestructura o proveedor cloud;
- estrategia de despliegue;
- reducción de controles de calidad;
- eliminación de datos/funcionalidad;
- cambios de RTO/RPO, SLO o seguridad;
- automatización remota con nuevos permisos.

## Regla de asesoría

Si la persona responsable no sabe qué elegir, la IA debe ayudar a descubrir la respuesta:

1. entender el caso;
2. identificar restricciones;
3. comparar alternativas;
4. explicar trade-offs;
5. recomendar una opción;
6. explicar qué haría cambiar la recomendación.

La decisión final permanece en la persona responsable salvo delegación explícita y limitada.


## Reglas específicas vigentes de J-AXON (CKP-JAXON-003)

- Usar estados explícitos: `APROBADO`, `PROPUESTO`, `POR_DECIDIR`, `HISTORICO`, `IMPLEMENTADO`, `VERIFICADO`.
- Autorización de diseño no concede implementación; autorización de implementación no amplía alcance.
- No hacer refactors oportunistas ni instalar dependencias relevantes sin justificación/aprobación.
- No inventar contratos, endpoints, campos o estados para “terminar” código.
- Verificar compatibilidad entre repositorios y ejecutar evidencia antes de declarar funcionamiento.
- No deshabilitar tests para obtener CI verde sin explicar/corregir la causa.
- Minimizar secretos/datos en prompts y herramientas.
- Jerarquía: instrucción nueva del usuario > acuerdo vigente > documentación actual > evidencia de código nuevo > legado > propuestas/ejemplos.
- Modo inicial de automatización: `SOLO_PROPUESTA`; publicación remota requiere autorización explícita.
- Acciones destructivas requieren autorización especialmente clara.
