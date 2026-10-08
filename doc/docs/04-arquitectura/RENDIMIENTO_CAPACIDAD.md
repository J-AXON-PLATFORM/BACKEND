# Rendimiento y capacidad

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Diseñar y verificar capacidad con workloads representativos; no afirmar soporte por intuición.

## Modelo de carga aprobado

- carga inicial: ~350 Agents ACTIVE + ~30 usuarios web concurrentes;
- objetivo de crecimiento: ~1000 Agents ACTIVE + ~30 usuarios sin rediseño arquitectónico;
- headroom de prueba: 1000 Agents + 60 usuarios;
- polling Agent: 60 s ±15 s jitter; startup jitter 0--60 s;
- snapshot periódico: cada 6 h, distribuido en ventana estable de 60 min;
- RAG: miles de documentos con crecimiento.

El objetivo de 1000 Agents permite escalado vertical; no obliga a que el mismo hardware de referencia sirva para todos los tamaños.

## Targets iniciales

| Flujo | p95 | p99 | Notas |
|---|---:|---:|---|
| API interactiva normal | <=500 ms | <=1 s | lado servidor/API bajo carga representativa |
| Login | <=1 s | <=2 s | no debilitar Argon2id para cumplir |
| Operación síncrona pesada | <=2 s | <=4 s | reevaluar asincronía si excede sistemáticamente |
| Emisión SSE tras commit | <=2 s | <=5 s | SSE avisa; REST conserva verdad |
| Retrieval vectorial interno | <=500 ms | `POR_DECIDIR` | sin incluir generación LLM |

- error rate 5xx atribuible a J-AXON bajo carga normal: **<1 %**;
- headroom orientativo sostenido: CPU <70 %, RAM <80 %, sin saturación sostenida de pools/I/O.

## Perfil de benchmark inicial

- 8 vCPU;
- 32 GiB RAM;
- SSD/NVMe;
- red 1 Gbps de referencia.

Este perfil sirve para comparar mediciones; **no es hardware mínimo definitivo de Production**.

## Herramientas

- **k6** para carga HTTP/REST;
- simulador Python específico para Agent/SFTP con identidades, jitter, contratos y comportamiento representativo;
- PostgreSQL real, SFTPGo real y contratos reales en pruebas representativas.

## Escenarios mínimos

- baseline;
- load: 350 Agents + 30 usuarios / 60 min;
- capacity: 1000 Agents + 30 usuarios / 60 min;
- headroom: 1000 Agents + 60 usuarios;
- spike: reconexiones/rampas representativas sin sincronizar artificialmente 1000 Agents;
- soak: 8 h, cubriendo al menos un ciclo de snapshots.

## Diagnóstico y optimización

Orden preferido:

1. identificar cuello;
2. eliminar trabajo innecesario;
3. queries/índices/N+1;
4. concurrencia/pools/locks;
5. I/O;
6. configuración runtime;
7. remeasure;
8. escalado vertical;
9. solo después evaluar cambios arquitectónicos.

No introducir Redis, réplicas, particionamiento, broker o microservicios preventivamente.

## Capacity planning

No declarar que J-AXON soporta 1000 equipos hasta una prueba representativa aprobada que registre versión, hardware, dataset, configuración y scripts.
