package com.thedariusz.todoai.ai.memory;

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
 * <p>There is no get-or-create here on purpose: {@code RegistrationService} creates the root in the
 * same transaction as the user, so the row exists from t=0. A missing one is therefore an invariant
 * breach, not a first-write — but it must not cost the user their proposal, so it renders as no
 * memory rather than as a failure.
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
}
