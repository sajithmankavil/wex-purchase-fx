#!/usr/bin/env bash
set -euo pipefail

echo "Running typecheck placeholder."
echo "Customize this script for your stack."

if [ -f package.json ]; then
  if npm run | grep -q " typecheck"; then
    npm run typecheck
  fi
fi

if [ -f pyproject.toml ]; then
  if command -v mypy >/dev/null 2>&1; then
    mypy .
  else
    echo "mypy not installed; skipping Python typecheck."
  fi
fi
