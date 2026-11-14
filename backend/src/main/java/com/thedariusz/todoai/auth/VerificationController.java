package com.thedariusz.todoai.auth;

import com.thedariusz.todoai.user.CodePurpose;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two public resources address verification is made of (DEV-51), verb-free like the rest of the
 * API: proving an address is creating a <em>verification</em>, asking for another code is creating a
 * <em>verification code</em>. Both are public and both are CSRF-protected, exactly as registration
 * is — public is not the same as unprotected.
 *
 * <p>One class for two collections because they are two halves of one conversation, and splitting
 * them would buy a file and no clarity.
 */
@RestController
class VerificationController {

	private final EmailVerificationService verification;

	VerificationController(EmailVerificationService verification) {
		this.verification = verification;
	}

	/**
	 * 204 rather than a body: there is nothing to say back that the caller does not know, and no
	 * session is opened — the user logs in next, through the one path that creates sessions. Spends a
	 * sign-in code exactly as {@code POST /api/sessions} does, minus the session; release 2 removes this
	 * endpoint, since the first code sign-in now proves the address on its own.
	 */
	@PostMapping("/api/verifications")
	ResponseEntity<Void> verify(@Valid @RequestBody EmailVerificationRequest request) {
		verification.redeem(request.email(), request.code(), CodePurpose.SIGN_IN);
		return ResponseEntity.noContent().build();
	}

	/**
	 * <b>202 for every address</b> — one with no account is sent nothing, and the answer does not say
	 * so. "Accepted" is the honest word for it: the server has taken the request, the message (if any)
	 * goes out afterwards on its own thread, and whether one follows is precisely what it declines to
	 * disclose. 429 and 503 are decided before the address is looked up, so they are uniform too.
	 */
	@PostMapping("/api/verification-codes")
	ResponseEntity<Void> sendCode(@Valid @RequestBody VerificationCodeRequest request) {
		verification.resendCode(request.email());
		return ResponseEntity.accepted().build();
	}
}
