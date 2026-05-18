#!/usr/bin/env bash
set -euo pipefail

# Typecheck driver.
#
# Java is statically typed at compile time; lint.sh already runs `mvn compile`
# which IS the typecheck for Java. This script is a no-op for Java projects to
# avoid double-compilation; kept as a stack-portable hook for Node/Python.

if [ -f pom.xml ]; then
  echo "Java/Maven project — typecheck performed by 'mvn compile' in the lint step. No-op here."
  exit 0
fi

if [ -f package.json ]; then
  if npm run 2>/dev/null | grep -q " typecheck"; then
    echo "Detected Node project with typecheck script — running npm run typecheck."
    exec npm run typecheck
  fi
fi

if [ -f pyproject.toml ]; then
  if command -v mypy >/dev/null 2>&1; then
    echo "Detected Python project — running mypy."
    exec mypy .
  else
    echo "mypy not installed; skipping Python typecheck."
  fi
fi
