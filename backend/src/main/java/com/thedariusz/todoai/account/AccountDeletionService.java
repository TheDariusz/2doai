package com.thedariusz.todoai.account;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.thedariusz.todoai.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates FR-019: erase every per-user record, then the user, in <b>one transaction</b> — a
 * partial deletion would leave data belonging to an account that no longer exists.
 *
 * <p>Ordering is the safety property. Because {@code ai_memory.user_id} carries a plain foreign key
 * with <em>no</em> {@code ON DELETE CASCADE} ({@code V5}), a module that forgets to register a
 * deleter makes the final user delete fail loudly on the FK constraint instead of silently
 * orphaning rows. The database is the backstop behind {@link #registeredTables()}'s guard test.
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
		deleters.forEach(deleter -> deleter.deleteAllForUser(userId));
		users.deleteById(userId);
	}

	/** The tables the registry currently covers — compared against the live schema by the orphan guard. */
	public Set<String> registeredTables() {
		return deleters.stream().map(PerUserDataDeleter::userScopedTable).collect(Collectors.toSet());
	}
}
