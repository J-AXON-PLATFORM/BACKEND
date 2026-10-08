# Validación documental

> **Clasificación:** OBLIGATORIO  
> **Estado:** Operativa

## Propósito

Detectar incoherencias mecánicas antes de que una persona o una IA tomen decisiones a partir de documentación incompleta o contradictoria.

La validación automática no sustituye una revisión arquitectónica. Solo comprueba propiedades verificables de forma estática.

## Comando

```bash
python tools/validate_docs.py
```

## Comprobaciones

El validador revisa:

- `VERSION` y versión declarada en `README.md`;
- presencia de documentos esenciales;
- existencia de `Clasificación` en documentos bajo `docs/`;
- enlaces Markdown locales rotos;
- marcadores heredados `YES`, `NO`, `UNDECIDED` en documentos en español;
- términos conocidos de placeholders que deberían haberse traducido;
- decisiones `POR_DECIDIR` del Proyecto Maestro como advertencias;
- presencia de archivos activados por señales explícitas del Proyecto Maestro cuando esas señales hayan sido completadas.

## Severidades

- `ERROR`: incoherencia estructural o enlace roto que debe corregirse.
- `ADVERTENCIA`: decisión pendiente o posible inconsistencia que requiere revisión.
- `INFO`: hallazgo no bloqueante.

## Extensibilidad

El script está escrito para ser simple. Cada proyecto puede agregar reglas específicas, por ejemplo:

- exigir ADR para ciertas decisiones;
- exigir threat model cuando existan datos clasificados;
- exigir pruebas de contrato para APIs públicas;
- exigir RTO/RPO en servicios críticos;
- impedir cerrar una versión con `POR_DECIDIR` críticos.

## Regla

No modificar documentación solo para “hacer pasar el linter”. Resolver la incoherencia real o documentar por qué la regla no aplica.
