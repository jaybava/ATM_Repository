package bank.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Black box tests for {@link FeesCalculator#calculateDepositInterest}, derived with
 * Weak Robust Equivalence Class Analysis from the deposit fee rules (the analysis
 * is in docs/sections/deposit.md). The method is named "interest" in the code,
 * but the specification defines the value it returns as the deposit fee.
 *
 * <p>DP-01 to DP-08 cover the eight valid equivalence classes (one representative
 * value per class: half the threshold for "at or below" classes, double the
 * threshold for "above" classes). DP-09 to DP-12 each hold exactly one invalid
 * value. The fee rules do not define the behaviour for invalid input; like the
 * withdrawal and transfer tests, we assume an IllegalArgumentException.
 */
class FeesCalculatorDepositTest {

	private static final double DELTA = 0.000001;

	private static final String INVALID = "INVALID";

	private final FeesCalculator calculator = new FeesCalculator();

	@ParameterizedTest(name = "{0}: student={1}, amount={2}, balance={3} -> {4}")
	@CsvSource({
		// id, student, amount, balance, expected rate in percent
		"DP-01, true, 100.00, 1000.00, 0.5",
		"DP-02, true, 100.00, 250.00, 0.25",
		"DP-03, true, 25.00, 10000.00, 0.5",
		"DP-04, true, 25.00, 2500.00, 0",
		"DP-05, false, 500.00, 5000.00, 0.8",
		"DP-06, false, 500.00, 1250.00, 0.4",
		"DP-07, false, 125.00, 20000.00, 0",
		"DP-08, false, 125.00, 5000.00, 0.1",
		"DP-09, true, -100.00, 1000.00, INVALID",
		"DP-10, false, -500.00, 5000.00, INVALID",
		"DP-11, true, 100.00, -1000.00, INVALID",
		"DP-12, false, 500.00, -5000.00, INVALID",
	})
	void depositFee(String id, boolean student, double amount, double balance, String expectedRatePercent) {
		if (INVALID.equals(expectedRatePercent)) {
			assertThrows(IllegalArgumentException.class,
					() -> calculator.calculateDepositInterest(amount, balance, student), id);
		} else {
			double expected = amount * Double.parseDouble(expectedRatePercent) / 100.0;
			assertEquals(expected, calculator.calculateDepositInterest(amount, balance, student), DELTA, id);
		}
	}
}
