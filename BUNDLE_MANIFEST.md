# Bundle Manifest

- `.claude/agents/architect.md`
- `.claude/agents/auditor.md`
- `.claude/agents/backend-implementer.md`
- `.claude/agents/devops-sre.md`
- `.claude/agents/frontend-implementer.md`
- `.claude/agents/product-analyst.md`
- `.claude/agents/release-manager.md`
- `.claude/agents/security-reviewer.md`
- `.claude/agents/test-engineer.md`
- `.claude/commands/create-architecture.md`
- `.claude/commands/implement-milestone.md`
- `.claude/commands/ingest-requirements.md`
- `.claude/commands/plan-milestone.md`
- `.claude/commands/production-readiness-review.md`
- `.claude/commands/review-pr.md`
- `.claude/hooks/post_edit_note.py`
- `.claude/hooks/pre_bash_guard.py`
- `.claude/hooks/protected_file_guard.py`
- `.claude/hooks/stop_summary_guard.py`
- `.claude/settings.json`
- `.env.example`
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-dev.yml`
- `.github/workflows/deploy-prod.yml`
- `.github/workflows/deploy-staging.yml`
- `.github/workflows/security.yml`
- `.gitignore`
- `CLAUDE.md`
- `CODEOWNERS`
- `Makefile`
- `README.md`
- `docs/architecture/adr-0001-core-architecture.md`
- `docs/architecture/api-contracts.md`
- `docs/architecture/component-design.md`
- `docs/architecture/data-model.md`
- `docs/architecture/deployment-architecture.md`
- `docs/architecture/system-context.md`
- `docs/operations/incident-response.md`
- `docs/operations/observability.md`
- `docs/operations/rollback-plan.md`
- `docs/operations/runbook.md`
- `docs/operations/slo-sli.md`
- `docs/release/production-readiness-review.md`
- `docs/release/release-checklist.md`
- `docs/release/release-notes-template.md`
- `docs/release/test-plan.md`
- `docs/requirements/acceptance-criteria.md`
- `docs/requirements/assumptions-and-open-questions.md`
- `docs/requirements/functional-requirements.md`
- `docs/requirements/non-functional-requirements.md`
- `docs/requirements/source-requirements.md`
- `docs/requirements/traceability-matrix.md`
- `docs/security/authn-authz-design.md`
- `docs/security/dependency-risk-policy.md`
- `docs/security/secrets-policy.md`
- `docs/security/security-review-findings.md`
- `docs/security/threat-model.md`
- `infra/README.md`
- `prompts/00-kickoff-requirements-ingestion.md`
- `prompts/01-architecture-package.md`
- `prompts/02-milestone-plan.md`
- `prompts/03-implement-milestone.md`
- `prompts/04-review-current-changes.md`
- `prompts/05-production-readiness.md`
- `prompts/optional-trading-system-guardrails.md`
- `scripts/quality/docs_check.py`
- `scripts/quality/lint.sh`
- `scripts/quality/test-integration.sh`
- `scripts/quality/test-unit.sh`
- `scripts/quality/test.sh`
- `scripts/quality/typecheck.sh`
- `scripts/security/local-security-check.sh`
- `tests/README.md`

## Added enforcement for planning/design/grill gates

- `docs/planning/requirements-grill.md`
- `docs/planning/design-session.md`
- `docs/planning/design-grill.md`
- `docs/planning/implementation-readiness-gate.md`
- `.claude/hooks/planning_phase_guard.py`
- `.human-approvals/README.md`
- Prompt and command files for requirements grill, design session, design grill, and implementation readiness
- Additional reviewer agents: planning grill reviewer and principal design reviewer

Implementation is blocked until the human approval marker is manually created outside Claude Code.


## v3 operational enforcement contents

Added:
- prompts/01c-operational-design-session.md
- prompts/01d-reliability-scalability-grill.md
- prompts/02b-operational-readiness-gate.md
- .claude/agents/observability-sre.md
- .claude/agents/reliability-scalability-reviewer.md
- .claude/agents/incident-commander.md
- docs/planning/operational-design-session.md
- docs/planning/reliability-scalability-grill.md
- docs/operations/service-catalog.md
- docs/operations/monitoring-alerting.md
- docs/operations/error-budget-policy.md
- docs/operations/capacity-scalability-plan.md
- docs/operations/failure-modes-and-resilience.md
- docs/operations/oncall-escalation.md
- docs/operations/operational-readiness-gate.md
- scripts/quality/operational_readiness_check.py
- .claude/hooks/operational_readiness_guard.py

Updated:
- CLAUDE.md
- Makefile
- .claude/settings.json
- .github/workflows/ci.yml
- .github/workflows/deploy-prod.yml
- prompts/05-production-readiness.md
- prompts/02a-implementation-readiness-gate.md


## v4 PCI Tier 1 enforcement additions

- `security-profile.yml` default PCI Tier 1 mode
- PCI security phase hook and production approval blocker
- `make pci-check` CI/local readiness gate
- PCI QSA-style reviewer, appsec architect, cloud security engineer, evidence auditor agents
- PCI security design, grill, and readiness prompts
- CDE scoping, data-flow, data-classification, tokenization/PAN-handling docs
- PCI DSS Req 1-12 control matrix and evidence register
- ASV, pen-test, segmentation-test, incident-response, QSA/ROC readiness templates
- Human-only approval markers for PCI security implementation and production release
