package com.thedariusz.todoai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} is here for exactly one job: {@code ProposalScheduler}'s tick, the loop
 * that lets the app come back to the user on its own (S-05, FR-011). It is also why the Fly machine
 * is pinned always-on in {@code fly.toml} — a scale-to-zero machine has no thread to tick with.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
