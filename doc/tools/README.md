# Herramientas del framework

## validate_docs.py

Validador estático de coherencia documental.

Uso normal:

```bash
python tools/validate_docs.py
```

Uso estricto para una instancia de proyecto ya inicializada:

```bash
python tools/validate_docs.py --strict
```

El modo normal valida estructura. El modo estricto también advierte decisiones `POR_DECIDIR`.

El script no sustituye una revisión arquitectónica, de seguridad ni de requisitos.
