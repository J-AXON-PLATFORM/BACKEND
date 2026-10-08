# Plantilla de flujo de aplicación


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Mostrar como una petición atraviesa componentes o sistemas


```mermaid
sequenceDiagram
    actor User
    participant UI
    participant API
    participant Module
    participant DB
    User->>UI: Action
    UI->>API: Request
    API->>Module: Execute use case
    Module->>DB: Persist/query
    DB-->>Module: Result
    Module-->>API: Result
    API-->>UI: Response
```
