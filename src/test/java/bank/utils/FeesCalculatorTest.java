package bank.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Black box tests for {@link FeesCalculator#calculateTransferFee}, derived with
 * Decision Table Analysis from the transfer fee rules (the decision table is in
 * docs/assignment1.md).
 *
 * <p>TR-01 to TR-16 are the 16 decision table rules, one test each. TR-17 to
 * TR-19 are invalid input rules. Representative values sit on the boundary of
 * every condition: the "less than" side uses threshold - 0.01 and the
 * "or more" side uses the threshold itself, so a wrong comparison operator or a
 * wrong threshold is exposed. Expected values come from the fee rules only.
 *
 * <p>This class replaces the starter's single sample test: it is the test class
 * for the transfer operation. Withdrawal and deposit fees are tested in their own
 * classes.
 */
class FeesCalculatorTest {

	private static final double DELTA = 0.01;

	private final FeesCalculator calculator = new FeesCalculator();

	private double transferFee(boolean student, double amount, double fromBalance, double toBalance) {
		return calculator.calculateTransferFee(amount, fromBalance, toBalance, student);
	}

	private void assertFee(boolean student, double amount, double fromBalance, double toBalance,
			double ratePercent) {
		double expected = amount * ratePercent / 100.0;
		assertEquals(expected, transferFee(student, amount, fromBalance, toBalance), DELTA);
	}

	/** TR-01. Rule: Transfer, student, amount < $200, from < $2,000, to < $1,000 -> 0.1%. */
	@Test
	@DisplayName("TR-01: student, amount < $200, from < $2,000, to < $1,000 -> 0.1%")
	void tr01StudentSmallLowFromLowTo() {
		assertFee(true, 199.99, 1999.99, 999.99, 0.1);
	}

	/** TR-02. Rule: Transfer, student, amount < $200, from < $2,000, to >= $1,000 -> 0.05%. */
	@Test
	@DisplayName("TR-02: student, amount < $200, from < $2,000, to >= $1,000 -> 0.05%")
	void tr02StudentSmallLowFromHighTo() {
		assertFee(true, 199.99, 1999.99, 1000.00, 0.05);
	}

	/** TR-03. Rule: Transfer, student, amount < $200, from >= $2,000, to < $1,000 -> 0.05%. */
	@Test
	@DisplayName("TR-03: student, amount < $200, from >= $2,000, to < $1,000 -> 0.05%")
	void tr03StudentSmallHighFromLowTo() {
		assertFee(true, 199.99, 2000.00, 999.99, 0.05);
	}

	/** TR-04. Rule: Transfer, student, amount < $200, from >= $2,000, to >= $1,000 -> 0.025%. */
	@Test
	@DisplayName("TR-04: student, amount < $200, from >= $2,000, to >= $1,000 -> 0.025%")
	void tr04StudentSmallHighFromHighTo() {
		assertFee(true, 199.99, 2000.00, 1000.00, 0.025);
	}

	/** TR-05. Rule: Transfer, student, amount >= $200, from < $2,000, to < $1,000 -> 0.05%. */
	@Test
	@DisplayName("TR-05: student, amount >= $200, from < $2,000, to < $1,000 -> 0.05%")
	void tr05StudentLargeLowFromLowTo() {
		assertFee(true, 200.00, 1999.99, 999.99, 0.05);
	}

	/** TR-06. Rule: Transfer, student, amount >= $200, from < $2,000, to >= $1,000 -> 0.025%. */
	@Test
	@DisplayName("TR-06: student, amount >= $200, from < $2,000, to >= $1,000 -> 0.025%")
	void tr06StudentLargeLowFromHighTo() {
		assertFee(true, 200.00, 1999.99, 1000.00, 0.025);
	}

	/** TR-07. Rule: Transfer, student, amount >= $200, from >= $2,000, to < $1,000 -> 0.25%. */
	@Test
	@DisplayName("TR-07: student, amount >= $200, from >= $2,000, to < $1,000 -> 0.25%")
	void tr07StudentLargeHighFromLowTo() {
		assertFee(true, 200.00, 2000.00, 999.99, 0.25);
	}

	/** TR-08. Rule: Transfer, student, amount >= $200, from >= $2,000, to >= $1,000 -> 0.125%. */
	@Test
	@DisplayName("TR-08: student, amount >= $200, from >= $2,000, to >= $1,000 -> 0.125%")
	void tr08StudentLargeHighFromHighTo() {
		assertFee(true, 200.00, 2000.00, 1000.00, 0.125);
	}

	/** TR-09. Rule: Transfer, non-student, amount < $100, from < $4,000, to < $2,000 -> 0.2%. */
	@Test
	@DisplayName("TR-09: non-student, amount < $100, from < $4,000, to < $2,000 -> 0.2%")
	void tr09NonStudentSmallLowFromLowTo() {
		assertFee(false, 99.99, 3999.99, 1999.99, 0.2);
	}

	/** TR-10. Rule: Transfer, non-student, amount < $100, from < $4,000, to >= $2,000 -> 0.1%. */
	@Test
	@DisplayName("TR-10: non-student, amount < $100, from < $4,000, to >= $2,000 -> 0.1%")
	void tr10NonStudentSmallLowFromHighTo() {
		assertFee(false, 99.99, 3999.99, 2000.00, 0.1);
	}

	/** TR-11. Rule: Transfer, non-student, amount < $100, from >= $4,000, to < $2,000 -> 1%. */
	@Test
	@DisplayName("TR-11: non-student, amount < $100, from >= $4,000, to < $2,000 -> 1%")
	void tr11NonStudentSmallHighFromLowTo() {
		assertFee(false, 99.99, 4000.00, 1999.99, 1);
	}

	/** TR-12. Rule: Transfer, non-student, amount < $100, from >= $4,000, to >= $2,000 -> 0.5%. */
	@Test
	@DisplayName("TR-12: non-student, amount < $100, from >= $4,000, to >= $2,000 -> 0.5%")
	void tr12NonStudentSmallHighFromHighTo() {
		assertFee(false, 99.99, 4000.00, 2000.00, 0.5);
	}

	/** TR-13. Rule: Transfer, non-student, amount >= $100, from < $2,000, to < $1,000 -> 0.2%. */
	@Test
	@DisplayName("TR-13: non-student, amount >= $100, from < $2,000, to < $1,000 -> 0.2%")
	void tr13NonStudentLargeLowFromLowTo() {
		assertFee(false, 100.00, 1999.99, 999.99, 0.2);
	}

	/** TR-14. Rule: Transfer, non-student, amount >= $100, from < $2,000, to >= $1,000 -> 0.1%. */
	@Test
	@DisplayName("TR-14: non-student, amount >= $100, from < $2,000, to >= $1,000 -> 0.1%")
	void tr14NonStudentLargeLowFromHighTo() {
		assertFee(false, 100.00, 1999.99, 1000.00, 0.1);
	}

	/** TR-15. Rule: Transfer, non-student, amount >= $100, from >= $2,000, to < $1,000 -> 0.5%. */
	@Test
	@DisplayName("TR-15: non-student, amount >= $100, from >= $2,000, to < $1,000 -> 0.5%")
	void tr15NonStudentLargeHighFromLowTo() {
		assertFee(false, 100.00, 2000.00, 999.99, 0.5);
	}

	/** TR-16. Rule: Transfer, non-student, amount >= $100, from >= $2,000, to >= $1,000 -> 0.25%. */
	@Test
	@DisplayName("TR-16: non-student, amount >= $100, from >= $2,000, to >= $1,000 -> 0.25%")
	void tr16NonStudentLargeHighFromHighTo() {
		assertFee(false, 100.00, 2000.00, 1000.00, 0.25);
	}

	// Invalid input rules. The fee rules do not define the behaviour for invalid
	// input; we assume an IllegalArgumentException (docs/assignment1.md).

	/** TR-17. Rule: not defined; a negative amount is assumed to be invalid input. */
	@Test
	@DisplayName("TR-17: negative amount is rejected")
	void tr17NegativeAmount() {
		assertThrows(IllegalArgumentException.class, () -> transferFee(true, -50.00, 1000.00, 500.00));
	}

	/** TR-18. Rule: not defined; a negative source balance is assumed to be invalid input. */
	@Test
	@DisplayName("TR-18: negative source balance is rejected")
	void tr18NegativeFromBalance() {
		assertThrows(IllegalArgumentException.class, () -> transferFee(false, 150.00, -1.00, 500.00));
	}

	/** TR-19. Rule: not defined; a negative destination balance is assumed to be invalid input. */
	@Test
	@DisplayName("TR-19: negative destination balance is rejected")
	void tr19NegativeToBalance() {
		assertThrows(IllegalArgumentException.class, () -> transferFee(false, 150.00, 1000.00, -1.00));
	}
}
