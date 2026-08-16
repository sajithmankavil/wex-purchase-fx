# Prompt: Implement Cardholder Benefit Eligibility Endpoint

```text
Implement the cardholder benefit eligibility endpoint per docs/eligibility-endpoint-spec.md.
That spec is the source of truth for contract, business rules, and NFRs — read it in full
before writing code. Do not re-derive decisions it already made; if you hit a genuine gap
the spec doesn't cover, stop and flag it rather than silently guessing.

Scope (branch: feat/benefit-eligibility-endpoint):

1. Domain layer
   - CardTier: PLATINUM < SIGNATURE < INFINITE (spec §3.1, §5 blocking #2 — this ordering
     is locked in for this exercise, treat it as fact, not something to re-derive).
   - BenefitEligibility value type: benefitId + minimumTier.
   - Pure eligibility comparison (tier >= minimumTier, inclusive boundary) — no Spring, no
     I/O, matches this codebase's existing domain-layer purity convention (see
     RateSelectionPolicy as the pattern to follow: stateless, static, fully unit-testable
     without mocks).

2. Persistence
   - Liquibase migration adding a benefit_eligibility table (benefit_id PK, minimum_tier,
     updated_at) — spec §3.2. Seed a handful of rows for local/test use; no admin API
     (spec §5 scope-affecting #4 — out of scope for v1).
   - Repository port + adapter following this codebase's existing port/adapter pattern
     (see PurchaseRepositoryPort / PurchaseRepoAdapter for the shape to match).

3. In-memory cache + background refresh (spec §4.1, §4.3)
   - Load the full table into memory on startup; periodic background refresh (e.g. every
     5 min) replaces the snapshot atomically — no torn reads during a swap (this needs a
     dedicated test, see below).
   - Readiness probe must NOT report UP until the first load succeeds (fail closed on cold
     start with the DB unreachable) — follow the existing health-indicator pattern
     (DbPoolHeadroomHealthIndicator / GatewayRequiredHealthIndicator are the examples).
   - If a background refresh fails after a successful initial load, keep serving the last
     known-good snapshot and log at WARN — never fail live requests over it. Match the
     WarmupApplicationListener's failure-tolerant style, but note it's a different concern
     (that class does Treasury warm-up, don't couple to it).

4. Application layer
   - EligibilityService (or equivalent use case) orchestrating the lookup. Keep it a pure
     POJO per this codebase's hexagonal convention — no Spring annotations, no logging
     framework imports here. IMPORTANT gotcha: ArchUnit enforces that the application
     package may only depend on application/domain/java.* — do NOT add an SLF4J logger to
     this layer (we hit this exact violation and had to back it out during an earlier
     round of this drill). If audit logging needs SLF4J, it belongs in the api or
     infrastructure layer, not here.
   - Unknown tier → reject before reaching eligibility lookup (spec §3.3) — this is a
     request-validation concern, not a domain concern.
   - Unknown benefitId → surface as a distinct outcome the API layer maps to 404
     (spec §3.3) — do not conflate with eligible:false.

5. API layer
   - GET /api/v1/benefits/{benefitId}/eligibility?tier={tier} (spec §2 — exact contract,
     including the 200/400/404/500 response shapes and errorCode values, is not open for
     reinterpretation).
   - Errors via this repo's existing RFC 9457 Problem Details convention
     (ProblemDetailExceptionHandler is the pattern — add new errorCode entries, don't
     invent a new error shape).
   - Correlation ID: reuse the existing CorrelationIdFilter convention, don't build a new one.
   - Audit logging: one structured log line per call with benefitId, tier, eligible,
     correlationId, latency — explicitly NO cardholder/session/PII field (spec §3.4,
     §5 scope-affecting #1). This absence is a regression-testable requirement, not just
     a design note — write a test that asserts no PII field is present in the log line.
   - Auth: out of scope for this implementation pass — spec §5 scope-affecting #3 makes
     this a hard milestone before the endpoint reaches any non-test environment, but for
     this drill we are implementing the endpoint logic only. Do not add auth here; do not
     let its absence block this PR. Note it explicitly in the PR description as a known
     pre-production gap.

6. Tests (spec §7 — all of these, not a subset)
   - Domain: full tier x minimum-tier boundary table, unknown-tier rejection.
   - Controller: every status/errorCode combination in spec §2, correlation-ID behavior,
     exact response-shape regression test.
   - Integration (Testcontainers Postgres, matching this repo's existing IT pattern):
     migration + load + HTTP path end-to-end; refresh reflects DB changes only after the
     next cycle, not before; cold-start-with-DB-down keeps readiness DOWN; refresh failure
     after a good initial load keeps serving the last snapshot.
   - Concurrent-refresh test (spec §7, just added): hammer the endpoint with requests while
     a background refresh swaps the snapshot; assert no torn reads and no request-visible
     stall.
   - Audit-log test: assert the log line's field set exactly, and explicitly assert no
     PII/cardholder field is present.

Constraints:
- Keep the diff scoped to this feature — don't refactor unrelated code.
- Run mvn test at the end and report the actual pass/fail/skip counts, not a guess.
- Follow this repo's existing package layout (domain / application / infrastructure / api /
  observability) rather than inventing a new structure.
- Gates (docs/requirements, design-grill, .human-approvals marker) are explicitly bypassed
  for this drill per prior agreement — do not block on them, do not ask for the approval
  marker. Test discipline is NOT bypassed — every item in the Tests section above is required.
- Follow CLAUDE.md's completion-summary format when done.
```
