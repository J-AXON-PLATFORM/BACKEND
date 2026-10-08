# Reglas de base de datos para IA

> **Clasificación:** CONDICIONAL

## Reglas

- no asumir SQL/NoSQL;
- analizar acceso, consistencia, transacciones, volumen y operación;
- definir ownership;
- no realizar cambios destructivos sin análisis/aprobación;
- evaluar compatibilidad de despliegue;
- diseñar índices a partir de queries;
- no aumentar conexiones sin medir capacidad;
- probar migraciones con datos representativos;
- probar restore cuando RPO/RTO lo requiera;
- no duplicar datos entre servicios sin estrategia de sincronización;
- considerar calidad/linaje si hay pipelines.
