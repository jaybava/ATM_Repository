package bank.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Black box tests for {@link FeesCalculator#calculateWithdrawalFee}, derived with
 * Robust Worst Case Boundary Value Analysis from the withdrawal fee rules (the
 * analysis is in docs/sections/withdrawal.md).
 *
 * <p>The test cases are the Cartesian product of the robust boundary values of
 * every input: student (2) x day of week (7) x balance (10) x amount (7) = 980
 * cases, WD-001 to WD-980, listed in
 * src/test/resources/bank/utils/withdrawal-fee-cases.csv. Each row gives the
 * expected fee rate in percent, or INVALID when at least one input is outside
 * its valid range, and the specification rule the case traces to. The fee rules
 * do not define the behaviour for invalid input; like the transfer tests, we
 * assume an IllegalArgumentException.
 */
class FeesCalculatorWithdrawalTest {

	private static final double DELTA = 0.000001;

	private static final String INVALID = "INVALID";

	private final FeesCalculator calculator = new FeesCalculator();

	@ParameterizedTest(name = "{0}: student={1}, day={2}, balance={3}, amount={4} | Rule: {6}")
	@CsvFileSource(resources = "/bank/utils/withdrawal-fee-cases.csv", numLinesToSkip = 1)
	void withdrawalFee(String id, boolean student, int dayOfWeek, double balance, double amount,
			String expectedRatePercent, String rule) {
		String message = id + " | Rule: " + rule;
		if (INVALID.equals(expectedRatePercent)) {
			assertThrows(IllegalArgumentException.class,
					() -> calculator.calculateWithdrawalFee(amount, balance, student, dayOfWeek), message);
		} else {
			double expected = amount * Double.parseDouble(expectedRatePercent) / 100.0;
			assertEquals(expected, calculator.calculateWithdrawalFee(amount, balance, student, dayOfWeek),
					DELTA, message);
		}
	}
}
