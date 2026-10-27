#!/usr/bin/env bash
# Verifies Kotlin import statements match Android Studio's gold-standard format (.editorconfig).
#
# Import rules:
#   1. Group 1: Regular & project imports (alphabetical, including kotlinx.*)
#   2. Group 2: java.* and javax.* (alphabetical)
#   3. Group 3: kotlin.* (alphabetical)
#   4. Group 4: Import aliases (`as ...`, alphabetical)
#   5. No blank lines inside the import block
#   6. No wildcard imports (`import foo.*`)
#
# Usage:
#   scripts/check-kotlin-imports.sh [--fix] [file.kt ...]
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

python3 - "$@" <<'PY'
import sys
import os
import re

args = sys.argv[1:]
fix_mode = False
if "--fix" in args:
    fix_mode = True
    args.remove("--fix")

if args:
    files = [f for f in args if f.endswith(".kt") or f.endswith(".kts")]
else:
    # Scan all tracked .kt and .kts files
    import subprocess
    try:
        out = subprocess.check_output(["git", "ls-files", "--", "*.kt", "*.kts"]).decode("utf-8")
        files = [line.strip() for line in out.splitlines() if line.strip()]
    except Exception:
        files = []
        for root, dirs, f_list in os.walk("."):
            if any(x in root for x in ["/build", "/.gradle", "/node_modules"]):
                continue
            for f in f_list:
                if f.endswith(".kt") or f.endswith(".kts"):
                    files.append(os.path.join(root, f))

def group_key(imp_line):
    # imp_line: "import foo.bar" or "import foo as bar"
    stmt = imp_line[len("import "):].strip()
    is_alias = " as " in stmt
    if is_alias:
        return (4, stmt)
    if stmt.startswith("java.") or stmt.startswith("javax."):
        return (2, stmt)
    if stmt.startswith("kotlin."):
        return (3, stmt)
    return (1, stmt)

has_error = False

for path in files:
    if not os.path.isfile(path):
        continue
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    lines = content.splitlines()
    
    # Locate import block
    first_import_idx = -1
    last_import_idx = -1
    imports = []
    
    # Find all import lines before the main body
    for idx, line in enumerate(lines):
        trimmed = line.strip()
        if trimmed.startswith("import "):
            if first_import_idx == -1:
                first_import_idx = idx
            last_import_idx = idx
            imports.append(trimmed)
        elif first_import_idx != -1 and last_import_idx != -1:
            # Check if we have passed the import section
            # The import section ends when a non-empty, non-comment, non-import line is reached
            if trimmed != "" and not trimmed.startswith("//") and not trimmed.startswith("/*") and not trimmed.startswith("*"):
                break

    if not imports:
        continue

    # Check for wildcards
    wildcards = [imp for imp in imports if imp.endswith(".*")]
    if wildcards:
        has_error = True
        print(f"{path}: Wildcard import not allowed:", file=sys.stderr)
        for w in wildcards:
            print(f"  {w}", file=sys.stderr)

    # Check for empty lines inside imports block
    has_empty_lines = False
    for i in range(first_import_idx, last_import_idx + 1):
        if lines[i].strip() == "":
            has_empty_lines = True
            break

    sorted_imports = sorted(imports, key=group_key)
    is_sorted = (imports == sorted_imports)

    if not is_sorted or has_empty_lines:
        has_error = True
        if fix_mode:
            # Reconstruct file with clean sorted imports
            before_imports = lines[:first_import_idx]
            after_imports = lines[last_import_idx + 1:]
            new_lines = before_imports + sorted_imports + after_imports
            with open(path, "w", encoding="utf-8") as f:
                f.write("\n".join(new_lines) + ("\n" if content.endswith("\n") else ""))
            print(f"Fixed imports in: {path}")
        else:
            print(f"{path}: Import ordering mismatch or empty lines in import block", file=sys.stderr)
            if has_empty_lines:
                print("  Found empty line(s) inside import block", file=sys.stderr)
            if not is_sorted:
                for orig, expected in zip(imports, sorted_imports):
                    if orig != expected:
                        print(f"  Expected: {expected}", file=sys.stderr)
                        print(f"  Found:    {orig}", file=sys.stderr)
                        break

if has_error and not fix_mode:
    sys.exit(1)
PY
