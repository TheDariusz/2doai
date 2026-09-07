-- The English half of the category label pair (DEV-49, FR-005): the 11 life domains gain the name a
-- caller reading English is served.
--
-- Why a second column rather than a translation table: the set is eleven rows fixed by V2 and a
-- Flyway seed owns every one of them, so the "many languages" shape would buy a join and a
-- migration per locale to model a list that cannot grow at runtime. The wire has said as much since
-- S-01 — `CategoryResponse.name` is named for its role ("the label for this caller"), so adding the
-- column changes what that field contains and no client changes at all.
--
-- Nullable, no DEFAULT, following V9__scheduler_state.sql and V10__account_language.sql. Expand-only
-- and safe under an image rollback: the previous image reads name_pl and never looks at this column,
-- and NOT NULL would have made the rollback the thing that broke. Nothing in the schema therefore
-- guarantees the seed ran, which is what CategorySeedTest.everyEnglishNameIsNonBlank is for.
--
-- UPDATE, not a re-INSERT: the rows exist and their `code` is the stable natural key both this file
-- and the LifeDomain enum are anchored on.

ALTER TABLE category ADD COLUMN name_en VARCHAR(255);

UPDATE category SET name_en = 'Health'                       WHERE code = 'HEALTH';
UPDATE category SET name_en = 'Finances'                     WHERE code = 'FINANCE';
UPDATE category SET name_en = 'Career & professional growth' WHERE code = 'CAREER';
UPDATE category SET name_en = 'Education & personal growth'  WHERE code = 'EDUCATION';
UPDATE category SET name_en = 'Relationships'                WHERE code = 'RELATIONSHIPS';
UPDATE category SET name_en = 'Home & surroundings'          WHERE code = 'HOME';
UPDATE category SET name_en = 'Leisure & hobbies'            WHERE code = 'LEISURE';
UPDATE category SET name_en = 'Admin & paperwork'            WHERE code = 'ADMIN';
UPDATE category SET name_en = 'Safety & preparedness'        WHERE code = 'SAFETY';
UPDATE category SET name_en = 'Transport & mobility'         WHERE code = 'TRANSPORT';
UPDATE category SET name_en = 'Inner growth & values'        WHERE code = 'INNER_GROWTH';
