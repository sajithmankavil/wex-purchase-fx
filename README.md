# Claude Code Enterprise Delivery Bundle

This bundle gives your Claude Code project a controlled, enterprise-style development and deployment model.

Use it by copying these files into a new repository or uploading/unzipping the bundle into your Claude Code project workspace. Then add your actual requirements into:

```text
docs/requirements/source-requirements.md
```

After that, start Claude Code with the kickoff prompt in:

```text
prompts/00-kickoff-requirements-ingestion.md
```

## What this bundle enforces

- Requirements-first development
- Architecture and ADR discipline
- Branch-based implementation
- Small milestone delivery
- Security and operational review
- CI/CD quality gates
- Human approval before production deployment
- Auditability through decision logs, runbooks, release notes, and acceptance criteria

## Recommended first command in Claude Code

```text
Read CLAUDE.md, docs/requirements/source-requirements.md, and prompts/00-kickoff-requirements-ingestion.md.

Do not code yet.

Follow the operating model in CLAUDE.md and produce the requirements analysis package.
```

## Folder layout

```text
.claude/
  agents/                 # Role-specific agent instructions
  commands/               # Reusable Claude Code slash-command style prompts
  hooks/                  # Guardrail scripts used by hooks
  settings.json           # Claude Code permissions and hooks
.github/workflows/        # CI/CD examples
docs/
  requirements/           # Requirement source + derived analysis
  architecture/           # Architecture docs and ADRs
  security/               # Threat model and security policy docs
  operations/             # Runbooks, observability, rollback
  release/                # Release readiness and evidence
prompts/                  # Human-friendly kickoff prompts
scripts/                  # Local quality/security helper scripts
```

## Important customization checklist

Before using this for a real production deployment, customize:

1. Runtime stack: Python, Node, Java, Go, etc.
2. Cloud platform: AWS, GCP, Azure, Kubernetes, ECS, etc.
3. Data stores and migration tooling.
4. Security scanners available in your environment.
5. Deployment strategy and approval process.
6. Secrets manager and environment configuration.
7. Compliance requirements.

## Non-negotiable rule

Claude Code may create plans, code, tests, PRs, reviews, and deployment templates.

Claude Code must not directly deploy to production or access production secrets without explicit human-controlled gates.


## Enforced planning and design workflow

This bundle now includes a hard planning-phase guard. Until a human creates `.human-approvals/implementation-approved.txt`, Claude Code is restricted to planning/design/security/operations/release documentation edits.

Required sequence:

```text
1. Requirements ingestion
2. Requirements grill
3. Design session
4. Design grill / adversarial review
5. Implementation readiness gate
6. Human approval marker
7. Implementation milestone work
```

After the readiness gate says `READY_FOR_HUMAN_APPROVAL`, create this file manually outside Claude Code:

```text
.human-approvals/implementation-approved.txt
```

with this exact content:

```text
APPROVED_FOR_IMPLEMENTATION
```

Claude Code is explicitly blocked from creating or editing that approval file.

Recommended first sequence in Claude Code:

```text
Read CLAUDE.md and prompts/00-kickoff-requirements-ingestion.md. Do not code. Complete requirements ingestion.
```

Then run:

```text
Read prompts/00a-requirements-grill.md. Do not code. Complete the requirements grill.
```

Then run:

```text
Read prompts/01a-design-session.md. Do not code. Complete the design session.
```

Then run:

```text
Read prompts/01b-design-grill.md. Do not code. Complete the design grill.
```

Then run:

```text
Read prompts/02a-implementation-readiness-gate.md. Do not code. Determine whether the project is ready for human approval.
```


## v3 Operational enforcement additions

This bundle now treats observability, reliability, scalability, and operations as release-blocking requirements.

Required planning sequence before implementation:

```text
1. Requirements ingestion
2. Requirements grill
3. Design session
4. Design grill
5. Operational design session
6. Reliability/scalability grill
7. Operational readiness gate
8. Implementation readiness gate
9. Human approval marker
```

Use these prompts before implementation:

```text
Read prompts/01c-operational-design-session.md. Do not code. Complete the operational design session.
```

```text
Read prompts/01d-reliability-scalability-grill.md. Do not code. Complete the reliability and scalability grill.
```

```text
Read prompts/02b-operational-readiness-gate.md. Do not code. Determine operational readiness.
```

CI now runs `make ops-check`. Once implementation is approved or application code exists, CI fails unless the required operational artifacts are complete and not placeholders.


## PCI DSS Tier 1 enforcement

This v4 bundle adds a PCI DSS Tier 1 security/compliance control layer. It assumes the project may store, process, transmit, or impact cardholder data until proven otherwise.

Additional required sequence:

```text
Read prompts/01e-pci-security-design-session.md. Do not code. Complete the PCI security design session.
```

```text
Read prompts/01f-pci-security-grill.md. Do not code. Complete the PCI adversarial security grill.
```

```text
Read prompts/02c-pci-security-readiness-gate.md. Do not code. Determine PCI security readiness.
```

Only after security readiness is acceptable should a human manually create `.human-approvals/pci-security-approved.txt` with `APPROVED_FOR_PCI_SECURITY_IMPLEMENTATION`.

Production release requires `.human-approvals/pci-production-approved.txt` with `APPROVED_FOR_PCI_PRODUCTION_RELEASE` after PCI production readiness, operational readiness, and release readiness are satisfied.
