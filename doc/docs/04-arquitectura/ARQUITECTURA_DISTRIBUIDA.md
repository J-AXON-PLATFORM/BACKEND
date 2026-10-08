# Arquitectura distribuida

> **Clasificación:** CONDICIONAL  
> **Activar si:** existen varias unidades desplegables, comunicación remota interna, mensajería o fallos parciales.


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- distribución real

- Backend es monolito modular; frontend y componentes Python son unidades separadas.
- Agent inicia SFTP hacia SFTPGo; no existe canal HTTP alternativo Agent<->backend para snapshots/tareas/respuestas.
- Un keypair SSH por Agent; buzón aislado por identidad. SFTPGo es transporte, Collector/backend fuente de estado.
- Delivery se trata como at-least-once con IDs estables, deduplicación, idempotencia y reconciliación; no exactly-once.
- Discovery executor es programa/identidad independiente y opera solo en equipos designados.
- Fallos parciales de SSE/IA/n8n no deben corromper el core; PostgreSQL sí es dependencia crítica para confirmar escrituras.
<!-- JAXON-CKP003:END -->

## Propósito

Diseñar explícitamente problemas que no existen en una llamada local: red, latencia, fallos parciales, duplicados, orden, consistencia y evolución independiente.

## Antes de distribuir

Responder:

- ¿qué necesidad concreta exige separación física?
- ¿un monolito modular resolvería el problema con menos coste?
- ¿los equipos necesitan despliegue independiente?
- ¿hay requisitos de escalado diferentes?
- ¿existen límites de dominio claros?
- ¿el equipo puede operar varias unidades?

## Límites

Por servicio/componente distribuido:

- responsabilidad;
- propietario;
- datos propios;
- API/eventos;
- dependencias;
- SLA/SLO esperado;
- estrategia de despliegue;
- compatibilidad.

## Comunicación síncrona

Definir:

- protocolo;
- timeout;
- retries;
- idempotencia;
- autenticación;
- versionado;
- rate limit;
- comportamiento ante caída;
- trazabilidad/correlación.

Evitar cadenas síncronas largas si degradan disponibilidad o latencia.

## Comunicación asíncrona

Definir:

- broker/medio;
- tópico/cola;
- contrato;
- productor;
- consumidores;
- semántica de entrega;
- ordering;
- particionamiento;
- idempotencia;
- deduplicación;
- retry;
- DLQ;
- replay;
- retención;
- evolución del esquema.

## Datos y consistencia

Por operación que cruza fronteras:

- dato fuente;
- propietario;
- consistencia requerida;
- consistencia eventual aceptable;
- compensación;
- reconciliación;
- auditoría.

No introducir transacciones distribuidas, sagas o event sourcing sin una necesidad demostrada.

## Service discovery y routing

Solo si la plataforma lo requiere, documentar:

- descubrimiento;
- balanceo;
- DNS;
- gateway;
- malla de servicios;
- políticas de red.

## Observabilidad

Obligatorio cuando existen llamadas remotas significativas:

- correlation/trace ID;
- logs estructurados;
- métricas por dependencia;
- trazas;
- latencia;
- errores;
- saturación;
- colas;
- DLQ.

## Pruebas

Considerar:

- contrato;
- integración;
- sistema;
- E2E críticos;
- resiliencia;
- latencia;
- compatibilidad de versiones;
- replay;
- fallos de red.

## Señales de sobrearquitectura

Reconsiderar distribución si:

- todos los servicios cambian juntos;
- comparten la misma base sin ownership claro;
- siempre se despliegan juntos;
- no existen límites de dominio claros;
- el coste operativo domina el beneficio;
- la comunicación remota reemplaza simples llamadas internas sin una razón.

## Salida

```text
Necesidad de distribución:
Alternativa monolítica evaluada:
Unidades desplegables:
Ownership:
Contratos:
Modelo de consistencia:
Resiliencia:
Observabilidad:
Despliegue:
Riesgos:
Decisiones ADR:
```
