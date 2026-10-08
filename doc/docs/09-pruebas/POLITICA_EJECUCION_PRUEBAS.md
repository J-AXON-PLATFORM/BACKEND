# Política de ejecución de pruebas

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- suites y gates

- `fast`: unit/component/static checks rápidos.
- `integration`: PostgreSQL/Flyway real, contratos, SFTP controlado y otras dependencias reales relevantes.
- `complete`: E2E sistema, seguridad/resiliencia/performance y evidencia de release que corresponda.
- PR `develop/main`: `fast + integration` bloqueantes.
- Release/promoción: suite más completa según riesgo; pruebas costosas pueden tener ejecución periódica.
- Cross-repo: validar combinaciones explícitas de backend/Agent/contratos.
<!-- JAXON-CKP003:END -->

## Propósito

Definir qué evidencia se obtiene en cada momento sin hacer que toda validación dependa de una suite lenta.

## Capas de ejecución

### Local/cambio rápido

- lint/formato;
- unitarias afectadas;
- checks rápidos.

### Pull request

- compilación;
- unitarias;
- integración relevante;
- contrato;
- seguridad rápida;
- componente según proyecto.

### Merge

Puede agregar pruebas de sistema e integración más amplias.

### Periódica

- E2E amplios;
- regresión;
- seguridad;
- performance;
- resiliencia;
- compatibilidad.

### Release

Ejecutar evidencia necesaria para el riesgo de la versión.

## Selección

Definir por suite:

```text
Suite:
Disparador:
Duración objetivo:
Bloquea:
Entorno:
Datos:
Responsable:
```

## Regla

No trasladar todas las pruebas costosas a cada PR si reduce productividad sin beneficio proporcional, pero tampoco omitirlas sin una ventana alternativa confiable.
