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
 * until somebody proves the address, and each of the four writes moves exactly its own columns.
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

		assertThat(users.markEmailVerified(id, now)).isEqualTo(1);

		User account = reload(id);
		assertThat(account.isEmailVerified()).isTrue();
		assertThat(account.getVerificationCodeHash()).isNull();
		assertThat(account.getVerificationExpiresAt()).isNull();
	}

	/**
	 * The re-registration path: an address whose owner never proved it holds nothing worth
	 * protecting, so signing up again simply replaces the credentials.
	 */
	@Test
	void replacesTheCredentialsOfAnUnverifiedAccount() {
		UUID id = persisted();

		assertThat(users.replaceUnverifiedAccount(id, "{bcrypt}$2a$10$second", AppLanguage.EN,
				OffsetDateTime.now())).isEqualTo(1);

		User account = reload(id);
		assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$second");
		assertThat(account.getLanguage()).isEqualTo(AppLanguage.EN);
	}

	/**
	 * The load-bearing guard, and the reason it lives in the JPQL rather than in a caller's
	 * {@code if}: a verified account's password is somebody's actual credential, and the one thing
	 * this write must never be able to do is hand it to whoever typed the address into the sign-up
	 * form. A caller can forget the check; a {@code where} clause cannot.
	 */
	@Test
	void refusesToReplaceAnAccountWhoseAddressWasAlreadyProved() {
		UUID id = persisted();
		users.markEmailVerified(id, OffsetDateTime.now());

		assertThat(users.replaceUnverifiedAccount(id, "{bcrypt}$2a$10$attacker", AppLanguage.EN,
				OffsetDateTime.now())).isZero();

		assertThat(reload(id).getPasswordHash()).isEqualTo("{bcrypt}$2a$10$first");
	}

	/** An account deleted between the read and the write matches nothing, on every one of the four. */
	@Test
	void reportsNoRowUpdatedWhenTheVerificationWritesMissTheirAccount() {
		UUID gone = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(users.issueVerificationCode(gone, CODE_HASH, now.plusMinutes(15), now)).isZero();
		assertThat(users.recordFailedVerificationAttempt(gone, now)).isZero();
		assertThat(users.markEmailVerified(gone, now)).isZero();
		assertThat(users.replaceUnverifiedAccount(gone, CODE_HASH, AppLanguage.EN, now)).isZero();
	}

	private UUID persisted() {
		return users.saveAndFlush(new User(Email.of("signup-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$first", AppLanguage.PL)).getId();
	}

	private User reload(UUID id) {
		return users.findById(id).orElseThrow();
	}
}
