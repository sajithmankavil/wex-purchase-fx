# Planning Gates

This folder contains the enforced planning and design-control records.

Implementation must not begin until:

1. Requirements ingestion is complete.
2. Requirements grill is complete.
3. Design session is complete.
4. Design grill is complete.
5. Implementation readiness says `READY_FOR_HUMAN_APPROVAL`.
6. A human manually creates `.human-approvals/implementation-approved.txt` with `APPROVED_FOR_IMPLEMENTATION`.

Claude Code must not create the human approval file.
