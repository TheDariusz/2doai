-- User identity aggregate (S-01, account-and-auth): the first per-user boundary in the app.
-- Follows the project conventions — UUID v7 surrogate PK (time-ordered, index-friendly) and
-- timestamptz audit columns populated by Hibernate @CreationTimestamp / @UpdateTimestamp.
-- Pure CREATE — backward-compatible (expand-only); safe under an image rollback.
--
-- Table is named app_user, NOT user: "user" is a reserved word in Postgres and would force
-- double-quoting in every migration and native query. The Java entity is User, mapped via
-- @Table(name = "app_user"); read data-model.md's / V3's prose "user(id)" as "app_user(id)".

CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    -- email stores the normalized (lowercased) login id; 320 is the RFC 5321 max address length.
    -- The UNIQUE index enforces one account per email AND serves the login lookup (findByEmail),
    -- so no separate index is needed.
    email         VARCHAR(320) NOT NULL UNIQUE,
    -- BCrypt/delegating-encoder hash ({bcrypt}$2a$...); 255 comfortably fits any encoder prefix.
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
