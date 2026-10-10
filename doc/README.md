# doc — Versión 1.0.0

> **Instancia J-AXON | CKP-JAXON-005 | 2026-09-29**
> Cumplimentación parcial: solo decisiones aprobadas. Las guías genéricas se conservan; sus ejemplos no son requisitos aprobados del producto.

## J-AXON: empezar aquí

Esta es una **instancia J-AXON de la plantilla doc 1.0.0**. El refinamiento/diseño v1 está cerrado hasta el **Lote 54** y el proyecto está `DESIGN_READY`. La implementación comenzó de forma incremental y AC-JAXON-059 añade contratos ejecutables mínimos, sin funcionalidad de negocio ni autorización general. El framework conserva su versión 1.0.0 porque estos cambios son de la instancia J-AXON, no una nueva versión genérica.

**Orden obligatorio de continuidad dentro de `doc/`:**

1. [Registro de acuerdos](docs/00-gobierno/REGISTRO_ACUERDOS_JAXON.md) -- fuente principal de decisiones aprobadas.
2. [Trabajo actual](docs/16-contexto/TRABAJO_ACTUAL.md) -- punto exacto de continuación.
3. [Proyecto Maestro](PROYECTO_MAESTRO.md) -- consolidación vigente.
4. [Pendientes](docs/16-contexto/PENDIENTES_JAXON.md) -- solo decisiones aún abiertas.
5. [Enrutador documental](docs/16-contexto/ENRUTADOR_DOCUMENTACION.md) y documentos específicos del lote.

El archivo de checkpoint conversacional `CHECKPOINT_JAXON_CKP_005.md` se entrega **junto al ZIP pero fuera de `doc/`**. No pertenece al framework reutilizable; sirve únicamente para transportar continuidad entre chats. Los archivos de `docs/16-contexto/` sí forman parte del mecanismo del framework y se conservan.

**Ubicación acordada:** `backend/doc/` para la documentación general de J-AXON. **Canales:** web HTTPS/REST/OpenAPI/Swagger UI; Agents SFTP/JSON/JSON Schema. Diseño aprobado != implementación. No reabrir un acuerdo sin identificar expresamente qué acuerdo se sustituye.

---


`doc` es un framework documental dinámico y reutilizable para dirigir el desarrollo profesional de software asistido por IA. Está diseñado para adaptarse al proyecto real en lugar de imponer una arquitectura, lenguaje, framework, proveedor cloud, modelo de despliegue o herramienta.

Puede utilizarse en proyectos nuevos, existentes, heredados o en migración, incluyendo:

- backend y APIs;
- frontend web;
- aplicaciones móviles y de escritorio;
- CLI;
- procesos batch y workers;
- monolitos y monolitos modulares;
- microservicios y sistemas distribuidos;
- arquitecturas orientadas a eventos;
- serverless;
- bases de datos y plataformas de datos;
- DevOps, infraestructura como código y nube;
- seguridad;
- testing;
- observabilidad y operación/SRE.

## Idioma

- **Documentación, explicaciones, decisiones y plantillas:** español.
- **Código fuente e identificadores técnicos propios del código:** inglés.
- Los nombres exigidos por herramientas externas, como `.github`, `README.md` o archivos de configuración, conservan el formato requerido.

## Principio principal

La IA debe seguir este ciclo:

`ANÁLISIS → ALTERNATIVAS → RECOMENDACIÓN → APROBACIÓN → IMPLEMENTACIÓN → PRUEBAS → DOCUMENTACIÓN → VERIFICACIÓN`

La IA no debe seleccionar tecnologías, patrones o arquitecturas por moda, popularidad o preferencia personal. Debe justificar la decisión con requisitos, restricciones, riesgos, coste, seguridad, operación, experiencia del equipo, mantenibilidad y reversibilidad.

## Dos dimensiones de adaptación

La plantilla se adapta mediante dos ejes independientes:

### 1. Perfil documental

Determina **cuánta formalidad y evidencia** necesita el proyecto:

- `LIGERO`
- `ESTANDAR`
- `EMPRESARIAL`

### 2. Perfiles técnicos

Determinan **qué áreas se activan**:

- backend/API;
- frontend web;
- móvil;
- escritorio;
- CLI;
- batch/workers;
- monolito;
- monolito modular;
- microservicios;
- orientado a eventos;
- serverless;
- datos/ETL;
- cloud;
- on-premise;
- híbrido.

Un proyecto puede combinar varios perfiles técnicos. Por ejemplo:

`FRONTEND_WEB + BACKEND_API + MONOLITO_MODULAR + CLOUD`

Las reglas de activación están en `docs/00-gobierno/MATRIZ_ACTIVACION.md`.

## Clasificación de artefactos

Cada artefacto se clasifica como:

- `OBLIGATORIO`: gobierno mínimo necesario;
- `CONDICIONAL`: se activa por arquitectura, riesgo o caso de uso;
- `OPCIONAL`: aporta valor en situaciones concretas.

No se debe completar documentación que no cambie decisiones, implementación, validación u operación.

## Inicio rápido

1. Completar `PROYECTO_MAESTRO.md`.
2. Seleccionar perfil documental y perfiles técnicos.
3. Ejecutar mentalmente o con una IA `docs/00-gobierno/MATRIZ_ACTIVACION.md`.
4. Resolver decisiones abiertas mediante `docs/00-gobierno/MARCO_TOMA_DECISIONES.md`.
5. Definir requisitos, módulos, flujos, arquitectura y contratos.
6. Activar solo los documentos necesarios de datos, seguridad, frontend, backend, plataformas, cloud y operación.
7. Diseñar la estrategia de pruebas según riesgo.
8. Definir entrega, observabilidad y operación cuando exista un entorno real.
9. Mantener trazabilidad entre requisito → flujo → decisión → implementación → prueba.
10. Ejecutar `python tools/validate_docs.py` para verificar coherencia documental básica.

## Capacidades principales

### Arquitectura

Incluye guías para:

- monolitos;
- monolitos modulares;
- microservicios;
- sistemas distribuidos;
- comunicación síncrona y asíncrona;
- contratos;
- eventos;
- resiliencia;
- capacidad y rendimiento;
- cloud;
- C4, HLD, SDD, LLD, ADR y RFC.

### Datos

Incluye:

- estrategia de persistencia;
- ownership;
- migraciones;
- backups;
- retención;
- clasificación;
- calidad;
- linaje;
- schema evolution;
- replicación;
- particionamiento;
- CDC;
- ETL/ELT;
- multi-tenancy;
- analítica cuando aplique.

### Ingeniería de producto

Incluye:

- frontend;
- backend;
- móvil;
- escritorio;
- CLI;
- batch/workers;
- serverless;
- contratos e integraciones.

### DevOps y nube

Incluye:

- CI;
- CD;
- contenedores;
- infraestructura como código;
- estrategias de despliegue;
- entornos;
- cloud/on-premise/híbrido;
- reversión;
- artefactos y secretos.

### Calidad

Incluye:

- TDD;
- pruebas unitarias;
- componente;
- integración;
- contrato;
- sistema;
- E2E;
- seguridad;
- rendimiento;
- resiliencia;
- accesibilidad;
- migraciones;
- regresión.

### Operación

Incluye:

- observabilidad;
- SLI/SLO/SLA;
- alertas;
- runbooks;
- incidentes;
- postmortems;
- recuperación ante desastres;
- RTO/RPO;
- prácticas SRE cuando el riesgo lo justifique.

## Automatización documental

`tools/validate_docs.py` realiza comprobaciones estáticas básicas:

- versión;
- existencia de archivos esenciales;
- clasificación de documentos;
- enlaces Markdown locales rotos;
- documentos críticos activados explícitamente cuando el Proyecto Maestro ya contiene decisiones;
- marcadores de idioma heredados o inconsistencias conocidas;
- estados `POR_DECIDIR` pendientes.

No sustituye revisión humana ni validación arquitectónica.

## Documentos clave

- `PROYECTO_MAESTRO.md`: configuración y descubrimiento.
- `MAPA_DOCUMENTACION.md`: mapa de áreas y rutas.
- `docs/00-gobierno/MATRIZ_ACTIVACION.md`: reglas para activar documentación.
- `docs/00-gobierno/MARCO_TOMA_DECISIONES.md`: cómo decidir.
- `docs/04-arquitectura/PERFILES_TECNICOS.md`: combinaciones arquitectónicas.
- `docs/04-arquitectura/CONTRATOS_SISTEMA.md`: fronteras del sistema.
- `docs/09-pruebas/VALIDACION_SISTEMA_COMPLETO.md`: cobertura integral.
- `docs/16-contexto/ENRUTADOR_DOCUMENTACION.md`: lectura mínima para IA.
- `docs/00-gobierno/VALIDACION_DOCUMENTAL.md`: coherencia del framework.

## Regla contra la sobrearquitectura

Que el framework tenga un documento no significa que el proyecto necesite esa tecnología o patrón.

Por ejemplo:

- tener `ARQUITECTURA_DISTRIBUIDA.md` no implica usar microservicios;
- tener `INFRAESTRUCTURA_COMO_CODIGO.md` no obliga a usar Terraform;
- tener `RESILIENCIA.md` no obliga a introducir circuit breakers;
- tener `ARQUITECTURA_CLOUD.md` no obliga a usar cloud;
- tener documentos de SRE no obliga a crear un equipo SRE.

Primero se demuestra la necesidad. Después se selecciona la solución.

## Versión

**1.0.0**
