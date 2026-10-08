# Perfiles técnicos

> **Clasificación:** OBLIGATORIO como catálogo; cada perfil es CONDICIONAL.

## Propósito

Separar la **forma del sistema** del **nivel de formalidad documental**.

Un perfil técnico no es una arquitectura completa. Es una señal que activa preguntas y documentos relevantes.

## Composición

Los perfiles pueden combinarse.

Ejemplos:

```text
BACKEND_API + MONOLITO_MODULAR + CLOUD
FRONTEND_WEB + BACKEND_API + MICROSERVICIOS + ORIENTADO_EVENTOS
CLI + LOCAL_ONLY
DATOS_ETL + BATCH_WORKERS + CLOUD
MOVIL + BACKEND_API + SERVERLESS
```

## Perfiles de canal

### BACKEND_API

Evaluar:

- contratos;
- validación;
- errores;
- seguridad;
- persistencia;
- concurrencia;
- integración;
- observabilidad.

### FRONTEND_WEB

Evaluar:

- renderizado;
- routing;
- estado;
- accesibilidad;
- rendimiento;
- compatibilidad;
- SEO;
- observabilidad cliente.

### MOVIL

Evaluar:

- plataforma;
- permisos;
- conectividad;
- offline/sincronización;
- almacenamiento seguro;
- deep links;
- push;
- distribución.

### ESCRITORIO

Evaluar:

- sistemas operativos;
- actualización;
- empaquetado;
- permisos locales;
- filesystem;
- integración nativa.

### CLI

Evaluar:

- contrato de comandos;
- códigos de salida;
- stdin/stdout/stderr;
- scripting;
- compatibilidad;
- configuración;
- distribución.

### BATCH_WORKERS

Evaluar:

- scheduling;
- idempotencia;
- reintentos;
- checkpoints;
- concurrencia;
- observabilidad;
- reanudación.

### DATOS_ETL

Evaluar:

- fuentes;
- transformaciones;
- calidad;
- linaje;
- esquema;
- particionamiento;
- reejecución;
- privacidad.

## Perfiles de arquitectura

### MONOLITO

Una unidad desplegable. No implica código desorganizado.

Evaluar:

- modularidad;
- dependencias;
- transacciones;
- escalado de la unidad completa.

### MONOLITO_MODULAR

Una unidad desplegable con fronteras internas explícitas.

Evaluar:

- ownership;
- contratos internos;
- dependencia direccional;
- aislamiento lógico;
- pruebas por módulo.

### MICROSERVICIOS

Servicios desplegables independientemente.

Activar obligatoriamente:

- arquitectura distribuida;
- contratos;
- resiliencia;
- observabilidad distribuida;
- estrategia de despliegue;
- ownership de datos.

No seleccionar por tamaño del código; seleccionar cuando la independencia operativa/organizativa lo justifique.

### ORIENTADO_EVENTOS

Evaluar:

- contrato del evento;
- productor/consumidor;
- idempotencia;
- ordering;
- entrega al menos/como máximo una vez según plataforma;
- DLQ;
- replay;
- evolución de esquema.

### SERVERLESS

Evaluar:

- modelo de ejecución;
- límites/cuotas;
- cold start;
- concurrencia;
- coste por uso;
- observabilidad;
- lock-in;
- eventos;
- persistencia.

## Perfiles de infraestructura

### ON_PREMISE

Evaluar capacidad, hardware, red, mantenimiento, backups y responsabilidad total del equipo.

### CLOUD

Evaluar servicios administrados, IAM, regiones, costes, cuotas, resiliencia y responsabilidad compartida.

### HIBRIDO

Además evaluar conectividad, latencia, identidad, seguridad y consistencia entre entornos.

### MULTICLOUD

No usar como objetivo abstracto. Justificar por regulación, resiliencia, adquisiciones u otra necesidad concreta.

## Perfiles operativos

La criticidad activa diferentes niveles de observabilidad, incidentes, SLO, DR y SRE.

Consultar `docs/00-gobierno/MATRIZ_ACTIVACION.md`.
