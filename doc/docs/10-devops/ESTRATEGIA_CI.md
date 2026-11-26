# Estrategia de integración continua

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- CI aprobado

- GitHub Actions independiente en `backend`, `frontend`, `automation-collector`.
- PR a `develop/main`: `fast + integration` obligatorios.
- Backend: compile, unit, Spring Modulith verification, PostgreSQL/Flyway, contratos y SFTP cuando aplique.
- Frontend: `npm ci`, lint, typecheck, Vitest/RTL, `next build`.
- Python: `uv sync --locked`, Ruff, pytest, contratos/SFTP y packaging cuando aplique.
- Cambios cross-repo: validar combinación explícita de versiones/contratos antes de release.
- Actions externas fijadas por SHA completo y permisos mínimos; Dependabot por PR; CodeQL/GHAS cuando esté disponible.
- PRs no reciben secretos productivos. Cancelar runs obsoletos del mismo PR cuando no sean deployments/releases.
<!-- JAXON-CKP003:END -->

## Estado implementado en backend -- TEC-PLT-003a, TEC-QLT-003a, TEC-QLT-004a, AC-JAXON-059, observabilidad, backup/restore y supply-chain base

El workflow `.github/workflows/backend-ci.yml`, denominado `Backend CI`, implementa la línea base ejecutable actual del backend:

- Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecución manual mediante `workflow_dispatch`;
- job estable `Backend verification` sobre `ubuntu-24.04`, con timeout de 30 minutos;
- Java 25 Temurin y caché Maven administrada por `setup-java`;
- build y scan de una fixture local endurecida PostgreSQL 18.6 + pgvector 0.8.6 antes de las pruebas;
- clave exacta, restauración validada y guardado best-effort de una caché BuildKit local para SFTPGo, sin reutilizar los gates de pruebas, vulnerabilidades o verificación runtime;
- `./mvnw --batch-mode --no-transfer-progress clean verify` para compilación, suite automatizada, modularidad, contratos, health/probes, logs JSON, correlación HTTP, métricas, tracing e integración Testcontainers/Flyway con los ocho schemas;
- build multi-stage del backend, smoke no-root/read-only con PostgreSQL real y scan Trivy bloqueante HIGH/CRITICAL;
- validación declarativa de `compose.yaml` y smoke Compose aislado con PostgreSQL predeterminado y backend opcional, reutilizando las imágenes construidas por los gates anteriores;
- gate `scripts/verify-postgresql-backup-restore.sh` con dos PostgreSQL efímeros, dump lógico custom, manifest SHA-256, restore transaccional, datos sintéticos y casos negativos;
- permisos read-only mínimos `contents: read`, `actions: read` y `pull-requests: read`, sin secretos, despliegues, publicación de artefactos ni escritura en el repositorio;
- `actions/checkout` y `actions/setup-java` fijadas por SHA completo, con credenciales persistentes deshabilitadas;
- concurrencia por workflow y rama o Pull Request, con cancelación de ejecuciones obsoletas.
- checkout de historial completo y Gitleaks 8.30.1 bloqueante, verificado mediante SHA-256 y ejecutado con redacción total antes de compilar.

La ampliación histórica de contenedores se integró mediante `BACKEND#5`, squash `90c550714b861beb7d6388cf571744efcda4af8d`, con runs verdes `37879426717` y `37879749721`. AC-JAXON-059 añade al mismo job pruebas contractuales Maven y `oasdiff` 1.33.0 verificado por SHA-256. `OLA2-SFTP-001a` añade al job existente el build/verificación de la fixture SFTPGo Local/Test y libera después su caché de construcción para preservar espacio sin eliminar gates. `OLA2-SNP-001a` amplía la prueba existente contra SFTPGo real para perfil, upgrade, límite 5 MiB, ACLs, transferencia atómica e aislamiento, sin cambiar el workflow. Los rulesets continúan pendientes por el plan del repositorio privado.

`TEC-QLT-004a` amplía el mismo job `Backend verification`; no crea otro workflow ni elimina gates. El verificador Compose usa proyecto, puertos y volumen efímeros propios, y su cleanup no alcanza el volumen persistente de un entorno Local. La publicación de imágenes, artefactos y despliegues permanece fuera de alcance.

`TEC-OPS-002a`, `TEC-OPS-003a` y `TEC-OPS-004a` quedan cubiertas funcionalmente por `clean verify`, por lo que no requieren otro workflow ni nuevos pasos. Los smokes de contenedor/Compose existentes verifican que el runtime arranca sin collector, y las imágenes continúan sometidas a Trivy HIGH/CRITICAL bloqueante.

`TEC-OPS-005a` y `TEC-OPS-006a` amplían el mismo job después de construir las imágenes locales. El harness usa red, contenedores, tmpfs y directorio temporal propios, no accede al volumen Local persistente, no recibe secretos reales y elimina sus recursos mediante ownership explícito. Los dumps no se cargan como artifacts ni se conservan tras el run. Se prueban alteraciones de backup/manifest, formato o permisos inválidos, sobrescritura, destino no vacío y rollback ante fallo de `pg_restore`. Este gate no valida DR, cifrado, retención, RPO o RTO Production.

## Caracterización backend -- TEC-QLT-005a

La medición del 2026-10-22 usa timestamps públicos por paso de cuatro ejecuciones verdes de `OLA3-SUP-004a`: PR técnico `39682421438` intento 2, post-merge técnico `39688305298`, PR documental `39691270114` y post-merge documental `39693528410`. Sus jobs duraron respectivamente 1213, 1244, 1179 y 1032 segundos. El intento 1 fallido de `39682421438` consumió otros 988 segundos antes de detectar la aserción SSE en `Verify backend`; se conserva como evidencia del coste de fallo tardío, no se mezcla con los rangos verdes.

| Grupo o paso | Rango en runs verdes | Lectura |
|---|---:|---|
| Build/verificación SFTPGo | 697--899 s | 68--73 % del job; coste dominante |
| Liberación de caché de build SFTPGo | 15--18 s | cleanup necesario después del gate |
| `Verify backend` Maven/Testcontainers | 71--90 s | aproximadamente 7 %; incluye la suite funcional |
| Build fixture PostgreSQL + scan Trivy | 46--63 s | imagen reproducible y scan bloqueante |
| Build backend + smoke + scan Trivy | 110--133 s | imagen productiva local, ejecución endurecida y scan |
| Validación/smoke Compose | 22--26 s | reutiliza imágenes locales y recursos aislados |
| Backup/restore lógico | 16--24 s | harness positivo/negativo aislado |
| OpenAPI compatibility | 2 s | gate contractual estable |
| Gitleaks | 1--2 s | historial completo con redacción |

El PR técnico y su push post-merge repitieron el job completo y sumaron 2457 segundos; el PR exclusivamente documental y su push post-merge repitieron los mismos gates y sumaron 2211 segundos. Esta duplicación es deliberada bajo el workflow actual y permite verificar exactamente cada commit integrado, pero hace que cambios documentales paguen el mismo coste de SFTPGo, imágenes, Trivy, Compose y backup/restore.

La única oportunidad priorizada para análisis futuro es una caché reproducible del build SFTPGo, ligada de forma cerrada a commit, checksums, builder y fuentes. El riesgo es reutilizar una imagen o capas obsoletas y omitir pruebas upstream, `govulncheck`, SBOM o Trivy; cualquier propuesta deberá demostrar invalidación correcta y conservar build/verificación/scans como gates efectivos. `TEC-QLT-005a` no modifica `.github/workflows/backend-ci.yml`, no elimina pasos ni autoriza esa optimización.

## Optimización backend -- TEC-QLT-006a

`TEC-QLT-006a` materializa bajo `AC-JAXON-076` la oportunidad identificada por la caracterización anterior. `actions/cache/restore` y `actions/cache/save` 6.1.0 están fijadas al SHA `55cc8345863c7cc4c66a329aec7e433d2d1c52a9`; usan `/tmp/jaxon-sftpgo-build-cache` y una clave exacta calculada por `docker/sftpgo/cache-key.sh`, sin `restore-keys`. La clave cubre esquema, plataforma, workflow, Dockerfile, `.dockerignore` y scripts efectivos. `cache-integrity.sh` genera y compara un inventario canónico SHA-256 de la caché OCI, rechaza symlinks, archivos adicionales, faltantes o alterados y obliga a reconstrucción completa ante rechazo.

Un hit válido se importa como read-only. Un miss exporta con `mode=min` una única caché de las capas alcanzables por el target verificado a un directorio temporal y la sustituye solo después del build y del manifiesto; un fallo de restore/save afecta únicamente a la optimización. Como el driver `docker` del runner GitHub no admite el exporter local, el script crea y elimina un builder efímero `docker-container` con `moby/buildkit:buildx-stable-1` fijada al índice multiarch SHA-256 `cec9f139f45e93c5c69c60f8b07cfad9f43f4ef6b6a6cd917527fea5ff2e3dea`. Tras el target verificado se elimina el estado interno del builder antes de hashear la exportación, y el build runtime importa la caché externa, evitando mantener ambas copias pesadas. `--no-cache-filter` fuerza las etapas `upstream-tests`, `vulnerability-check` y `verification`, mientras el segundo build carga la imagen runtime y `verify-image.sh` conserva versión, Trivy HIGH/CRITICAL y SBOM CycloneDX temporal. Gitleaks, PostgreSQL/pgvector, Maven, OpenAPI, contenedor backend, Compose y backup/restore no cambian. Los hashes de caché detectan corrupción accidental dentro de la frontera de confianza de GitHub Actions; no autentican contenido frente a un actor que controle esa misma frontera.

La verificación local cubrió determinismo e invalidación por cada entrada, plataforma inválida, corrupción, contenido no inventariado, manifiesto truncado, cache miss, hit exacto y caché inválida descartada. Antes de la corrección remota, la caché completa `mode=max` midió 582,83 s en miss y 403,54 s en hit; una prueba anterior de rechazo y reconstrucción completa midió 576,97 s. La configuración final `mode=min` se verificó desde una caché creada por esa misma configuración y un builder nuevo: importó el hit read-only, ejecutó de nuevo pruebas upstream y `govulncheck`, reconstruyó la imagen runtime, pasó Trivy y generó el SBOM en 516,74 s. Frente al rango histórico 697--899 s, redujo entre 180 y 382 segundos sin omitir gates. La evidencia remota de PR y post-merge se incorpora al reporte de cierre después de existir; no se anticipa en este documento.

## CI escalonada por riesgo -- TEC-QLT-007a

Los workflows backend y frontend mantienen sus nombres y jobs de verificación estables, precedidos por un job corto `Classify CI risk`. Un script puro recibe la lista completa de rutas y produce exactamente `DOCS_ONLY`, `APPLICATION` o `PLATFORM_FULL`; pruebas de tabla ejecutadas en el propio workflow cubren rutas documentales, aplicación, plataforma, combinaciones, entrada vacía y rutas desconocidas. La precedencia es cerrada: cualquier ruta de plataforma o desconocida domina y fuerza `PLATFORM_FULL`.

En Pull Requests se compara el SHA base/head del evento. En push a `develop`, el workflow consulta mediante permisos read-only el único PR fusionado asociado al commit, verifica que `merge_commit_sha` coincide y usa la colección paginada de archivos del PR; ausencia, ambigüedad o inconsistencia degradan a `PLATFORM_FULL`. `workflow_dispatch` ofrece los tres niveles pero conserva `PLATFORM_FULL` como valor obligatorio predeterminado. No existe trigger periódico.

`DOCS_ONLY` ejecuta checkout completo, whitespace y Gitleaks. `APPLICATION` añade toolchain, dependencias, lint/compilación, pruebas, build y contratos; backend construye PostgreSQL/pgvector y ejecuta Maven/Testcontainers/Flyway excluyendo únicamente `SftpGoClientIntegrationTest`, etiquetada `sftpgo`. `PLATFORM_FULL` ejecuta además todos los gates históricos: scans/auditorías, caché y build/verificación SFTPGo, contenedores, Trivy, Compose y backup/restore según el repositorio. Cambios bajo `.github/**`, scripts CI, dependencias/lockfiles, Docker/Compose, supply chain, seguridad de dependencias y rutas no reconocidas siempre son completos. Los PR de `OLA3-SUP-006a` + `TEC-QLT-007a` incluyen workflows y por tanto deben clasificarse `PLATFORM_FULL`.

`TEC-QLT-008a` corrige dos bordes sin alterar los niveles: frontend clasifica `e2e/**` como `APPLICATION`, mientras scripts/mecanismos CI y rutas desconocidas siguen forzando `PLATFORM_FULL`; backend calcula la clave SFTPGo con nombres relativos canónicos y hashes de contenido, por lo que checkouts distintos con bytes iguales comparten clave. Los PR del bloque modifican esos mecanismos y deben ejecutar `PLATFORM_FULL`.

`OLA4-DIS-003a` conserva workflows y niveles. Frontend clasifica `APPLICATION`; backend fuerza `PLATFORM_FULL` mediante una regla exacta para `DiscoverySftpGoClientIntegrationTest`, porque `APPLICATION` excluye el grupo `sftpgo` y omitiría la prueba real nueva. La verificación local quedó verde con 122 pruebas Python y Ruff, 69 pruebas frontend más lint/typecheck/build, E2E Chromium, 127 pruebas backend `clean verify` incluidas SFTPGo real y 17 migraciones, y `verify-openapi`.

`OLA4-DIS-004a` no modifica workflows ni clasificadores. Frontend clasificó `PLATFORM_FULL`, en vez de la `APPLICATION` prevista, porque el E2E real amplía un script del arnés; LUIS autorizó puntualmente aceptar ese nivel más estricto para `FRONTEND#24`. Backend modifica Docker/Compose/SFTPGo y debe clasificar `PLATFORM_FULL`. La verificación local quedó verde con 74 pruebas frontend, lint/typecheck/build, E2E Chromium multirepo y 141 pruebas backend `clean verify`, incluidas SFTPGo real, 18 migraciones, contratos y OpenAPI.

`OLA4-DIS-007a` conserva workflows y clasificadores. Backend y frontend clasifican `APPLICATION` por modificar exclusivamente aplicación, migración, contratos, documentación y pruebas; una clasificación fail-closed más estricta también sería válida. No se autorizan cambios de workflow, dependencias, lockfiles, Compose o infraestructura.

`OLA4-DIS-008a` conserva igualmente workflows y clasificadores. Backend y frontend deben clasificar `APPLICATION`; una clasificación fail-closed más estricta es aceptable. `automation-collector` permanece byte a byte intacto. `BACKEND#58` clasificó `APPLICATION`; el Pull Request correctivo autorizado de la fixture SFTPGo (`otel v1.47.0`, `GO-2026-6974`) modifica `docker/sftpgo/*` y debe clasificar `PLATFORM_FULL`. El cierre real confirmó `BACKEND#59` `PLATFORM_FULL` y `FRONTEND#34` `APPLICATION`.

`OLA6-AUD-001a` conserva workflows, clasificadores, dependencias y lockfiles y añade dos migraciones (`ai`, `audit_reporting`). Backend y frontend deben clasificar `APPLICATION`, salvo nivel fail-closed más estricto. `automation-collector` permanece byte a byte intacto.

`OLA6-AUD-001b` conserva workflows, clasificadores, dependencias, lockfiles y OpenAPI 8.3 y añade exactamente dos migraciones (`identity`, `audit_reporting`). Backend debe clasificar `APPLICATION`, salvo nivel fail-closed más estricto. `frontend` y `automation-collector` permanecen byte a byte intactos.

`OLA6-AUD-001c` conserva workflows, clasificadores, dependencias, lockfiles y OpenAPI 8.3 y añade exactamente dos migraciones (`identity`, `audit_reporting`). Backend debe clasificar `APPLICATION`, salvo nivel fail-closed más estricto. `frontend` y `automation-collector` permanecen byte a byte intactos.

`OLA6-AUD-001d` conserva workflows, clasificadores, dependencias, lockfiles, Docker/Compose y OpenAPI 8.3 y añade exactamente dos migraciones (`identity`, `audit_reporting`). Backend debe clasificar `APPLICATION`, salvo nivel fail-closed más estricto. `frontend` y `automation-collector` permanecen byte a byte intactos.

`OLA6-AUD-001e` conserva workflows, clasificadores, dependencias, lockfiles, Docker/Compose, SFTPGo y OpenAPI 8.3 y añade exactamente tres migraciones (`inventory`, `collector`, `audit_reporting`). Backend debe clasificar `APPLICATION`, salvo nivel fail-closed más estricto. `frontend` y `automation-collector` permanecen byte a byte intactos.

`OLA4-DIS-008b` conserva workflows y clasificadores, no añade migración ni dependencia y debe clasificar `APPLICATION` en backend y frontend; un nivel fail-closed más estricto también es válido. `automation-collector` permanece byte a byte intacto.

`OLA5-AI-001a` conserva workflows, clasificadores, dependencias y lockfiles. Backend y frontend deben clasificar `APPLICATION`; un nivel fail-closed más estricto también es válido. `automation-collector` permanece byte a byte en `3dac71ca95442e4f50e77dd2a172b561ca49face`.

`OLA5-AI-002a` conserva workflows y clasificadores, pero backend añade la dependencia exacta Spring AI Transformers `2.0.2`; por la regla existente de rutas debe clasificar `PLATFORM_FULL` y ejecutar todos los gates históricos. Los pesos ONNX/tokenizer no se versionan, cachean ni publican en CI. Durante la validación local, Trivy bloqueó `CVE-2026-111497` en `libbrotli1 1.1.0-2build2` de la imagen runtime; la corrección acotada fija `1.1.0-2ubuntu0.1`, conserva la base y el gate, y fue revalidada localmente con 0 HIGH/CRITICAL. Frontend debe clasificar `APPLICATION` salvo nivel fail-closed más estricto. `automation-collector` permanece byte a byte intacto. El cierre real clasificó `PLATFORM_FULL` en los cuatro runs: backend por `pom.xml` y frontend por el harness `scripts/run-auth-e2e.sh`.

`OLA5-AI-003a` conserva workflows, clasificadores, dependencias y lockfiles y no añade migraciones. Backend debe clasificar `APPLICATION`; frontend también, salvo nivel fail-closed más estricto. El benchmark de recuperación no se ejecuta en CI y nunca usa artefactos del modelo en GitHub Actions. `automation-collector` permanece byte a byte intacto. `BACKEND#56` clasificó `APPLICATION` en PR y post-merge. La excepción correctiva posterior modifica `docker/sftpgo/**`, la fixture PostgreSQL y el `Dockerfile` backend, y debe clasificar `PLATFORM_FULL`, reejecutando todos los gates históricos. El primer run `45063540936` de `BACKEND#57` falló solo por el directorio transitorio `ingest-locks/` de la exportación BuildKit; la corrección autorizada lo poda de forma acotada y fail-closed mediante `cache-integrity.sh prune-transient`. El Pull Request frontend modifica `package.json`, `package-lock.json` y `scripts/verify-npm-audit.mjs`, por lo que también debe clasificar `PLATFORM_FULL`. El cierre real confirmó `BACKEND#56` `APPLICATION`, `BACKEND#57` y `FRONTEND#33` `PLATFORM_FULL`; `FRONTEND#33` fijó además `musl`/`musl-utils 1.2.6-r3` y `zlib 1.4-r0` en su runtime Alpine.

`OLA5-AI-006a` conserva workflows, clasificadores, dependencias y lockfiles y añade solo una migración del schema `ai`. Backend y frontend deben clasificar `APPLICATION`, salvo nivel fail-closed más estricto. `automation-collector` permanece byte a byte intacto.

Durante `BACKEND#47`, dos descargas consecutivas de la DB Trivy desde `mirror.gcr.io` devolvieron `404` antes de ejecutar el scan y ECR respondió después `TOOMANYREQUESTS`. Las excepciones puntuales autorizadas mantienen Trivy 0.75.0, severidades, formatos, condiciones y gates, y cambian únicamente sus cuatro invocaciones existentes para usar `ghcr.io/aquasecurity/trivy-db:2` como origen oficial primario y `public.ecr.aws/aquasecurity/trivy-db:2` como fallback.

El run inicial de `BACKEND#44`, `40060796361`, confirmó de forma bloqueante el nuevo `CVE-2026-111497` en `brotli-libs 1.2.0-r1` de la fixture PostgreSQL Local/Test. La corrección autorizada fija `1.2.1-r0` en la etapa runtime sin excepción de scan ni cambio de PostgreSQL/pgvector; el mismo PR debe reconstruir la fixture, pasar Trivy y repetir todos los gates `PLATFORM_FULL`.

El primer run de PR `39865316279` falló en 60 segundos porque confirmó que el driver `docker` del runner no soporta `cache-to type=local`; no se relanzó. La corrección incorpora el builder efímero fijado descrito arriba y debe validarse mediante un nuevo run asociado al nuevo commit.

El segundo run `39866621799` ejecutó pruebas upstream y `govulncheck` y completó la exportación `mode=max`, pero salió antes del build runtime al confirmar la caché, tras 18 min 31 s; tampoco se relanzó. La corrección limita la exportación a `mode=min`, libera el estado interno del builder antes de confirmar la copia externa y añade diagnósticos explícitos de layout. El tercer run `39872945593` completó de nuevo pruebas upstream, `govulncheck` y la exportación, pero el inventario rechazó el archivo transitorio superior `cache.lock` que BuildKit dejó en el directorio exportado; tampoco se relanzó. La corrección elimina exclusivamente ese lock después de terminar con el builder y antes de inventariar, sin admitir otros archivos adicionales.

## Estado implementado en frontend -- TEC-QLT-001a y TEC-QLT-003a

El workflow `.github/workflows/frontend-ci.yml`, denominado `Frontend CI`, implementa la línea base ejecutable actual del frontend:

- Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecución manual mediante `workflow_dispatch`;
- job estable `Frontend verification` sobre `ubuntu-24.04`, con timeout de 20 minutos;
- Node.js 24.21.0 y npm 11.19.0 desde `.nvmrc`, con caché npm ligada a `package-lock.json`;
- pasos separados para `npm ci`, lint, typecheck, Vitest/RTL, Next.js build, auditoría de producción y auditoría completa;
- build standalone, smoke no-root/read-only que confirma ausencia de gestores JavaScript y scan Trivy bloqueante HIGH/CRITICAL;
- `NEXT_TELEMETRY_DISABLED=1`, permisos read-only `contents: read`, `actions: read` y `pull-requests: read`, sin secretos, artefactos, publicación o despliegue;
- `actions/checkout@v7.0.1` y `actions/setup-node@v7.0.0` fijadas por SHA completo, con credenciales persistentes deshabilitadas;
- concurrencia por workflow y rama o Pull Request, con cancelación de ejecuciones obsoletas.
- checkout de historial completo y el mismo gate Gitleaks bloqueante, sin modificar las auditorías npm ni su excepción cerrada.

La activación fue integrada mediante el Pull Request frontend `#1`, squash `0a34d6dadacc95351544996235faf6bcb0b21b65`. El run de PR `37849248403` y el run post-merge sobre `develop` `37849379045` finalizaron correctamente.

La ampliación de contenedor se integró mediante `FRONTEND#2`, squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` de PR y `37880065517` post-merge finalizaron correctamente. Las imágenes permanecieron locales a CI y no fueron publicadas.

Las auditorías de producción y completa son bloqueantes y están limpias. Durante `FRONTEND#21`, el feed incorporó tres advisories para `undici@8.11.2` y uno para `zod@4.6.5`, ambos transitivos exclusivamente de desarrollo; una excepción correctiva autorizada actualizó solo sus entradas existentes de lockfile a `8.12.2` y `4.6.6`. `scripts/verify-npm-audit.mjs` permanece fail-closed ante cualquier hallazgo no reconocido. ESLint 9.39.5 permanece como riesgo EOL temporal porque la combinación aprobada con ESLint 10.12.0 falló de forma ejecutable y debe revisarse cuando la cadena oficial publique compatibilidad.

## Estado implementado en automation-collector -- TEC-QLT-002a

El workflow `.github/workflows/python-ci.yml`, denominado `Python CI`, implementa la línea base ejecutable actual de Python:

- Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecución manual mediante `workflow_dispatch`;
- job estable `Python verification` sobre `ubuntu-24.04`, con timeout de 20 minutos;
- Python 3.14.8 desde `.python-version` y uv 0.12.24 fijado también mediante `tool.uv.required-version`;
- pasos separados para sincronización `uv sync --locked`, `uv lock --check`, Ruff lint, Ruff format check, seis pruebas pytest, build, pip-audit y ejecución de ambos entrypoints;
- permisos `contents: read`, sin secretos, publicación, artefactos o despliegue;
- `actions/checkout@v7.0.1`, `actions/setup-python@v7.0.0` y `astral-sh/setup-uv@v10.2.0` fijadas por SHA completo, con credenciales persistentes deshabilitadas;
- concurrencia por workflow y rama o Pull Request, con cancelación de ejecuciones obsoletas.
- checkout de historial completo y el mismo gate Gitleaks bloqueante, sin modificar `pyproject.toml` o `uv.lock`.

La activación base fue integrada mediante `AUTOMATION-COLLECTOR#1`, squash `a040c5fba34fae22903088b82191e8355deaab26`, con runs verdes `37852626079` y `37852683236`. AC-JAXON-059 añade `jsonschema`/`referencing`, pruebas contractuales y verificación local del manifest al mismo workflow. `OLA2-SFTP-001a` añade Paramiko 5.0.0 y pruebas de configuración/host-key/conexión. `OLA2-SNP-001a` añade al mismo pytest generación mínima, validación offline previa a red, límite, transferencia/cleanup atómicos y salida segura; no cambia el workflow. PyInstaller, polling, retries, spool y tareas quedan pendientes.

## Baseline Dependabot y detección de secretos

Los tres repositorios mantienen un único workflow por CI; backend y frontend incorporan un job corto de clasificación además de su job estable de verificación, mientras Python conserva un único job. Sus configuraciones Dependabot cubren los ecosistemas versionados y agrupan solo security updates; `open-pull-requests-limit: 0` impide PR ordinarios de versión y la ausencia deliberada de `target-branch` permite aplicar los security updates sobre la rama predeterminada `develop`. No hay auto-merge.

Gitleaks se ejecuta dentro de cada job existente inmediatamente después del checkout. La descarga usa la release exacta 8.30.1 y SHA-256 `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb`; cualquier hallazgo falla el job y `--redact=100` evita imprimir el valor. No existen allowlists o exclusiones. GitHub Secret Protection, CodeQL y Dependency Review Action permanecen fuera por disponibilidad del plan; no se crean workflows, artifacts o despliegues.

## Propósito

Detectar problemas temprano en cada cambio integrable.

## Disparadores

Definir:

- push;
- pull request;
- merge;
- tag;
- manual;
- programado.

## Etapas

Seleccionar según proyecto:

- formato/lint;
- compilación;
- unitarias;
- integración;
- contrato;
- seguridad;
- build;
- SBOM;
- scan;
- empaquetado.

## Quality gates

Los gates deben corresponder a riesgos reales.

Definir qué falla el pipeline y qué produce solo advertencia.

## Caché

Cachear dependencias/builds sin permitir artefactos corruptos u obsoletos.

## Secretos y permisos

- mínimo privilegio;
- tokens de corta duración si posible;
- no imprimir secretos;
- revisar actions/plugins externos.

## Artefactos

Definir:

- naming;
- versión;
- digest/checksum;
- retención;
- registry;
- promoción.

## Rendimiento

Mantener una suite rápida en PR y separar validaciones costosas si mejora feedback sin perder seguridad.
