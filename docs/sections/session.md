## atm.session.Session: PIN format and withdrawal amount

Test class: `src/test/java/atm/session/SessionTest.java` (JUnit 5 + Mockito, 20 tests).

### Process and assumptions

**Technique.** The specification asks for "Robust Worst Case Boundary Value Analysis under the single fault assumption principle." The two halves of that phrase pull in different directions: robust worst case drops the single fault assumption and tests the full Cartesian product of the seven robust values of every variable (7^n cases), while robust BVA *with* the single fault assumption is Robustness testing (6n+1 cases: one variable takes min-, min, min+, max-, max, max+ while the others stay nominal, plus the all-nominal case).

We read "single fault assumption" as the binding part and applied **Robustness BVA (6n+1)**. The reason is structural: the PIN format and the withdrawal amount are checked at different steps of the session (PIN first, amount only after a PIN is accepted). In a combined case with an invalid PIN, the amount is never examined, so most of the 49 worst-case combinations would only re-test the PIN. Varying one variable at a time keeps every test aimed at exactly one boundary. If the TA requires the full 49 robust worst case combinations, the tests are structured to extend directly: the helper `withdrawThroughAtm(pin, amount)` drives a complete withdrawal, so the extension is the cross product of the PIN and amount values below passed to that helper.

**Variables and boundaries.**

- *PIN* (variable 1). The spec says a valid PIN is 5 digits. The length domain is the single value 5, so min = nominal = max = 5. The robust slots collapse: min- = max- = 4 and min+ = max+ = 6. Nominal PIN: `12345`. We added special values: the empty PIN (length 0), a non-numeric PIN of valid length (`12a45`), and the boundaries of each digit's own range, all-min `00000` and all-max `99999`.
- *Withdrawal amount* (variable 2). The spec fixes the upper boundary at the $1,000 daily limit (balance $5,000, so the balance never binds below the limit). **The spec gives no lower bound**, so we took min = the smallest dispensable amount, $20. Valid amounts are multiples of $10 (made of $20 and $50 bills), so the step between boundary values is $10, except min+ = $40 because $30 cannot be dispensed. Nominal amount: $500.
  - Values: min- $10, min $20, min+ $40, nominal $500, max- $990, max $1,000, max+ $1,010.
  - Extra cases: $30 and $55 (not dispensable), $0 and -$20 (robustness, below any valid amount), $1,050 (a *dispensable* amount above the limit, to separate the daily-limit check from the bill check, see A7 below), and $60 (3 x $20, a dispensable amount that needs three twenties).

**Test set-up.** Expected results come from the specification, not the code. The bank (`MessageDispatcher`) and UI (`MainPanel`) are Mockito stubs; no database is used. The stubbed bank accepts any card/PIN pair (only the PIN *format* is under test) and holds an account with a $5,000 balance. The PIN format is not validated inside `Session` itself (`Session.addPin` only stores the PIN); it is validated by `FormatChecker.checkPinFormat`, called from `ATM.checkPin` before the PIN reaches the session. PIN cases therefore run through `ATM.checkPin` with the real `FormatChecker`; amount cases drive a real `Session` directly to the amount step of a Chequing withdrawal and call `Session.setAmount`.

### Test cases and results

Expected/actual: "accepted" = no exception; "InvalidPin" = `InvalidPinFormatException`; "InvalidAmount" = `InvalidAmountException`.

| ID | PIN | Amount | Boundary label | Expected | Actual | Result |
|---|---|---|---|---|---|---|
| P1 | `1234` | $500 | PIN length min- (4) | InvalidPin | accepted | **FAIL** |
| P2 | `12345` | $500 | PIN length min = nominal = max (5) | accepted | InvalidPin | **FAIL** |
| P3 | `123456` | $500 | PIN length max+ (6) | InvalidPin | InvalidPin | PASS |
| P4 | `""` | $500 | PIN empty (length 0) | InvalidPin | InvalidPin | PASS |
| P5 | `12a45` | $500 | PIN non-numeric, length 5 | InvalidPin | InvalidPin | PASS* |
| P6 | `00000` | $500 | PIN digit min ('0') | accepted | InvalidPin | **FAIL** |
| P7 | `99999` | $500 | PIN digit max ('9') | accepted | InvalidPin | **FAIL** |
| A1 | `12345` | $10 | amount min- | InvalidAmount | InvalidAmount | PASS |
| A2 | `12345` | $20 | amount min | accepted | accepted | PASS |
| A3 | `12345` | $40 | amount min+ | accepted | accepted | PASS |
| A4 | `12345` | $500 | amount nominal | accepted | accepted | PASS |
| A5 | `12345` | $990 | amount max- | accepted | accepted | PASS |
| A6 | `12345` | $1,000 | amount max (daily limit) | accepted | accepted | PASS |
| A7 | `12345` | $1,010 | amount max+ | InvalidAmount | InvalidAmount | PASS* |
| A8 | `12345` | $30 | not dispensable | InvalidAmount | InvalidAmount | PASS |
| A9 | `12345` | $55 | not dispensable | InvalidAmount | InvalidAmount | PASS |
| A10 | `12345` | $0 | robustness: zero | InvalidAmount | accepted | **FAIL** |
| A11 | `12345` | -$20 | robustness: negative | InvalidAmount | accepted | **FAIL** |
| A12 | `12345` | $1,050 | max+, dispensable (21 x $50) | InvalidAmount | accepted | **FAIL** |
| A13 | `12345` | $60 | dispensable, needs 3 x $20 | accepted | InvalidAmount | **FAIL** |

Totals: 20 tests, 12 pass, 8 fail.

\* *Passes for the wrong reason.*
- **A7** ($1,010) is rejected, but not because of the daily limit. $1,010 can be dispensed (19 x $50 + 3 x $20), and the code has no daily-limit check at all (A12). It is rejected only because `CashValidator.validateWithdrawal` wrongly treats it as non-dispensable, the same fault A13 exposes. Once that fault is fixed, A7 will fail until the daily limit is implemented.
- **P5** (`12a45`) is rejected only because the current check rejects *every* 5-character PIN (fault F1). It says nothing about the digit check until F1 is fixed.

JUnit method names use camelCase because the course Checkstyle `MethodName` rule forbids underscores. Each test's `@DisplayName` carries the ID and the case name above (e.g. `A7 withdraw_amountMaxPlus_1010_throwsInvalidAmount`).

### Fault localization

| Fault | Failing tests | Suspected fault (class, method, line) | Justification |
|---|---|---|---|
| F1 | P1, P2, P6, P7 | `atm.utils.FormatChecker.checkPinFormat`, line 33: `if (pin.length == 4)` | The length check accepts 4-digit PINs and rejects all 5-digit PINs, the exact opposite of the spec's 5-digit rule. |
| F2 | P7 (masked by F1) | `atm.utils.FormatChecker.checkPinFormat`, line 35: `pin[i] >= '9'` | The digit range check excludes '9'; it should be `> '9'`. P7 (`99999`) will still fail on this condition after F1 is fixed. |
| F3 | A12 (A7 masked) | `atm.session.Session.setAmount`, withdrawal branch, lines 87-91 | Expected but missing: the only check is `CashValidator.validateWithdrawal`; nothing compares the amount to the $1,000 daily limit, so a dispensable $1,050 is accepted. |
| F4 | A13 (and why A7 passes) | `atm.utils.CashValidator.validateWithdrawal`, lines 23-28 | `amount % 50` then `% 20` only accepts amounts whose remainder after removing $50s is a multiple of $20, so amounts needing three or more $20s ($60, $110, $1,010) are rejected even though they can be dispensed. |
| F5 | A10, A11 | `atm.session.Session.setAmount` (lines 87-91) / `atm.utils.CashValidator.validateWithdrawal` (lines 23-24) | Expected but missing: no lower-bound check; $0 and -$20 satisfy the modulo test (`0 % 50 == 0`, `-20 % 20 == 0`), so non-positive withdrawals are accepted. |

Note: F1 and F2 are in `FormatChecker`, not in `Session`. `Session` delegates PIN format validation to `FormatChecker` (via `ATM.checkPin`) and never checks the PIN itself.

### Continuous integration

The workflow is `.github/workflows/ci.yml` and implements TESTING.md:

- **Triggers:** pull requests targeting `main` (opened, synchronize, reopened, labeled, unlabeled) and pushes to `dev`.
- **Lint job** (Java 11, Temurin): `mvn -B checkstyle:check` against `checkstyle.xml`. It always blocks merging.
- **Tests job** (Java 11, Temurin): first reads the PR's labels and requires exactly one of `phase:tests` / `phase:fix`. Zero or both fails the job with an error naming the labels found. Adding or removing a label re-runs the check.
  - `phase:tests`: `mvn -B -Dmaven.test.failure.ignore=true test`. Failing tests are reported but do not fail the job.
  - `phase:fix`: `mvn -B test`. Any failing test fails the job.
  - Push to `dev`: same as `phase:tests` (informational).
- **Reports** (informational, never block): JUnit results are published as a "JUnit test report" check and PR comment (EnricoMi/publish-unit-test-result-action, `fail_on: nothing`). JaCoCo coverage is posted as a PR comment (madrapps/jacoco-report), and the HTML report is uploaded as a build artifact.
