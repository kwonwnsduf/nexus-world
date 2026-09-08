import ast
from pathlib import Path

API_ROOT = Path(__file__).parents[1] / "app" / "api"
FORBIDDEN_PREFIXES = ("app.infrastructure", "app.providers", "app.persistence")


def test_api_adapters_do_not_import_infrastructure() -> None:
    violations: list[str] = []

    for module_path in API_ROOT.rglob("*.py"):
        tree = ast.parse(module_path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            names: list[str] = []
            if isinstance(node, ast.Import):
                names = [alias.name for alias in node.names]
            elif isinstance(node, ast.ImportFrom) and node.module:
                names = [node.module]
            for name in names:
                if name.startswith(FORBIDDEN_PREFIXES):
                    violations.append(f"{module_path.relative_to(API_ROOT)} imports {name}")

    assert not violations, "API boundary violations: " + ", ".join(violations)

