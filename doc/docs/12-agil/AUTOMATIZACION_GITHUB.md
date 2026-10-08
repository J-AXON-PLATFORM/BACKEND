# Automatización de planificación en GitHub

> **Clasificación:** CONDICIONAL  
> **Aplica cuando:** `PROYECTO_MAESTRO.md` autoriza explícitamente la automatización con GitHub. Usar GitHub como repositorio no activa esta capacidad por sí solo.

## Objetivo

Permitir que una IA convierta la documentación aprobada en elementos de trabajo de GitHub sin inventar requisitos ni publicar cambios antes de la aprobación humana.

## Activación

La automatización está deshabilitada por defecto hasta que `PROYECTO_MAESTRO.md` defina su modo y alcance. Puede configurarse como solo propuesta, propuesta con publicación aprobada o automatización delegada.

La IA debe respetar cada permiso granular del Proyecto Maestro: crear elementos, relacionarlos, añadirlos a Projects, completar campos, actualizar, cerrar o crear ramas/PRs. Una autorización para una acción no implica autorización para las demás.

## Flujo obligatorio

1. Leer `PROYECTO_MAESTRO.md`, requisitos, flujos y documentación del módulo afectado.
2. Proponer la jerarquía de trabajo sin crear nada en GitHub.
3. Mostrar al usuario las Épicas, Funcionalidades, Historias de Usuario y Tareas propuestas.
4. Esperar aprobación explícita.
5. Crear los elementos en GitHub usando las plantillas definidas.
6. Relacionar padres e hijos.
7. Añadirlos al GitHub Project seleccionado y completar los campos configurados.
8. Verificar que no existan duplicados y devolver los enlaces creados.

## Jerarquía

```text
Épica
└── Funcionalidad
    └── Historia de Usuario
        └── Tarea
```

En GitHub, cada elemento puede implementarse como una Issue con su tipo correspondiente. Las Tareas son unidades pequeñas de trabajo derivadas de una Historia de Usuario.

## Project/Kanban

Si la organización dispone de un Project plantilla, la automatización puede copiarlo y utilizar sus vistas, campos y estados. Si ya existe un Project para el producto, debe reutilizarse en lugar de crear otro.

Estados recomendados como punto de partida:

`Backlog → Preparado → En progreso → Revisión → QA → Terminado`

Los nombres y estados son configurables por proyecto.

## Permisos

La IA solo debe solicitar los permisos mínimos necesarios. Para manipular GitHub Projects mediante GitHub CLI normalmente se requiere el alcance `project`, además de permisos de Issues cuando corresponda.

## Regla de seguridad

Crear/modificar Issues o Projects es una acción remota. La IA debe permanecer en modo análisis hasta recibir aprobación explícita, salvo que el usuario haya autorizado previamente una automatización concreta con alcance claramente definido.
