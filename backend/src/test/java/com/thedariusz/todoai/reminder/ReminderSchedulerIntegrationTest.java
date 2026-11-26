package com.thedariusz.todoai.reminder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.thedariusz.todoai.ApiTestBase;
import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.TestcontainersConfiguration.RecordingEmailSender.Sent;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.user.UserRepository;
import com.thedariusz.todoai.user.UserZone;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One reminder end to end against a real Postgres (DEV-63): a task saved through the API is armed by
 * the event its write published, fired by {@code fireDue}, stamped, and mailed to its owner — and
 * nothing the natural rhythm owns moves.
 *
 * <p><b>The real tick runs in this shared context</b>, with the one shared recording sender. So
 * every term here is three days ahead (the tick will not reach it during a test run), the fire is
 * driven only through {@code fireDue} with a synthetic moment, and mail is asserted by the owner's
 * address, never by the total count.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReminderSchedulerIntegrationTest extends ApiTestBase {

	private static final LocalDate DAY = LocalDate.now(UserZone.ZONE).plusDays(3);

	@Autowired
	private ReminderScheduler scheduler;

	@Autowired
	private GoalRepository goals;

	@Autowired
	private UserRepository users;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void remindsTheOwnerOnceAndLeavesTheRhythmAlone() {
		String email = givenLoggedInUser();
		UUID owner = users.findByEmail(email).orElseThrow().getId();
		UUID goal = createTimedTask("Zapłacić za prąd", "12:00", "ONE_HOUR");
		OffsetDateTime updatedAt = goals.findByIdAndUserId(goal, owner).orElseThrow().getUpdatedAt();
		Snapshot before = snapshot(owner);

		scheduler.fireDue(at(11, 0));

		assertThat(reminders(email)).singleElement()
				.satisfies(message -> assertThat(message.subject()).contains("Zapłacić za prąd"));
		Goal stamped = goals.findByIdAndUserId(goal, owner).orElseThrow();
		assertThat(stamped.getReminderSentAt()).isNotNull();
		assertThat(stamped.getUpdatedAt())
				.as("ProposalSelector reads updated_at as the user's last engagement; a machine stamp "
						+ "must not reset the rhythm's neglect clock")
				.isEqualTo(updatedAt);
		assertThat(snapshot(owner))
				.as("a reminder is not a proposal: no proposal, rhythm or memory write")
				.isEqualTo(before);

		scheduler.fireDue(at(11, 1));
		assertThat(reminders(email)).hasSize(1);
	}

	/**
	 * A row whose term moved without the map hearing of it — here written straight to the table, in
	 * production two edits whose after-commit listeners ran out of order — is re-armed to the window
	 * the row actually has, instead of being sent early or dropped.
	 */
	@Test
	void rearmsAStaleEntryToTheTermTheRowActuallyHas() {
		String email = givenLoggedInUser();
		UUID goal = createTimedTask("Odebrać paczkę", "12:00", "ONE_HOUR");
		jdbc.update("UPDATE goal SET due_time = '15:00' WHERE id = ?", goal);

		scheduler.fireDue(at(11, 0));
		assertThat(reminders(email)).isEmpty();

		scheduler.fireDue(at(14, 0));
		assertThat(reminders(email)).singleElement()
				.satisfies(message -> assertThat(message.subject()).contains("Odebrać paczkę"));
	}

	@Test
	void sendsNothingForATaskCompletedBeforeItsMoment() {
		String email = givenLoggedInUser();
		Map<String, Object> payload = timedTask("Wynieść śmieci", "12:00", "ONE_HOUR");
		UUID goal = create(payload);
		payload.put("completed", true);
		payload.put("withdrawn", false);
		csrfAware().body(payload).when().put("/api/goals/" + goal).then().statusCode(200);

		scheduler.fireDue(at(11, 0));

		assertThat(reminders(email)).isEmpty();
	}

	private UUID createTimedTask(String content, String time, String lead) {
		return create(timedTask(content, time, lead));
	}

	private UUID create(Map<String, Object> payload) {
		return UUID.fromString(csrfAware()
				.body(payload)
				.when()
				.post("/api/goals")
				.then()
				.statusCode(201)
				.extract()
				.path("id"));
	}

	private static Map<String, Object> timedTask(String content, String time, String lead) {
		Map<String, Object> body = new HashMap<>();
		body.put("content", content);
		body.put("layer", "TASK");
		body.put("horizon", null);
		body.put("category_code", "HOME");
		body.put("due_date", DAY.toString());
		body.put("due_time", time);
		body.put("reminder_lead", lead);
		return body;
	}

	/**
	 * What reached this address other than its sign-in codes — which always carry an HTML alternative,
	 * while a reminder is plain text.
	 */
	private List<Sent> reminders(String email) {
		return mail.sent().stream()
				.filter(message -> message.to().equalsIgnoreCase(email))
				.filter(message -> message.html() == null)
				.toList();
	}

	/** Everything the natural rhythm owns for one account. */
	private Snapshot snapshot(UUID owner) {
		return new Snapshot(
				jdbc.queryForObject("SELECT count(*) FROM proposal WHERE user_id = ?", Long.class, owner),
				jdbc.queryForObject("SELECT next_proposal_at FROM app_user WHERE id = ?", OffsetDateTime.class,
						owner),
				jdbc.queryForObject("""
						SELECT count(*) FROM ai_memory_episode e JOIN ai_memory m ON m.id = e.ai_memory_id
						WHERE m.user_id = ?
						""", Long.class, owner));
	}

	private record Snapshot(long proposals, OffsetDateTime nextProposalAt, long memoryEpisodes) {
	}

	private static OffsetDateTime at(int hour, int minute) {
		return DAY.atTime(hour, minute).atZone(UserZone.ZONE).toOffsetDateTime();
	}
}
