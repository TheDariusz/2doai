package com.thedariusz.todoai.ai.memory;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The seam other bounded contexts reach AI memory through — today only S-04b's proposal package,
 * which needs the rendered block to put in front of Sonnet.
 *
 * <p>It exists so {@code proposal} never touches {@link AiMemoryRepository}: the aggregate's own
 * package owns how it is loaded (the join-fetch, the render cap) and every caller outside gets a
 * plain string. One class rather than two injected beans at each call site, and the seam Phase 3's
 * episode writing lands on.
 *
 * <p>A missing root is an invariant breach rather than a first write: {@code RegistrationService}
 * creates it in the same transaction as the user, so the row exists from t=0. Neither method
 * therefore treats absence as normal — but neither punishes the user for it either. Rendering falls
 * back to no memory, and {@link #record} creates the row rather than dropping the episode; losing
 * what the user just answered would be the more expensive of the two failures.
 */
@Service
public class AiMemoryService {

	private final AiMemoryRepository memories;

	private final AiMemoryRenderer renderer;

	AiMemoryService(AiMemoryRepository memories, AiMemoryRenderer renderer) {
		this.memories = memories;
		this.renderer = renderer;
	}

	/** @return the user's memory as a prompt-ready block, or blank when they have none */
	@Transactional(readOnly = true)
	public String renderFor(UUID userId) {
		return memories.findByUserId(userId).map(renderer::render).orElse("");
	}

	/**
	 * Append one episode to the user's log — S-04b's proposal answers are its first writer, which is
	 * what finally gives {@link AiMemoryRenderer} something to render.
	 *
	 * <p>Joins the caller's transaction when there is one ({@code REQUIRED}), so the episode and
	 * whatever the caller was recording it about commit together.
	 *
	 * <p><b>The load is deliberately the join-fetching finder</b>, which drags the whole episodic log
	 * across the wire only to append one row to it. It is the only finder scoped by user, and at
	 * single-user scale the log is tiny; the repository's own javadoc already names the bounded
	 * projection as the upgrade, and this is the second caller that will want it.
	 *
	 * @param eventType the discriminator the rendered block prints, at most 64 characters
	 * @param payload an opaque JSON document — the caller owns its shape
	 */
	@Transactional
	public void record(UUID userId, String eventType, String payload) {
		AiMemory memory = memories.findByUserId(userId)
				.orElseGet(() -> memories.save(new AiMemory(userId)));
		// Truncated for the reason every timestamp here is: timestamptz holds microseconds, and a
		// nanosecond-capable clock would make the written value differ from every later read.
		memory.recordEpisode(eventType, payload, OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
		memories.save(memory);
	}
}
