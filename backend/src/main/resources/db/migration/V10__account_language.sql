-- The account's preferred language (DEV-49, FR-002/FR-003/FR-004): the one piece of locale state
-- that outlives a request.
--
-- Why a column at all, when Accept-Language is the transport: the header answers "what should THIS
-- response be rendered in" and cannot answer "what language does this account read" at a moment when
-- no request exists — which is exactly what the natural-rhythm e-mail (FR-009) needs at send time,
-- and what the SPA seeds itself from at login. The header decides a response; this column decides
-- the account.
--
-- Nullable, no DEFAULT, following V9__scheduler_state.sql. Null means "never chosen; read as
-- Polish", and that IS the FR-004 backfill: every account that existed before this migration keeps
-- reading Polish without a single row being rewritten and without ever being asked. Expand-only and
-- safe under an image rollback — the previous image simply never reads it.
--
-- VARCHAR(2) holds the AppLanguage name() ('PL' / 'EN'), not a BCP 47 tag: the app has exactly two
-- locales and no region variants, and the enum is the anchor both sides map onto.

ALTER TABLE app_user ADD COLUMN preferred_language VARCHAR(2);
