package com.thedariusz.todoai.goal;

import java.util.UUID;

import jakarta.validation.Valid;

import com.thedariusz.todoai.goal.GoalResponse.GoalCollection;
import org.springframework.http.HttpStatus;
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
 * <p>Also <b>no query parameters</b>: the list is returned whole and grouped client-side. At
 * single-user scale that is one round-trip instead of several — S-08 shipped the layer and category
 * filters in the browser and deliberately left this signature alone. A server-side filter contract
 * waits for a list that outgrows the round-trip. Authenticated by default via
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

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	GoalResponse create(@Valid @RequestBody GoalCreation request) {
		return goals.create(request);
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
