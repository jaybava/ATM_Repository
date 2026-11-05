## bank.utils.FeesCalculator: deposit fee

Test class: `src/test/java/bank/utils/FeesCalculatorDepositTest.java` (JUnit 5 parameterized test, 12 cases).

### Process and assumptions

**Technique.** The specification asks for **Weak Robust Equivalence Class Analysis** for deposit. We partitioned every input into equivalence classes (sets of inputs the fee rules treat the same way), then:

- *Weak*: every valid class is covered by at least one test, without testing every combination of classes.
- *Robust*: invalid classes are added, with **exactly one invalid value per test** and every other input valid, so a failure points to a single invalid input.

**Method under test.** `calculateDepositInterest(double amount, double accountBalance, boolean student)`. The code calls the result "interest", but the specification defines it as the deposit **fee**, so we test it against the fee rules. It returns the fee in dollars.

**Specification (deposit fee rules).**

| Client | Amount | Balance | Fee |
|---|---|---|---|
| Student | > $50 | > $500 | 0.5% |
| Student | > $50 | <= $500 | 0.25% |
| Student | <= $50 | > $5,000 | 0.5% |
| Student | <= $50 | <= $5,000 | none |
| Not a student | > $250 | > $2,500 | 0.8% |
| Not a student | > $250 | <= $2,500 | 0.4% |
| Not a student | <= $250 | > $10,000 | none |
| Not a student | <= $250 | <= $10,000 | 0.1% |

**Equivalence classes.** The classes are not independent: the amount threshold depends on whether the client is a student, and the balance threshold depends on both. Each valid class is therefore a leaf of the rule tree above, and covering every class means covering all eight leaves (a weak test set with fewer cases would leave some balance classes untested).

| Variable | Valid classes | Invalid classes |
|---|---|---|
| Student | `true`, `false` | none (boolean) |
| Amount, student | (0, 50], (50, inf) | amount <= 0 |
| Amount, not a student | (0, 250], (250, inf) | amount <= 0 |
| Balance, student, amount > $50 | [0, 500], (500, inf) | balance < 0 |
| Balance, student, amount <= $50 | [0, 5,000], (5,000, inf) | balance < 0 |
| Balance, not a student, amount > $250 | [0, 2,500], (2,500, inf) | balance < 0 |
| Balance, not a student, amount <= $250 | [0, 10,000], (10,000, inf) | balance < 0 |

**Assumptions.**

- **The fee rules give no range for the amount or the balance.** We assumed a deposit amount must be positive (amount <= 0 is invalid) and the balance must not be negative (balance < 0 is invalid). Neither has an upper limit, since the rules define none.
- The fee rules do not say what happens on invalid input. We assumed `calculateDepositInterest` rejects it with an `IllegalArgumentException`, the same assumption the withdrawal and transfer tests make.
- **Representative values.** Equivalence class analysis takes a value from inside each class, away from its edges (edges are the job of boundary value analysis). Most classes are open-ended, so they have no true middle; we used one rule throughout: **half the threshold** for an "at or below" class and **double the threshold** for an "above" class (e.g. student amount <= $50 uses $25, > $50 uses $100; balance <= $500 uses $250, > $500 uses $1,000).
- Invalid amounts use the negative of the valid amount; invalid balances use the negative of the valid balance. The invalid classes are tested once for a student and once for a non-student, because the amount and balance classes differ between the two.

**Expected results.** Expected values come from the fee rules only, never from the code: `amount x rate`, compared with a tolerance of 0.000001.

### Test cases and results

Totals: **12 tests, 1 pass, 11 fail.**

| ID | Student | Amount | Balance | Class covered | Expected | Actual | Result |
|---|---|---|---|---|---|---|---|
| DP-01 | true | 100.00 | 1,000.00 | amount > $50, balance > $500 | 0.5% = 0.50 | 0.20 | **FAIL** |
| DP-02 | true | 100.00 | 250.00 | amount > $50, balance <= $500 | 0.25% = 0.25 | 0.20 | **FAIL** |
| DP-03 | true | 25.00 | 10,000.00 | amount <= $50, balance > $5,000 | 0.5% = 0.125 | 0.125 | PASS |
| DP-04 | true | 25.00 | 2,500.00 | amount <= $50, balance <= $5,000 | none = 0.00 | 0.05 | **FAIL** |
| DP-05 | false | 500.00 | 5,000.00 | amount > $250, balance > $2,500 | 0.8% = 4.00 | 0.00 | **FAIL** |
| DP-06 | false | 500.00 | 1,250.00 | amount > $250, balance <= $2,500 | 0.4% = 2.00 | 0.00 | **FAIL** |
| DP-07 | false | 125.00 | 20,000.00 | amount <= $250, balance > $10,000 | none = 0.00 | 0.625 | **FAIL** |
| DP-08 | false | 125.00 | 5,000.00 | amount <= $250, balance <= $10,000 | 0.1% = 0.125 | 0.00 | **FAIL** |
| DP-09 | true | -100.00 | 1,000.00 | invalid: amount <= 0 (student) | `IllegalArgumentException` | no exception | **FAIL** |
| DP-10 | false | -500.00 | 5,000.00 | invalid: amount <= 0 (not a student) | `IllegalArgumentException` | no exception | **FAIL** |
| DP-11 | true | 100.00 | -1,000.00 | invalid: balance < 0 (student) | `IllegalArgumentException` | no exception | **FAIL** |
| DP-12 | false | 500.00 | -5,000.00 | invalid: balance < 0 (not a student) | `IllegalArgumentException` | no exception | **FAIL** |

Each row of the test's `@CsvSource` also names the **specification rule it traces to** (the "Class covered" column above, with its fee). The JUnit display name shows the ID, the inputs and the rule (e.g. `DP-05: student=false, amount=500.00, balance=5000.00 | Rule: Deposit: not a student; amount > $250; balance > $2500 -> 0.8%`), and the ID and rule are the assertion message.

### Fault localization

All faults are in `bank.utils.FeesCalculator.calculateDepositInterest`.

| Fault | Failing tests | Suspected fault (class, method, line) | Justification |
|---|---|---|---|
| FD1 | DP-01, DP-02 | `FeesCalculator.calculateDepositInterest`, line 39: `if (amount > 100.00)` | The student amount threshold is $100 instead of $50, so a $100 student deposit is handled as a small deposit; both cases return 0.2% (the small-deposit, low-balance rate) whatever the balance. |
| FD2 | DP-04 (also the 0.20 returned in DP-01, DP-02) | `FeesCalculator.calculateDepositInterest`, line 50: `interestPercentage = 0.002` | A student with a small deposit and a balance of $5,000 or less should pay no fee, but the code charges 0.2%. |
| FD3 | DP-05, DP-06 | `FeesCalculator.calculateDepositInterest`, line 54: `if (amount > 500.00)` | The non-student amount threshold is $500 instead of $250, so a $500 deposit is handled as a small deposit and gets no fee instead of 0.8% / 0.4%. |
| FD4 | DP-07 | `FeesCalculator.calculateDepositInterest`, line 62: `interestPercentage = 0.005` | A non-student with a small deposit and a balance over $10,000 should pay no fee, but the code charges 0.5%. |
| FD5 | DP-08 (also the 0.00 returned in DP-05, DP-06) | `FeesCalculator.calculateDepositInterest`, line 64: `interestPercentage = 0.0` | A non-student with a small deposit and a balance of $10,000 or less should pay 0.1%, but the code charges nothing. |
| FD6 | DP-09, DP-10, DP-11, DP-12 | `FeesCalculator.calculateDepositInterest`, line 33 (method entry): no input validation | Expected but missing: the method never checks its inputs, so a negative amount or a negative balance returns a fee instead of being rejected (this rests on our `IllegalArgumentException` assumption). |

**Faults masked by FD1 and FD3.** Because the wrong amount thresholds send DP-01, DP-02, DP-05 and DP-06 into the small-deposit branch, the large-deposit branches are never reached by these tests. Reading those branches shows further suspects that the current tests cannot expose yet, and that will surface once FD1 and FD3 are fixed:

- Student, large deposit (lines 41-44): balance threshold `> 1000.00` instead of `> $500`, and rates 1% / 0.5% instead of 0.5% / 0.25%.
- Not a student, large deposit (lines 55-58): balance threshold `> 5000.00` instead of `> $2,500`, and rates 1% / 0.5% instead of 0.8% / 0.4%.
