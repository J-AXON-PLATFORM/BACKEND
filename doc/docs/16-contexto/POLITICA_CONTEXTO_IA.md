# Política de contexto de IA

> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla dinámica

## Propósito

Reducir tokens y ruido sin sacrificar seguridad ni precisión.

## Regla principal

Leer primero índices y resúmenes; ampliar contexto solo cuando una decisión o implementación lo requiera.

## Capas sugeridas

1. Constitución de la IA.
2. Proyecto Maestro.
3. Contexto del módulo.
4. Funcionalidad / Historia de Usuario / flujo.
5. Contratos afectados.
6. Arquitectura, datos o seguridad específicos.
7. Código afectado.

No todas las capas son necesarias para cada tarea.

## Obligaciones

- no cargar módulos no relacionados;
- no releer archivos sin cambios salvo necesidad;
- seguir referencias solo cuando puedan cambiar la decisión;
- usar búsqueda por símbolo/sección antes de leer repositorios completos;
- resumir contexto grande y estable;
- no duplicar documentación;
- mantener `TRABAJO_ACTUAL.md` corto;
- reutilizar contexto ya verificado durante la misma tarea;
- no sacrificar una comprobación necesaria solo por ahorrar tokens.
## Instancia J-AXON -- prioridad de continuidad

En J-AXON la lectura progresiva no autoriza a omitir fuentes canónicas. Para un nuevo chat o un lote nuevo, leer primero `REGISTRO_ACUERDOS_JAXON.md`, después `TRABAJO_ACTUAL.md`, `PROYECTO_MAESTRO.md` y `PENDIENTES_JAXON.md`; luego ampliar a los documentos específicos activados. Un checkpoint conversacional es ayuda externa y **nunca sustituye** esta revisión. Antes de recomendar una decisión, comprobar explícitamente que no contradiga un acuerdo anterior.

