# Pruebas E2E

> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla dinámica


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- primer E2E aprobado

Playwright pertenece conceptualmente al sistema completo aunque el harness pueda residir en `frontend`.

Primera vertical: 1) login `IT_OPERATOR`; 2) crear activo; 3) pre-registrar/enrolar Agent controlado; 4) Agent genera snapshot; 5) upload `.part` + rename SFTP; 6) Collector valida JSON/version/schema/identidad/deduplicación; 7) PostgreSQL persiste; 8) SSE avisa; 9) frontend consulta REST y muestra datos técnicos. Cruza frontend, identity, inventory, collector, PostgreSQL, SFTPGo, Agent Python, JSON Schema y SSE.
<!-- JAXON-CKP003:END -->


## Estrategia browser aprobada Lote 47

- suite E2E principal de PR: Chromium;
- flujos críticos seleccionados (login, navegación, formularios principales, primera vertical, Support) se validan además en Firefox y WebKit antes de releases relevantes;
- Playwright pertenece al sistema completo aunque el harness resida en `frontend`;
- no multiplicar toda la suite por navegador en cada PR sin valor adicional.

## Propósito

Cubrir recorridos críticos sin convertir E2E en sustituto de unitarias, integración o contrato.

Derivar escenarios de los flujos E2E y criterios de aceptación documentados.

## Principios

- probar journeys de alto valor;
- usar datos controlados;
- preferir selectores/interacciones estables desde la perspectiva del usuario;
- evitar assertions sobre detalles internos innecesarios;
- no usar E2E para comprobar exhaustivamente cada regla pequeña;
- distinguir fallos de producto, test y entorno;
- conservar evidencia útil de fallos.
