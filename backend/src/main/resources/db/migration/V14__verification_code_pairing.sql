-- The pairing V13 left to convention (DEV-51): a verification code and the moment it stops being
-- accepted are one fact, written together by issueVerificationCode and cleared together by
-- markEmailVerified — but the columns were independently nullable, so a half-written pair was a
-- latent NPE on the read that null-checks the hash and then dereferences the expiry.
--
-- Expand-only and safe under an image rollback: every row today has both columns null (V13 added
-- them empty and every write since sets or clears both), so the constraint validates without
-- rewriting anything, and the previous image writes the same pairs this one does. Hibernate's
-- ddl-auto=validate ignores CHECK constraints, so no mapping moves with it.

ALTER TABLE app_user ADD CONSTRAINT verification_code_pairing
    CHECK ((verification_code_hash IS NULL) = (verification_expires_at IS NULL));
