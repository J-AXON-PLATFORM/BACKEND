# Plataforma CLI

> **Clasificación:** CONDICIONAL

## Activar si

Existe una interfaz de línea de comandos para humanos o automatización.

## Contrato

Por comando:

- nombre;
- argumentos;
- flags;
- stdin;
- stdout;
- stderr;
- código de salida;
- efectos;
- idempotencia;
- compatibilidad.

## Diseño

- comportamiento scriptable;
- mensajes para humanos vs salida estructurada;
- modo no interactivo;
- configuración;
- variables de entorno;
- secretos;
- logging;
- completado si aporta valor.

## Distribución

Definir empaquetado, plataformas, checksums, firma y actualización.

## Discovery Executor -- OLA4-DIS-005a

- comando: `jaxon-discovery-executor run-once --state-dir <ruta>`;
- stdin: no usado;
- stdout: JSON cerrado con `executorId`, `jobId` nullable y `status=IDLE|DELIVERED`;
- stderr: error seguro sin payloads, rutas internas, excepciones, secretos o credenciales;
- códigos: `0` para ejecución/idle correcto y `2` para fallo cerrado;
- efectos: como máximo un Result local pendiente entregado o un Job final recogido, ejecutado y entregado;
- idempotencia: ledger local y comparación remota byte a byte evitan reejecución/overwrite;
- compatibilidad: POSIX/Linux, foreground manual, sin polling, retries automáticos, daemon o PyInstaller.
