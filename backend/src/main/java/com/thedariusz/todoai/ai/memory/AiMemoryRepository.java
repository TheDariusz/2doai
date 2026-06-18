package com.thedariusz.todoai.ai.memory;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the AI-memory aggregate. Loaded by user for rendering (S-04) and
 * written by the enrichment/outcome flows (S-03/S-04); F-02 only establishes the seam.
 *
 * <p>{@code findByUserId} join-fetches both child collections in one round-trip (the
 * aggregate is always rendered whole), so callers see a fully-initialized aggregate even
 * with {@code open-in-view=false}. The collections are {@link java.util.Set}s precisely so
 * the two fetches can ride one query without Hibernate's {@code MultipleBagFetchException}.
 *
 * <p>Trade-off, acceptable at MVP scale (single user, no rows yet): fetching both children
 * together is a Cartesian join, and the episodic log grows unbounded. When that log gets
 * large, S-04 should switch to a bounded "last-N episodes" projection rather than loading
 * the whole log — the render cap is already last-N.
 */
public interface AiMemoryRepository extends JpaRepository<AiMemory, UUID> {

	@EntityGraph(attributePaths = {"profileFacts", "episodes"})
	Optional<AiMemory> findByUserId(UUID userId);
}
