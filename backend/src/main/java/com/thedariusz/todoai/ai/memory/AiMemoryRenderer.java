package com.thedariusz.todoai.ai.memory;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Renders an {@link AiMemory} to a deterministic markdown block — the seam by which memory is
 * injected into a future Sonnet prompt (S-04) and the source a future user-facing export will
 * reuse. Pure function over the aggregate: no persistence, no HTTP.
 *
 * <p>Determinism is load-bearing twice over: prompts stay reproducible and the block is
 * diff-friendly for export. So profile facts are ordered by {@code (kind, content)} and
 * episodes by {@code occurred_at} (id as a stable tiebreaker), never by the aggregate's
 * incidental {@link java.util.Set} iteration order.
 *
 * <p>Episodic bounding lives here, not in the schema: the log is append-only and never pruned,
 * so only the last N episodes (N from {@link MemoryProperties}) reach the block — keeping
 * prompt-token cost independent of history size. Empty sections are omitted entirely (no stray
 * headers); a memory with nothing recorded renders a single explicit placeholder.
 *
 * <p>The output is a plain string with no surrounding structure, so a caller drops it straight
 * into an {@code LlmMessage} as system/context content with zero reshaping (S-04 wiring seam).
 */
@Service
public class AiMemoryRenderer {

	private static final String TITLE = "# AI memory";
	private static final String EMPTY = "_No memory recorded yet._";

	private static final Comparator<ProfileFact> BY_KIND_THEN_CONTENT =
			Comparator.comparing(ProfileFact::getKind).thenComparing(ProfileFact::getContent);

	// UUIDv7 ids are time-ordered but null until persist, so nullsFirst keeps in-memory
	// (unpersisted) episodes with a tied occurred_at deterministic rather than NPE-ing.
	private static final Comparator<Episode> CHRONOLOGICAL =
			Comparator.comparing(Episode::getOccurredAt)
					.thenComparing(Episode::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

	private final int defaultMaxEpisodes;

	AiMemoryRenderer(MemoryProperties properties) {
		this.defaultMaxEpisodes = properties.render().maxEpisodes();
	}

	/** Render using the configured default episode cap. */
	public String render(AiMemory memory) {
		return render(memory, defaultMaxEpisodes);
	}

	/** Render with an explicit cap on how many of the most-recent episodes are included. */
	public String render(AiMemory memory, int maxEpisodes) {
		List<ProfileFact> facts = memory.getProfileFacts().stream()
				.sorted(BY_KIND_THEN_CONTENT)
				.toList();
		List<Episode> recent = memory.getEpisodes().stream()
				.sorted(CHRONOLOGICAL.reversed())   // newest first, so the cap keeps the latest N
				.limit(Math.max(maxEpisodes, 0))
				.sorted(CHRONOLOGICAL)              // then oldest→newest for a readable timeline
				.toList();

		StringBuilder block = new StringBuilder(TITLE);
		if (facts.isEmpty() && recent.isEmpty()) {
			return block.append("\n\n").append(EMPTY).toString();
		}
		if (!facts.isEmpty()) {
			block.append("\n\n## Profile");
			for (ProfileFact fact : facts) {
				block.append("\n- ").append(fact.getKind()).append(": ").append(fact.getContent());
			}
		}
		if (!recent.isEmpty()) {
			block.append("\n\n## Recent activity");
			for (Episode episode : recent) {
				block.append("\n- ")
						.append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(episode.getOccurredAt()))
						.append(" · ").append(episode.getEventType())
						.append(": ").append(episode.getPayload());
			}
		}
		return block.toString();
	}
}
