package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Re-authentication payload for the irreversible FR-019 account deletion (the
 * {@code AccountDeletion} schema in {@code openapi.yaml}). A live session is not sufficient
 * authority to erase an account — an unattended logged-in browser must not be one click from it.
 */
public record DeleteAccountRequest(

		@NotBlank
		@Utf8ByteLength(max = 72)
		String password) {
}
