package com.thedariusz.todoai.proposal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code proposals} resource (S-04a, FR-015, the "give me something now" trigger) — ask the engine for one
 * neglected entry to act on.
 *
 * <p><b>POST, not GET</b>, and no request body. Asking for a proposal is an action the user takes,
 * and it becomes a genuinely unsafe one as soon as state sits behind it: the four responses of
 * FR-013 (every answer shapes what is proposed next) and the at-most-one-pending rule of
 * FR-018 both do. Choosing GET now for a body-less read would make that a breaking change, and would
 * invite caching of an answer that must not be cached.
 *
 * <p><b>204 when nothing is neglected</b>, never 404: the resource exists and answered, the user
 * simply has nothing gathering dust. A 404 here would say the endpoint is gone. Authenticated by
 * default via {@code SecurityConfig}; being a mutation it needs the CSRF header like any other.
 */
@RestController
@RequestMapping("/api/proposals")
class ProposalController {

	private final ProposalService proposals;

	ProposalController(ProposalService proposals) {
		this.proposals = proposals;
	}

	@PostMapping
	ResponseEntity<ProposalResponse> propose() {
		return proposals.propose()
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
