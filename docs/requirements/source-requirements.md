# Source Requirements (Verbatim)

> **Provenance:** `WEX-Requirement.docx` (uploaded 2026-05-14).
> **Status:** Authoritative source input. Do not edit the verbatim section. Interpretations live in `functional-requirements.md`, `non-functional-requirements.md`, `acceptance-criteria.md`, `assumptions-and-open-questions.md`, and `traceability-matrix.md`.
> **Owner:** Product Analyst (Phase 1) — to be confirmed by human reviewer.
> **Change control:** Any subsequent revision of the requirement document must be added as `source-requirements-vN.md`; this file is never overwritten in place.

---

## Verbatim text from `WEX-Requirement.docx`

### Requirements

#### Requirement #1: Store a Purchase Transaction

Your application must be able to accept and store (i.e., persist) a purchase transaction with a description, transaction date, and a purchase amount in United States dollars. When the transaction is stored, it will be assigned a unique identifier.

**Field requirements**

- Description: must not exceed 50 characters
- Transaction date: must be a valid date format
- Purchase amount: must be a valid positive amount rounded to the nearest cent
- Unique identifier: must uniquely identify the purchase

#### Requirement #2: Retrieve a Purchase Transaction in a Specified Country's Currency

Based upon purchase transactions previously submitted and stored, your application must provide a way to retrieve the stored purchase transactions converted to currencies supported by the Treasury Reporting Rates of Exchange API based upon the exchange rate active for the date of the purchase.

https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange

The retrieved purchase should include the identifier, the description, the transaction date, the original US dollar purchase amount, the exchange rate used, and the converted amount based upon the specified currency's exchange rate for the date of the purchase.

**Currency conversion requirements**

- When converting between currencies, you do not need an exact date match, but must use a currency conversion rate less than or equal to the purchase date from within the last 6 months.
- If no currency conversion rate is available within 6 months equal to or before the purchase date, an error should be returned stating the purchase cannot be converted to the target currency.
- The converted purchase amount to the target currency should be rounded to two decimal places (i.e., cent).

### Technical Implementation

The technical implementation, including frameworks, libraries, etc. is your own design except for the language. That is, if you are applying for Gateways (written in Java), you should implement the solution in Java. If you are applying for TAG (written in GoLang), you may choose to implement the solution in GoLang or Java.

You should build this application as if you are building an application to be deployed in a Production environment. This should be interpreted to mean that all functional automated testing you would include for a Production application should be expected. Please note that non-functional test (e.g., performance testing) automation is not needed.

Your application repository should be fully functional without installing separate databases, web servers, or servlet containers (e.g., Jetty, Tomcat, etc).

---

## Extracted external dependencies (informational, not part of verbatim text)

- **Treasury Reporting Rates of Exchange API** — U.S. Department of the Treasury, Bureau of the Fiscal Service.
  - Documentation: https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange
  - API base (informational, to be confirmed in Phase 3): `https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange`
