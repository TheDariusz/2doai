package com.thedariusz.todoai.proposal;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
 * <p><b>{@code /pending} is the exception that proves the rule</b> (S-05, FR-018). Once the natural
 * rhythm opens proposals on its own, "what is waiting for me" stops being the same question as
 * "give me something now": it selects nothing, calls no model and writes nothing, which is exactly
 * the safety GET promises. A sub-path rather than a filter on the collection, because at most one
 * proposal can ever be waiting — it is a slot, not a query.
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
	 * What is waiting for the user, so the app can show it the moment they open it — the in-app half
	 * of FR-018, beside the email the scheduler sends. 204 when the slot is empty, for the same
	 * reason {@link #propose()} answers 204: the resource answered, nothing is gathering dust.
	 */
	@GetMapping("/pending")
	ResponseEntity<ProposalResponse> pending() {
		return proposals.pending()
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
