# Pruebas de sistema

> **Clasificación:** CONDICIONAL

## Propósito

Verificar el producto o un conjunto amplio de componentes desde una frontera externa sin depender necesariamente de una UI.

## Ejemplos

- API desplegada + DB + broker;
- backend completo por HTTP;
- servicio con integraciones controladas;
- aplicación desplegada en entorno efímero.

## Validar

- configuración;
- wiring;
- contratos;
- seguridad;
- persistencia;
- integración;
- comportamiento externo.

## Diferencia con E2E

Una prueba de sistema puede comprobar la arquitectura integrada sin simular un journey visual completo.

## Criterio

Seleccionar escenarios que detecten errores de integración de alto impacto sin duplicar todos los casos unitarios.
