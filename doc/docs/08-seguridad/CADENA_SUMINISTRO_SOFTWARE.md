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
