# Review follow-ups — pl-en-localization

Queued by `/10x-impl-review` triage. Each entry names the phase that must pick it up.

## Phase 4 — the SPA must not PATCH a language the account already has

From F2 of the Phase 1 review (`reviews/impl-review-phase-1.md`).

`PATCH /api/users/me` writes unconditionally. That is deliberate: the obvious server-side guard
(`and u.preferredLanguage <> :language`) would return 0 rows for a no-op, which the handler reads as
"the account is gone" and answers **401** — a user re-selecting their current language would be
logged out. The trap is recorded in `UserController.updateCurrentUser`'s javadoc.

So the guard belongs on the client, and it is not cosmetic: a database query is what wakes the
metered Neon compute (`context/foundation/lessons.md`, "Let a scale-to-zero database actually
sleep"). A person pressing the switch by hand is rare; an SPA reconciling the browser locale against
the account on every boot would be one write per session, forever.

**Required in Phase 4:** the account-menu switch calls `PATCH` only when the chosen language differs
from the `language` already on `/me`. If Phase 4 adds any boot-time reconcile between the detected
browser locale and the account's language, that reconcile must change i18next only — never write.
