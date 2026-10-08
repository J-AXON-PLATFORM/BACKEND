# Reglas de backend para IA

> **Clasificación:** CONDICIONAL

## Antes de modificar

Leer:

- contexto del módulo;
- requisito;
- flujo;
- arquitectura backend;
- contrato;
- datos;
- resiliencia si hay I/O remoto.

## Reglas

- respetar límites;
- validar en fronteras;
- errores consistentes;
- transacciones explícitas;
- no aumentar pools sin medir el recurso destino;
- no agregar cache sin estrategia de invalidación;
- no agregar paralelismo sin analizar thread safety/recursos;
- timeouts en dependencias remotas;
- idempotencia si una operación puede repetirse;
- actualizar contrato y tests al cambiar interfaces;
- mantener seguridad y observabilidad.
