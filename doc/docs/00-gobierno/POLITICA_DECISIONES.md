# Política de decisiones

> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla dinámica

## Propósito

Determinar el nivel de análisis, aprobación y registro requerido por una decisión.

La forma de comparar alternativas está definida en `MARCO_TOMA_DECISIONES.md`.

## Niveles

### Local

Decisión interna, reversible y de impacto limitado.

Ejemplos: nombres internos, pequeña refactorización, detalle de implementación ya cubierto por convenciones.

- Puede resolverse dentro del plan aprobado.
- No requiere ADR salvo que el proyecto lo exija.
- Debe respetar contratos, pruebas y convenciones existentes.

### Significativa

Afecta arquitectura, datos, seguridad, contratos, dependencias importantes, rendimiento, operación, coste o varios módulos.

- Requiere análisis de alternativas.
- Requiere aprobación humana explícita, salvo delegación documentada.
- Requiere ADR.

### Transformacional

Cambia de forma amplia el sistema o su dirección: plataforma, arquitectura global, migración grande, partición de servicios, reemplazo tecnológico relevante, estrategia de datos o infraestructura transversal.

- Requiere RFC antes de decidir.
- Debe incluir estrategia de migración, riesgos y rollback/contingencia.
- Tras aprobación, registrar ADR con la decisión final.

## Reglas

1. La IA no debe elevar artificialmente todas las decisiones a ADR/RFC.
2. Tampoco debe ocultar una decisión significativa tratándola como detalle técnico.
3. Toda recomendación debe indicar sus supuestos y condiciones de cambio.
4. Las decisiones reemplazadas conservan su histórico (`Superseded`/`Deprecated`) en lugar de borrarse.
5. El responsable humano mantiene la decisión final salvo autorización delegada explícita.
