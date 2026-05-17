# Optional Prompt: Trading / Financial System Guardrails

Use this only if the project is a trading, financial-risk, broker, or decision-support system.

```text
Apply the stricter high-risk-system rules from CLAUDE.md.

Non-negotiables:
1. Separate signal/recommendation from execution.
2. Default to read-only integrations.
3. No live trading/broker execution in v1.
4. Use paper trading or simulation first.
5. Maintain a decision log for every signal.
6. Require reproducible replay/backtest evidence.
7. Add kill switch and hard risk limits outside the agent.
8. Never expose broker credentials to Claude Code.
9. Human approval required before any live mode.
10. Production deployment requires explicit readiness review.

Create or update:
- docs/security/threat-model.md
- docs/operations/runbook.md
- docs/operations/rollback-plan.md
- docs/release/production-readiness-review.md
```
