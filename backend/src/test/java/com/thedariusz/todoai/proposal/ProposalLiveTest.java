package com.thedariusz.todoai.proposal;

import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.ai.LlmClient;
import com.thedariusz.todoai.ai.LlmProperties;
import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalHorizon;
import com.thedariusz.todoai.goal.GoalLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Sonnet round-trips through {@link ProposalPrompt} — the human-in-the-loop check that this
 * slice's two prompts produce something worth showing a user. Gated on {@code OPENROUTER_API_KEY} the
 * same way {@code OpenRouterLiveTest} is, so CI stays hermetic and free:
 * <pre>{@code OPENROUTER_API_KEY=… mvn test -Dtest=ProposalLiveTest}</pre>
 *
 * <p>It lives here rather than beside {@code OpenRouterLiveTest} because {@link ProposalPrompt} is
 * package-private, and widening a production class's visibility so a test can reach it would be the
 * wrong trade. That test still owns the transport proof (free-text and strict {@code json_schema});
 * this one owns the wording.
 *
 * <p>The assertions are deliberately thin — a model's prose cannot be pinned without making the test
 * a liar the first time the model improves. What they can hold is the one rule both prompts state as
 * non-negotiable: the answer must be about <em>this</em> entry. Everything else is for the human,
 * which is why the output is logged rather than swallowed. "Are these steps concrete enough to act
 * on?" is a question only a person can answer, and it is the reason this class prints them.
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProposalLiveTest {

	private static final Logger log = LoggerFactory.getLogger(ProposalLiveTest.class);

	@Autowired
	LlmClient llmClient;

	@Autowired
	LlmProperties properties;

	@Test
	void phrasesANeglectedEntryInPolishWithoutInventingADifferentOne() {
		Goal entry = new Goal(UUID.randomUUID(), "Zrobić prawo jazdy kategorii B", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, null, LifeDomain.TRANSPORT);

		String message = llmClient.complete(ProposalPrompt.forProposal(
				properties.model().sonnet(),
				"""
						# AI memory

						## Recent activity
						- 2026-06-14T20:11:00Z · proposal_answered: {"answer":"NOT_NOW"}""",
				entry, 243));

		log.info("Proposal message:\n{}", message);
		assertThat(message).isNotBlank();
		// The hard rule the system message states: cite the entry, never a goal of the model's own.
		assertThat(message).containsIgnoringCase("prawo jazdy");
	}

	@Test
	void returnsThreeToFiveConcreteStepsForTheEntryTheUserIsStarting() {
		Goal entry = new Goal(UUID.randomUUID(), "Zrobić prawo jazdy kategorii B", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, null, LifeDomain.TRANSPORT);

		// The strict json_schema path, which OpenRouterLiveTest proves in the abstract and this one
		// exercises with the schema production actually sends.
		FirstStep firstStep = llmClient.completeStructured(
				ProposalPrompt.forFirstStep(properties.model().sonnet(), "", entry),
				FirstStep.class, FirstStep.SCHEMA);

		log.info("First step:\n- {}", String.join("\n- ", firstStep.steps()));
		assertThat(firstStep.steps()).hasSizeBetween(3, 5).allSatisfy(step -> assertThat(step).isNotBlank());
	}
}
