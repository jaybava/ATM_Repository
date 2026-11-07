package atm.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import atm.ATM;
import atm.dispatcher.MessageDispatcher;
import atm.exceptions.InvalidAmountException;
import atm.exceptions.InvalidCardNumberException;
import atm.exceptions.InvalidPinFormatException;
import atm.session.transactions.ATMTransaction;
import atm.session.transactions.ATMWithdrawal;
import atm.ui.panels.MainPanel;
import atm.utils.CredentialsCheck;
import atm.utils.FormatChecker;
import bank.transactions.utils.AccountType;
import bank.transactions.utils.TransactionResult;
import bank.transactions.utils.TransactionType;

/**
 * Black box tests for the ATM session (Assignment 1, "The Testing Targets" -
 * atm.session.Session, and "ATM Session").
 *
 * Technique: Robustness Boundary Value Analysis (single fault assumption, 6n+1).
 * One variable (PIN or withdrawal amount) takes its boundary values while the
 * other is held at its nominal value.
 *
 * Spec rules:
 *  - a valid PIN is 5 digits, otherwise InvalidPinFormatException;
 *  - a valid withdrawal amount can be dispensed in $20 and $50 bills and is
 *    within the daily limit, otherwise InvalidAmountException;
 *  - the account has a daily limit of $1,000 (upper boundary) and an
 *    available balance of $5,000.
 *
 * The PIN format is validated before the session is given the PIN, so PIN
 * cases go through ATM.checkPin with the real FormatChecker. Amount cases
 * drive Session directly. The bank (dispatcher) and UI (MainPanel) are stubbed.
 *
 * To extend to Robust Worst Case (7 x 7 = 49 combinations), iterate the
 * cross product of the PIN and amount values through withdrawThroughAtm().
 */
public class SessionTest {
	private static final String NOMINAL_CARD = "4000000000000000";
	private static final String NOMINAL_PIN = "12345";
	private static final int NOMINAL_AMOUNT = 500;
	private static final int DAILY_LIMIT = 1000;
	private static final double ACCOUNT_BALANCE = 5000.0;

	private MessageDispatcher dispatcher;
	private MainPanel mainPanel;

	@BeforeEach
	public void setUp() {
		dispatcher = Mockito.mock(MessageDispatcher.class);
		mainPanel = Mockito.mock(MainPanel.class);

		// Any card/PIN pair is accepted by the bank: only the PIN *format* is under test.
		Mockito.when(dispatcher.checkCredentials(ArgumentMatchers.any(), ArgumentMatchers.any()))
				.thenReturn(true);

		// The bank holds an account with a $5,000 balance; a dispatched withdrawal succeeds.
		Mockito.when(dispatcher.performTransaction(ArgumentMatchers.any(), ArgumentMatchers.any(),
				ArgumentMatchers.any())).thenAnswer(invocation -> {
					ATMTransaction t = invocation.getArgument(2);
					return new TransactionResult(true, "", 0.0,
							new double[] {ACCOUNT_BALANCE - t.getAmount()});
				});
	}

	// ---------------------------------------------------------------------
	// PIN format (amount held at nominal $500)
	// ---------------------------------------------------------------------

	// P1 - PIN length min- (4 digits). Expected: InvalidPinFormatException.
	@Test
	@DisplayName("P1 pin_lengthMinMinus_4digits_throwsInvalidPinFormat")
	public void pinLengthMinMinus4DigitsThrowsInvalidPinFormat() throws Exception {
		assertPinRejected("1234");
	}

	// P2 - PIN length min = nominal = max (5 digits). Expected: accepted, withdrawal proceeds.
	@Test
	@DisplayName("P2 pin_lengthNominal_5digits_accepted")
	public void pinLengthNominal5DigitsAccepted() throws Exception {
		assertPinAccepted(NOMINAL_PIN);
	}

	// P3 - PIN length max+ (6 digits). Expected: InvalidPinFormatException.
	@Test
	@DisplayName("P3 pin_lengthMaxPlus_6digits_throwsInvalidPinFormat")
	public void pinLengthMaxPlus6DigitsThrowsInvalidPinFormat() throws Exception {
		assertPinRejected("123456");
	}

	// P4 - empty PIN (length 0, extreme below min). Expected: InvalidPinFormatException.
	@Test
	@DisplayName("P4 pin_empty_throwsInvalidPinFormat")
	public void pinEmptyThrowsInvalidPinFormat() throws Exception {
		assertPinRejected("");
	}

	// P5 - non-numeric PIN of valid length (5). Expected: InvalidPinFormatException.
	@Test
	@DisplayName("P5 pin_nonNumeric_12a45_throwsInvalidPinFormat")
	public void pinNonNumeric12a45ThrowsInvalidPinFormat() throws Exception {
		assertPinRejected("12a45");
	}

	// P6 - every digit at its min ('0'), length 5. Expected: accepted.
	@Test
	@DisplayName("P6 pin_digitMin_00000_accepted")
	public void pinDigitMin00000Accepted() throws Exception {
		assertPinAccepted("00000");
	}

	// P7 - every digit at its max ('9'), length 5. Expected: accepted.
	@Test
	@DisplayName("P7 pin_digitMax_99999_accepted")
	public void pinDigitMax99999Accepted() throws Exception {
		assertPinAccepted("99999");
	}

	// ---------------------------------------------------------------------
	// Withdrawal amount (PIN held at nominal 12345)
	// ---------------------------------------------------------------------

	// A1 - amount min- ($10, below smallest dispensable). Expected: InvalidAmountException.
	@Test
	@DisplayName("A1 withdraw_amountMinMinus_10_throwsInvalidAmount")
	public void withdrawAmountMinMinus10ThrowsInvalidAmount() {
		assertAmountRejected(10);
	}

	// A2 - amount min ($20, smallest dispensable). Expected: accepted.
	@Test
	@DisplayName("A2 withdraw_amountMin_20_accepted")
	public void withdrawAmountMin20Accepted() {
		assertAmountAccepted(20);
	}

	// A3 - amount min+ ($40, next dispensable above min). Expected: accepted.
	@Test
	@DisplayName("A3 withdraw_amountMinPlus_40_accepted")
	public void withdrawAmountMinPlus40Accepted() {
		assertAmountAccepted(40);
	}

	// A4 - amount nominal ($500). Expected: accepted.
	@Test
	@DisplayName("A4 withdraw_amountNominal_500_accepted")
	public void withdrawAmountNominal500Accepted() {
		assertAmountAccepted(NOMINAL_AMOUNT);
	}

	// A5 - amount max- ($990). Expected: accepted.
	@Test
	@DisplayName("A5 withdraw_amountMaxMinus_990_accepted")
	public void withdrawAmountMaxMinus990Accepted() {
		assertAmountAccepted(DAILY_LIMIT - 10);
	}

	// A6 - amount max ($1,000, the daily limit). Expected: accepted.
	@Test
	@DisplayName("A6 withdraw_amountMax_1000_accepted")
	public void withdrawAmountMax1000Accepted() {
		assertAmountAccepted(DAILY_LIMIT);
	}

	// A7 - amount max+ ($1,010, over the daily limit). Expected: InvalidAmountException.
	@Test
	@DisplayName("A7 withdraw_amountMaxPlus_1010_throwsInvalidAmount")
	public void withdrawAmountMaxPlus1010ThrowsInvalidAmount() {
		assertAmountRejected(DAILY_LIMIT + 10);
	}

	// A8 - $30 cannot be made with $20/$50 bills. Expected: InvalidAmountException.
	@Test
	@DisplayName("A8 withdraw_amountNotDispensable_30_throwsInvalidAmount")
	public void withdrawAmountNotDispensable30ThrowsInvalidAmount() {
		assertAmountRejected(30);
	}

	// A9 - $55 cannot be made with $20/$50 bills. Expected: InvalidAmountException.
	@Test
	@DisplayName("A9 withdraw_amountNotDispensable_55_throwsInvalidAmount")
	public void withdrawAmountNotDispensable55ThrowsInvalidAmount() {
		assertAmountRejected(55);
	}

	// A10 - $0 (robustness, far below min). Expected: InvalidAmountException.
	@Test
	@DisplayName("A10 withdraw_amountZero_0_throwsInvalidAmount")
	public void withdrawAmountZero0ThrowsInvalidAmount() {
		assertAmountRejected(0);
	}

	// A11 - -$20 (robustness, negative). Expected: InvalidAmountException.
	@Test
	@DisplayName("A11 withdraw_amountNegative_minus20_throwsInvalidAmount")
	public void withdrawAmountNegativeMinus20ThrowsInvalidAmount() {
		assertAmountRejected(-20);
	}

	// A12 - max+ that is dispensable ($1,050 = 21 x $50), over the daily limit.
	// Expected: InvalidAmountException (isolates the daily limit from the bill check).
	@Test
	@DisplayName("A12 withdraw_amountMaxPlusDispensable_1050_throwsInvalidAmount")
	public void withdrawAmountMaxPlusDispensable1050ThrowsInvalidAmount() {
		assertAmountRejected(DAILY_LIMIT + 50);
	}

	// A13 - $60 = 3 x $20, dispensable and within the daily limit. Expected: accepted.
	@Test
	@DisplayName("A13 withdraw_amountDispensable_60_accepted")
	public void withdrawAmountDispensable60Accepted() {
		assertAmountAccepted(60);
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	/** A real ATM with a fresh session, past the card step and waiting for a PIN. */
	private ATM atmAtPinStep() throws InvalidCardNumberException {
		ATM atm = new ATM(new FormatChecker(), new CredentialsCheck(dispatcher), dispatcher);
		atm.setMainPanel(mainPanel);
		atm.createSession();
		atm.checkCardNumber(NOMINAL_CARD);
		return atm;
	}

	/** Full withdrawal through the ATM: PIN, then a Chequing withdrawal of the given amount. */
	private void withdrawThroughAtm(String pin, int amount) throws Exception {
		ATM atm = atmAtPinStep();
		atm.checkPin(pin.toCharArray());
		atm.setTransaction(TransactionType.Withdrawal);
		atm.setFromAccount(AccountType.Chequing);
		atm.setAmount(amount);
	}

	private void assertPinAccepted(String pin) {
		assertDoesNotThrow(() -> withdrawThroughAtm(pin, NOMINAL_AMOUNT),
				"PIN \"" + pin + "\" is 5 digits and must be accepted");
	}

	private void assertPinRejected(String pin) throws InvalidCardNumberException {
		ATM atm = atmAtPinStep();
		assertThrows(InvalidPinFormatException.class, () -> atm.checkPin(pin.toCharArray()),
				"PIN \"" + pin + "\" is not 5 digits and must be rejected");
	}

	/** A real Session at the amount step of a Chequing withdrawal, with nominal card and PIN. */
	private Session sessionAtWithdrawalAmountStep() {
		Session session = new Session(mainPanel, dispatcher);
		session.insertCard();
		session.addCard(NOMINAL_CARD);
		session.addPin(NOMINAL_PIN.toCharArray());
		session.setTransaction(new ATMWithdrawal());
		session.setFromAccount(AccountType.Chequing);
		return session;
	}

	private void assertAmountAccepted(int amount) {
		Session session = sessionAtWithdrawalAmountStep();
		assertDoesNotThrow(() -> session.setAmount(amount),
				"$" + amount + " is dispensable and within the daily limit; it must be accepted");
		Mockito.verify(dispatcher).performTransaction(ArgumentMatchers.any(), ArgumentMatchers.any(),
				ArgumentMatchers.any());
	}

	private void assertAmountRejected(int amount) {
		Session session = sessionAtWithdrawalAmountStep();
		assertThrows(InvalidAmountException.class, () -> session.setAmount(amount),
				"$" + amount + " is not a valid withdrawal; it must be rejected");
		Mockito.verify(dispatcher, Mockito.never()).performTransaction(ArgumentMatchers.any(),
				ArgumentMatchers.any(), ArgumentMatchers.any());
	}
}
