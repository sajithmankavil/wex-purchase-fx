#!/usr/bin/env bash
set -euo pipefail

# Test driver.
#
# Stack detection priority:
#   1. Java/Maven (pom.xml present) — runs `mvn verify` for unit + integration tests + JaCoCo + ArchUnit.
#   2. Node (package.json + a "test" script).
#   3. Python (pyproject.toml + pytest installed).
#
# Closes assessor MUST-DO finding B2: `mvn verify` is now invoked when a Java
# project is detected; the prior placeholder left Java tests unrun in CI.

if [ -f pom.xml ]; then
  echo "Detected Java/Maven project — running mvn verify."
  if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: pom.xml present but 'mvn' is not on PATH." >&2
    echo "Install Maven 3.9+ and Java 21, or fix the runner image, then re-run." >&2
    exit 1
  fi
  exec mvn -B -ntp verify
fi

if [ -f package.json ]; then
  if npm run 2>/dev/null | grep -q " test"; then
    echo "Detected Node project with test script — running npm test."
    exec npm test
  fi
fi

if [ -f pyproject.toml ] || [ -f requirements.txt ]; then
  if command -v pytest >/dev/null 2>&1; then
    echo "Detected Python project — running pytest."
    exec pytest
  fi
fi

echo "No test stack detected (no pom.xml / package.json / pyproject.toml). Nothing to run."
