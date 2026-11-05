## bank.utils.FeesCalculator: withdrawal fee

Test class: `src/test/java/bank/utils/FeesCalculatorWithdrawalTest.java` (JUnit 5 parameterized test, 980 cases).
Test case table: `src/test/resources/bank/utils/withdrawal-fee-cases.csv`.

### Process and assumptions

**Technique.** The specification asks for **Robust Worst Case Boundary Value Analysis** for withdrawal. Unlike the Session part, it does not mention the single fault assumption, so we applied the full technique: every input takes its robust boundary values (min-, min, min+, nominal, max-, max, max+) and the test cases are the **Cartesian product** of all of them. This deliberately combines boundaries of different inputs, so a fault that only shows up for a particular combination (for example, a student on a weekend, or a non-student just above a balance threshold) is still exercised.

**Method under test.** `calculateWithdrawalFee(double amount, double accountBalance, boolean student, int dayOfWeek)` returns the fee in dollars. `dayOfWeek` uses the `java.util.Calendar` constants (`SUNDAY` = 1, `MONDAY` = 2, ..., `SATURDAY` = 7).

**Specification (withdrawal fee rules).**

| Client | Condition | Fee |
|---|---|---|
| Student | weekday (Mon-Fri) | none |
| Student | weekend (Sat, Sun) | 0.1% of amount |
| Not a student | balance < $1,000 | 0.3% of amount |
| Not a student | $1,000 <= balance < $5,000 | 0.1% of amount |
| Not a student | balance >= $5,000 | none |

**Variables and boundaries.**

- *Student* (boolean). A boolean has no boundaries, so both values are used: `true`, `false`.
- *Day of week* (integer, valid domain 1 to 7). The Calendar numbering puts the weekend at both ends of the domain (Sunday = 1, Saturday = 7), so the standard robust values land exactly on the weekday/weekend transitions:
  - min- 0 (invalid), min 1 (Sun, weekend), min+ 2 (Mon, weekday), nominal 4 (Wed), max- 6 (Fri, weekday), max 7 (Sat, weekend), max+ 8 (invalid).
- *Amount* (dollars, step $0.01). **The fee rules give no range for the amount**, so we assumed the valid range is $0.01 to $1,000.00: a withdrawal must be positive, and the assignment limits a withdrawal to the $1,000 daily limit (the same upper boundary used in the Session tests).
  - min- 0.00, min 0.01, min+ 0.02, nominal 500.00, max- 999.99, max 1,000.00, max+ 1,000.01.
- *Balance* (dollars, step $0.01). The valid domain is balance >= $0 (we assumed a negative balance is invalid input). The fee rules have **two internal boundaries**, $1,000 and $5,000, which matter more than the domain edges, so we took the robust values at the lower domain edge plus the values on both sides of each threshold. **The rules define no upper limit on the balance** (every balance of $5,000 or more is in the same no-fee band), so there is no max/max+ beyond the $5,000 threshold.
  - Lower edge: min- -0.01 (invalid), min 0.00, min+ 0.01.
  - $1,000 threshold: 999.99, 1,000.00, 1,000.01.
  - Nominal: 2,500.00.
  - $5,000 threshold: 4,999.99, 5,000.00, 5,000.01.

**Number of cases.** 2 (student) x 7 (day) x 10 (balance) x 7 (amount) = **980 cases**, `WD-001` to `WD-980`. Of these, 450 have all inputs valid, and 530 contain at least one invalid value (worst case analysis keeps every combination, including several invalid values at once).

**Expected results.** All expected values come from the fee rules above, never from the code. The expected fee is `amount x rate`, compared with a tolerance of 0.000001 (the smallest expected non-zero fee is $0.01 x 0.1% = $0.00001). The fee rules do not say what happens on invalid input; we assumed `calculateWithdrawalFee` rejects it with an `IllegalArgumentException`, which is the same assumption the transfer tests make. A case is invalid when the amount is <= 0 or > $1,000, the balance is negative, or the day is outside 1 to 7.

**Test implementation.** One `@ParameterizedTest` reads every row of `withdrawal-fee-cases.csv` (`@CsvFileSource`). Each row holds the case ID, the four inputs, the expected rate in percent (or `INVALID`) and the **specification rule the case traces to**. The JUnit display name shows the ID, the inputs and the rule, e.g. `WD-097: student=true, day=1, balance=999.99, amount=1000.00 | Rule: Withdrawal: student; weekend -> 0.1%`, and the ID and rule are the assertion message, so a failure in the CI report points straight back to the table and to the specification.

### Test results

Totals: **980 tests, 100 pass, 880 fail.**

| Group of cases | Cases | Expected | Result |
|---|---|---|---|
| Student, valid inputs, weekday (Mon, Wed, Fri) | 135 | no fee | all **FAIL** (FW1) |
| Student, valid inputs, weekend (Sat, Sun) | 90 | 0.1% | all **FAIL** (FW1) |
| Not a student, valid inputs, balance < $1,000 | 75 | 0.3% | all **FAIL** (FW2) |
| Not a student, valid inputs, $1,000 <= balance < $5,000 | 100 | 0.1% | all PASS |
| Not a student, valid inputs, balance >= $5,000 | 50 | no fee | all **FAIL** (FW3) |
| At least one invalid input | 530 | `IllegalArgumentException` | all **FAIL** (FW4) |

Representative cases:

| ID | Student | Day | Balance | Amount | Expected | Actual | Result |
|---|---|---|---|---|---|---|---|
| WD-097 | true | 1 Sun | 999.99 | 1,000.00 | 0.1% = 1.00000 | 0.00000 | **FAIL** |
| WD-186 | true | 2 Mon | 2,500.00 | 500.00 | no fee = 0.00000 | 0.50000 | **FAIL** |
| WD-725 | false | 4 Wed | 999.99 | 500.00 | 0.3% = 1.50000 | 1.00000 | **FAIL** |
| WD-613 | false | 1 Sun | 4,999.99 | 500.00 | 0.1% = 0.50000 | 0.50000 | PASS |
| WD-760 | false | 4 Wed | 5,000.00 | 500.00 | no fee = 0.00000 | 0.50000 | **FAIL** |
| WD-001 | true | 0 (invalid) | -0.01 | 0.00 | `IllegalArgumentException` | no exception | **FAIL** |
| WD-536 | false | 0 (invalid) | 2,500.00 | 500.00 | `IllegalArgumentException` | no exception | **FAIL** |

WD-613 and WD-760 show the $5,000 boundary directly: one cent below it the fee is correct, at it the fee is wrong.

The full table of all 980 cases with their results is at the end of this section.

### Fault localization

All four faults are in `bank.utils.FeesCalculator.calculateWithdrawalFee`.

| Fault | Failing tests | Suspected fault (class, method, line) | Justification |
|---|---|---|---|
| FW1 | All 225 valid student cases (e.g. WD-097, WD-186) | `FeesCalculator.calculateWithdrawalFee`, lines 12-16: weekend check `(dayOfWeek == Calendar.SATURDAY) \|\| (dayOfWeek == Calendar.SUNDAY)` sets 0.0, else 0.001 | The two branches are swapped: students are charged 0.1% on weekdays and nothing on weekends, the exact opposite of the rule, which is why every student case fails, both weekday and weekend. |
| FW2 | All 75 valid non-student cases with balance < $1,000 (e.g. WD-725) | `FeesCalculator.calculateWithdrawalFee`, line 19: `feePercentage = 0.002` | The rate for a balance under $1,000 is 0.2% instead of the specified 0.3%, so every such case returns two thirds of the expected fee (WD-725: $1.00 instead of $1.50). |
| FW3 | All 50 valid non-student cases with balance >= $5,000 (e.g. WD-760) | `FeesCalculator.calculateWithdrawalFee`, line 20: `else if (accountBalance < 10000.00)` | The upper threshold is $10,000 instead of $5,000, so balances from $5,000 up to $9,999.99 are still charged 0.1% instead of no fee; WD-613 ($4,999.99) passes while WD-760 ($5,000.00) fails. |
| FW4 | All 530 cases with an invalid input (e.g. WD-001, WD-536) | `FeesCalculator.calculateWithdrawalFee`, line 6 (method entry): no input validation before the fee calculation | Expected but missing: the method never checks the amount, balance or day, so a zero, negative or over-limit amount, a negative balance, or a day outside 1-7 still returns a fee instead of being rejected (this rests on our `IllegalArgumentException` assumption). |

Note on FW4: an invalid day such as 0 or 8 is not a weekend constant, so for a student it falls into the weekday branch and is charged a fee as if it were a valid weekday.

### Full test case table and results

<details>
<summary>All 980 withdrawal cases (click to expand)</summary>

| ID | Student | Day | Balance | Amount | Spec rule | Expected | Actual | Result | Fault |
|---|---|---|---|---|---|---|---|---|---|
| WD-001 | true | 0 (invalid) | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-002 | true | 0 (invalid) | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-003 | true | 0 (invalid) | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-004 | true | 0 (invalid) | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-005 | true | 0 (invalid) | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-006 | true | 0 (invalid) | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-007 | true | 0 (invalid) | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-008 | true | 0 (invalid) | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-009 | true | 0 (invalid) | 0.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-010 | true | 0 (invalid) | 0.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-011 | true | 0 (invalid) | 0.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-012 | true | 0 (invalid) | 0.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-013 | true | 0 (invalid) | 0.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-014 | true | 0 (invalid) | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-015 | true | 0 (invalid) | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-016 | true | 0 (invalid) | 0.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-017 | true | 0 (invalid) | 0.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-018 | true | 0 (invalid) | 0.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-019 | true | 0 (invalid) | 0.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-020 | true | 0 (invalid) | 0.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-021 | true | 0 (invalid) | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-022 | true | 0 (invalid) | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-023 | true | 0 (invalid) | 999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-024 | true | 0 (invalid) | 999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-025 | true | 0 (invalid) | 999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-026 | true | 0 (invalid) | 999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-027 | true | 0 (invalid) | 999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-028 | true | 0 (invalid) | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-029 | true | 0 (invalid) | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-030 | true | 0 (invalid) | 1000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-031 | true | 0 (invalid) | 1000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-032 | true | 0 (invalid) | 1000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-033 | true | 0 (invalid) | 1000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-034 | true | 0 (invalid) | 1000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-035 | true | 0 (invalid) | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-036 | true | 0 (invalid) | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-037 | true | 0 (invalid) | 1000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-038 | true | 0 (invalid) | 1000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-039 | true | 0 (invalid) | 1000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-040 | true | 0 (invalid) | 1000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-041 | true | 0 (invalid) | 1000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-042 | true | 0 (invalid) | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-043 | true | 0 (invalid) | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-044 | true | 0 (invalid) | 2500.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-045 | true | 0 (invalid) | 2500.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-046 | true | 0 (invalid) | 2500.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-047 | true | 0 (invalid) | 2500.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-048 | true | 0 (invalid) | 2500.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-049 | true | 0 (invalid) | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-050 | true | 0 (invalid) | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-051 | true | 0 (invalid) | 4999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-052 | true | 0 (invalid) | 4999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-053 | true | 0 (invalid) | 4999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-054 | true | 0 (invalid) | 4999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-055 | true | 0 (invalid) | 4999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-056 | true | 0 (invalid) | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-057 | true | 0 (invalid) | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-058 | true | 0 (invalid) | 5000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-059 | true | 0 (invalid) | 5000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-060 | true | 0 (invalid) | 5000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-061 | true | 0 (invalid) | 5000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-062 | true | 0 (invalid) | 5000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-063 | true | 0 (invalid) | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-064 | true | 0 (invalid) | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-065 | true | 0 (invalid) | 5000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-066 | true | 0 (invalid) | 5000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-067 | true | 0 (invalid) | 5000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-068 | true | 0 (invalid) | 5000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-069 | true | 0 (invalid) | 5000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-070 | true | 0 (invalid) | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-071 | true | 1 Sun | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-072 | true | 1 Sun | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-073 | true | 1 Sun | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-074 | true | 1 Sun | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-075 | true | 1 Sun | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-076 | true | 1 Sun | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-077 | true | 1 Sun | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-078 | true | 1 Sun | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-079 | true | 1 Sun | 0.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-080 | true | 1 Sun | 0.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-081 | true | 1 Sun | 0.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-082 | true | 1 Sun | 0.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-083 | true | 1 Sun | 0.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-084 | true | 1 Sun | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-085 | true | 1 Sun | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-086 | true | 1 Sun | 0.01 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-087 | true | 1 Sun | 0.01 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-088 | true | 1 Sun | 0.01 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-089 | true | 1 Sun | 0.01 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-090 | true | 1 Sun | 0.01 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-091 | true | 1 Sun | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-092 | true | 1 Sun | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-093 | true | 1 Sun | 999.99 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-094 | true | 1 Sun | 999.99 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-095 | true | 1 Sun | 999.99 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-096 | true | 1 Sun | 999.99 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-097 | true | 1 Sun | 999.99 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-098 | true | 1 Sun | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-099 | true | 1 Sun | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-100 | true | 1 Sun | 1000.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-101 | true | 1 Sun | 1000.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-102 | true | 1 Sun | 1000.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-103 | true | 1 Sun | 1000.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-104 | true | 1 Sun | 1000.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-105 | true | 1 Sun | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-106 | true | 1 Sun | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-107 | true | 1 Sun | 1000.01 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-108 | true | 1 Sun | 1000.01 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-109 | true | 1 Sun | 1000.01 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-110 | true | 1 Sun | 1000.01 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-111 | true | 1 Sun | 1000.01 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-112 | true | 1 Sun | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-113 | true | 1 Sun | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-114 | true | 1 Sun | 2500.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-115 | true | 1 Sun | 2500.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-116 | true | 1 Sun | 2500.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-117 | true | 1 Sun | 2500.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-118 | true | 1 Sun | 2500.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-119 | true | 1 Sun | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-120 | true | 1 Sun | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-121 | true | 1 Sun | 4999.99 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-122 | true | 1 Sun | 4999.99 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-123 | true | 1 Sun | 4999.99 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-124 | true | 1 Sun | 4999.99 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-125 | true | 1 Sun | 4999.99 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-126 | true | 1 Sun | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-127 | true | 1 Sun | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-128 | true | 1 Sun | 5000.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-129 | true | 1 Sun | 5000.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-130 | true | 1 Sun | 5000.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-131 | true | 1 Sun | 5000.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-132 | true | 1 Sun | 5000.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-133 | true | 1 Sun | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-134 | true | 1 Sun | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-135 | true | 1 Sun | 5000.01 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-136 | true | 1 Sun | 5000.01 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-137 | true | 1 Sun | 5000.01 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-138 | true | 1 Sun | 5000.01 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-139 | true | 1 Sun | 5000.01 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-140 | true | 1 Sun | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-141 | true | 2 Mon | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-142 | true | 2 Mon | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-143 | true | 2 Mon | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-144 | true | 2 Mon | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-145 | true | 2 Mon | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-146 | true | 2 Mon | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-147 | true | 2 Mon | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-148 | true | 2 Mon | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-149 | true | 2 Mon | 0.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-150 | true | 2 Mon | 0.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-151 | true | 2 Mon | 0.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-152 | true | 2 Mon | 0.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-153 | true | 2 Mon | 0.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-154 | true | 2 Mon | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-155 | true | 2 Mon | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-156 | true | 2 Mon | 0.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-157 | true | 2 Mon | 0.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-158 | true | 2 Mon | 0.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-159 | true | 2 Mon | 0.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-160 | true | 2 Mon | 0.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-161 | true | 2 Mon | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-162 | true | 2 Mon | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-163 | true | 2 Mon | 999.99 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-164 | true | 2 Mon | 999.99 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-165 | true | 2 Mon | 999.99 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-166 | true | 2 Mon | 999.99 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-167 | true | 2 Mon | 999.99 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-168 | true | 2 Mon | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-169 | true | 2 Mon | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-170 | true | 2 Mon | 1000.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-171 | true | 2 Mon | 1000.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-172 | true | 2 Mon | 1000.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-173 | true | 2 Mon | 1000.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-174 | true | 2 Mon | 1000.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-175 | true | 2 Mon | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-176 | true | 2 Mon | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-177 | true | 2 Mon | 1000.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-178 | true | 2 Mon | 1000.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-179 | true | 2 Mon | 1000.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-180 | true | 2 Mon | 1000.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-181 | true | 2 Mon | 1000.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-182 | true | 2 Mon | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-183 | true | 2 Mon | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-184 | true | 2 Mon | 2500.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-185 | true | 2 Mon | 2500.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-186 | true | 2 Mon | 2500.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-187 | true | 2 Mon | 2500.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-188 | true | 2 Mon | 2500.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-189 | true | 2 Mon | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-190 | true | 2 Mon | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-191 | true | 2 Mon | 4999.99 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-192 | true | 2 Mon | 4999.99 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-193 | true | 2 Mon | 4999.99 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-194 | true | 2 Mon | 4999.99 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-195 | true | 2 Mon | 4999.99 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-196 | true | 2 Mon | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-197 | true | 2 Mon | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-198 | true | 2 Mon | 5000.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-199 | true | 2 Mon | 5000.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-200 | true | 2 Mon | 5000.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-201 | true | 2 Mon | 5000.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-202 | true | 2 Mon | 5000.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-203 | true | 2 Mon | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-204 | true | 2 Mon | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-205 | true | 2 Mon | 5000.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-206 | true | 2 Mon | 5000.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-207 | true | 2 Mon | 5000.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-208 | true | 2 Mon | 5000.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-209 | true | 2 Mon | 5000.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-210 | true | 2 Mon | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-211 | true | 4 Wed | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-212 | true | 4 Wed | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-213 | true | 4 Wed | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-214 | true | 4 Wed | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-215 | true | 4 Wed | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-216 | true | 4 Wed | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-217 | true | 4 Wed | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-218 | true | 4 Wed | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-219 | true | 4 Wed | 0.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-220 | true | 4 Wed | 0.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-221 | true | 4 Wed | 0.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-222 | true | 4 Wed | 0.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-223 | true | 4 Wed | 0.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-224 | true | 4 Wed | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-225 | true | 4 Wed | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-226 | true | 4 Wed | 0.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-227 | true | 4 Wed | 0.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-228 | true | 4 Wed | 0.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-229 | true | 4 Wed | 0.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-230 | true | 4 Wed | 0.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-231 | true | 4 Wed | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-232 | true | 4 Wed | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-233 | true | 4 Wed | 999.99 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-234 | true | 4 Wed | 999.99 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-235 | true | 4 Wed | 999.99 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-236 | true | 4 Wed | 999.99 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-237 | true | 4 Wed | 999.99 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-238 | true | 4 Wed | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-239 | true | 4 Wed | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-240 | true | 4 Wed | 1000.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-241 | true | 4 Wed | 1000.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-242 | true | 4 Wed | 1000.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-243 | true | 4 Wed | 1000.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-244 | true | 4 Wed | 1000.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-245 | true | 4 Wed | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-246 | true | 4 Wed | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-247 | true | 4 Wed | 1000.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-248 | true | 4 Wed | 1000.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-249 | true | 4 Wed | 1000.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-250 | true | 4 Wed | 1000.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-251 | true | 4 Wed | 1000.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-252 | true | 4 Wed | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-253 | true | 4 Wed | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-254 | true | 4 Wed | 2500.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-255 | true | 4 Wed | 2500.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-256 | true | 4 Wed | 2500.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-257 | true | 4 Wed | 2500.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-258 | true | 4 Wed | 2500.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-259 | true | 4 Wed | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-260 | true | 4 Wed | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-261 | true | 4 Wed | 4999.99 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-262 | true | 4 Wed | 4999.99 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-263 | true | 4 Wed | 4999.99 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-264 | true | 4 Wed | 4999.99 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-265 | true | 4 Wed | 4999.99 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-266 | true | 4 Wed | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-267 | true | 4 Wed | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-268 | true | 4 Wed | 5000.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-269 | true | 4 Wed | 5000.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-270 | true | 4 Wed | 5000.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-271 | true | 4 Wed | 5000.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-272 | true | 4 Wed | 5000.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-273 | true | 4 Wed | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-274 | true | 4 Wed | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-275 | true | 4 Wed | 5000.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-276 | true | 4 Wed | 5000.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-277 | true | 4 Wed | 5000.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-278 | true | 4 Wed | 5000.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-279 | true | 4 Wed | 5000.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-280 | true | 4 Wed | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-281 | true | 6 Fri | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-282 | true | 6 Fri | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-283 | true | 6 Fri | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-284 | true | 6 Fri | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-285 | true | 6 Fri | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-286 | true | 6 Fri | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-287 | true | 6 Fri | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-288 | true | 6 Fri | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-289 | true | 6 Fri | 0.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-290 | true | 6 Fri | 0.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-291 | true | 6 Fri | 0.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-292 | true | 6 Fri | 0.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-293 | true | 6 Fri | 0.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-294 | true | 6 Fri | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-295 | true | 6 Fri | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-296 | true | 6 Fri | 0.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-297 | true | 6 Fri | 0.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-298 | true | 6 Fri | 0.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-299 | true | 6 Fri | 0.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-300 | true | 6 Fri | 0.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-301 | true | 6 Fri | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-302 | true | 6 Fri | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-303 | true | 6 Fri | 999.99 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-304 | true | 6 Fri | 999.99 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-305 | true | 6 Fri | 999.99 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-306 | true | 6 Fri | 999.99 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-307 | true | 6 Fri | 999.99 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-308 | true | 6 Fri | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-309 | true | 6 Fri | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-310 | true | 6 Fri | 1000.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-311 | true | 6 Fri | 1000.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-312 | true | 6 Fri | 1000.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-313 | true | 6 Fri | 1000.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-314 | true | 6 Fri | 1000.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-315 | true | 6 Fri | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-316 | true | 6 Fri | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-317 | true | 6 Fri | 1000.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-318 | true | 6 Fri | 1000.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-319 | true | 6 Fri | 1000.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-320 | true | 6 Fri | 1000.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-321 | true | 6 Fri | 1000.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-322 | true | 6 Fri | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-323 | true | 6 Fri | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-324 | true | 6 Fri | 2500.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-325 | true | 6 Fri | 2500.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-326 | true | 6 Fri | 2500.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-327 | true | 6 Fri | 2500.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-328 | true | 6 Fri | 2500.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-329 | true | 6 Fri | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-330 | true | 6 Fri | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-331 | true | 6 Fri | 4999.99 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-332 | true | 6 Fri | 4999.99 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-333 | true | 6 Fri | 4999.99 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-334 | true | 6 Fri | 4999.99 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-335 | true | 6 Fri | 4999.99 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-336 | true | 6 Fri | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-337 | true | 6 Fri | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-338 | true | 6 Fri | 5000.00 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-339 | true | 6 Fri | 5000.00 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-340 | true | 6 Fri | 5000.00 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-341 | true | 6 Fri | 5000.00 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-342 | true | 6 Fri | 5000.00 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-343 | true | 6 Fri | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-344 | true | 6 Fri | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-345 | true | 6 Fri | 5000.01 | 0.01 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW1 |
| WD-346 | true | 6 Fri | 5000.01 | 0.02 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW1 |
| WD-347 | true | 6 Fri | 5000.01 | 500.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW1 |
| WD-348 | true | 6 Fri | 5000.01 | 999.99 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW1 |
| WD-349 | true | 6 Fri | 5000.01 | 1000.00 | Withdrawal: student; weekday -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW1 |
| WD-350 | true | 6 Fri | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-351 | true | 7 Sat | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-352 | true | 7 Sat | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-353 | true | 7 Sat | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-354 | true | 7 Sat | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-355 | true | 7 Sat | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-356 | true | 7 Sat | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-357 | true | 7 Sat | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-358 | true | 7 Sat | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-359 | true | 7 Sat | 0.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-360 | true | 7 Sat | 0.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-361 | true | 7 Sat | 0.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-362 | true | 7 Sat | 0.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-363 | true | 7 Sat | 0.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-364 | true | 7 Sat | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-365 | true | 7 Sat | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-366 | true | 7 Sat | 0.01 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-367 | true | 7 Sat | 0.01 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-368 | true | 7 Sat | 0.01 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-369 | true | 7 Sat | 0.01 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-370 | true | 7 Sat | 0.01 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-371 | true | 7 Sat | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-372 | true | 7 Sat | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-373 | true | 7 Sat | 999.99 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-374 | true | 7 Sat | 999.99 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-375 | true | 7 Sat | 999.99 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-376 | true | 7 Sat | 999.99 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-377 | true | 7 Sat | 999.99 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-378 | true | 7 Sat | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-379 | true | 7 Sat | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-380 | true | 7 Sat | 1000.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-381 | true | 7 Sat | 1000.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-382 | true | 7 Sat | 1000.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-383 | true | 7 Sat | 1000.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-384 | true | 7 Sat | 1000.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-385 | true | 7 Sat | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-386 | true | 7 Sat | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-387 | true | 7 Sat | 1000.01 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-388 | true | 7 Sat | 1000.01 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-389 | true | 7 Sat | 1000.01 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-390 | true | 7 Sat | 1000.01 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-391 | true | 7 Sat | 1000.01 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-392 | true | 7 Sat | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-393 | true | 7 Sat | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-394 | true | 7 Sat | 2500.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-395 | true | 7 Sat | 2500.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-396 | true | 7 Sat | 2500.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-397 | true | 7 Sat | 2500.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-398 | true | 7 Sat | 2500.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-399 | true | 7 Sat | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-400 | true | 7 Sat | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-401 | true | 7 Sat | 4999.99 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-402 | true | 7 Sat | 4999.99 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-403 | true | 7 Sat | 4999.99 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-404 | true | 7 Sat | 4999.99 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-405 | true | 7 Sat | 4999.99 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-406 | true | 7 Sat | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-407 | true | 7 Sat | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-408 | true | 7 Sat | 5000.00 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-409 | true | 7 Sat | 5000.00 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-410 | true | 7 Sat | 5000.00 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-411 | true | 7 Sat | 5000.00 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-412 | true | 7 Sat | 5000.00 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-413 | true | 7 Sat | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-414 | true | 7 Sat | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-415 | true | 7 Sat | 5000.01 | 0.01 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00001 | fee 0.00000 | **FAIL** | FW1 |
| WD-416 | true | 7 Sat | 5000.01 | 0.02 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.00002 | fee 0.00000 | **FAIL** | FW1 |
| WD-417 | true | 7 Sat | 5000.01 | 500.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.50000 | fee 0.00000 | **FAIL** | FW1 |
| WD-418 | true | 7 Sat | 5000.01 | 999.99 | Withdrawal: student; weekend -> 0.1% | 0.1% = 0.99999 | fee 0.00000 | **FAIL** | FW1 |
| WD-419 | true | 7 Sat | 5000.01 | 1000.00 | Withdrawal: student; weekend -> 0.1% | 0.1% = 1.00000 | fee 0.00000 | **FAIL** | FW1 |
| WD-420 | true | 7 Sat | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-421 | true | 8 (invalid) | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-422 | true | 8 (invalid) | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-423 | true | 8 (invalid) | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-424 | true | 8 (invalid) | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-425 | true | 8 (invalid) | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-426 | true | 8 (invalid) | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-427 | true | 8 (invalid) | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-428 | true | 8 (invalid) | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-429 | true | 8 (invalid) | 0.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-430 | true | 8 (invalid) | 0.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-431 | true | 8 (invalid) | 0.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-432 | true | 8 (invalid) | 0.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-433 | true | 8 (invalid) | 0.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-434 | true | 8 (invalid) | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-435 | true | 8 (invalid) | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-436 | true | 8 (invalid) | 0.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-437 | true | 8 (invalid) | 0.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-438 | true | 8 (invalid) | 0.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-439 | true | 8 (invalid) | 0.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-440 | true | 8 (invalid) | 0.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-441 | true | 8 (invalid) | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-442 | true | 8 (invalid) | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-443 | true | 8 (invalid) | 999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-444 | true | 8 (invalid) | 999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-445 | true | 8 (invalid) | 999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-446 | true | 8 (invalid) | 999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-447 | true | 8 (invalid) | 999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-448 | true | 8 (invalid) | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-449 | true | 8 (invalid) | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-450 | true | 8 (invalid) | 1000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-451 | true | 8 (invalid) | 1000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-452 | true | 8 (invalid) | 1000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-453 | true | 8 (invalid) | 1000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-454 | true | 8 (invalid) | 1000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-455 | true | 8 (invalid) | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-456 | true | 8 (invalid) | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-457 | true | 8 (invalid) | 1000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-458 | true | 8 (invalid) | 1000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-459 | true | 8 (invalid) | 1000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-460 | true | 8 (invalid) | 1000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-461 | true | 8 (invalid) | 1000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-462 | true | 8 (invalid) | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-463 | true | 8 (invalid) | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-464 | true | 8 (invalid) | 2500.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-465 | true | 8 (invalid) | 2500.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-466 | true | 8 (invalid) | 2500.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-467 | true | 8 (invalid) | 2500.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-468 | true | 8 (invalid) | 2500.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-469 | true | 8 (invalid) | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-470 | true | 8 (invalid) | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-471 | true | 8 (invalid) | 4999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-472 | true | 8 (invalid) | 4999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-473 | true | 8 (invalid) | 4999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-474 | true | 8 (invalid) | 4999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-475 | true | 8 (invalid) | 4999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-476 | true | 8 (invalid) | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-477 | true | 8 (invalid) | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-478 | true | 8 (invalid) | 5000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-479 | true | 8 (invalid) | 5000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-480 | true | 8 (invalid) | 5000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-481 | true | 8 (invalid) | 5000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-482 | true | 8 (invalid) | 5000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-483 | true | 8 (invalid) | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-484 | true | 8 (invalid) | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-485 | true | 8 (invalid) | 5000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-486 | true | 8 (invalid) | 5000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-487 | true | 8 (invalid) | 5000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-488 | true | 8 (invalid) | 5000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-489 | true | 8 (invalid) | 5000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-490 | true | 8 (invalid) | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-491 | false | 0 (invalid) | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-492 | false | 0 (invalid) | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-493 | false | 0 (invalid) | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-494 | false | 0 (invalid) | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-495 | false | 0 (invalid) | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-496 | false | 0 (invalid) | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-497 | false | 0 (invalid) | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-498 | false | 0 (invalid) | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-499 | false | 0 (invalid) | 0.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-500 | false | 0 (invalid) | 0.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-501 | false | 0 (invalid) | 0.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-502 | false | 0 (invalid) | 0.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-503 | false | 0 (invalid) | 0.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-504 | false | 0 (invalid) | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-505 | false | 0 (invalid) | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-506 | false | 0 (invalid) | 0.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-507 | false | 0 (invalid) | 0.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-508 | false | 0 (invalid) | 0.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-509 | false | 0 (invalid) | 0.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-510 | false | 0 (invalid) | 0.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-511 | false | 0 (invalid) | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-512 | false | 0 (invalid) | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-513 | false | 0 (invalid) | 999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-514 | false | 0 (invalid) | 999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-515 | false | 0 (invalid) | 999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-516 | false | 0 (invalid) | 999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-517 | false | 0 (invalid) | 999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-518 | false | 0 (invalid) | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-519 | false | 0 (invalid) | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-520 | false | 0 (invalid) | 1000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-521 | false | 0 (invalid) | 1000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-522 | false | 0 (invalid) | 1000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-523 | false | 0 (invalid) | 1000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-524 | false | 0 (invalid) | 1000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-525 | false | 0 (invalid) | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-526 | false | 0 (invalid) | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-527 | false | 0 (invalid) | 1000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-528 | false | 0 (invalid) | 1000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-529 | false | 0 (invalid) | 1000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-530 | false | 0 (invalid) | 1000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-531 | false | 0 (invalid) | 1000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-532 | false | 0 (invalid) | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-533 | false | 0 (invalid) | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-534 | false | 0 (invalid) | 2500.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-535 | false | 0 (invalid) | 2500.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-536 | false | 0 (invalid) | 2500.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-537 | false | 0 (invalid) | 2500.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-538 | false | 0 (invalid) | 2500.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-539 | false | 0 (invalid) | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-540 | false | 0 (invalid) | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-541 | false | 0 (invalid) | 4999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-542 | false | 0 (invalid) | 4999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-543 | false | 0 (invalid) | 4999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-544 | false | 0 (invalid) | 4999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-545 | false | 0 (invalid) | 4999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-546 | false | 0 (invalid) | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-547 | false | 0 (invalid) | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-548 | false | 0 (invalid) | 5000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-549 | false | 0 (invalid) | 5000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-550 | false | 0 (invalid) | 5000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-551 | false | 0 (invalid) | 5000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-552 | false | 0 (invalid) | 5000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-553 | false | 0 (invalid) | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-554 | false | 0 (invalid) | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-555 | false | 0 (invalid) | 5000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-556 | false | 0 (invalid) | 5000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-557 | false | 0 (invalid) | 5000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-558 | false | 0 (invalid) | 5000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-559 | false | 0 (invalid) | 5000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-560 | false | 0 (invalid) | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-561 | false | 1 Sun | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-562 | false | 1 Sun | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-563 | false | 1 Sun | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-564 | false | 1 Sun | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-565 | false | 1 Sun | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-566 | false | 1 Sun | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-567 | false | 1 Sun | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-568 | false | 1 Sun | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-569 | false | 1 Sun | 0.00 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-570 | false | 1 Sun | 0.00 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-571 | false | 1 Sun | 0.00 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-572 | false | 1 Sun | 0.00 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-573 | false | 1 Sun | 0.00 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-574 | false | 1 Sun | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-575 | false | 1 Sun | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-576 | false | 1 Sun | 0.01 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-577 | false | 1 Sun | 0.01 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-578 | false | 1 Sun | 0.01 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-579 | false | 1 Sun | 0.01 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-580 | false | 1 Sun | 0.01 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-581 | false | 1 Sun | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-582 | false | 1 Sun | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-583 | false | 1 Sun | 999.99 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-584 | false | 1 Sun | 999.99 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-585 | false | 1 Sun | 999.99 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-586 | false | 1 Sun | 999.99 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-587 | false | 1 Sun | 999.99 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-588 | false | 1 Sun | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-589 | false | 1 Sun | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-590 | false | 1 Sun | 1000.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-591 | false | 1 Sun | 1000.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-592 | false | 1 Sun | 1000.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-593 | false | 1 Sun | 1000.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-594 | false | 1 Sun | 1000.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-595 | false | 1 Sun | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-596 | false | 1 Sun | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-597 | false | 1 Sun | 1000.01 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-598 | false | 1 Sun | 1000.01 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-599 | false | 1 Sun | 1000.01 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-600 | false | 1 Sun | 1000.01 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-601 | false | 1 Sun | 1000.01 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-602 | false | 1 Sun | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-603 | false | 1 Sun | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-604 | false | 1 Sun | 2500.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-605 | false | 1 Sun | 2500.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-606 | false | 1 Sun | 2500.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-607 | false | 1 Sun | 2500.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-608 | false | 1 Sun | 2500.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-609 | false | 1 Sun | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-610 | false | 1 Sun | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-611 | false | 1 Sun | 4999.99 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-612 | false | 1 Sun | 4999.99 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-613 | false | 1 Sun | 4999.99 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-614 | false | 1 Sun | 4999.99 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-615 | false | 1 Sun | 4999.99 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-616 | false | 1 Sun | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-617 | false | 1 Sun | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-618 | false | 1 Sun | 5000.00 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-619 | false | 1 Sun | 5000.00 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-620 | false | 1 Sun | 5000.00 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-621 | false | 1 Sun | 5000.00 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-622 | false | 1 Sun | 5000.00 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-623 | false | 1 Sun | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-624 | false | 1 Sun | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-625 | false | 1 Sun | 5000.01 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-626 | false | 1 Sun | 5000.01 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-627 | false | 1 Sun | 5000.01 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-628 | false | 1 Sun | 5000.01 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-629 | false | 1 Sun | 5000.01 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-630 | false | 1 Sun | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-631 | false | 2 Mon | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-632 | false | 2 Mon | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-633 | false | 2 Mon | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-634 | false | 2 Mon | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-635 | false | 2 Mon | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-636 | false | 2 Mon | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-637 | false | 2 Mon | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-638 | false | 2 Mon | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-639 | false | 2 Mon | 0.00 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-640 | false | 2 Mon | 0.00 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-641 | false | 2 Mon | 0.00 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-642 | false | 2 Mon | 0.00 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-643 | false | 2 Mon | 0.00 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-644 | false | 2 Mon | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-645 | false | 2 Mon | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-646 | false | 2 Mon | 0.01 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-647 | false | 2 Mon | 0.01 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-648 | false | 2 Mon | 0.01 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-649 | false | 2 Mon | 0.01 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-650 | false | 2 Mon | 0.01 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-651 | false | 2 Mon | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-652 | false | 2 Mon | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-653 | false | 2 Mon | 999.99 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-654 | false | 2 Mon | 999.99 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-655 | false | 2 Mon | 999.99 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-656 | false | 2 Mon | 999.99 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-657 | false | 2 Mon | 999.99 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-658 | false | 2 Mon | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-659 | false | 2 Mon | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-660 | false | 2 Mon | 1000.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-661 | false | 2 Mon | 1000.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-662 | false | 2 Mon | 1000.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-663 | false | 2 Mon | 1000.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-664 | false | 2 Mon | 1000.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-665 | false | 2 Mon | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-666 | false | 2 Mon | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-667 | false | 2 Mon | 1000.01 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-668 | false | 2 Mon | 1000.01 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-669 | false | 2 Mon | 1000.01 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-670 | false | 2 Mon | 1000.01 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-671 | false | 2 Mon | 1000.01 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-672 | false | 2 Mon | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-673 | false | 2 Mon | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-674 | false | 2 Mon | 2500.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-675 | false | 2 Mon | 2500.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-676 | false | 2 Mon | 2500.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-677 | false | 2 Mon | 2500.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-678 | false | 2 Mon | 2500.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-679 | false | 2 Mon | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-680 | false | 2 Mon | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-681 | false | 2 Mon | 4999.99 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-682 | false | 2 Mon | 4999.99 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-683 | false | 2 Mon | 4999.99 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-684 | false | 2 Mon | 4999.99 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-685 | false | 2 Mon | 4999.99 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-686 | false | 2 Mon | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-687 | false | 2 Mon | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-688 | false | 2 Mon | 5000.00 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-689 | false | 2 Mon | 5000.00 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-690 | false | 2 Mon | 5000.00 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-691 | false | 2 Mon | 5000.00 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-692 | false | 2 Mon | 5000.00 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-693 | false | 2 Mon | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-694 | false | 2 Mon | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-695 | false | 2 Mon | 5000.01 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-696 | false | 2 Mon | 5000.01 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-697 | false | 2 Mon | 5000.01 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-698 | false | 2 Mon | 5000.01 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-699 | false | 2 Mon | 5000.01 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-700 | false | 2 Mon | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-701 | false | 4 Wed | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-702 | false | 4 Wed | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-703 | false | 4 Wed | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-704 | false | 4 Wed | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-705 | false | 4 Wed | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-706 | false | 4 Wed | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-707 | false | 4 Wed | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-708 | false | 4 Wed | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-709 | false | 4 Wed | 0.00 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-710 | false | 4 Wed | 0.00 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-711 | false | 4 Wed | 0.00 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-712 | false | 4 Wed | 0.00 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-713 | false | 4 Wed | 0.00 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-714 | false | 4 Wed | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-715 | false | 4 Wed | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-716 | false | 4 Wed | 0.01 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-717 | false | 4 Wed | 0.01 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-718 | false | 4 Wed | 0.01 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-719 | false | 4 Wed | 0.01 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-720 | false | 4 Wed | 0.01 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-721 | false | 4 Wed | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-722 | false | 4 Wed | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-723 | false | 4 Wed | 999.99 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-724 | false | 4 Wed | 999.99 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-725 | false | 4 Wed | 999.99 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-726 | false | 4 Wed | 999.99 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-727 | false | 4 Wed | 999.99 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-728 | false | 4 Wed | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-729 | false | 4 Wed | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-730 | false | 4 Wed | 1000.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-731 | false | 4 Wed | 1000.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-732 | false | 4 Wed | 1000.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-733 | false | 4 Wed | 1000.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-734 | false | 4 Wed | 1000.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-735 | false | 4 Wed | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-736 | false | 4 Wed | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-737 | false | 4 Wed | 1000.01 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-738 | false | 4 Wed | 1000.01 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-739 | false | 4 Wed | 1000.01 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-740 | false | 4 Wed | 1000.01 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-741 | false | 4 Wed | 1000.01 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-742 | false | 4 Wed | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-743 | false | 4 Wed | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-744 | false | 4 Wed | 2500.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-745 | false | 4 Wed | 2500.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-746 | false | 4 Wed | 2500.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-747 | false | 4 Wed | 2500.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-748 | false | 4 Wed | 2500.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-749 | false | 4 Wed | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-750 | false | 4 Wed | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-751 | false | 4 Wed | 4999.99 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-752 | false | 4 Wed | 4999.99 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-753 | false | 4 Wed | 4999.99 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-754 | false | 4 Wed | 4999.99 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-755 | false | 4 Wed | 4999.99 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-756 | false | 4 Wed | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-757 | false | 4 Wed | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-758 | false | 4 Wed | 5000.00 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-759 | false | 4 Wed | 5000.00 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-760 | false | 4 Wed | 5000.00 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-761 | false | 4 Wed | 5000.00 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-762 | false | 4 Wed | 5000.00 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-763 | false | 4 Wed | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-764 | false | 4 Wed | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-765 | false | 4 Wed | 5000.01 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-766 | false | 4 Wed | 5000.01 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-767 | false | 4 Wed | 5000.01 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-768 | false | 4 Wed | 5000.01 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-769 | false | 4 Wed | 5000.01 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-770 | false | 4 Wed | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-771 | false | 6 Fri | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-772 | false | 6 Fri | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-773 | false | 6 Fri | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-774 | false | 6 Fri | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-775 | false | 6 Fri | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-776 | false | 6 Fri | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-777 | false | 6 Fri | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-778 | false | 6 Fri | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-779 | false | 6 Fri | 0.00 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-780 | false | 6 Fri | 0.00 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-781 | false | 6 Fri | 0.00 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-782 | false | 6 Fri | 0.00 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-783 | false | 6 Fri | 0.00 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-784 | false | 6 Fri | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-785 | false | 6 Fri | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-786 | false | 6 Fri | 0.01 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-787 | false | 6 Fri | 0.01 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-788 | false | 6 Fri | 0.01 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-789 | false | 6 Fri | 0.01 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-790 | false | 6 Fri | 0.01 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-791 | false | 6 Fri | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-792 | false | 6 Fri | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-793 | false | 6 Fri | 999.99 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-794 | false | 6 Fri | 999.99 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-795 | false | 6 Fri | 999.99 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-796 | false | 6 Fri | 999.99 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-797 | false | 6 Fri | 999.99 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-798 | false | 6 Fri | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-799 | false | 6 Fri | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-800 | false | 6 Fri | 1000.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-801 | false | 6 Fri | 1000.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-802 | false | 6 Fri | 1000.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-803 | false | 6 Fri | 1000.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-804 | false | 6 Fri | 1000.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-805 | false | 6 Fri | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-806 | false | 6 Fri | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-807 | false | 6 Fri | 1000.01 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-808 | false | 6 Fri | 1000.01 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-809 | false | 6 Fri | 1000.01 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-810 | false | 6 Fri | 1000.01 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-811 | false | 6 Fri | 1000.01 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-812 | false | 6 Fri | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-813 | false | 6 Fri | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-814 | false | 6 Fri | 2500.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-815 | false | 6 Fri | 2500.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-816 | false | 6 Fri | 2500.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-817 | false | 6 Fri | 2500.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-818 | false | 6 Fri | 2500.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-819 | false | 6 Fri | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-820 | false | 6 Fri | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-821 | false | 6 Fri | 4999.99 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-822 | false | 6 Fri | 4999.99 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-823 | false | 6 Fri | 4999.99 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-824 | false | 6 Fri | 4999.99 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-825 | false | 6 Fri | 4999.99 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-826 | false | 6 Fri | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-827 | false | 6 Fri | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-828 | false | 6 Fri | 5000.00 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-829 | false | 6 Fri | 5000.00 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-830 | false | 6 Fri | 5000.00 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-831 | false | 6 Fri | 5000.00 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-832 | false | 6 Fri | 5000.00 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-833 | false | 6 Fri | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-834 | false | 6 Fri | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-835 | false | 6 Fri | 5000.01 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-836 | false | 6 Fri | 5000.01 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-837 | false | 6 Fri | 5000.01 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-838 | false | 6 Fri | 5000.01 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-839 | false | 6 Fri | 5000.01 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-840 | false | 6 Fri | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-841 | false | 7 Sat | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-842 | false | 7 Sat | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-843 | false | 7 Sat | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-844 | false | 7 Sat | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-845 | false | 7 Sat | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-846 | false | 7 Sat | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-847 | false | 7 Sat | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-848 | false | 7 Sat | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-849 | false | 7 Sat | 0.00 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-850 | false | 7 Sat | 0.00 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-851 | false | 7 Sat | 0.00 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-852 | false | 7 Sat | 0.00 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-853 | false | 7 Sat | 0.00 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-854 | false | 7 Sat | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-855 | false | 7 Sat | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-856 | false | 7 Sat | 0.01 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-857 | false | 7 Sat | 0.01 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-858 | false | 7 Sat | 0.01 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-859 | false | 7 Sat | 0.01 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-860 | false | 7 Sat | 0.01 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-861 | false | 7 Sat | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-862 | false | 7 Sat | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-863 | false | 7 Sat | 999.99 | 0.01 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00003 | fee 0.00002 | **FAIL** | FW2 |
| WD-864 | false | 7 Sat | 999.99 | 0.02 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 0.00006 | fee 0.00004 | **FAIL** | FW2 |
| WD-865 | false | 7 Sat | 999.99 | 500.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 1.50000 | fee 1.00000 | **FAIL** | FW2 |
| WD-866 | false | 7 Sat | 999.99 | 999.99 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 2.99997 | fee 1.99998 | **FAIL** | FW2 |
| WD-867 | false | 7 Sat | 999.99 | 1000.00 | Withdrawal: not a student; balance < $1000 -> 0.3% | 0.3% = 3.00000 | fee 2.00000 | **FAIL** | FW2 |
| WD-868 | false | 7 Sat | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-869 | false | 7 Sat | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-870 | false | 7 Sat | 1000.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-871 | false | 7 Sat | 1000.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-872 | false | 7 Sat | 1000.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-873 | false | 7 Sat | 1000.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-874 | false | 7 Sat | 1000.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-875 | false | 7 Sat | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-876 | false | 7 Sat | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-877 | false | 7 Sat | 1000.01 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-878 | false | 7 Sat | 1000.01 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-879 | false | 7 Sat | 1000.01 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-880 | false | 7 Sat | 1000.01 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-881 | false | 7 Sat | 1000.01 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-882 | false | 7 Sat | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-883 | false | 7 Sat | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-884 | false | 7 Sat | 2500.00 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-885 | false | 7 Sat | 2500.00 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-886 | false | 7 Sat | 2500.00 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-887 | false | 7 Sat | 2500.00 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-888 | false | 7 Sat | 2500.00 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-889 | false | 7 Sat | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-890 | false | 7 Sat | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-891 | false | 7 Sat | 4999.99 | 0.01 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00001 | as expected | PASS |  |
| WD-892 | false | 7 Sat | 4999.99 | 0.02 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.00002 | as expected | PASS |  |
| WD-893 | false | 7 Sat | 4999.99 | 500.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.50000 | as expected | PASS |  |
| WD-894 | false | 7 Sat | 4999.99 | 999.99 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 0.99999 | as expected | PASS |  |
| WD-895 | false | 7 Sat | 4999.99 | 1000.00 | Withdrawal: not a student; $1000 <= balance < $5000 -> 0.1% | 0.1% = 1.00000 | as expected | PASS |  |
| WD-896 | false | 7 Sat | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-897 | false | 7 Sat | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-898 | false | 7 Sat | 5000.00 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-899 | false | 7 Sat | 5000.00 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-900 | false | 7 Sat | 5000.00 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-901 | false | 7 Sat | 5000.00 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-902 | false | 7 Sat | 5000.00 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-903 | false | 7 Sat | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-904 | false | 7 Sat | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-905 | false | 7 Sat | 5000.01 | 0.01 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00001 | **FAIL** | FW3 |
| WD-906 | false | 7 Sat | 5000.01 | 0.02 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.00002 | **FAIL** | FW3 |
| WD-907 | false | 7 Sat | 5000.01 | 500.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.50000 | **FAIL** | FW3 |
| WD-908 | false | 7 Sat | 5000.01 | 999.99 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 0.99999 | **FAIL** | FW3 |
| WD-909 | false | 7 Sat | 5000.01 | 1000.00 | Withdrawal: not a student; balance >= $5000 -> no fee | 0% = 0.00000 | fee 1.00000 | **FAIL** | FW3 |
| WD-910 | false | 7 Sat | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-911 | false | 8 (invalid) | -0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-912 | false | 8 (invalid) | -0.01 | 0.01 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-913 | false | 8 (invalid) | -0.01 | 0.02 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-914 | false | 8 (invalid) | -0.01 | 500.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-915 | false | 8 (invalid) | -0.01 | 999.99 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-916 | false | 8 (invalid) | -0.01 | 1000.00 | Invalid input (assumed rejected): balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-917 | false | 8 (invalid) | -0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; balance < $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-918 | false | 8 (invalid) | 0.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-919 | false | 8 (invalid) | 0.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-920 | false | 8 (invalid) | 0.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-921 | false | 8 (invalid) | 0.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-922 | false | 8 (invalid) | 0.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-923 | false | 8 (invalid) | 0.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-924 | false | 8 (invalid) | 0.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-925 | false | 8 (invalid) | 0.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-926 | false | 8 (invalid) | 0.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-927 | false | 8 (invalid) | 0.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-928 | false | 8 (invalid) | 0.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-929 | false | 8 (invalid) | 0.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-930 | false | 8 (invalid) | 0.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-931 | false | 8 (invalid) | 0.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-932 | false | 8 (invalid) | 999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-933 | false | 8 (invalid) | 999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-934 | false | 8 (invalid) | 999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-935 | false | 8 (invalid) | 999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-936 | false | 8 (invalid) | 999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-937 | false | 8 (invalid) | 999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-938 | false | 8 (invalid) | 999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-939 | false | 8 (invalid) | 1000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-940 | false | 8 (invalid) | 1000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-941 | false | 8 (invalid) | 1000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-942 | false | 8 (invalid) | 1000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-943 | false | 8 (invalid) | 1000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-944 | false | 8 (invalid) | 1000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-945 | false | 8 (invalid) | 1000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-946 | false | 8 (invalid) | 1000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-947 | false | 8 (invalid) | 1000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-948 | false | 8 (invalid) | 1000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-949 | false | 8 (invalid) | 1000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-950 | false | 8 (invalid) | 1000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-951 | false | 8 (invalid) | 1000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-952 | false | 8 (invalid) | 1000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-953 | false | 8 (invalid) | 2500.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-954 | false | 8 (invalid) | 2500.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-955 | false | 8 (invalid) | 2500.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-956 | false | 8 (invalid) | 2500.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-957 | false | 8 (invalid) | 2500.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-958 | false | 8 (invalid) | 2500.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-959 | false | 8 (invalid) | 2500.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-960 | false | 8 (invalid) | 4999.99 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-961 | false | 8 (invalid) | 4999.99 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-962 | false | 8 (invalid) | 4999.99 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-963 | false | 8 (invalid) | 4999.99 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-964 | false | 8 (invalid) | 4999.99 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-965 | false | 8 (invalid) | 4999.99 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-966 | false | 8 (invalid) | 4999.99 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-967 | false | 8 (invalid) | 5000.00 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-968 | false | 8 (invalid) | 5000.00 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-969 | false | 8 (invalid) | 5000.00 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-970 | false | 8 (invalid) | 5000.00 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-971 | false | 8 (invalid) | 5000.00 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-972 | false | 8 (invalid) | 5000.00 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-973 | false | 8 (invalid) | 5000.00 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-974 | false | 8 (invalid) | 5000.01 | 0.00 | Invalid input (assumed rejected): amount <= $0; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-975 | false | 8 (invalid) | 5000.01 | 0.01 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-976 | false | 8 (invalid) | 5000.01 | 0.02 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-977 | false | 8 (invalid) | 5000.01 | 500.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-978 | false | 8 (invalid) | 5000.01 | 999.99 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-979 | false | 8 (invalid) | 5000.01 | 1000.00 | Invalid input (assumed rejected): day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |
| WD-980 | false | 8 (invalid) | 5000.01 | 1000.01 | Invalid input (assumed rejected): amount > $1000 daily limit; day outside 1-7 | IllegalArgumentException | no exception | **FAIL** | FW4 |

</details>
