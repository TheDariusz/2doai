package com.thedariusz.todoai.ai.memory;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bean Validation constraint tests for the AI-memory aggregate and its children. The aggregate
 * declares its invariants as Jakarta constraints — a required {@code userId} (also guarded at
 * construction, so the root can never exist without an owner); non-blank {@code kind} /
 * {@code content} / {@code eventType} / {@code payload}; a required {@code occurredAt}; and the
 * {@code VARCHAR(64)} size bound on the {@code kind} / {@code event_type} discriminators (see
 * {@code V3__create_ai_memory.sql}). The field constraints are asserted as <em>declared</em>
 * here, independent of a database; that Hibernate actually <em>enforces</em> them on flush is
 * proven in {@link AiMemoryRepositoryTest}.
 */
class AiMemoryTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	private final AiMemory memory = new AiMemory(UUID.randomUUID());

	@BeforeAll
	static void setUpValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	// --- Required identity: the root cannot exist without an owner. Fail-fast at construction
	//     (the identity invariant), belt-and-suspenders with @NotNull for persist/schema. ---

	@Test
	void rejectsNullUserIdAtConstruction() {
		assertThatThrownBy(() -> new AiMemory(null))
				.isInstanceOf(NullPointerException.class);
	}

	// --- ProfileFact: kind / content are NOT NULL columns; kind is VARCHAR(64) ---

	@Test
	void requiresNonBlankProfileFactKind() {
		ProfileFact blank = new ProfileFact(memory, "   ", "Software engineer", "onboarding");

		assertThat(validator.validate(blank))
				.anyMatch(v -> v.getPropertyPath().toString().equals("kind"));
	}

	@Test
	void rejectsProfileFactKindLongerThanColumnLimit() {
		ProfileFact tooLong = new ProfileFact(memory, "k".repeat(65), "Software engineer", "onboarding");

		assertThat(validator.validate(tooLong))
				.anyMatch(v -> v.getPropertyPath().toString().equals("kind"));
	}

	@Test
	void requiresNonBlankProfileFactContent() {
		ProfileFact blank = new ProfileFact(memory, "occupation", "   ", "onboarding");

		assertThat(validator.validate(blank))
				.anyMatch(v -> v.getPropertyPath().toString().equals("content"));
	}

	@Test
	void acceptsValidProfileFact() {
		ProfileFact valid = new ProfileFact(memory, "occupation", "Software engineer", "onboarding");

		assertThat(validator.validate(valid)).isEmpty();
	}

	// --- Episode: event_type / payload are NOT NULL columns; event_type is VARCHAR(64) ---

	@Test
	void requiresNonBlankEpisodeEventType() {
		Episode blank = new Episode(memory, "   ", "{}", OffsetDateTime.now());

		assertThat(validator.validate(blank))
				.anyMatch(v -> v.getPropertyPath().toString().equals("eventType"));
	}

	@Test
	void rejectsEpisodeEventTypeLongerThanColumnLimit() {
		Episode tooLong = new Episode(memory, "e".repeat(65), "{}", OffsetDateTime.now());

		assertThat(validator.validate(tooLong))
				.anyMatch(v -> v.getPropertyPath().toString().equals("eventType"));
	}

	@Test
	void requiresNonBlankEpisodePayload() {
		Episode blank = new Episode(memory, "completion", "   ", OffsetDateTime.now());

		assertThat(validator.validate(blank))
				.anyMatch(v -> v.getPropertyPath().toString().equals("payload"));
	}

	@Test
	void requiresEpisodeOccurredAt() {
		Episode noTime = new Episode(memory, "completion", "{}", null);

		assertThat(validator.validate(noTime))
				.anyMatch(v -> v.getPropertyPath().toString().equals("occurredAt"));
	}

	@Test
	void acceptsValidEpisode() {
		Episode valid = new Episode(memory, "completion", "{}", OffsetDateTime.now());

		assertThat(validator.validate(valid)).isEmpty();
	}
}
