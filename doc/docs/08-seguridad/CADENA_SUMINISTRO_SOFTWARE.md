# Seguridad de la cadena de suministro de software

> **Clasificación:** CONDICIONAL  
> **Activar si:** se consumen dependencias externas o se producen artefactos desplegables.

## Propósito

Proteger el camino desde código fuente y dependencias hasta el artefacto que llega a un entorno.

## Dependencias

Definir:

- fuentes permitidas;
- lockfiles;
- actualización;
- vulnerabilidades;
- licencias;
- dependencias abandonadas;
- paquetes internos;
- revisión de procedencia.

## CI

Proteger:

- runners;
- credenciales;
- permisos;
- variables;
- acciones/plugins de terceros;
- branches/tags;
- artefactos.

## Artefactos

Considerar:

- repositorio/registry;
- inmutabilidad;
- checksums;
- firma;
- procedencia;
- retención;
- promoción entre entornos.

## Contenedores

Si aplica:

- imagen base;
- digest;
- usuario no root;
- mínimos paquetes;
- scan;
- SBOM;
- firma;
- actualización.

## Controles

Seleccionar según riesgo:

- SAST;
- DAST;
- secret scanning;
- dependency scanning;
- container scanning;
- IaC scanning;
- SBOM;
- firma;
- attestations/provenance.

## Baseline implementada en OLA 0

Los tres repositorios privados aplican una baseline proporcional al estado actual:

- Dependency Graph, Dependabot alerts y Dependabot security updates sobre la rama predeterminada `develop`;
- security updates agrupados por ecosistema mediante `.github/dependabot.yml`, sin actualizaciones ordinarias, auto-merge, registries privados o secretos;
- Gitleaks 8.30.1 bloqueante dentro de cada job CI existente, descargado por versión exacta y validado con SHA-256 antes de escanear todo el historial Git con redacción completa;
- auditorías npm y Python y scans Trivy existentes conservados sin relajar sus gates ni excepciones cerradas.

No se habilitan GitHub Secret Protection, CodeQL, Dependency Review Action o attestations porque no están disponibles en el plan privado actual. SBOM, firma y provenance se difieren hasta la primera publicación inmutable; DAST y SAST adicional se reevaluarán cuando exista superficie funcional suficiente. La baseline no publica artefactos ni demuestra readiness Production.

## Vulnerabilidades

Definir:

- severidades;
- SLA de remediación;
- excepciones;
- dueño;
- parche urgente;
- evidencia.

## Regla

No incorporar un control solo para cumplir una lista: vincularlo a una amenaza, requisito o riesgo.
