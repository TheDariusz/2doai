-- Category reference table: the 11 fixed life domains (FR-007).
-- Keyed by a stable natural code that mirrors the LifeDomain enum. Static
-- reference data, so no audit columns. Pure CREATE — backward-compatible
-- (expand-only); safe under an image rollback.
CREATE TABLE category (
    code          VARCHAR(32)  PRIMARY KEY,
    name_pl       VARCHAR(255) NOT NULL,
    display_order INTEGER      NOT NULL UNIQUE
);
