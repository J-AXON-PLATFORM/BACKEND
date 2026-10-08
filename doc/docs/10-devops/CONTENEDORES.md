# Contenedores

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- contenedores

- Backend/frontend: Dockerfiles multi-stage y runtime no-root.
- Una imagen por componente/version; tags SemVer + Git SHA, identidad de promoción por digest inmutable.
- Local: Compose principalmente para infraestructura; Development/QA pueden usar las imágenes CI; Production no compila código durante deployment.
- Compose con base común + overrides donde aporte; la topología final Production sigue abierta.
- Agent no se fuerza a Docker: tiene packaging PyInstaller por SO. SFTPGo sí puede operar como infraestructura containerizada cuando corresponda.
- DB y administración no se exponen innecesariamente; web HTTPS y SFTP solo para Agents autorizados.
<!-- JAXON-CKP003:END -->

## Propósito

Definir uso de contenedores cuando aportan aislamiento y reproducibilidad.

## Imagen

- base mínima y mantenida;
- versiones/digests;
- multi-stage build si aporta valor;
- usuario no root;
- filesystem read-only cuando sea viable;
- healthcheck según plataforma;
- sin secretos embebidos.

## Runtime

Definir:

- CPU;
- memoria;
- puertos;
- volúmenes;
- variables;
- señales;
- shutdown;
- logs;
- red.

## Seguridad

- scan;
- SBOM;
- firma si aplica;
- actualización;
- capabilities mínimas;
- no ejecutar privilegiado sin justificación.

## Persistencia

El contenedor debe considerarse reemplazable. Datos persistentes viven en mecanismos explícitos.

## Pruebas

Validar build, arranque, health y shutdown.
