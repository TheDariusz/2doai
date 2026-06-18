package com.thedariusz.todoai.ai.memory;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.thedariusz.todoai.ai.LlmMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link AiMemoryRenderer} — no Spring, no DB. The renderer is a pure
 * function over an in-memory aggregate, so these build {@link AiMemory} instances directly
 * (children have null ids, mirroring the pre-persist state) and assert the rendered block is
 * deterministic, bounded to the last N episodes, and free of stray headers when empty.
 */
class AiMemoryRendererTest {

	private final AiMemoryRenderer renderer =
			new AiMemoryRenderer(new MemoryProperties(new MemoryProperties.Render(20)));

	@Test
	void rendersProfileFactsAndRecentEpisodes() {
		AiMemory memory = new AiMemory(UUID.randomUUID());
		memory.addFact("occupation", "Software engineer", "onboarding");
		memory.recordEpisode("task-completed", "{\"seq\":1}", OffsetDateTime.parse("2026-06-10T08:00:00Z"));

		String block = renderer.render(memory);

		assertThat(block)
				.contains("## Profile")
				.contains("occupation: Software engineer")
				.contains("## Recent activity")
				.contains("task-completed")
				.contains("{\"seq\":1}")
				.doesNotContain("null");
	}

	@Test
	void ordersProfileFactsDeterministicallyRegardlessOfInsertionOrder() {
		AiMemory scrambled = new AiMemory(UUID.randomUUID());
		scrambled.addFact("occupation", "Software engineer", "onboarding");
		scrambled.addFact("goal", "Run a marathon", "inferred");
		scrambled.addFact("location", "Kraków", "onboarding");

		AiMemory other = new AiMemory(UUID.randomUUID());
		other.addFact("location", "Kraków", "onboarding");
		other.addFact("occupation", "Software engineer", "onboarding");
		other.addFact("goal", "Run a marathon", "inferred");

		String first = renderer.render(scrambled);
		String second = renderer.render(other);

		assertThat(first).isEqualTo(second);
		// Sorted by kind: goal < location < occupation.
		assertThat(first.indexOf("goal:"))
				.isLessThan(first.indexOf("location:"))
				.isLessThan(first.indexOf("occupation:"));
	}

	@Test
	void isDeterministicForTheSameAggregate() {
		AiMemory memory = sampleMemory();

		assertThat(renderer.render(memory)).isEqualTo(renderer.render(memory));
	}

	@Test
	void boundsToTheLastNEpisodesByOccurredAt() {
		AiMemory memory = new AiMemory(UUID.randomUUID());
		// Five episodes, inserted out of chronological order to prove ordering is by occurred_at.
		memory.recordEpisode("e", "{\"seq\":3}", OffsetDateTime.parse("2026-06-14T08:00:00Z"));
		memory.recordEpisode("e", "{\"seq\":1}", OffsetDateTime.parse("2026-06-10T08:00:00Z"));
		memory.recordEpisode("e", "{\"seq\":5}", OffsetDateTime.parse("2026-06-18T08:00:00Z"));
		memory.recordEpisode("e", "{\"seq\":2}", OffsetDateTime.parse("2026-06-12T08:00:00Z"));
		memory.recordEpisode("e", "{\"seq\":4}", OffsetDateTime.parse("2026-06-16T08:00:00Z"));

		String block = renderer.render(memory, 3);

		// Only the three most recent survive the cap...
		assertThat(block)
				.contains("{\"seq\":3}")
				.contains("{\"seq\":4}")
				.contains("{\"seq\":5}")
				.doesNotContain("{\"seq\":1}")
				.doesNotContain("{\"seq\":2}");
		// ...rendered oldest→newest for a coherent timeline.
		assertThat(block.indexOf("{\"seq\":3}"))
				.isLessThan(block.indexOf("{\"seq\":4}"))
				.isLessThan(block.indexOf("{\"seq\":5}"));
	}

	@Test
	void usesConfiguredDefaultEpisodeCap() {
		AiMemoryRenderer capped =
				new AiMemoryRenderer(new MemoryProperties(new MemoryProperties.Render(2)));
		AiMemory memory = new AiMemory(UUID.randomUUID());
		memory.recordEpisode("e", "{\"seq\":1}", OffsetDateTime.parse("2026-06-10T08:00:00Z"));
		memory.recordEpisode("e", "{\"seq\":2}", OffsetDateTime.parse("2026-06-12T08:00:00Z"));
		memory.recordEpisode("e", "{\"seq\":3}", OffsetDateTime.parse("2026-06-14T08:00:00Z"));

		String block = capped.render(memory);

		assertThat(block)
				.contains("{\"seq\":2}")
				.contains("{\"seq\":3}")
				.doesNotContain("{\"seq\":1}");
	}

	@Test
	void emptyMemoryRendersWithoutStrayHeaders() {
		AiMemory empty = new AiMemory(UUID.randomUUID());

		String block = renderer.render(empty);

		assertThat(block)
				.doesNotContain("## Profile")
				.doesNotContain("## Recent activity")
				.contains("No memory recorded yet");
	}

	@Test
	void factsWithoutEpisodesOmitTheActivityHeader() {
		AiMemory memory = new AiMemory(UUID.randomUUID());
		memory.addFact("occupation", "Software engineer", "onboarding");

		String block = renderer.render(memory);

		assertThat(block)
				.contains("## Profile")
				.doesNotContain("## Recent activity");
	}

	@Test
	void episodesWithoutFactsOmitTheProfileHeader() {
		AiMemory memory = new AiMemory(UUID.randomUUID());
		memory.recordEpisode("e", "{\"seq\":1}", OffsetDateTime.parse("2026-06-10T08:00:00Z"));

		String block = renderer.render(memory);

		assertThat(block)
				.doesNotContain("## Profile")
				.contains("## Recent activity");
	}

	@Test
	void renderedBlockDropsIntoAnLlmMessageUnchanged() {
		// Wiring seam (3.2): S-04 will pass render(...) straight into an LlmRequest message with
		// zero reshaping — the rendered block is a plain string suitable as message content.
		String block = renderer.render(sampleMemory());

		LlmMessage message = LlmMessage.system(block);

		assertThat(message.content()).isEqualTo(block);
	}

	private static AiMemory sampleMemory() {
		AiMemory memory = new AiMemory(UUID.randomUUID());
		memory.addFact("occupation", "Software engineer", "onboarding");
		memory.addFact("goal", "Run a marathon", "inferred");
		memory.recordEpisode("task-completed", "{\"seq\":1}", OffsetDateTime.parse("2026-06-14T08:00:00Z"));
		memory.recordEpisode("proposal-accepted", "{\"seq\":2}", OffsetDateTime.parse("2026-06-16T08:00:00Z"));
		return memory;
	}
}
