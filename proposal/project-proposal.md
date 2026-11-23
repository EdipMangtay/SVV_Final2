# Project Proposal — Software Verification & Validation Assignment

**Status:** Pending Instructor Approval *(Required before formal implementation acceptance per Assignment Section 2 and Section 7.)*  
**Submission Window:** 17–24 November 2026  
**Student:** `[STUDENT_NUMBER]` — `[FIRST_NAME LAST_NAME]` *(Replace placeholders with real student details before submission)*

---

## 1. Project Title

**Prop Firm Risk Management & Trade Validation System**

---

## 2. Real-World Problem

Proprietary trading ("prop") firms grant traders virtual funded accounts (commonly $50,000.00) governed by strict, automated daily and overall drawdown limits. Violating either threshold terminates trading privileges for the day or permanently breaches the funded account. Under live trading pressure—especially after consecutive losing trades—traders frequently miscalculate remaining risk headroom. For example, a trader accustomed to risking $250.00 per trade (0.50% of a $50,000.00 account) may propose a $250.00 trade when only $80.00 of daily drawdown capacity remains. Approving such a trade exposes the account to an immediate rule violation.

The central software verification problem is deterministic and clearly bounded: **given an account's current financial state and a proposed trade request, should the system approve or reject the trade, and how must account state evolve upon settlement and day transitions?**

---

## 3. Proposed Java Solution

A standalone Java 17 command-line application built with Apache Maven (no GUI, no database, no external APIs or broker integrations). The system comprises eight focused production classes under `com.svv.risk`:

- `RiskMode` and `TradeOutcome`: Domain enums defining the five dynamic risk modes (`LOCKED > RECOVERY > SAFE > GROWTH > NORMAL`) and trade settlement outcomes (`WIN`, `LOSS`).
- `TradeRequest`, `TradeDecision`, and `AccountStatus`: Immutable Java 17 records for validated proposals, structured approval/rejection explanations, and read-only status snapshots.
- `RiskPolicy`: Pure, deterministic business-rule calculator using `java.math.BigDecimal` with conservative monetary rounding (`CEILING` for loss floors, `DOWN` for nominal risk caps, `HALF_UP` for winning P&L, exact deduction for losses).
- `TradingAccount`: Stateful aggregate enforcing single pending trade authorization, trade-ID normalization and uniqueness, daily and static overall loss-floor locks, consecutive-loss persistence, and forward-only trading-day transitions.
- `Main`: Minimal interactive CLI (`help`, `status`, `risk`, `propose`, `settle`, `nextday`, `exit`) with injectable I/O streams for automated testing.

---

## 4. Core Functional Requirements (Summary)

1. **Account & Floor Rules (FR-01, FR-02):** Initialize a $50,000.00 account with a dynamic 1.00% daily floor ($49,500.00) and a static 3.00% overall floor ($48,500.00) that never trails upward.
2. **Dynamic Risk Modes & Risk Cap (FR-03, FR-04):** Select mode via strict priority `LOCKED (0.00%) > RECOVERY (0.10%, >=3 losses) > SAFE (0.25%, <=-2.00%) > GROWTH (0.75%, >=+2.00%) > NORMAL (0.50%)`, capping allowed risk at `min(NominalRisk, RemainingDailyCapacity, RemainingOverallCapacity)`.
3. **Validation & Trade Lifecycle (FR-05 to FR-10):** Validate structural request inputs (`IllegalArgumentException`), issue structured `APPROVED`/`REJECTED` decisions, enforce single pending trade settlement using internally stored terms, reject duplicate/blank IDs and double settlements, and track losing streaks.
4. **Locks, Day Transitions & CLI (FR-11 to FR-15):** Activate inclusive (`<=`) daily and permanent overall locks, permit forward-only day transitions only when no trade is pending (clearing daily lock while preserving overall lock and losing streak), maintain state consistency on failed operations, and support resilient CLI interaction.

---

## 5. Planned Testing Approach

Automated verification will use **JUnit Jupiter 5** and **JaCoCo** via `mvn clean verify`, organized into six test classes demonstrating all five mandatory testing techniques:

- **Equivalence Partitioning (EP):** Valid/invalid trade IDs, positive/zero/negative/fractional-cent risks, valid/invalid reward-to-risk ratios, mode precedence, and rounding rules.
- **Boundary Value Analysis (BVA):** Exact thresholds (`at`, `below`, `above`) for `$250.00` risk cap (`$249.99 / $250.00 / $250.01`), `$49,500.00` daily floor, `$48,500.00` overall floor, `+2.00%` GROWTH, `-2.00%` SAFE, and `3` consecutive losses for RECOVERY.
- **Decision Table Testing (DT):** Eight executable multi-condition rules combining lock state, active risk mode, trading-day match, proposed risk, and binding capacity constraints.
- **Negative Testing (NT):** Blank/null inputs, fractional cents, duplicate IDs, concurrent pending trades, unauthorized/double settlement, invalid day transitions, locked-account proposals, and malformed CLI commands—verifying account state invariance on failures.
- **Regression Testing (RT):** Permanent regression suite protecting critical invariants, validated by rerunning the full suite after code changes and controlled boundary fault-injection experiments.

---

## 6. Scope Suitability

The scope is deliberately right-sized for a 3–4 week individual undergraduate assignment: it avoids enterprise complexity (no databases, frameworks, or GUIs) while offering rich numerical boundaries, interacting state transitions, strict financial precision rules, and multi-condition decision logic that genuinely benefit from systematic JUnit 5 verification.
