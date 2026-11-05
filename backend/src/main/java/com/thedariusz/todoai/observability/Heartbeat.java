package com.thedariusz.todoai.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Logs {@code event=heartbeat} every five minutes, so that silence is something an alert can see:
 * the "2doai went quiet" rule in thedariusz-ops fires when no heartbeat arrives within 15 minutes.
 * The HTTP check on {@code /api/ping} proves the web tier answers; this proves the JVM, its
 * scheduler and the log pipeline to Loki are all still alive.
 *
 * <p><b>Late, not lost.</b> It shares Spring Boot's single scheduler thread with
 * {@code ProposalScheduler}'s tick. A long fire (a model call plus SMTP) delays the next beat until
 * the thread is free; {@code fixedRate} then runs the overdue beat at once. The 15-minute window
 * tolerates one missed beat, so only a scheduler stuck for two whole periods alerts — which is
 * exactly what should.
 */
@Component
public class Heartbeat {

	private static final Logger log = LoggerFactory.getLogger(Heartbeat.class);

	@Scheduled(fixedRate = 300_000)
	void beat() {
		log.atInfo().addKeyValue("event", "heartbeat").log("heartbeat");
	}

}
