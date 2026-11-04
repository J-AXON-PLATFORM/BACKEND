# Modelo de amenazas


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Identificar activos, límites de confianza, amenazas y mitigaciones

- Assets
- Entry points
- Trust boundaries
- Threats
- Mitigations
- Residual risks

## OLA5-AI-002a

- Activos: originales privados, chunks, embeddings, artefactos ONNX/tokenizer y estados/outbox AI.
- Entradas: petición humana autenticada y original ya registrado; no existe endpoint de contenido, vector o retrieval.
- Mitigaciones: `SYSTEM_ADMIN` + CSRF + versión optimista; lectura no-follow con tamaño/SHA-256; modelo/tokenizer externos fijados por tamaño y hash streaming; límites de 1 MiB, 480 tokens, batch y concurrencia; inferencia fuera de transacción; publicación atómica; claims expirados terminales y resultados tardíos descartados; logs/métricas sin contenido ni IDs de alta cardinalidad.
- Riesgos residuales: capacidad/RSS del runtime local, protección operativa de los paths externos de modelo y tokenizer, y tuning HNSW/hardware Production. No se presentan como resueltos por este corte.
