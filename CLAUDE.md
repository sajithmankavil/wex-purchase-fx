# Claude Code Operating Rules for This Project

You are working inside an enterprise-grade software delivery process.

Your job is to help build a production-ready system from the requirement document while preserving safety, traceability, testability, and operational quality.

## 1. Core behavior

- Do not implement code until requirements have been analyzed and an implementation plan exists.
- Do not work directly on `main` or `master`.
- Do not make broad unrelated edits.
- Do not remove tests, weaken validation, or bypass security controls to make work pass.
- Do not introduce new runtime dependencies without documenting why.
- Do not modify production deployment files without calling it out explicitly.
- Do not create or commit secrets, tokens, credentials, private keys, cookies, or `.env` files.
- Ask for clarification only when blocked. Otherwise, document assumptions and proceed conservatively.
- Prefer small, reviewable changes over large rewrites.

## 2. Required workflow

For every meaningful task:

1. Restate the goal.
2. Identify requirement references.
3. List assumptions and open questions.
4. Propose design or implementation approach.
5. List files expected to change.
6. Define tests to add or update.
7. Implement only the approved/specified scope.
8. Run quality checks.
9. Summarize results, risks, and follow-ups.



## 2A. Mandatory planning, grill, and design gates

This project uses enforced phase gates. Do not skip them.

### Gate 1: Requirements ingestion
Before design or implementation, create/update:

```text
docs/requirements/functional-requirements.md
docs/requirements/non-functional-requirements.md
docs/requirements/acceptance-criteria.md
docs/requirements/assumptions-and-open-questions.md
docs/requirements/traceability-matrix.md
docs/planning/requirements-grill.md
```

The requirements grill must challenge the requirement document. It must identify contradictions, vague terms, missing enterprise constraints, implied integrations, data ownership, access control assumptions, scale assumptions, compliance risks, operational gaps, and acceptance-test gaps.

### Gate 2: Design session
Before implementation, run a design session and create/update:

```text
docs/planning/design-session.md
docs/architecture/system-context.md
docs/architecture/component-design.md
docs/architecture/data-model.md
docs/architecture/api-contracts.md
docs/architecture/deployment-architecture.md
docs/architecture/adr-0001-core-architecture.md
```

The design session must include options considered, tradeoffs, rejected alternatives, deployment model, data model, API boundaries, security model, observability model, failure modes, rollback approach, and operational ownership.

### Gate 3: Design grill / adversarial review
Before implementation, run an adversarial design grill and create/update:

```text
docs/planning/design-grill.md
docs/security/threat-model.md
docs/operations/observability.md
docs/operations/rollback-plan.md
```

The design grill must attack the design as a principal engineer, security reviewer, SRE, tester, and auditor. It must list P0/P1/P2 findings. Implementation is blocked while any P0 or unresolved P1 exists.



### Gate 3A: Observability, reliability, scalability, and operations design
Before implementation, run an operational design session and create/update:

```text
docs/planning/operational-design-session.md
docs/planning/reliability-scalability-grill.md
docs/operations/service-catalog.md
docs/operations/observability.md
docs/operations/monitoring-alerting.md
docs/operations/slo-sli.md
docs/operations/error-budget-policy.md
docs/operations/capacity-scalability-plan.md
docs/operations/failure-modes-and-resilience.md
docs/operations/runbook.md
docs/operations/oncall-escalation.md
docs/operations/operational-readiness-gate.md
```

This gate must define concrete SLIs/SLOs, golden signals, dashboards, alert conditions, runbooks, owner/escalation paths, health checks, synthetic checks, log/trace correlation, capacity assumptions, load-test plan, failure-mode handling, rollback signals, and operational ownership.

Claude Code must not declare the project ready for implementation if observability, reliability, scalability, and operational process are vague, missing, or not testable.



### Gate 3B: PCI DSS Tier 1 security design and compliance gate
This project is configured for PCI DSS Tier 1 posture by default via `security-profile.yml`.

Before implementation, run a PCI security design session and adversarial PCI security grill. Create/update:

```text
docs/security/pci-scope-and-cde.md
docs/security/pci-dss-control-matrix.md
docs/security/cardholder-data-flow.md
docs/security/cardholder-data-classification.md
docs/security/tokenization-and-pan-handling.md
docs/security/encryption-key-management.md
docs/security/network-segmentation.md
docs/security/access-control-pci.md
docs/security/logging-monitoring-pci.md
docs/security/secure-sdlc-pci.md
docs/security/vulnerability-management-pci.md
docs/security/change-control-pci.md
docs/security/third-party-service-provider-pci.md
docs/security/evidence-register.md
docs/security/pci-security-grill.md
```

The PCI grill must challenge CDE scope, PAN/SAD flows, storage decisions, tokenization, logging redaction, access control, network segmentation, key management, vulnerability management, incident response, service-provider responsibility, and QSA/ROC evidence readiness.

Implementation/runtime/infrastructure edits are blocked until a human manually creates:

```text
.human-approvals/pci-security-approved.txt
```

with exact content:

```text
APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION
```

Claude Code must not create or edit this approval marker.

Production-sensitive changes and production deployment are blocked until a human manually creates:

```text
.human-approvals/pci-production-approved.txt
```

with exact content:

```text
APPROVED_FOR_PCI_PRODUCTION_RELEASE
```

No production release may be considered ready unless `make pci-check`, `make ops-check`, and all release/security checks pass.

### Gate 4: Implementation readiness
Before code changes outside documentation, create/update:

```text
docs/planning/implementation-readiness-gate.md
```

The readiness gate must state one of:

```text
Status: BLOCKED
Status: CONDITIONALLY_READY
Status: READY_FOR_HUMAN_APPROVAL
```

Claude Code may not mark itself approved for implementation. Human approval is external to Claude Code.

### Human approval marker
Implementation is blocked until a human manually creates this file outside Claude Code:

```text
.human-approvals/implementation-approved.txt
```

The file must contain:

```text
APPROVED_FOR_IMPLEMENTATION
```

Claude Code must not create, edit, or request permission to bypass this file. If it is absent, continue planning/design/review only.


## 3. Requirements-first rule

The source requirement belongs in:

```text
docs/requirements/source-requirements.md
```

Before coding, create or update:

```text
docs/requirements/functional-requirements.md
docs/requirements/non-functional-requirements.md
docs/requirements/acceptance-criteria.md
docs/requirements/assumptions-and-open-questions.md
docs/requirements/traceability-matrix.md
```

Every feature should map back to acceptance criteria.

## 4. Architecture rule

Before major implementation, create or update:

```text
docs/architecture/system-context.md
docs/architecture/component-design.md
docs/architecture/data-model.md
docs/architecture/api-contracts.md
docs/architecture/deployment-architecture.md
docs/architecture/adr-0001-core-architecture.md
```

Use ADRs for important decisions.

An ADR must include:

- Context
- Decision
- Options considered
- Consequences
- Rollback or migration notes

## 5. Testing rule

Every behavior change needs appropriate tests.

Minimum expected test layers:

- Unit tests
- Integration tests
- Contract/API tests where applicable
- Negative/error-path tests
- Security-relevant tests
- Migration tests where schema changes are involved
- Smoke tests for deployment

Do not mark work complete unless you report which tests were run.

## 6. Security rule

Treat all external input as hostile.

Required security practices:

- Server-side authorization checks
- Input validation
- Output encoding where relevant
- Least-privilege access
- No secrets in code
- Dependency risk review
- Audit logging for important actions
- Safe error handling without leaking internals

Before production readiness, update:

```text
docs/security/threat-model.md
docs/security/secrets-policy.md
docs/security/authn-authz-design.md
docs/security/dependency-risk-policy.md
```



## 6A. PCI DSS Tier 1 security rule

This bundle assumes a Tier 1 PCI posture until explicitly downgraded by a qualified compliance owner. Treat any component that stores, processes, transmits, secures, administers, monitors, deploys, or can impact cardholder data as in-scope or connected-to until proven otherwise.

Hard rules:

- Do not store PAN unless explicitly justified, encrypted, access-controlled, logged, retained only as required, and reviewed for PCI scope.
- Never store sensitive authentication data after authorization.
- Never log PAN, CVV/CVC, track data, PIN/PIN block, tokens, secrets, cookies, or raw payment payloads.
- Prefer tokenization, hosted fields, payment-provider vaulting, network tokens, and scope reduction.
- Every CDE path must have network segmentation, least-privilege access, MFA for admin access, audit logging, monitoring, and evidence.
- Every PCI DSS Req 1-12 control must have owner, status, evidence, and test method.
- Every production-bound change must have security review, change-control evidence, rollback plan, and monitoring evidence.
- ASV scan plan, vulnerability remediation process, penetration/segmentation testing plan, incident response, and QSA/ROC readiness must be documented before production.

Required command before implementation:

```text
make pci-check
```

Required production artifacts:

```text
docs/security/pci-production-readiness-gate.md
docs/security/qsa-roc-readiness.md
docs/security/asv-scan-plan.md
docs/security/penetration-test-plan.md
docs/security/incident-response-pci.md
docs/security/evidence-register.md
```

## 7. Operations rule

Production is not complete without operations.

Before production readiness, update:

```text
docs/operations/runbook.md
docs/operations/observability.md
docs/operations/incident-response.md
docs/operations/rollback-plan.md
docs/operations/slo-sli.md
```

Every service must define:

- Service owner, business criticality, upstream/downstream dependencies
- Health checks: liveness, readiness, dependency health, startup behavior
- Structured logs with request/trace/correlation IDs and safe redaction
- Metrics: RED metrics for request-driven services and USE metrics for resource pools
- Distributed tracing across external calls, queues, jobs, and critical workflows
- Alerts tied to user impact, SLO burn rate, saturation, dependency failure, and data correctness
- Dashboards for service health, dependency health, rollout health, and business-critical workflows
- Failure modes, degradation behavior, retry/timeouts/circuit-breakers, idempotency, backpressure, and bulkheads
- Capacity assumptions, expected load, peak load, data growth, concurrency limits, and scale strategy
- Load/performance test plan and minimum passing criteria
- Incident process, severity levels, on-call/escalation, runbook, and post-incident review
- Rollback method, rollback triggers, and post-rollback validation
- Audit-log retention ≥ 1 year, most recent 3 months online; append-only / signed / WORM in production; access to the audit log is itself audit-logged and reviewed quarterly. This is a Phase-5 / Phase-7 deliverable, not optional. (Closes Phase-2 grill G-P1-6 / OQ-022.)

## 8. Release rule

Release is blocked unless operational readiness is complete. Production readiness must include:

- SLO/SLI definitions with measurable targets
- Monitoring and alerting review
- Dashboard links or dashboard specifications
- Load/performance test evidence or an explicit approved waiver
- Rollback and incident-response evidence
- Operational owner and escalation path
- Capacity and dependency-risk review


A production release requires:

```text
docs/release/release-checklist.md
docs/release/production-readiness-review.md
docs/release/release-notes-template.md
```

Production release must have:

- Passing CI
- Passing security checks
- Passing staging smoke tests
- Human approval
- Rollback plan
- Monitoring plan

## 9. Completion summary format

At the end of every task, respond with:

```text
Summary:
- ...

Files changed:
- ...

Tests run:
- ...

Requirement coverage:
- ...

Risks:
- ...

Follow-ups:
- ...
```

## 10. If this is a trading, financial, healthcare, legal, or other high-risk system

> Note: this project's PCI Tier-1 posture (`security-profile.yml`) and currency-conversion domain place it under §10 as a high-risk financial system. The rules below are in force by default; no separate decision is required.

Use stricter controls:

- Separate recommendation from execution.
- Default to read-only integrations.
- Require human approval for irreversible actions.
- Create audit logs for every decision.
- Add kill switches and hard risk limits outside the agent.
- Never expose production credentials to Claude Code.
