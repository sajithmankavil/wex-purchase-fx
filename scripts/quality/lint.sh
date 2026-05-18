#!/usr/bin/env bash
set -euo pipefail

# Lint driver.
#
# For Java projects, Maven runs the compile step which performs the strict
# syntax + import checks. Dedicated style enforcement (Checkstyle / Spotless)
# would be wired here if the project adopts them; today the strict compile
# is the lint gate.

if [ -f pom.xml ]; then
  if command -v mvn >/dev/null 2>&1; then
    echo "Detected Java/Maven project — running mvn compile (strict syntax + import check)."
    exec mvn -B -ntp -q compile
  else
    echo "WARN: pom.xml present but 'mvn' is not on PATH; skipping Java lint." >&2
  fi
fi

if [ -f package.json ]; then
  if npm run 2>/dev/null | grep -q " lint"; then
    echo "Detected Node project with lint script — running npm run lint."
    exec npm run lint
  fi
fi

if [ -f pyproject.toml ]; then
  if command -v ruff >/dev/null 2>&1; then
    echo "Detected Python project — running ruff."
    exec ruff check .
  else
    echo "ruff not installed; skipping Python lint."
  fi
fi
