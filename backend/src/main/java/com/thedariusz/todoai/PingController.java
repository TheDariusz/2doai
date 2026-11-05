package com.thedariusz.todoai;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke endpoint under the {@code /api} namespace. Exists to verify the
 * Pattern B chain end-to-end (Cloudflare Pages Function → Fly backend), since
 * {@code /actuator/health} is not proxied through {@code /api/*}.
 *
 * <p>Load-bearing: Grafana Cloud Synthetic Monitoring probes it every 5 minutes (thedariusz-ops,
 * plan 05), and {@code ApiTestBase} primes CSRF through it. Do not remove.
 */
@RestController
@RequestMapping("/api")
public class PingController {

	@GetMapping("/ping")
	public Map<String, String> ping() {
		return Map.of("status", "ok");
	}

}
