package com.thedariusz.todoai.goal;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.thedariusz.todoai.goal.GoalResponse.GoalCollection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code goals} resource (S-02, FR-004/FR-005): both non-task layers behind one collection,
 * discriminated by {@code layer}.
 *
 * <p><b>DELETE is a hard delete</b> (DEV-44) — not withdrawing a goal (S-04's "nigdy" story, which
 * keeps the row) and not erasing an account (FR-019).
 *
 * <p>Also <b>no query parameters and no pagination</b>: the list is returned whole and grouped
 * client-side. At single-user scale that is one round-trip instead of several — S-08 shipped the
 * layer and category filters in the browser and deliberately left this signature alone. Unlike the
 * eleven-row {@code categories} collection this one only grows, so {@code openapi.yaml} carries the
 * trigger that ends the exception: cursor pagination once a caller's list passes ~500 entries or
 * ~250 kB. Both are compatible additions, which is why waiting is free. Authenticated by default via
 * {@code SecurityConfig}; mutations need the CSRF header.
 */
@RestController
@RequestMapping("/api/goals")
class GoalController {

	private final GoalService goals;

	GoalController(GoalService goals) {
		this.goals = goals;
	}

	@GetMapping
	GoalCollection list() {
		return new GoalCollection(goals.list());
	}

	/**
	 * {@code Location} points at the created entry (Zalando #180), like {@code POST /api/users} and
	 * {@code POST /api/sessions} already do — a 201 that does not say where the thing now lives makes
	 * every client parse the body to find out. Relative on purpose: the app is served from one origin
	 * behind Cloudflare, and an absolute URL would have to guess the public host from inside the
	 * container.
	 */
	@PostMapping
	ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalCreation request) {
		GoalResponse created = goals.create(request);
		return ResponseEntity.created(URI.create("/api/goals/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalUpdate request) {
		return goals.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		goals.delete(id);
	}
}
