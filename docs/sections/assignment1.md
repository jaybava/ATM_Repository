# Black Box Testing of the ATM System

- Team: _<names>_
- Repository: _<URL>_

This document describes the black box tests written for the ATM system: how the test cases were derived, the test case tables, the results of running them, and the faults they point to. Part 1 covers the process and assumptions, Part 2 the test cases, results and fault localization, and Part 3 summarises the results.

| Target | Technique | Test class |
|---|---|---|
| `atm.session.Session` (PIN format, withdrawal amount) | Robustness BVA, single fault assumption | `SessionTest` |
| `FeesCalculator` withdrawal fees | Robust Worst Case BVA | _pending_ |
| `FeesCalculator` deposit fees | Weak Robust Equivalence Class Analysis | _pending_ |
| `FeesCalculator` transfer fees | Decision Table Analysis | `FeesCalculatorTest` |

---

# Part 1: Process and assumptions

## 1.1 `atm.session.Session`: PIN format and withdrawal amount

**Technique.** Robustness Boundary Value Analysis under the single fault assumption (6n+1 cases): one variable at a time takes the values min-, min, min+, max-, max, max+ while the other variable stays at its nominal value, plus the all-nominal case. The full cross product of both variables (7^n combinations) was not used. The reason is structural: the PIN format and the withdrawal amount are checked at different steps of the session (PIN first, amount only after a PIN is accepted). In a combined case with an invalid PIN, the amount is never examined, so most combinations would only re-test the PIN. Varying one variable at a time keeps every test aimed at exactly one boundary. The helper `withdrawThroughAtm(pin, amount)` drives a complete withdrawal, so a cross product of the values below can be added directly if it is wanted.

**Variables and boundaries.**

- *PIN* (variable 1). A valid PIN is 5 digits. The length domain is the single value 5, so min = nominal = max = 5. The robust slots collapse: min- = max- = 4 and min+ = max+ = 6. Nominal PIN: `12345`. Special values were added: the empty PIN (length 0), a non-numeric PIN of valid length (`12a45`), and the boundaries of each digit's own range, all-min `00000` and all-max `99999`.
- *Withdrawal amount* (variable 2). The upper boundary is the $1,000 daily limit (the account balance is $5,000, so the balance never binds below the limit). **No lower bound is given**, so min was taken as the smallest dispensable amount, $20. Valid amounts are multiples of $10 (made of $20 and $50 bills), so the step between boundary values is $10, except min+ = $40 because $30 cannot be dispensed. Nominal amount: $500.
  - Values: min- $10, min $20, min+ $40, nominal $500, max- $990, max $1,000, max+ $1,010.
  - Extra cases: $30 and $55 (not dispensable), $0 and -$20 (robustness, below any valid amount), $1,050 (a *dispensable* amount above the limit, to separate the daily-limit check from the bill check, see A7 below), and $60 (3 x $20, a dispensable amount that needs three twenties).

**Test set-up.** Expected results come from the business rules, not from the code. The bank (`MessageDispatcher`) and UI (`MainPanel`) are Mockito stubs; no database is used. The stubbed bank accepts any card/PIN pair (only the PIN *format* is under test) and holds an account with a $5,000 balance. The PIN format is not validated inside `Session` itself (`Session.addPin` only stores the PIN); it is validated by `FormatChecker.checkPinFormat`, called from `ATM.checkPin` before the PIN reaches the session. PIN cases therefore run through `ATM.checkPin` with the real `FormatChecker`; amount cases drive a real `Session` directly to the amount step of a Chequing withdrawal and call `Session.setAmount`.

## 1.2 `FeesCalculator` withdrawal fees

_Pending: withdrawal and deposit fee tests have not been added to this document yet._

## 1.3 `FeesCalculator` deposit fees

_Pending: withdrawal and deposit fee tests have not been added to this document yet._

## 1.4 `FeesCalculator` transfer: Decision Table Analysis

**Process.**

1. Read the transfer fee rules and identified the conditions. The fee depends on four conditions:
   - C1: is the client a student?
   - C2: transfer amount (threshold **$200** for students, **$100** for non-students)
   - C3: balance of the source account (thresholds **$2,000** for students and for non-students with a large amount, **$4,000** for non-students with a small amount)
   - C4: balance of the destination account (thresholds **$1,000** for students and for non-students with a large amount, **$2,000** for non-students with a small amount)
2. Because the thresholds for C2, C3 and C4 depend on C1 (and, for non-students, on C2), each condition is binary *within its branch*. This gives 2 (student / not) x 2 (amount) x 2 (from) x 2 (to) = **16 rules**.
3. Each rule maps to exactly one action: the fee percentage. Every combination of conditions is covered, so the table is complete, and no two rules share the same conditions, so it has no conflicts.
4. One JUnit test per rule (TR-01 to TR-16).
5. Added three rules for invalid input (negative amount, negative source balance, negative destination balance), TR-17 to TR-19.

**Assumptions.**

- Fee = amount x percentage. Because the implementation may round to cents, expected values are compared with a tolerance of **$0.01**. The smallest difference between two different rates at our chosen amounts is greater than $0.05, so the tolerance cannot hide a wrong rate.
- The rules only say *less than* / *or more*. The "less than" side of every condition is therefore tested with **threshold - $0.01**, and the "or more" side with **exactly the threshold**. Each rule test thus also checks the comparison operator (`<` vs `<=`), which is where off-by-one faults usually hide.
- The rules do not say what happens for invalid input. We assume `IllegalArgumentException` for a negative amount or negative balance. A failure here is a robustness finding, not a violation of a stated rule, and is noted as such in the fault localization.
- A zero amount is not tested as invalid, since the rules do not say it is.
- Tests call `new FeesCalculator().calculateTransferFee(amount, fromBalance, toBalance, student)`, isolated in one helper method in the test class.

---

# Part 2: Test cases, results and fault localization

## 2.1 `atm.session.Session`: PIN format and withdrawal amount

Test class: `src/test/java/atm/session/SessionTest.java` (JUnit 5 + Mockito, 20 tests).

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
- **A7** ($1,010) is rejected, but not because of the daily limit. $1,010 can be dispensed (19 x $50 + 3 x $20), and the code has no daily-limit check at all (A12). It is rejected only because `CashValidator.validateWithdrawal` wrongly treats it as non-dispensable, the same fault A13 exposes (S4). Once that fault is fixed, A7 will fail until the daily limit is implemented.
- **P5** (`12a45`) is rejected only because the current check rejects *every* 5-character PIN (fault S1). It says nothing about the digit check until S1 is fixed.

JUnit method names use camelCase because the Checkstyle `MethodName` rule forbids underscores. Each test's `@DisplayName` carries the ID and the case name above (e.g. `A7 withdraw_amountMaxPlus_1010_throwsInvalidAmount`).

### Fault localization (session)

| Fault | Failing tests | Suspected fault (class, method, line) | Justification |
|---|---|---|---|
| S1 | P1, P2, P6, P7 | `atm.utils.FormatChecker.checkPinFormat`, line 33: `if (pin.length == 4)` | The length check accepts 4-digit PINs and rejects all 5-digit PINs, the exact opposite of the 5-digit rule. |
| S2 | P7 (masked by S1) | `atm.utils.FormatChecker.checkPinFormat`, line 35: `pin[i] >= '9'` | The digit range check excludes '9'; it should be `> '9'`. P7 (`99999`) will still fail on this condition after S1 is fixed. |
| S3 | A12 (A7 masked) | `atm.session.Session.setAmount`, withdrawal branch, lines 87-91 | Expected but missing: the only check is `CashValidator.validateWithdrawal`; nothing compares the amount to the $1,000 daily limit, so a dispensable $1,050 is accepted. |
| S4 | A13 (and why A7 passes) | `atm.utils.CashValidator.validateWithdrawal`, lines 23-28 | `amount % 50` then `% 20` only accepts amounts whose remainder after removing $50s is a multiple of $20, so amounts needing three or more $20s ($60, $110, $1,010) are rejected even though they can be dispensed. |
| S5 | A10, A11 | `atm.session.Session.setAmount` (lines 87-91) / `atm.utils.CashValidator.validateWithdrawal` (lines 23-24) | Expected but missing: no lower-bound check; $0 and -$20 satisfy the modulo test (`0 % 50 == 0`, `-20 % 20 == 0`), so non-positive withdrawals are accepted. |

S1 and S2 are in `FormatChecker`, not in `Session`. `Session` delegates PIN format validation to `FormatChecker` (via `ATM.checkPin`) and never checks the PIN itself.

## 2.2 `FeesCalculator` withdrawal fees

_Pending: withdrawal and deposit fee tests have not been added to this document yet._

## 2.3 `FeesCalculator` deposit fees

_Pending: withdrawal and deposit fee tests have not been added to this document yet._

## 2.4 `FeesCalculator` transfer

### Decision table

Test class: `src/test/java/bank/utils/FeesCalculatorTest.java` (the existing starter class, edited: its single sample test was replaced by the transfer tests)

"Low" means below the threshold, "High" means at or above it. Thresholds are given per branch in section 1.4.

| Rule | C1 Student | C2 Amount | C3 From balance | C4 To balance | Action: fee |
|---|---|---|---|---|---|
| R1 | Yes | < $200 | < $2,000 | < $1,000 | 0.1% |
| R2 | Yes | < $200 | < $2,000 | >= $1,000 | 0.05% |
| R3 | Yes | < $200 | >= $2,000 | < $1,000 | 0.05% |
| R4 | Yes | < $200 | >= $2,000 | >= $1,000 | 0.025% |
| R5 | Yes | >= $200 | < $2,000 | < $1,000 | 0.05% |
| R6 | Yes | >= $200 | < $2,000 | >= $1,000 | 0.025% |
| R7 | Yes | >= $200 | >= $2,000 | < $1,000 | 0.25% |
| R8 | Yes | >= $200 | >= $2,000 | >= $1,000 | 0.125% |
| R9 | No | < $100 | < $4,000 | < $2,000 | 0.2% |
| R10 | No | < $100 | < $4,000 | >= $2,000 | 0.1% |
| R11 | No | < $100 | >= $4,000 | < $2,000 | 1% |
| R12 | No | < $100 | >= $4,000 | >= $2,000 | 0.5% |
| R13 | No | >= $100 | < $2,000 | < $1,000 | 0.2% |
| R14 | No | >= $100 | < $2,000 | >= $1,000 | 0.1% |
| R15 | No | >= $100 | >= $2,000 | < $1,000 | 0.5% |
| R16 | No | >= $100 | >= $2,000 | >= $1,000 | 0.25% |

### Test cases and results

Result of a local run against the repository code (19 tests, 0 passed, 19 failed). Re-confirm against the CI run on the PR.

| ID | Rule | Student | Amount | From balance | To balance | Expected rate | Expected fee | Result |
|---|---|---|---|---|---|---|---|---|
| TR-01 | R1 | Yes | 199.99 | 1999.99 | 999.99 | 0.1% | 0.19999 | Fail |
| TR-02 | R2 | Yes | 199.99 | 1999.99 | 1000.00 | 0.05% | 0.099995 | Fail |
| TR-03 | R3 | Yes | 199.99 | 2000.00 | 999.99 | 0.05% | 0.099995 | Fail |
| TR-04 | R4 | Yes | 199.99 | 2000.00 | 1000.00 | 0.025% | 0.0499975 | Fail |
| TR-05 | R5 | Yes | 200.00 | 1999.99 | 999.99 | 0.05% | 0.10 | Fail |
| TR-06 | R6 | Yes | 200.00 | 1999.99 | 1000.00 | 0.025% | 0.05 | Fail |
| TR-07 | R7 | Yes | 200.00 | 2000.00 | 999.99 | 0.25% | 0.50 | Fail |
| TR-08 | R8 | Yes | 200.00 | 2000.00 | 1000.00 | 0.125% | 0.25 | Fail |
| TR-09 | R9 | No | 99.99 | 3999.99 | 1999.99 | 0.2% | 0.19998 | Fail |
| TR-10 | R10 | No | 99.99 | 3999.99 | 2000.00 | 0.1% | 0.09999 | Fail |
| TR-11 | R11 | No | 99.99 | 4000.00 | 1999.99 | 1% | 0.9999 | Fail |
| TR-12 | R12 | No | 99.99 | 4000.00 | 2000.00 | 0.5% | 0.49995 | Fail |
| TR-13 | R13 | No | 100.00 | 1999.99 | 999.99 | 0.2% | 0.20 | Fail |
| TR-14 | R14 | No | 100.00 | 1999.99 | 1000.00 | 0.1% | 0.10 | Fail |
| TR-15 | R15 | No | 100.00 | 2000.00 | 999.99 | 0.5% | 0.50 | Fail |
| TR-16 | R16 | No | 100.00 | 2000.00 | 1000.00 | 0.25% | 0.25 | Fail |
| TR-17 | Invalid | Yes | -50.00 | 1000.00 | 500.00 | IllegalArgumentException | n/a | Fail |
| TR-18 | Invalid | No | 150.00 | -1.00 | 500.00 | IllegalArgumentException | n/a | Fail |
| TR-19 | Invalid | No | 150.00 | 1000.00 | -1.00 | IllegalArgumentException | n/a | Fail |

### Fault localization (transfer)

All faults are in `src/main/java/bank/utils/FeesCalculator.java`, method `calculateTransferFee` (starts at line 75). Every test case lands in a branch that has more than one wrong condition or constant, so a failing test usually exposes several faults at once. The failures are therefore grouped by fault.

| Fault | Failing tests | Location (line) | Justification |
|---|---|---|---|
| T1: wrong amount threshold for students | TR-01 to TR-04 | line 80, `if (amount < 100.00)` in the `student` branch | The rules split student transfers at $200, but the code splits at $100, so amounts from $100 to $199.99 take the "large amount" branch. |
| T2: wrong source-balance thresholds | TR-01, 02, 05, 06, 09, 10, 13, 14 | lines 81, 95, 111, 125, `fromAccountBalance < 1000.00` | The rules use $2,000 (students, and non-students at $100 or more) and $4,000 (non-students under $100). A source balance of $1,999.99 or $3,999.99 gets the "high balance" rate instead of the "low balance" one. |
| T3: wrong destination-balance threshold for non-students under $100 | TR-09, TR-11 | lines 112 and 118, `toAccountBalance < 1000.00` | The rules use $2,000 here, so a destination balance of $1,999.99 gets the "high balance" rate instead of the "low balance" one. |
| T4: wrong fee percentages | TR-01 to TR-16 | lines 83, 85, 89, 91, 97, 99, 103, 105, 113, 115, 119, 121, 127, 129, 133, 135, the `feePercentage = ...` constants | All 16 constants differ from the rules, between 5 and 100 times too large (for example line 135 charges 5.5% where the rules say 0.25%). |
| T5: no validation of invalid input | TR-17, TR-18, TR-19 | line 75, start of the method (no argument check) | Negative amounts and negative balances are accepted and produce a fee instead of an exception. The rules do not define this behaviour, so this is a robustness finding based on our assumption. |

#### Tracing the failures through the code

Faults T1 to T5 were found by following each test input through `calculateTransferFee` and comparing the branch it reaches with the decision table rule. The code compares `amount < 100.00` (lines 80 and 110) and `fromAccountBalance < 1000.00` / `toAccountBalance < 1000.00` in every branch. The table shows, for each test, the branch reached (by the line of its fee constant), why it is reached, and the percentage the code charges against the percentage the rules give.

| Test | Branch reached | Why it is reached | Code charges | Rules say |
|---|---|---|---|---|
| TR-01 | l.103 | 199.99 is not < 100 (T1); 1999.99 is not < 1000 (T2); 999.99 < 1000 | 2.5% | 0.1% |
| TR-02 | l.105 | 199.99 is not < 100 (T1); 1999.99 is not < 1000 (T2); 1000 is not < 1000 | 1.25% | 0.05% |
| TR-03 | l.103 | 199.99 is not < 100 (T1); 2000 is not < 1000; 999.99 < 1000 | 2.5% | 0.05% |
| TR-04 | l.105 | 199.99 is not < 100 (T1); 2000 is not < 1000; 1000 is not < 1000 | 1.25% | 0.025% |
| TR-05 | l.103 | 200 is not < 100; 1999.99 is not < 1000 (T2); 999.99 < 1000 | 2.5% | 0.05% |
| TR-06 | l.105 | 200 is not < 100; 1999.99 is not < 1000 (T2); 1000 is not < 1000 | 1.25% | 0.025% |
| TR-07 | l.103 | 200 is not < 100; 2000 is not < 1000; 999.99 < 1000 | 2.5% | 0.25% |
| TR-08 | l.105 | 200 is not < 100; 2000 is not < 1000; 1000 is not < 1000 | 1.25% | 0.125% |
| TR-09 | l.121 | 99.99 < 100; 3999.99 is not < 1000 (T2); 1999.99 is not < 1000 (T3) | 5% | 0.2% |
| TR-10 | l.121 | 99.99 < 100; 3999.99 is not < 1000 (T2); 2000 is not < 1000 | 5% | 0.1% |
| TR-11 | l.121 | 99.99 < 100; 4000 is not < 1000; 1999.99 is not < 1000 (T3) | 5% | 1% |
| TR-12 | l.121 | 99.99 < 100; 4000 is not < 1000; 2000 is not < 1000 | 5% | 0.5% |
| TR-13 | l.133 | 100 is not < 100; 1999.99 is not < 1000 (T2); 999.99 < 1000 | 5% | 0.2% |
| TR-14 | l.135 | 100 is not < 100; 1999.99 is not < 1000 (T2); 1000 is not < 1000 | 5.5% | 0.1% |
| TR-15 | l.133 | 100 is not < 100; 2000 is not < 1000; 999.99 < 1000 | 5% | 0.5% |
| TR-16 | l.135 | 100 is not < 100; 2000 is not < 1000; 1000 is not < 1000 | 5.5% | 0.25% |

In every row the code charges a different percentage from the rules (T4). TR-07, TR-08, TR-12, TR-15 and TR-16 reach the correct branch, so T4 is the only fault they expose. The other failing tests reach a wrong branch because of T1, T2 or T3 as marked, and then also charge a wrong constant.

---

# Part 3: Summary

Results come from running each test class against the unmodified code.

| Target | Tests | Passed | Failed | Distinct faults found |
|---|---|---|---|---|
| Session (PIN format, withdrawal amount) | 20 | 12 | 8 | 5 (S1 to S5) |
| Withdrawal fees | _pending_ | | | |
| Deposit fees | _pending_ | | | |
| Transfer fees | 19 | 0 | 19 | 5 (T1 to T5) |

Failing tests are expected: the faults they expose are the point of the exercise, and none of them was fixed here.
