# Pruebas de rendimiento

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Verificar NFR de rendimiento y capacidad con cargas reproducibles y representativas.

## Herramientas aprobadas

- k6 para HTTP/REST;
- simulador Python específico para Agent/SFTP;
- observabilidad del Lote 45 para correlacionar p95/p99 con CPU/RAM/GC/pools/DB/I/O/SFTP.

## Entorno de referencia

Benchmark inicial: 8 vCPU, 32 GiB RAM, SSD/NVMe, 1 Gbps. Registrar siempre hardware real usado; no tratar este perfil como mínimo Productivo.

## Datos

Dataset sintético/reproducible alineado con escenario: assets/Agents, historial de snapshots, tickets, usuarios, auditoría y miles de documentos/chunks cuando aplique. No usar producción sin sanitización explícita.

## Escenarios mínimos

### Baseline

Costo de operaciones críticas con carga mínima.

### Load

- 350 Agents;
- 30 usuarios;
- 60 min.

### Capacity

- 1000 Agents;
- 30 usuarios;
- 60 min.

### Headroom

- 1000 Agents;
- 60 usuarios.

### Spike

Reconexiones y aumentos breves de tráfico, preservando jitter realista.

### Soak

8 h con carga representativa para detectar leaks/degradación, cubriendo ciclo de snapshot de 6 h.

## Criterios iniciales

- API normal p95 <=500 ms / p99 <=1 s;
- login p95 <=1 s / p99 <=2 s;
- operaciones pesadas p95 <=2 s / p99 <=4 s;
- SSE post-commit p95 <=2 s / p99 <=5 s;
- retrieval vectorial p95 <=500 ms;
- 5xx atribuibles a J-AXON <1 % en carga normal;
- CPU sostenida <70 %, RAM <80 %, sin saturación sostenida de pools/I/O.

Los stress tests deliberadamente pueden superar estos límites para descubrir saturación.

## Realismo Agent

El simulador reproduce 60 s ±15 s polling, startup jitter, snapshots cada 6 h/ventana 60 min y SFTP/contratos reales; no lanzar 1000 conexiones simultáneas salvo escenario de spike explícito.

## Reproducibilidad

Guardar Git SHA/versión, hardware, configuración, dataset, script, fecha y resultados. Capacidad de 1000 solo se declara verificada tras ejecución aprobada.
