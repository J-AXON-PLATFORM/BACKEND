#!/usr/bin/env python3
"""Static documentation validator for the doc framework."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ESSENTIAL_FILES = [
    "VERSION",
    "README.md",
    "PROYECTO_MAESTRO.md",
    "MAPA_DOCUMENTACION.md",
    "docs/00-gobierno/CONSTITUCION_IA.md",
    "docs/00-gobierno/MARCO_TOMA_DECISIONES.md",
    "docs/00-gobierno/MATRIZ_ACTIVACION.md",
    "docs/00-gobierno/VALIDACION_DOCUMENTAL.md",
    "docs/04-arquitectura/PERFILES_TECNICOS.md",
    "docs/09-pruebas/VALIDACION_SISTEMA_COMPLETO.md",
    "docs/16-contexto/ENRUTADOR_DOCUMENTACION.md",
]

PROFILE_REQUIREMENTS = {
    "BACKEND_API": ["docs/06-backend/ARQUITECTURA_BACKEND.md"],
    "FRONTEND_WEB": ["docs/07-frontend/ARQUITECTURA_FRONTEND.md"],
    "MOVIL": ["docs/18-plataformas/MOVIL.md"],
    "ESCRITORIO": ["docs/18-plataformas/ESCRITORIO.md"],
    "CLI": ["docs/18-plataformas/CLI.md"],
    "BATCH_WORKERS": ["docs/18-plataformas/BATCH_WORKERS.md"],
    "DATOS_ETL": [
        "docs/05-datos/ARQUITECTURA_DATOS.md",
        "docs/05-datos/CALIDAD_LINAJE_DATOS.md",
    ],
    "MICROSERVICIOS": [
        "docs/04-arquitectura/ARQUITECTURA_DISTRIBUIDA.md",
        "docs/04-arquitectura/RESILIENCIA.md",
    ],
    "ORIENTADO_EVENTOS": [
        "docs/03-flujos/PLANTILLA_FLUJO_ASINCRONO_EVENTOS.md",
        "docs/04-arquitectura/ARQUITECTURA_DISTRIBUIDA.md",
    ],
    "SERVERLESS": ["docs/18-plataformas/SERVERLESS.md"],
    "SISTEMA_DISTRIBUIDO": [
        "docs/04-arquitectura/ARQUITECTURA_DISTRIBUIDA.md",
        "docs/04-arquitectura/RESILIENCIA.md",
    ],
    "CLOUD": ["docs/04-arquitectura/ARQUITECTURA_CLOUD.md"],
    "HIBRIDO": ["docs/04-arquitectura/ARQUITECTURA_CLOUD.md"],
    "MULTICLOUD": ["docs/04-arquitectura/ARQUITECTURA_CLOUD.md"],
    "PRODUCCION_24X7": [
        "docs/11-observabilidad/ESTRATEGIA_OBSERVABILIDAD.md",
        "docs/14-operaciones/GUIA_OPERATIVA.md",
        "docs/14-operaciones/RECUPERACION_DESASTRES.md",
    ],
    "ALTA_CRITICIDAD": [
        "docs/04-arquitectura/RESILIENCIA.md",
        "docs/14-operaciones/PRACTICAS_SRE.md",
        "docs/14-operaciones/RECUPERACION_DESASTRES.md",
    ],
}

SUSPICIOUS_PHRASES = [
    "Rendering strategy:",
    "State ownership:",
    "API client boundaries:",
    "Funcionalidad/module",
    "Need for datosbase",
    "Triggers, stages, quality gates",
    "Manual/semi-automatic/automatic",
    "Startup, shutdown, health verification",
    "Critical dependencies, RPO, RTO",
]


def add(results: list[tuple[str, str]], level: str, message: str) -> None:
    results.append((level, message))


def markdown_files() -> list[Path]:
    return sorted(ROOT.rglob("*.md"))


def check_essential(results: list[tuple[str, str]]) -> None:
    for rel in ESSENTIAL_FILES:
        if not (ROOT / rel).exists():
            add(results, "ERROR", f"Falta archivo esencial: {rel}")


def check_version(results: list[tuple[str, str]]) -> None:
    version_file = ROOT / "VERSION"
    readme = ROOT / "README.md"
    if not version_file.exists() or not readme.exists():
        return
    version = version_file.read_text(encoding="utf-8").strip()
    readme_text = readme.read_text(encoding="utf-8")
    if version not in readme_text:
        add(results, "ERROR", f"README.md no declara la version {version}.")


def check_manifest(results: list[tuple[str, str]]) -> None:
    manifest = ROOT / "MANIFIESTO_FRAMEWORK.md"
    if not manifest.exists():
        add(results, "ERROR", "Falta MANIFIESTO_FRAMEWORK.md.")
        return
    text = manifest.read_text(encoding="utf-8", errors="replace")
    match = re.search(r"Total de archivos:\s*\*\*(\d+)\*\*", text)
    if not match:
        add(results, "ADVERTENCIA", "El manifiesto no declara el total de archivos.")
        return
    declared = int(match.group(1))
    actual = sum(1 for p in ROOT.rglob("*") if p.is_file())
    if declared != actual:
        add(
            results,
            "ERROR",
            f"MANIFIESTO_FRAMEWORK.md declara {declared} archivos, pero existen {actual}.",
        )


def check_classification(results: list[tuple[str, str]]) -> None:
    for path in sorted((ROOT / "docs").rglob("*.md")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "**Clasificación:**" not in text:
            add(
                results,
                "ADVERTENCIA",
                f"Sin clasificacion explicita: {path.relative_to(ROOT)}",
            )


def is_external_link(target: str) -> bool:
    lowered = target.lower()
    return (
        lowered.startswith("http://")
        or lowered.startswith("https://")
        or lowered.startswith("mailto:")
        or lowered.startswith("#")
        or lowered.startswith("data:")
    )


def normalize_target(target: str) -> str:
    target = target.strip().split("#", 1)[0]
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    return target


def check_links(results: list[tuple[str, str]]) -> None:
    pattern = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
    for path in markdown_files():
        text = path.read_text(encoding="utf-8", errors="replace")
        for raw in pattern.findall(text):
            target = normalize_target(raw)
            if not target or is_external_link(target):
                continue
            if " " in target and not target.startswith("../") and not target.startswith("./"):
                # Likely a title or unusual Markdown destination; avoid false positives.
                continue
            candidate = (path.parent / target).resolve()
            try:
                candidate.relative_to(ROOT.resolve())
            except ValueError:
                continue
            if not candidate.exists():
                add(
                    results,
                    "ERROR",
                    f"Enlace local roto en {path.relative_to(ROOT)} -> {raw}",
                )


def check_language_markers(results: list[tuple[str, str]]) -> None:
    allowed = {
        ROOT / "docs/00-gobierno/VALIDACION_DOCUMENTAL.md",
    }
    for path in markdown_files():
        if path in allowed:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for phrase in SUSPICIOUS_PHRASES:
            if phrase in text:
                add(
                    results,
                    "ADVERTENCIA",
                    f"Marcador heredado/no traducido en {path.relative_to(ROOT)}: {phrase}",
                )
        if re.search(r"\[\s*YES\s*\|\s*NO(?:\s*\|\s*UNDECIDED)?\s*\]", text):
            add(
                results,
                "ADVERTENCIA",
                f"Selector YES/NO heredado en {path.relative_to(ROOT)}.",
            )


def selected_profiles(master_text: str) -> set[str]:
    selected: set[str] = set()
    for line in master_text.splitlines():
        match = re.search(r"-\s*\[[xX]\]\s*`([^`]+)`", line)
        if match:
            selected.add(match.group(1).strip())
    return selected


def check_profile_activation(results: list[tuple[str, str]]) -> None:
    master = ROOT / "PROYECTO_MAESTRO.md"
    if not master.exists():
        return
    text = master.read_text(encoding="utf-8")
    for profile in sorted(selected_profiles(text)):
        for rel in PROFILE_REQUIREMENTS.get(profile, []):
            if not (ROOT / rel).exists():
                add(
                    results,
                    "ERROR",
                    f"Perfil {profile} activado pero falta {rel}.",
                )


def check_pending_decisions(
    results: list[tuple[str, str]], strict: bool
) -> None:
    if not strict:
        return
    master = ROOT / "PROYECTO_MAESTRO.md"
    if not master.exists():
        return
    text = master.read_text(encoding="utf-8")
    count = len(re.findall(r"\bPOR_DECIDIR\b", text))
    if count:
        add(
            results,
            "ADVERTENCIA",
            f"PROYECTO_MAESTRO.md contiene {count} referencia(s) a POR_DECIDIR.",
        )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Valida la coherencia estructural basica del framework doc."
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Incluye advertencias de decisiones pendientes del proyecto.",
    )
    args = parser.parse_args()

    results: list[tuple[str, str]] = []
    check_essential(results)
    check_version(results)
    check_manifest(results)
    check_classification(results)
    check_links(results)
    check_language_markers(results)
    check_profile_activation(results)
    check_pending_decisions(results, args.strict)

    order = {"ERROR": 0, "ADVERTENCIA": 1, "INFO": 2}
    results.sort(key=lambda item: (order[item[0]], item[1]))

    if results:
        for level, message in results:
            print(f"[{level}] {message}")
    else:
        print("[OK] No se encontraron problemas estructurales.")

    errors = sum(1 for level, _ in results if level == "ERROR")
    warnings = sum(1 for level, _ in results if level == "ADVERTENCIA")
    print(f"Resumen: {errors} error(es), {warnings} advertencia(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
