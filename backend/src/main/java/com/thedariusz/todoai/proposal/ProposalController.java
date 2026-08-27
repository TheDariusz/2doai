package com.thedariusz.todoai.proposal;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 *
 * <p><b>Answering is a sub-resource POST, not a PUT or PATCH on the proposal</b> (S-04b, FR-013).
 * The four responses are not field edits: each one also acts on the <em>entry</em> — quieting it
 * until a date, or withdrawing it — and {@code STARTING} additionally calls a model. A PATCH would
 * publish {@code answer} as a settable field and invite a client to change it, when in fact it can
 * be written exactly once. {@code /answer} names the act instead (#138 favours resources, and this
 * is one: the answer belongs to the proposal), and its 409 says so out loud.
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

	/**
	 * Returns the same representation {@link #propose()} does — now carrying the answer and, after
	 * {@code STARTING}, the stored bullets. One shape, so the client renders one thing whether it
	 * just asked for a proposal or just answered one.
	 */
	@PostMapping("/{id}/answer")
	ProposalResponse answer(@PathVariable UUID id, @Valid @RequestBody ProposalAnswerRequest request) {
		return proposals.answer(id, request);
	}
}
