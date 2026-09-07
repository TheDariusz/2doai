package com.thedariusz.todoai.auth;

import com.thedariusz.todoai.user.AppLanguage;
import jakarta.validation.constraints.NotNull;

/**
 * Update payload for {@code PATCH /api/users/me} (the {@code UserUpdate} schema in
 * {@code openapi.yaml}) — FR-002, the account's language setting.
 *
 * <p><b>PATCH rather than PUT</b>, unlike {@code GoalUpdate}'s full replace: the other members of
 * the {@code User} schema are the id and the email, neither of which this operation may touch, so a
 * full-replace body would consist of two fields the server must ignore and one it acts on.
 *
 * <p>A field the client omits is a <b>422</b> (this {@code @NotNull}), while a value outside the
 * enum never deserializes at all and is a <b>400</b> — the same split {@code GoalApiTest} pins for
 * the goal enums, and the reason the SPA can branch on the status alone.
 */
public record UserUpdate(

		@NotNull
		AppLanguage language) {
}
