# Implementation Readiness Gate

Status: BLOCKED

## Purpose

This gate determines whether implementation may begin. Claude Code cannot approve itself.

## Required evidence

### Requirements
- [ ] Source requirements present
- [ ] Functional requirements complete
- [ ] Non-functional requirements complete
- [ ] Acceptance criteria complete
- [ ] Traceability matrix started
- [ ] Requirements grill complete

### Design
- [ ] Design session complete
- [ ] System context complete
- [ ] Component design complete
- [ ] Data model complete
- [ ] API contracts complete, if applicable
- [ ] Deployment architecture complete
- [ ] ADRs created for major decisions

### Review
- [ ] Design grill complete
- [ ] No unresolved P0 findings
- [ ] P1 findings resolved or explicitly accepted by the human
- [ ] Security model reviewed
- [ ] Operations model reviewed
- [ ] Test strategy reviewed

### Implementation plan
- [ ] Milestones defined
- [ ] First implementation slice is small and testable
- [ ] Files expected to change are listed
- [ ] Tests required are listed
- [ ] Rollback plan exists for risky changes

## Readiness decision

Use exactly one:

```text
Status: BLOCKED
Status: CONDITIONALLY_READY
Status: READY_FOR_HUMAN_APPROVAL
```

## Human approval requirement

Even if this file says `READY_FOR_HUMAN_APPROVAL`, implementation remains blocked until a human manually creates:

```text
.human-approvals/implementation-approved.txt
```

with this exact content:

```text
APPROVED_FOR_IMPLEMENTATION
```

Claude Code must not create or edit that file.

## Decision notes

- Decision:
- Remaining risks:
- Human approvals needed:
- First implementation milestone:
