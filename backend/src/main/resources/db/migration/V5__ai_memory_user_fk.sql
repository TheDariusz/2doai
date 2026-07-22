-- Close the intentionally-deferred FK from V3 (ai_memory.user_id → user table): now that
-- app_user exists (V4), turn the plain UNIQUE UUID column into a real foreign key. Expand-only
-- ALTER, safe under an image rollback.
--
-- Safe because ai_memory is empty — F-02 shipped no writers, so no existing row can violate the
-- constraint. NO ON DELETE CASCADE: FR-019 account deletion is app-orchestrated (an
-- AccountDeletionService walks a PerUserDataDeleter registry, S-01 Phase 2). A plain FK is the
-- DB backstop behind that orchestration — an out-of-order delete of app_user (a forgotten
-- deleter) fails loudly with an FK violation instead of silently orphaning ai_memory rows.

ALTER TABLE ai_memory
    ADD CONSTRAINT fk_ai_memory_user
    FOREIGN KEY (user_id) REFERENCES app_user (id);
