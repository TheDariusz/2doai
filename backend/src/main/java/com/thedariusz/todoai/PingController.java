package com.thedariusz.todoai;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke endpoint under the {@code /api/v1} namespace. Exists to verify the
 * Pattern B chain end-to-end (Cloudflare Pages Function → Fly backend), since
 * {@code /actuator/health} is not proxied through {@code /api/*}. Removable once
 * real controllers exist.
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

	@GetMapping("/ping")
	public Map<String, String> ping() {
		return Map.of("status", "ok");
	}

}
