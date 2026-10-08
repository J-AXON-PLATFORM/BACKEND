# Guía de selección de arquitectura

> **Clasificación:** CONDICIONAL

## Propósito

Ayudar a elegir una forma de sistema a partir de requisitos y restricciones, no de tendencias.

## Secuencia

### 1. Empezar por la solución más simple

Preguntar si una sola unidad desplegable puede cumplir:

- escala;
- disponibilidad;
- seguridad;
- velocidad de desarrollo;
- organización del equipo;
- evolución prevista.

Si la respuesta es sí, un monolito o monolito modular puede ser suficiente.

### 2. Monolito

Adecuado cuando:

- el dominio todavía cambia mucho;
- un equipo pequeño mantiene el producto;
- las partes se despliegan juntas;
- las transacciones locales simplifican el sistema;
- la independencia operativa no es necesaria.

Riesgos:

- acoplamiento si no existe modularidad;
- despliegue conjunto;
- escalado de toda la unidad.

### 3. Monolito modular

Adecuado cuando se necesitan límites internos fuertes sin pagar todavía el coste distribuido.

Evaluar:

- módulos;
- ownership;
- dependencias;
- contratos internos;
- transacciones.

Puede ser paso final o base para extraer servicios solo si aparece una razón real.

### 4. Microservicios

Considerar cuando existen una o más necesidades fuertes:

- despliegues realmente independientes;
- escalado muy diferente;
- ownership por equipos autónomos;
- aislamiento operativo;
- límites de dominio maduros.

Costes:

- red;
- observabilidad;
- contratos;
- consistencia;
- deployment;
- seguridad;
- datos;
- fallos parciales.

### 5. Orientada a eventos

Considerar cuando:

- desacoplamiento temporal aporta valor;
- productores no necesitan respuesta inmediata;
- existen múltiples consumidores;
- buffering absorbe picos;
- auditoría/eventos son parte del dominio.

Costes:

- eventual consistency;
- duplicados;
- ordering;
- debugging;
- replay;
- schema evolution.

### 6. Serverless

Considerar cuando:

- carga variable;
- eventos;
- operaciones acotadas;
- modelo administrado reduce operación;
- coste por uso es favorable.

Evaluar límites, lock-in y observabilidad.

## Regla

Arquitecturas pueden combinarse. Por ejemplo, un monolito modular puede publicar eventos externos sin convertirse en microservicios.

## Salida

Usar `MARCO_TOMA_DECISIONES.md` y registrar una ADR si la elección es significativa.
