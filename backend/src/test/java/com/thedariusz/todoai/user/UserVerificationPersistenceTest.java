package com.thedariusz.todoai.user;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The code slot of the {@link User} aggregate against a real Postgres with {@code V13}-{@code V15}
 * applied: the columns exist and the mapping validates against them, a new account is inert until
 * somebody proves the address, and each of the writes moves exactly its own columns.
 *
 * <p>The {@code where} clauses get their own cases here, and they are the point of the class as much
 * as the columns are: each one is an invariant the code is built on ("a caller can forget the check,
 * a {@code where} clause cannot"), and stripping one from the JPQL must go red somewhere.
 *
 * <p>Every write here is a targeted update rather than a {@code save}, for the reason spelled out on
 * {@code UserRepository.scheduleNextProposalAt} — so each one is also asserted to report 0 for an
 * account that is gone, which is the only way a caller can learn that from an update.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserVerificationPersistenceTest {

	private static final String CODE_HASH = "{bcrypt}$2a$10$code";

	@Autowired
	UserRepository users;

	/** Registration creates an account nobody has proved yet: no code drawn, nothing to spend. */
	@Test
	void createsAnAccountThatIsUnverifiedAndHoldsNoCode() {
		User account = reload(persisted());

		assertThat(account.isEmailVerified()).isFalse();
		assertThat(account.getVerificationCodeHash()).isNull();
		assertThat(account.getVerificationExpiresAt()).isNull();
		assertThat(account.getVerificationAttempts()).isZero();
	}

	/**
	 * A fresh code is a fresh budget of attempts — otherwise five wrong guesses would lock the address
	 * out permanently, and "send me another one" would be a button that does nothing. It also says what
	 * it is for, so a code drawn for one purpose cannot be spent on the other.
	 */
	@Test
	void issuingACodeStoresItsHashExpiryAndPurposeAndResetsTheAttemptsSpentOnTheLastOne() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.issueVerificationCode(id, CODE_HASH, CodePurpose.SIGN_IN, now.plusMinutes(15), now);
		users.chargeVerificationAttempt(id, now);

		assertThat(users.issueVerificationCode(id, CODE_HASH, CodePurpose.ACCOUNT_DELETION,
				now.plusMinutes(15), now)).isEqualTo(1);

		User account = reload(id);
		assertThat(account.getVerificationCodeHash()).isEqualTo(CODE_HASH);
		assertThat(account.getVerificationExpiresAt()).isNotNull();
		assertThat(account.getVerificationPurpose()).isEqualTo(CodePurpose.ACCOUNT_DELETION);
		assertThat(account.getVerificationAttempts()).isZero();
		assertThat(account.isEmailVerified()).isFalse();
	}

	/**
	 * The code is the sign-in credential now, so a proved account must be handed one. The where clause
	 * that used to refuse this is gone, and this is what goes red if it comes back.
	 */
	@Test
	void issuesACodeToAnAccountThatAlreadyProvedItsAddress() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.markEmailVerified(id, now);

		assertThat(users.issueVerificationCode(id, CODE_HASH, CodePurpose.SIGN_IN, now.plusMinutes(15), now))
				.isEqualTo(1);

		assertThat(reload(id).getVerificationCodeHash()).isEqualTo(CODE_HASH);
	}

	/**
	 * The cap holds in the database, not in a read the caller made earlier: the charge applies only
	 * while the code still has guesses left, and the one that would exceed it updates nothing. That is
	 * what keeps parallel guesses at five per code.
	 */
	@Test
	void chargesGuessesOnlyWhileTheCodeStillHasSomeLeft() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.issueVerificationCode(id, CODE_HASH, CodePurpose.SIGN_IN, now.plusMinutes(15), now);

		for (int guess = 0; guess < User.MAX_VERIFICATION_ATTEMPTS; guess++) {
			assertThat(users.chargeVerificationAttempt(id, now)).isEqualTo(1);
		}
		assertThat(users.chargeVerificationAttempt(id, now)).as("the sixth guess is refused").isZero();

		User account = reload(id);
		assertThat(account.getVerificationAttempts()).isEqualTo(User.MAX_VERIFICATION_ATTEMPTS);
		assertThat(account.getVerificationCodeHash()).isEqualTo(CODE_HASH);
	}

	/** With no code outstanding there is nothing to guess at, and nothing is counted. */
	@Test
	void chargesNothingWhenNoCodeIsOutstanding() {
		UUID id = persisted();

		assertThat(users.chargeVerificationAttempt(id, OffsetDateTime.now())).isZero();

		assertThat(reload(id).getVerificationAttempts()).isZero();
	}

	/**
	 * A spent code is worth nothing: consuming clears it, so it can never be replayed — and only the
	 * hash that was checked can be consumed, so a code re-issued between the check and the write is
	 * left alone. Consuming does not decide whether the address is proved; that is its own write.
	 */
	@Test
	void consumingClearsOnlyTheCodeThatWasChecked() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.issueVerificationCode(id, CODE_HASH, CodePurpose.SIGN_IN, now.plusMinutes(15), now);
		users.chargeVerificationAttempt(id, now);

		assertThat(users.consumeVerificationCode(id, "{bcrypt}$2a$10$other", now))
				.as("a code re-issued since the check is not the one being spent").isZero();
		assertThat(users.consumeVerificationCode(id, CODE_HASH, now)).isEqualTo(1);
		assertThat(users.consumeVerificationCode(id, CODE_HASH, now)).as("spent once").isZero();

		User account = reload(id);
		assertThat(account.getVerificationCodeHash()).isNull();
		assertThat(account.getVerificationExpiresAt()).isNull();
		// The count is guesses against an outstanding code, and there is none any more.
		assertThat(account.getVerificationAttempts()).isZero();
		assertThat(account.isEmailVerified()).isFalse();
	}

	/**
	 * The one write that can prove an address, and the 1 is what says "this was the first proof" —
	 * which is what keeps {@code UserVerified} from being published on every sign-in.
	 */
	@Test
	void provesAnAddressOnceAndSaysSoOnlyTheFirstTime() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(users.markEmailVerified(id, now)).isEqualTo(1);
		assertThat(users.markEmailVerified(id, now.plusMinutes(1))).isZero();

		assertThat(reload(id).isEmailVerified()).isTrue();
	}

	/**
	 * The rhythm's guard. It is the app's only unprompted outbound mail, so "only an address somebody
	 * has proved" may not rest on the entry points each remembering it — and the next one somebody
	 * adds will not know. Strip {@code email_verified_at is not null} from the JPQL and this is what
	 * goes red.
	 */
	@Test
	void refusesToScheduleAnAccountThatNeverProvedItsAddress() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(users.scheduleNextProposalAt(id, now.plusDays(1), now)).isZero();

		assertThat(reload(id).getNextProposalAt()).isNull();
	}

	/** An account deleted between the read and the write matches nothing, on every one of them. */
	@Test
	void reportsNoRowUpdatedWhenTheVerificationWritesMissTheirAccount() {
		UUID gone = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(users.issueVerificationCode(gone, CODE_HASH, CodePurpose.SIGN_IN, now.plusMinutes(15), now))
				.isZero();
		assertThat(users.chargeVerificationAttempt(gone, now)).isZero();
		assertThat(users.consumeVerificationCode(gone, CODE_HASH, now)).isZero();
		assertThat(users.markEmailVerified(gone, now)).isZero();
	}

	private UUID persisted() {
		return users.saveAndFlush(new User(Email.of("signup-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$first", AppLanguage.PL)).getId();
	}

	private User reload(UUID id) {
		return users.findById(id).orElseThrow();
	}
}
