#!/usr/bin/env bash
set -euo pipefail

echo "Running unit test placeholder."
if [ -d tests/unit ] && command -v pytest >/dev/null 2>&1; then
  pytest tests/unit
fi
