# Mapa de documentación

> **Instancia J-AXON | CKP-JAXON-005 | 2026-09-29**
> Descubrimiento inicial cerrado documentalmente; las guías genéricas se conservan y sus ejemplos no sustituyen acuerdos del producto.

## Entrada de continuidad para J-AXON

[Acuerdos](docs/00-gobierno/REGISTRO_ACUERDOS_JAXON.md) -> [trabajo actual](docs/16-contexto/TRABAJO_ACTUAL.md) -> [Proyecto Maestro](PROYECTO_MAESTRO.md) -> [pendientes por sección](docs/16-contexto/PENDIENTES_JAXON.md) -> documentos específicos activados.

Los archivos genéricos se conservan completos. El refinamiento/diseño v1 está cerrado hasta el Lote 54 y el proyecto está `DESIGN_READY`; no existe un siguiente lote funcional obligatorio antes de implementación. Antes de cualquier trabajo se deben leer acuerdos y documentos específicos activados, no decidir desde memoria o solo desde un checkpoint. La existencia de archivos de cloud, microservicios, móvil u otros perfiles no los convierte en alcance aprobado. El checkpoint conversacional se distribuye fuera de `doc/`.

---


> **Clasificación:** OBLIGATORIO  
> **Versión:** 1.0.0

## Propósito

Indicar qué artefactos existen, para qué sirven, cuándo se activan y cuál es su relación con otras áreas.

La fuente principal de reglas de activación es `docs/00-gobierno/MATRIZ_ACTIVACION.md`.

## Regla de lectura

Una IA no debe cargar todo el framework.

Ruta normal:

1. `PROYECTO_MAESTRO.md`
2. `docs/00-gobierno/MATRIZ_ACTIVACION.md`
3. `docs/16-contexto/ENRUTADOR_DOCUMENTACION.md`
4. contexto del módulo/flujo
5. documentos especializados activados

## Gobierno

| Documento | Nivel | Uso |
|---|---|---|
| `CONSTITUCION_IA.md` | OBLIGATORIO | Principios invariantes |
| `FLUJO_TRABAJO_IA.md` | OBLIGATORIO | Ciclo de trabajo |
| `MARCO_TOMA_DECISIONES.md` | OBLIGATORIO | Comparar alternativas |
| `MATRIZ_ACTIVACION.md` | OBLIGATORIO | Activar documentación |
| `VALIDACION_DOCUMENTAL.md` | OBLIGATORIO | Verificar coherencia |
| `POLITICA_DECISIONES.md` | OBLIGATORIO | Nivel de aprobación |
| `POLITICA_DOCUMENTACION.md` | OBLIGATORIO | Documentación viva |
| `POLITICA_TRAZABILIDAD.md` | OBLIGATORIO | Enlaces entre evidencias |
| `DEFINICION_PREPARADO.md` | OBLIGATORIO | Entrada al trabajo |
| `DEFINICION_TERMINADO.md` | OBLIGATORIO | Cierre del trabajo |

## Producto y requisitos

Activar según producto, criticidad y perfil documental:

- visión;
- contexto;
- alcance;
- personas;
- interesados;
- riesgos;
- glosario;
- SRS;
- requisitos funcionales;
- requisitos no funcionales;
- casos de uso;
- criterios de aceptación;
- trazabilidad.

## Flujos

`docs/03-flujos/` modela comportamiento observable:

- negocio;
- aplicación;
- técnico;
- datos;
- estados;
- entre módulos;
- eventos;
- E2E.

Todo flujo crítico debe tener un identificador trazable.

## Arquitectura

| Documento | Activación |
|---|---|
| `RESUMEN_ARQUITECTURA.md` | Siempre |
| `PERFILES_TECNICOS.md` | Catálogo obligatorio |
| `GUIA_SELECCION_ARQUITECTURA.md` | Elegir monolito/modular/distribuido/eventos/serverless |
| `HLD.md` | Sistemas medianos/grandes |
| `SDD.md` | Diseño detallado |
| `lld/` | Diseño local cuando aporta valor |
| `CONTRATOS_SISTEMA.md` | Fronteras reales |
| `ARQUITECTURA_DISTRIBUIDA.md` | Varias unidades/límites remotos |
| `ARQUITECTURA_CLOUD.md` | Cloud/híbrido/multicloud |
| `RESILIENCIA.md` | Fallos parciales/dependencias |
| `RENDIMIENTO_CAPACIDAD.md` | NFR de capacidad |
| `ARQUITECTURA_INTEGRACIONES.md` | Sistemas externos |
| `ARQUITECTURA_DESPLIEGUE.md` | Topología de ejecución |
| `adr/` | Decisiones significativas |
| `rfc/` | Cambios transformacionales |

## Datos

Activar según persistencia y complejidad:

- `ARQUITECTURA_DATOS.md`;
- `ESTRATEGIA_BASE_DATOS.md`;
- `ESTRATEGIA_MIGRACIONES.md`;
- `DICCIONARIO_DATOS.md`;
- `ERD.md`;
- `CLASIFICACION_DATOS.md`;
- `RETENCION_DATOS.md`;
- `COPIAS_SEGURIDAD_RECUPERACION.md`;
- `GOBIERNO_DATOS.md`;
- `CALIDAD_LINAJE_DATOS.md`;
- `ESCALABILIDAD_DATOS.md`.

## Backend

`docs/06-backend/` se activa con `BACKEND_API` o cuando exista lógica server-side relevante.

Incluye:

- arquitectura;
- APIs;
- validación;
- errores;
- seguridad;
- eventos;
- estándares de código.

## Frontend

`docs/07-frontend/` se activa con `FRONTEND_WEB`.

Incluye:

- arquitectura;
- renderizado/entrega;
- routing;
- estado;
- componentes;
- sistema de diseño;
- responsive;
- accesibilidad;
- rendimiento;
- internacionalización;
- errores;
- observabilidad cliente.

## Seguridad

`REQUISITOS_SEGURIDAD.md` aplica a todo software no trivial.

Condicionales:

- autenticación;
- autorización;
- criptografía;
- secretos;
- modelo de amenazas;
- pruebas;
- dependencias;
- cadena de suministro.

## Pruebas

Obligatorios como política:

- estrategia;
- TDD;
- validación de sistema completo.

Condicionales por riesgo:

- unitarias;
- componente;
- integración;
- contrato;
- sistema;
- E2E;
- seguridad;
- rendimiento;
- resiliencia;
- migraciones;
- accesibilidad;
- regresión.

## DevOps

| Documento | Activación |
|---|---|
| `ESTRATEGIA_DEVOPS.md` | Siempre; puede declarar no aplica |
| `ESTRATEGIA_CI.md` | CI |
| `ESTRATEGIA_CD.md` | CD |
| `ESTRATEGIAS_DESPLIEGUE.md` | Entornos compartidos/producción |
| `INFRAESTRUCTURA.md` | Infraestructura no trivial |
| `INFRAESTRUCTURA_COMO_CODIGO.md` | IaC |
| `CONTENEDORES.md` | Contenedores |
| `ENTORNOS.md` | Más de un entorno o configuración relevante |
| `ESTRATEGIA_RELEASES.md` | Releases |
| `VERSIONADO.md` | Versionado de software/contratos |

## Observabilidad

Activar cuando el sistema se opera en un entorno real:

- estrategia;
- logs;
- métricas;
- trazas;
- health checks;
- alertas;
- SLI/SLO/SLA.

En sistemas distribuidos, trazas y correlación adquieren mayor prioridad.

## Operaciones

Activar según criticidad:

- guía operativa;
- despliegue;
- reversión;
- incidentes;
- postmortem;
- DR;
- prácticas SRE.

## Plataformas

`docs/18-plataformas/` evita forzar todo dentro de frontend/backend:

- móvil;
- escritorio;
- CLI;
- batch/workers;
- serverless.

## Ágil y gestión

Incluye Scrum + Kanban, backlog, épicas, funcionalidades, historias, tareas, errores, spikes y deuda técnica.

La automatización remota siempre es explícita y opcional.

## IA y contexto

`docs/15-ia/` define reglas por disciplina: análisis, arquitectura, desarrollo, backend, frontend, datos, seguridad, pruebas, DevOps y operaciones.

`docs/16-contexto/` reduce el consumo de contexto mediante lectura progresiva y contexto local por módulo.

## Plantillas

`docs/17-plantillas/` contiene artefactos reutilizables para decisiones, backlog, contratos, incidentes y planes.

## Validación

Antes de una versión:

```bash
python tools/validate_docs.py
```

Resolver errores y revisar advertencias relevantes.
