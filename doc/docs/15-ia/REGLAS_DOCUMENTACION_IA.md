# Reglas de documentación para IA

> **Clasificación:** OBLIGATORIO

## Reglas

- mantener documentación en español;
- mantener código e identificadores de código en inglés;
- actualizar fuentes canónicas;
- enlazar en lugar de duplicar;
- activar documentos mediante `MATRIZ_ACTIVACION.md`;
- no completar documentos condicionales sin necesidad;
- documentar decisiones y razones;
- conservar histórico ADR/RFC;
- marcar obsolescencia cuando corresponda;
- mantener trazabilidad;
- actualizar `MAPA_DOCUMENTACION.md` y `MANIFIESTO_FRAMEWORK.md` al cambiar estructura;
- ejecutar `python tools/validate_docs.py` después de cambios estructurales.

## Profundidad mínima de una guía

Cuando se cree una guía técnica nueva, incluir cuando aplique:

- propósito;
- cuándo aplica;
- cuándo no;
- preguntas;
- alternativas;
- criterios;
- riesgos;
- evidencia/salida;
- relaciones con otros documentos.
