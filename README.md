# Prop Firm Risk Management & Trade Validation System

A deterministic Java 17 command-line application and JUnit 5 verification suite developed for the **Software Verification & Validation (SVV)** Research Assignment.

Given a virtual proprietary trading account's current state and a proposed trade, the application deterministically decides whether to **APPROVE** or **REJECT** the trade with an explicit explanation, enforces dynamic risk modes and daily/overall drawdown locks, and maintains strict trade-lifecycle invariants using `java.math.BigDecimal`.

**Student:** 210911044 — Ali Edip Mangtay  
**Project Topic Status:** Instructor-approved topic (proposal archived in [`proposal/project-proposal.md`](proposal/project-proposal.md)).

---

## Requirements

- **Java Development Kit (JDK):** Java 17 (verified with OpenJDK 17.0.21.1)
- **Build Tool:** Apache Maven 3.8+ (verified with Apache Maven 3.10.1)
- **Frameworks & Plugins (managed by Maven):**
  - JUnit Jupiter `5.11.3`
  - Maven Compiler Plugin `3.13.0` (`release=17`)
  - Maven Surefire Plugin `3.5.2`
  - JaCoCo Maven Plugin `0.8.12`

---

## Project Structure

```text
PropFirmRiskManagement/
├── pom.xml
├── README.md
├── .gitignore
├── src/
│   ├── main/java/com/svv/risk/
│   │   ├── AccountStatus.java      # Immutable account status snapshot (record)
│   │   ├── Main.java               # Interactive CLI with injectable I/O
│   │   ├── RiskMode.java           # NORMAL, GROWTH, SAFE, RECOVERY, LOCKED enum
│   │   ├── RiskPolicy.java         # Pure deterministic risk & P&L rules
│   │   ├── TradeDecision.java      # Immutable approval/rejection result (record)
│   │   ├── TradeOutcome.java       # WIN, LOSS enum
│   │   ├── TradeRequest.java       # Immutable structurally validated trade (record)
│   │   └── TradingAccount.java     # Stateful account & trade-lifecycle aggregate
│   └── test/java/com/svv/risk/
│       ├── DecisionTableTest.java  # 8 executable decision-table rules (DT)
│       ├── MainTest.java           # CLI integration & malformed-input tests (EP, NT)
│       ├── RegressionTest.java     # 5 permanent invariant guards (RT)
│       ├── RiskPolicyTest.java     # Pure BVA, EP, rounding & priority tests
│       ├── TradeRequestTest.java   # Structural validation tests (EP, NT)
│       └── TradingAccountTest.java # Lifecycle, multi-day & negative tests
├── evidence/
│   ├── maven-verify.txt            # Verbatim 'mvn -B clean verify' output
│   ├── test-results.csv            # Machine-parsed Surefire invocation results (59 rows)
│   ├── jacoco.csv                  # JaCoCo class-level coverage counters
│   ├── jacoco-html.zip             # Archived JaCoCo HTML report
│   ├── mutation-before.txt         # Controlled mutation failure log (<= changed to <)
│   └── mutation-after.txt          # Restored regression verification log
├── proposal/
│   └── project-proposal.md         # Approved project proposal summary
└── report/
    ├── 210911044_ALIEDIP_MANGTAY.docx  # Final academic report (Word)
    └── 210911044_ALIEDIP_MANGTAY.pdf   # Final academic report (PDF, 7 pages)
```

---

## How to Build, Run, Test, and Measure Coverage

All commands are run from the `PropFirmRiskManagement/` directory.

### 1. Compile the Application

```bash
mvn clean compile
```

### 2. Run the Interactive CLI

```bash
mvn compile && java -cp target/classes com.svv.risk.Main
```

Or using the packaged JAR after `mvn package` / `mvn verify`:

```bash
java -jar target/prop-firm-risk-management-1.0.0.jar
```

Example interactive CLI session:

```text
help
status
risk
propose T1 250.00 2.00
settle T1 WIN
propose T2 400.00 2.00
nextday 2026-11-24
exit
```

### 3. Execute All JUnit 5 Tests and Generate JaCoCo Coverage

```bash
mvn clean verify
```

This command compiles the Java 17 sources, runs all JUnit Jupiter 5 tests via Maven Surefire, builds the JAR, and generates the JaCoCo coverage report in:

- HTML report: `target/site/jacoco/index.html`
- CSV counters: `target/site/jacoco/jacoco.csv`
- XML report: `target/site/jacoco/jacoco.xml`

---

## Verified Test & Coverage Summary

From the latest `mvn -B clean verify` run recorded in [`evidence/maven-verify.txt`](evidence/maven-verify.txt):

- **Production Java files:** 8
- **JUnit 5 test classes:** 6
- **Test methods:** 42
- **Executed JUnit test invocations:** 59 (59 passed, 0 failed, 0 errors, 0 skipped)
- **Maven build status:** `BUILD SUCCESS`
- **JaCoCo Code Coverage (All 8 Classes):**
  - **Line coverage:** 97.43% (379 / 389)
  - **Branch coverage:** 93.26% (166 / 178)
  - **Method coverage:** 97.92% (47 / 48)
  - **Core domain classes (excluding `Main`):** 99.23% line (258 / 260), 94.12% branch (128 / 136), 100.00% method (35 / 35)

---

## Evidence & Report Locations

- **Testing & Coverage Evidence:** [`evidence/`](evidence/)
  - [`evidence/maven-verify.txt`](evidence/maven-verify.txt) — final Maven verification output
  - [`evidence/test-results.csv`](evidence/test-results.csv) — all 59 executed test invocations
  - [`evidence/jacoco.csv`](evidence/jacoco.csv) and [`evidence/jacoco-html.zip`](evidence/jacoco-html.zip) — coverage artifacts
  - [`evidence/mutation-before.txt`](evidence/mutation-before.txt) and [`evidence/mutation-after.txt`](evidence/mutation-after.txt) — controlled mutation experiment (`isDailyLocked`: `<=` mutated to `<`, detected by 5 tests across 5 test classes, restored and re-verified)
- **Academic Report:** [`report/`](report/)
  - [`report/210911044_ALIEDIP_MANGTAY.docx`](report/210911044_ALIEDIP_MANGTAY.docx) — editable Word document
  - [`report/210911044_ALIEDIP_MANGTAY.pdf`](report/210911044_ALIEDIP_MANGTAY.pdf) — final 7-page A4 PDF report
