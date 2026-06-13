package com.thedariusz.todoai.category;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the {@code category} reference data. Reused by later
 * slices (S-02 / S-08 / S-09). Read-only in practice — categories are fixed in
 * the MVP and owned by Flyway migrations.
 */
public interface CategoryRepository extends JpaRepository<Category, String> {
}
