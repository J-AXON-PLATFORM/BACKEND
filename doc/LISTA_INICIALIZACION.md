# Lista de inicialización

> **Clasificación:** OBLIGATORIO

## Proyecto nuevo

- [ ] Completar `PROYECTO_MAESTRO.md`.
- [ ] Elegir perfil documental.
- [ ] Seleccionar perfiles técnicos.
- [ ] Revisar `MATRIZ_ACTIVACION.md`.
- [ ] Definir objetivo, alcance y éxito.
- [ ] Identificar módulos/dominos y ownership.
- [ ] Definir flujos críticos.
- [ ] Preparar requisitos funcionales y NFR.
- [ ] Identificar decisiones abiertas.
- [ ] Aplicar `MARCO_TOMA_DECISIONES.md`.
- [ ] Definir arquitectura de alto nivel.
- [ ] Evaluar monolito/monolito modular/distribución según necesidad.
- [ ] Identificar contratos.
- [ ] Definir datos.
- [ ] Definir rendimiento/capacidad.
- [ ] Definir seguridad.
- [ ] Definir estrategia de pruebas.
- [ ] Definir CI/CD y despliegue si aplica.
- [ ] Definir cloud/IaC si aplica.
- [ ] Definir observabilidad/operación si habrá producción.
- [ ] Crear backlog inicial.
- [ ] Ejecutar `python tools/validate_docs.py`.

## Proyecto existente

- [ ] Inventariar repositorios y módulos.
- [ ] Inspeccionar arquitectura real.
- [ ] Separar estado actual de estado objetivo.
- [ ] Reconstruir flujos desde evidencia.
- [ ] Identificar unidades desplegables.
- [ ] Identificar contratos actuales.
- [ ] Identificar ownership real de datos.
- [ ] Mapear integraciones.
- [ ] Mapear resiliencia existente.
- [ ] Medir o recopilar NFR conocidos.
- [ ] Mapear pruebas y huecos.
- [ ] Mapear pipelines, infraestructura y entornos.
- [ ] Mapear observabilidad y operación.
- [ ] Registrar deuda técnica.
- [ ] No inventar razones históricas.
- [ ] Seleccionar perfiles técnicos según la realidad actual.
- [ ] Ejecutar validador documental.

## Activaciones especiales

### Microservicios/sistema distribuido

- [ ] `ARQUITECTURA_DISTRIBUIDA.md`
- [ ] `RESILIENCIA.md`
- [ ] ownership de datos
- [ ] contratos
- [ ] compatibilidad
- [ ] observabilidad distribuida
- [ ] estrategia de despliegue independiente

### Cloud

- [ ] `ARQUITECTURA_CLOUD.md`
- [ ] IAM
- [ ] región/zonas
- [ ] red
- [ ] backups
- [ ] costes
- [ ] responsabilidad compartida
- [ ] IaC si corresponde

### Producción 24x7/alta criticidad

- [ ] SLI/SLO
- [ ] alertas
- [ ] runbooks
- [ ] gestión de incidentes
- [ ] RTO/RPO
- [ ] DR probado
- [ ] prácticas SRE cuando aporten valor

## Automatización de gestión de trabajo

- [ ] Decidir si la IA solo propone o también publica.
- [ ] Definir proveedor.
- [ ] Definir permisos granulares.
- [ ] Confirmar que ninguna acción remota queda autorizada implícitamente.
