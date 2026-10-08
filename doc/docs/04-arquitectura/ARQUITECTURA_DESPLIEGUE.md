# Arquitectura de despliegue

> **Clasificación:** CONDICIONAL

## Propósito

Definir dónde y cómo se ejecutan las unidades del sistema sin asumir proveedor.

## Unidades desplegables

Por unidad:

```text
Nombre:
Responsabilidad:
Runtime:
Réplicas:
CPU/memoria:
Puertos:
Dependencias:
Configuración:
Secretos:
Persistencia:
Health:
Escalado:
```

## Topología

Documentar:

- entornos;
- nodos/plataformas;
- zonas/redes;
- balanceadores;
- gateways;
- bases de datos;
- brokers;
- almacenamiento;
- dependencias externas.

## Fronteras de red

Definir:

- tráfico entrante;
- tráfico saliente;
- puertos/protocolos;
- autenticación;
- cifrado;
- segmentación.

## Configuración y secretos

Separar:

- artefacto;
- configuración;
- secreto.

No hornear secretos dentro de imágenes/binarios.

## Escalado

Relacionar con `RENDIMIENTO_CAPACIDAD.md`.

Definir si la unidad escala:

- verticalmente;
- horizontalmente;
- por cola;
- por métrica;
- manualmente.

## Despliegue

Relacionar con `docs/10-devops/ESTRATEGIAS_DESPLIEGUE.md`.

## Evidencia

Mantener diagrama de despliegue y referencias a IaC cuando aplique.
