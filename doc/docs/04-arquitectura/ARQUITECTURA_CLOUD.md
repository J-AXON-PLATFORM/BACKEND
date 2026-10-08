# Arquitectura cloud

> **Clasificación:** CONDICIONAL  
> **Activar si:** el proyecto utiliza cloud, híbrido o multicloud.

## Propósito

Diseñar infraestructura cloud sin acoplar el framework a AWS, Azure, GCP u otro proveedor.

## Contexto

- proveedor(es);
- regiones disponibles;
- requisitos de residencia de datos;
- regulación;
- presupuesto;
- experiencia del equipo;
- servicios existentes;
- portabilidad requerida.

## Topología

Definir:

- región;
- zonas de disponibilidad;
- segmentación de red;
- subredes;
- rutas;
- exposición pública/privada;
- DNS;
- certificados;
- balanceo;
- gateways;
- conectividad con terceros/on-premise.

## Identidad y acceso

Definir:

- IAM;
- identidad humana;
- identidad de workloads;
- mínimo privilegio;
- segregación por entorno;
- break-glass;
- rotación;
- auditoría.

## Cómputo

Comparar según necesidad:

- máquinas virtuales;
- contenedores;
- orquestadores;
- plataformas administradas;
- funciones/serverless.

Considerar:

- arranque;
- escalado;
- aislamiento;
- coste;
- mantenimiento;
- límites;
- portabilidad.

## Datos y almacenamiento

Definir:

- persistencia;
- backup;
- replicación;
- cifrado;
- residencia;
- lifecycle;
- recuperación;
- transferencia de datos;
- costes de salida.

## Alta disponibilidad y recuperación

Definir:

- fallos tolerados;
- multi-zona;
- multi-región si realmente se necesita;
- RTO;
- RPO;
- estrategia de failover;
- pruebas de restauración.

No usar multi-región como valor por defecto.

## Autoescalado y cuotas

Registrar:

- señal de escalado;
- mínimos/máximos;
- límites del proveedor;
- cuotas;
- comportamiento en saturación;
- protección de dependencias.

## Responsabilidad compartida

Por servicio administrado, aclarar:

- qué opera el proveedor;
- qué configura el equipo;
- qué parchea cada parte;
- quién protege datos;
- quién controla acceso;
- quién verifica backups.

## Costes

Registrar:

- estimación;
- principales cost drivers;
- presupuestos/alertas;
- coste de red;
- almacenamiento;
- observabilidad;
- entornos no productivos;
- reservas/compromisos si aplican.

## Vendor lock-in

Evaluar:

- qué capacidades propietarias se usan;
- beneficio obtenido;
- coste de migración;
- probabilidad real de migración;
- si la abstracción para evitar lock-in compensa su complejidad.

## Evidencias

- diagrama;
- inventario de recursos;
- ADR principales;
- IaC;
- controles de seguridad;
- RTO/RPO;
- estimación de costes.
