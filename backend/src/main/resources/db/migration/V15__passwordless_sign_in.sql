-- Passwordless sign-in, release 1 (expand). The e-mailed code becomes a sign-in credential beside the
-- password, and release 2 removes the password. This migration only widens what the schema accepts,
-- so the image currently in production runs unharmed against it.
--
-- password_hash drops NOT NULL so an account can exist without a password. Release 1 never writes a
-- null here; release 2 does, and a rollback from release 2 to release 1 must still read those rows
-- and sign them in by code. The column itself goes in a later, separate change.
--
-- verification_purpose says what the one outstanding code is for: SIGN_IN, or ACCOUNT_DELETION from
-- release 2 on. The slot stays single — requesting one kind of code overwrites the other — and a code
-- is refused when redeemed for the other purpose. NOT NULL is safe only because of the DEFAULT: the
-- previous image inserts rows without naming the column (the same move V13 made with
-- verification_attempts). No CHECK on the values, so nothing the previous image writes can violate one.

ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE app_user ADD COLUMN verification_purpose VARCHAR(32) NOT NULL DEFAULT 'SIGN_IN';
