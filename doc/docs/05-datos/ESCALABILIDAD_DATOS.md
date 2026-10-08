# Escalabilidad de datos

> **Clasificación:** CONDICIONAL  
> **Activar si:** el volumen, tasa de escritura/lectura, retención o crecimiento puede superar el diseño simple.

## Preguntas

- ¿cuánto dato existe hoy?
- ¿cuánto crecerá?
- ¿cuál es la tasa de lectura/escritura?
- ¿qué consultas dominan?
- ¿qué retención se necesita?
- ¿qué consistencia se requiere?
- ¿qué downtime de migración es aceptable?

## Técnicas a evaluar

Solo cuando el problema lo justifique:

- índices;
- archivado;
- particionamiento;
- sharding;
- réplicas de lectura;
- materialización;
- caché;
- almacenamiento especializado;
- compresión;
- tiering.

## Particionamiento

Definir:

- clave;
- distribución;
- hot partitions;
- mantenimiento;
- consultas cross-partition;
- reequilibrio.

## Replicación

Definir:

- objetivo;
- lag tolerable;
- lecturas;
- failover;
- consistencia;
- operación.

## CDC

Si aplica:

- fuente;
- consumidores;
- ordering;
- replay;
- esquema;
- retención;
- recuperación.

## Regla

Escalar el diseño cuando exista evidencia o una proyección razonable, no por posibilidad abstracta.
