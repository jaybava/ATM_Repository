# Assignment 1 - Work Split

Three members, each owning a roughly equal share of test design, JUnit code,
and write-up. Each person owns separate files to avoid merge conflicts.

| Member | Owns |
|---|---|
| Person A | CI workflow, repo setup, branch protection, `atm.session.Session` tests |
| Person B | `FeesCalculator` withdrawal fees and deposit fees |
| Person C | `FeesCalculator` transfer fees, assembling the document, opening the PR |

> Replace "Person A/B/C" with your names.

---

## Person A - CI, repo setup, and `atm.session.Session`

### Setup (do this first, day 1)
- [ ] Create the team repository, push the course code, add the instructor and TA as collaborators.
- [ ] Keep the `dev` branch for day-to-day work.
- [ ] Write `.github/workflows/ci.yml` per `TESTING.md`:
  - [ ] Triggers: PRs to `main` (opened, synchronize, reopened, labeled, unlabeled) and pushes to `dev`.
  - [ ] **Lint** job on Java 11 (Temurin): `mvn -B checkstyle:check`. Always blocks merging.
  - [ ] **Tests** job on Java 11: requires exactly one of `phase:tests` / `phase:fix`, otherwise fails with an error.
    - `phase:tests`: `mvn -B -Dmaven.test.failure.ignore=true test`
    - `phase:fix`: `mvn -B test`
    - push to `dev`: failures tolerated (informational).
  - [ ] Publish JUnit results and JaCoCo coverage as PR comments (informational).
- [ ] Branch protection on `main`: require PRs, require **2** approving reviews, dismiss stale approvals.
- [ ] Create the `phase:tests` and `phase:fix` labels.

### Testing: `atm.session.Session` (withdrawal, transaction choice 1)
- Technique: **Robust Worst Case Boundary Value Analysis** (single fault assumption).
- File: `src/test/java/atm/session/SessionTest.java`
- **PIN format** (`InvalidPinFormatException`): valid PIN is 5 digits.
  - Boundaries on length: 4 (min-), 5 (valid), 6 (max+); also non-numeric / empty input.
- **Withdrawal amount** (`InvalidAmountException`): account with daily limit **$1,000** and balance **$5,000**; upper boundary is $1,000.
  - Boundaries: min-, min, min+, nominal, max-, max, max+.
  - Amounts that cannot be dispensed with $20 and $50 bills (e.g. $10, $30, $55).

---

## Person B - Withdrawal and deposit fees (`bank.utils.FeesCalculator`)

### Withdrawal
- Technique: **Robust Worst Case Boundary Value Analysis**.
- File: `src/test/java/bank/utils/FeesCalculatorWithdrawalTest.java`
- Variables:
  - Student: yes / no
  - Weekday: yes / no (students only affected)
  - Balance boundaries around **$1,000** and **$5,000** (plus negative / zero for robustness)
  - Amount boundaries (min-, min, min+, nominal, max-, max, max+)
- Expected fees:
  - Student + weekday: no fee; student otherwise: 0.1%
  - Non-student: balance < $1,000 -> 0.3%; $1,000 <= balance < $5,000 -> 0.1%; balance >= $5,000 -> no fee

### Deposit
- Technique: **Weak Robust Equivalence Class Analysis**.
- File: `src/test/java/bank/utils/FeesCalculatorDepositTest.java`
- Valid classes:
  - Student: amount > $50 (balance > $500 -> 0.5%, else 0.25%); amount <= $50 (balance > $5,000 -> 0.5%, else no fee)
  - Non-student: amount > $250 (balance > $2,500 -> 0.8%, else 0.4%); amount <= $250 (balance > $10,000 -> no fee, else 0.1%)
- Invalid classes: negative amount, zero amount, negative balance.

---

## Person C - Transfer fees, document assembly, and the PR

### Transfer
- Technique: **Decision Table Analysis**.
- File: `src/test/java/bank/utils/FeesCalculatorTransferTest.java`
- Conditions (2 x 2 x 2 x 2 = 16 rules, plus invalid-input rules such as negative amount):

| Student | Amount | From balance | To balance | Fee |
|---|---|---|---|---|
| Yes | < $200 | < $2,000 | < $1,000 | 0.1% |
| Yes | < $200 | < $2,000 | >= $1,000 | 0.05% |
| Yes | < $200 | >= $2,000 | < $1,000 | 0.05% |
| Yes | < $200 | >= $2,000 | >= $1,000 | 0.025% |
| Yes | >= $200 | < $2,000 | < $1,000 | 0.05% |
| Yes | >= $200 | < $2,000 | >= $1,000 | 0.025% |
| Yes | >= $200 | >= $2,000 | < $1,000 | 0.25% |
| Yes | >= $200 | >= $2,000 | >= $1,000 | 0.125% |
| No | < $100 | < $4,000 | < $2,000 | 0.2% |
| No | < $100 | < $4,000 | >= $2,000 | 0.1% |
| No | < $100 | >= $4,000 | < $2,000 | 1% |
| No | < $100 | >= $4,000 | >= $2,000 | 0.5% |
| No | >= $100 | < $2,000 | < $1,000 | 0.2% |
| No | >= $100 | < $2,000 | >= $1,000 | 0.1% |
| No | >= $100 | >= $2,000 | < $1,000 | 0.5% |
| No | >= $100 | >= $2,000 | >= $1,000 | 0.25% |

> Double-check these against the assignment spec before writing tests.

### Assembly and PR
- [ ] Merge everyone's sections into `docs/assignment1.md`.
- [ ] Open the PR `dev` -> `main` with exactly one label: `phase:tests`.
- [ ] Fill in `.github/pull_request_template.md` (including the fault-localization table).

---

## Everyone

- [ ] Write your own section of `docs/assignment1.md`:
  1. Process and assumptions for your technique.
  2. Test case table.
  3. Test results (pass/fail, matching the CI run).
  4. Fault localization for each failing test: class, method, condition/statement, one-sentence justification. Group tests that expose the same fault.
- [ ] Keep a **failure log** (needed for Assignment 3): failure ID, test case that exposed it, time spent testing before finding it, time spent diagnosing it.
- [ ] Cross-review test designs so everyone understands all techniques:
  - A reviews B, B reviews C, C reviews A.
- [ ] Approve the final PR (both non-authors), then merge.
- [ ] Submit the repo URL and merged PR link on OWL.
- Do **not** fix faults in the code under test.

### Note on existing files
`src/test/java/bank/utils/FeesCalculatorTest.java` already exists. Split it into
the three per-operation classes above (one class per transaction, as required).

---

## Timeline

| Days | Work |
|---|---|
| 1-2 | A: CI + branch protection. B and C: design test case tables (no code needed). |
| 2-4 | Everyone: write JUnit classes, push to `dev` often. |
| 4-5 | Everyone: fault localization + write-up. C assembles doc, team reviews, opens PR, merges. |
