package com.thedariusz.todoai.goal;

/**
 * How far ahead of a timed task's due moment its reminder e-mail goes out (DEV-63). A field of the
 * entry, so {@code goal} owns the type; the {@code reminder} module is the one that interprets it.
 *
 * <p>Only a task with a time of day carries one. {@link #ONE_HOUR} is the default a timed task gets
 * when the client sends a time without a lead. A date-only task has no lead: its reminder goes out
 * on the morning of the due day.
 *
 * <p>The constant names are the wire contract ({@code ReminderLead} in {@code openapi.yaml}), held
 * against the spec and the SPA by {@code GoalApiTest.publishesTheWireEnumsTheContractAnchors}.
 */
public enum ReminderLead {

	/** At the due moment itself. */
	AT_TIME,

	FIFTEEN_MINUTES,

	/** The default for a timed task. */
	ONE_HOUR,

	/** At the morning hour of the due day, never later than the due moment. */
	MORNING_OF
}
