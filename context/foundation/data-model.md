# Data Model

Living data-model doc for 2do AI. Designed **before** the migration that implements
it (design-first). Future slices extend this file as they add tables. Schema is owned
by **Flyway** (`backend/src/main/resources/db/migration`); Hibernate runs
`ddl-auto=validate` and never alters it.

## Entity-Relationship Diagram

```mermaid
erDiagram
    category {
        varchar code PK "stable UPPER_SNAKE natural key; mirrors LifeDomain enum"
        varchar name_pl "Polish display name"
        int display_order "1..11, unique, canonical FR-007 order"
    }
```

`category` is the only table in the persistence baseline (F-01). It is **reference
data** — the 11 fixed life domains (FR-007), seeded by migration and never edited at
runtime in the MVP. It uses a stable natural key (`code`) rather than a surrogate PK
because the code is the identity the AI auto-tag layer (FR-008) classifies into.

### Internationalization

The **language-neutral identity is `code`**, never the label. All domain and AI logic
keys off `code` (= the `LifeDomain` enum) and `display_order`; `name_pl` is *purely a
display label* that nothing functional reads. The explicit `_pl` suffix documents the
locale assumption rather than hiding it behind a bare `name`.

The MVP is Polish-only (single user), so we store one label and stop there (YAGNI).
Adding a language later never touches identity or behavior and is always **expand-only**
(backward-compatible). When a second language is genuinely needed, pick by how many
locales we expect to support:

- **A few fixed languages (PL + EN):** add a `name_en` column — one expand-only
  migration, no joins.
- **Many/growing locales, or a translator workflow:** add a
  `category_translation(category_code, locale, name)` table (locales become data, not
  schema).
- **Adopting i18n tooling anyway:** move labels out of the DB into message catalogs
  keyed by `code`; the table then keeps only `code` + `display_order`. Arguably the
  cleanest for pure display strings.

Because `code` is the identity, any of these is reachable without a destructive
migration.

## Conventions

Every later slice inherits these rules (also recorded in `CLAUDE.md`):

- **Domain aggregates** (User, Goal, Dream, CurrentTask, AI-memory, …) use a **UUID v7**
  surrogate primary key, generated via Hibernate `@UuidGenerator`
  (RFC 9562, `UuidVersion7Strategy` — time-ordered, index-friendly).
- **Audit columns**: every domain table carries `created_at` and `updated_at` of type
  `timestamptz`.
- **Reference tables** (like `category`) may instead use a **stable natural key** and
  omit audit columns, since the data is static and versioned by migration.
- **Columns are `snake_case`**; Java fields map to them (`name_pl` ↔ `namePl`).
- **Migrations are expand-only** in spirit (backward-compatible create/insert), so an
  image rollback never strands the schema. Destructive changes follow expand/contract.

## Planned (not yet designed)

These tables arrive with later slices and are intentionally **not drawn** here yet, to
avoid pre-deciding their schema:

- **`user`** and auth-related tables — S-01 (`account-and-auth`).
- **`goal`**, **`dream`** — S-02 (`goals-and-dreams`).
- **`current_task`** — S-07.
- AI-memory tables (**`profile`**, episodic **log**) — F-02.

Each will reference `category.code` where it needs a life domain.
