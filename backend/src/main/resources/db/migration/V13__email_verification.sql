-- Proof that whoever signed up actually reads the address they typed (DEV-51): the account exists
-- from the moment it registers, but it is inert — it cannot log in, and the natural rhythm never
-- writes to it — until a 6-digit code sent to that mailbox comes back.
--
-- Why the state lives on app_user rather than in a table of its own: there is at most one code
-- outstanding per account, it dies with the account, and it is read on exactly the paths that
-- already load the row (login, verify, resend). A pending_verification table would be a join and a
-- second lifetime to keep in step for a value that never outlives the row it belongs to.
--
-- email_verified_at is the state, not a boolean: "when" answers "whether" and also tells us how
-- long an account sat unproved, which is what a future cleanup of dead sign-ups would key on.
-- The hash and its expiry are cleared when the code is spent, so a code can never be replayed.
-- The code itself is never stored — verification_code_hash holds the same PasswordEncoder output
-- app_user.password_hash does, hence the identical VARCHAR(255).
--
-- The backfill is load-bearing, unlike V9's and V10's: null here means "never proved", so shipping
-- this without the UPDATE would lock every existing account out of its own app on the deploy that
-- applied it. Existing accounts are verified as of the moment they were created and notice nothing.
--
-- verification_attempts is the one NOT NULL column, and it is safe only because of the DEFAULT:
-- the currently deployed image inserts rows without naming it (see V12's prose on why the same
-- move on category was safe and on app_user would not have been). Expand-only otherwise — under an
-- image rollback the columns are simply never read, and the previous image sends no codes either.

ALTER TABLE app_user ADD COLUMN email_verified_at       TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN verification_code_hash  VARCHAR(255);
ALTER TABLE app_user ADD COLUMN verification_expires_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN verification_attempts   INTEGER NOT NULL DEFAULT 0;

UPDATE app_user SET email_verified_at = created_at WHERE email_verified_at IS NULL;
