# Pruebas de componente

> **Clasificación:** CONDICIONAL

## Propósito

Verificar un componente o módulo a través de su interfaz pública, con un alcance mayor que una unidad y menor que el sistema completo.

## Cuándo aportan valor

- módulo con reglas internas complejas;
- frontend component/feature;
- servicio ejecutado con dependencias sustituidas;
- módulo de monolito modular.

## Diseño

Definir:

- frontera pública;
- dependencias reales;
- dobles permitidos;
- datos;
- errores;
- estados;
- rendimiento si es relevante.

## Regla

No crear una capa de pruebas adicional si unitarias + integración ya dan evidencia suficiente.
