package com.thedariusz.todoai.account;

import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates FR-019: erase every per-user record, then the user, in <b>one transaction</b> — a
 * partial deletion would leave data belonging to an account that no longer exists.
 *
 * <p>Ordering is the safety property. Because every user-scoped table carries a plain foreign key to
 * {@code app_user} with <em>no</em> {@code ON DELETE} action (the {@code V5} pattern, which new
 * migrations must follow), a module that forgets to register a deleter makes the final user delete
 * fail loudly on the FK constraint instead of silently orphaning rows. That precondition is not a
 * convention to be remembered — {@code AccountDeletionIntegrationTest} asserts it against
 * {@code information_schema} on every run.
 */
@Service
public class AccountDeletionService {

	private final List<PerUserDataDeleter> deleters;

	private final UserRepository users;

	public AccountDeletionService(List<PerUserDataDeleter> deleters, UserRepository users) {
		this.deleters = deleters;
		this.users = users;
	}

	@Transactional
	public void deleteAccount(UUID userId) {
		User user = users.findById(userId)
				.orElseThrow(() -> new IllegalStateException("No account to delete for user " + userId));
		deleters.forEach(deleter -> deleter.deleteAllForUser(userId));
		users.delete(user);
	}
}
