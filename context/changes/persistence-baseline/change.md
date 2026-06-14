---
id: persistence-baseline
title: "Persistence baseline — Postgres + JPA + Flyway + 11 seeded categories"
roadmap_id: F-01
status: impl_reviewed
created: 2026-06-13
updated: 2026-06-14
prd_refs:
  - NFR (trwałość: 100% odzysk po awarii)
  - FR-007 (stała lista 11 kategorii)
---

# Persistence baseline (F-01)

Root foundation of the 2do AI roadmap: wire a durable data layer (PostgreSQL via
Spring Data JPA/Hibernate + Flyway migrations) onto the existing Spring Boot
skeleton, and seed the 11 fixed life-domain categories as reference data. No
domain entities — those arrive with the first slice that integrates the DB
through real user behaviour (S-01 `account-and-auth`, S-02 `goals-and-dreams`).

Unlocks: S-01, S-02, S-07 and every slice that persists data; exposes the 11
categories (FR-007) consumed by S-02 / S-08 / S-09.

- Plan: `plan.md`
- Brief: `plan-brief.md`
