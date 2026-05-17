#!/usr/bin/env bash
set -euo pipefail

echo "Running local security placeholder."
echo "Customize this script for your tooling."

echo "Checking for obvious committed secret file names..."
if find . -type f \( -name "*.pem" -o -name "*.key" -o -name ".env" \) \
  -not -path "./.git/*" \
  -not -name ".env.example" | grep -q .; then
  echo "Potential secret file detected."
  find . -type f \( -name "*.pem" -o -name "*.key" -o -name ".env" \) \
    -not -path "./.git/*" \
    -not -name ".env.example"
  exit 1
fi

if command -v gitleaks >/dev/null 2>&1; then
  gitleaks detect --source . --redact
else
  echo "gitleaks not installed; skipping secret scan."
fi

if command -v trivy >/dev/null 2>&1; then
  trivy fs .
else
  echo "trivy not installed; skipping vulnerability scan."
fi
