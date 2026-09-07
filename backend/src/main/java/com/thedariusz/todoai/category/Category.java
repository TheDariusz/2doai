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

	/** Added nullable by V11, which seeds all 11 rows; NOT NULL since V12 states that as a rule. */
	@Column(name = "name_en", nullable = false)
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
