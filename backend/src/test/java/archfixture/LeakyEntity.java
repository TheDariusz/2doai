package archfixture;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * A deliberately non-conforming per-user entity: it carries {@code user_id} but skips the
 * {@code UserOwned} marker. Its only job is to prove the ArchUnit rule in
 * {@code UserOwnedConventionTest} actually rejects something.
 *
 * <p>It lives in {@code archfixture} — <b>outside</b> {@code com.thedariusz.todoai} — on purpose:
 * Spring Boot's entity scan starts at the {@code Application} package, so a fixture placed under the
 * base package would be picked up by Hibernate and fail {@code ddl-auto=validate} against a table
 * that does not exist, breaking every {@code @SpringBootTest} in the suite.
 */
@Entity
public class LeakyEntity {

	@Id
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;
}
