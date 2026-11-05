package com.thedariusz.todoai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} is here for two jobs. The one that matters is {@code ProposalScheduler}'s
 * tick, the loop that lets the app come back to the user on its own (S-05, FR-011); the other is the
 * five-minute {@code Heartbeat}, which lets an alert notice when the app has gone quiet. Both share
 * Boot's single scheduler thread. It is also why the Fly machine is pinned always-on in
 * {@code fly.toml} — a scale-to-zero machine has no thread to tick with.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
