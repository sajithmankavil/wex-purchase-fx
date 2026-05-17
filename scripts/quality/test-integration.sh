#!/usr/bin/env bash
set -euo pipefail

echo "Running integration test placeholder."
if [ -d tests/integration ] && command -v pytest >/dev/null 2>&1; then
  pytest tests/integration
fi
