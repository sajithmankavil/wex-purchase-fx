#!/usr/bin/env bash
set -euo pipefail

echo "Running lint placeholder."
echo "Customize this script for your stack."

if [ -f package.json ]; then
  if npm run | grep -q " lint"; then
    npm run lint
  fi
fi

if [ -f pyproject.toml ]; then
  if command -v ruff >/dev/null 2>&1; then
    ruff check .
  else
    echo "ruff not installed; skipping Python lint."
  fi
fi
