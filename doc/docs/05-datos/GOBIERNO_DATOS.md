# Gobierno de datos

> **Clasificación:** CONDICIONAL  
> **Activar si:** existen múltiples dominios, equipos, servicios, requisitos regulatorios o datos compartidos.

## Propósito

Definir quién es responsable de cada dato y qué reglas gobiernan su ciclo de vida.

## Ownership

Por dominio/conjunto de datos:

- propietario;
- sistema fuente;
- consumidores;
- clasificación;
- contrato;
- calidad esperada;
- retención;
- acceso.

Evitar bases compartidas sin ownership explícito en arquitecturas distribuidas.

## Acceso

Definir:

- mínimo privilegio;
- acceso humano;
- acceso de servicios;
- lectura/escritura;
- auditoría;
- datos de producción en entornos no productivos.

## Privacidad

Según contexto:

- minimización;
- propósito;
- consentimiento;
- anonimización;
- pseudonimización;
- derechos del titular;
- retención;
- borrado;
- residencia.

## Ciclo de vida

- creación;
- modificación;
- sincronización;
- archivo;
- retención;
- eliminación;
- backup;
- restauración.

## Multi-tenancy

Si aplica:

- aislamiento lógico/físico;
- claves de partición;
- autorización;
- backups;
- migraciones;
- riesgo de fuga entre tenants.

## Salida

Mantener una tabla de dominios de datos y propietarios enlazada con arquitectura y contratos.
