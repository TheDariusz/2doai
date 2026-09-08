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
 * The verification state of the {@link User} aggregate against a real Postgres with {@code V13}
 * applied: the four columns exist and the mapping validates against them, a new account is inert
 * until somebody proves the address, and each of the writes moves exactly its own columns.
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
	 * out permanently, and "send me another one" would be a button that does nothing.
	 */
	@Test
	void issuingACodeStoresItsHashAndExpiryAndResetsTheAttemptsSpentOnTheLastOne() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.recordFailedVerificationAttempt(id, now);

		assertThat(users.issueVerificationCode(id, CODE_HASH, now.plusMinutes(15), now)).isEqualTo(1);

		User account = reload(id);
		assertThat(account.getVerificationCodeHash()).isEqualTo(CODE_HASH);
		assertThat(account.getVerificationExpiresAt()).isNotNull();
		assertThat(account.getVerificationAttempts()).isZero();
		assertThat(account.isEmailVerified()).isFalse();
	}

	/** The wrong guess is counted even though the request it came in on answers a failure. */
	@Test
	void countsAFailedAttemptWithoutSpendingTheCode() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.issueVerificationCode(id, CODE_HASH, now.plusMinutes(15), now);

		assertThat(users.recordFailedVerificationAttempt(id, now)).isEqualTo(1);
		assertThat(users.recordFailedVerificationAttempt(id, now)).isEqualTo(1);

		User account = reload(id);
		assertThat(account.getVerificationAttempts()).isEqualTo(2);
		assertThat(account.getVerificationCodeHash()).isEqualTo(CODE_HASH);
	}

	/** A spent code is worth nothing: verifying clears it, so it can never be replayed. */
	@Test
	void markingVerifiedClearsTheCodeAndItsExpiry() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.issueVerificationCode(id, CODE_HASH, now.plusMinutes(15), now);
		users.recordFailedVerificationAttempt(id, now);

		assertThat(users.markEmailVerified(id, now)).isEqualTo(1);

		User account = reload(id);
		assertThat(account.isEmailVerified()).isTrue();
		assertThat(account.getVerificationCodeHash()).isNull();
		assertThat(account.getVerificationExpiresAt()).isNull();
		// The count is guesses against an outstanding code, and there is none any more.
		assertThat(account.getVerificationAttempts()).isZero();
	}

	/**
	 * The rhythm's guard. It is the app's only unprompted outbound mail, so "only an address somebody
	 * has proved" may not rest on the three entry points each remembering it — and the fourth one
	 * somebody adds will not know. Strip {@code email_verified_at is not null} from the JPQL and this
	 * is what goes red.
	 */
	@Test
	void refusesToScheduleAnAccountThatNeverProvedItsAddress() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(users.scheduleNextProposalAt(id, now.plusDays(1), now)).isZero();

		assertThat(reload(id).getNextProposalAt()).isNull();
	}

	/** And its mirror: a proved account has no code outstanding, so it must not be handed one. */
	@Test
	void refusesToIssueACodeToAnAccountThatAlreadyProvedItsAddress() {
		UUID id = persisted();
		OffsetDateTime now = OffsetDateTime.now();
		users.markEmailVerified(id, now);

		assertThat(users.issueVerificationCode(id, CODE_HASH, now.plusMinutes(15), now)).isZero();

		assertThat(reload(id).getVerificationCodeHash()).isNull();
	}

	/** An account deleted between the read and the write matches nothing, on every one of the three. */
	@Test
	void reportsNoRowUpdatedWhenTheVerificationWritesMissTheirAccount() {
		UUID gone = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(users.issueVerificationCode(gone, CODE_HASH, now.plusMinutes(15), now)).isZero();
		assertThat(users.recordFailedVerificationAttempt(gone, now)).isZero();
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
