#!/usr/bin/env bash
set -euo pipefail

echo "Running test placeholder."
echo "Customize this script for your stack."

if [ -f package.json ]; then
  if npm run | grep -q " test"; then
    npm test
  fi
fi

if [ -d tests ]; then
  if command -v pytest >/dev/null 2>&1; then
    pytest
  else
    echo "pytest not installed; skipping Python tests."
  fi
fi
