# Cardholder Data Classification and Retention

Status: BLOCKED
Owner:

| Data element | Classification | Storage allowed? | Retention | Masking/redaction | Encryption | Access role | Notes |
|---|---|---:|---|---|---|---|---|
| PAN | CHD | TBD | TBD | TBD | TBD | TBD | TBD |
| Cardholder name | CHD if with PAN | TBD | TBD | TBD | TBD | TBD | TBD |
| Expiration date | CHD if with PAN | TBD | TBD | TBD | TBD | TBD | TBD |
| Service code | CHD if with PAN | TBD | TBD | TBD | TBD | TBD | TBD |
| CVV/CVC | SAD | No after authorization | None | Never log | N/A | None | TBD |
| Track data | SAD | No after authorization | None | Never log | N/A | None | TBD |
| PIN/PIN block | SAD | No unless explicitly applicable PCI PIN scope | None | Never log | TBD | None | TBD |
| Token | Sensitive internal/payment token | TBD | TBD | TBD | TBD | TBD | TBD |

## Required controls
- PAN masking standard:
- Data minimization decision:
- Retention purge job:
- Data discovery cadence:
- Exception approval process:
