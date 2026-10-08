# Estrategias de despliegue

> **Clasificación:** CONDICIONAL  
> **Activar si:** el software se despliega a entornos compartidos o producción.

## Propósito

Seleccionar cómo introducir cambios y cómo recuperarse si fallan.

## Alternativas

### Recreate

Simple, pero puede generar downtime.

### Rolling

Reemplazo gradual de instancias. Exige compatibilidad temporal entre versiones.

### Blue/green

Dos entornos/versiones y conmutación de tráfico. Reduce tiempo de reversión, aumenta coste.

### Canary

Exposición progresiva a una fracción de tráfico. Requiere señales, control de tráfico y criterios de promoción.

### Feature flags

Separan despliegue de activación funcional. Añaden deuda de flags y necesidad de gobernanza.

### Shadow traffic

Duplica tráfico para observar comportamiento sin responder al usuario. Requiere proteger datos y efectos laterales.

## Criterios de selección

- tolerancia a downtime;
- coste;
- estado;
- compatibilidad de DB;
- capacidad de rollback;
- observabilidad;
- volumen;
- criticidad.

## Base de datos

Para zero-downtime considerar `expand-contract`:

1. agregar cambios compatibles;
2. desplegar consumidores compatibles;
3. migrar datos;
4. retirar compatibilidad antigua después.

No ejecutar cambios destructivos incompatibles antes de retirar consumidores.

## Reversión vs roll-forward

Definir cuándo:

- revertir binario/configuración;
- restaurar tráfico;
- corregir hacia adelante;
- revertir datos;
- no revertir migraciones destructivas.

## Criterios de promoción

- salud;
- errores;
- latencia;
- métricas de negocio;
- logs/trazas;
- pruebas smoke;
- ausencia de alertas críticas.

## Evidencia

Registrar estrategia por servicio o sistema y su runbook.
