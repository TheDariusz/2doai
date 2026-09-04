package com.thedariusz.todoai.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only JPA mapping of the {@code category} reference table (the 11 fixed
 * life domains). Hibernate {@code ddl-auto=validate} checks this mapping against
 * Flyway's {@code V1} at boot — a free schema drift guard. No write paths are
 * exposed; the data is static reference data seeded by migration.
 */
@Entity
@Table(name = "category")
public class Category {

	@Id
	private String code;

	@Column(name = "name_pl", nullable = false)
	private String namePl;

	/** Nullable in the schema (V11 is expand-only), seeded for all 11 rows by that same migration. */
	@Column(name = "name_en")
	private String nameEn;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected Category() {
		// JPA requires a no-arg constructor.
	}

	public String getCode() {
		return code;
	}

	public String getNamePl() {
		return namePl;
	}

	public String getNameEn() {
		return nameEn;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}
}
