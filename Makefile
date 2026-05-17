SHELL := /bin/bash

.PHONY: help setup lint typecheck test test-unit test-integration security pci-check ci docs-check ops-check

help:
	@echo "Available targets:"
	@echo "  make setup              Install local tooling, customize per stack"
	@echo "  make lint               Run lint checks"
	@echo "  make typecheck          Run type checks"
	@echo "  make test               Run all tests"
	@echo "  make security           Run local security checks"
	@echo "  make pci-check          Run PCI Tier 1 readiness gate"
	@echo "  make ci                 Run local CI approximation"
	@echo "  make docs-check         Check required enterprise docs exist"
	@echo "  make ops-check          Check operational readiness artifacts"

setup:
	@echo "Customize setup for your stack."

lint:
	@bash scripts/quality/lint.sh

typecheck:
	@bash scripts/quality/typecheck.sh

test:
	@bash scripts/quality/test.sh

test-unit:
	@bash scripts/quality/test-unit.sh

test-integration:
	@bash scripts/quality/test-integration.sh

security:
	@bash scripts/security/local-security-check.sh

pci-check:
	@python3 scripts/security/pci_readiness_check.py

docs-check:
	@python3 scripts/quality/docs_check.py

ops-check:
	@python3 scripts/quality/operational_readiness_check.py

ci: docs-check ops-check pci-check lint typecheck test security
