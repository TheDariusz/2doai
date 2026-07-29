package com.thedariusz.todoai.category;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-only {@code categories} resource — the 11 fixed life domains (FR-007), which the
 * frontend navigation renders instead of hard-coding the list.
 *
 * <p>Deliberately <b>not paginated</b> despite the usual guideline: this is bounded reference data
 * of exactly 11 rows owned by a Flyway seed, so paging would add a cursor round-trip and buy
 * nothing (a documented exception to Zalando #159).
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

	/**
	 * Read once at startup, not per request. {@code CategorySyncCheck} fails boot if the table drifts
	 * from the {@code LifeDomain} enum, so these rows cannot change under a running process — and every
	 * query avoided is idle time Neon can autosuspend through.
	 */
	private final CategoryCollection collection;

	public CategoryController(CategoryRepository categories) {
		this.collection = new CategoryCollection(categories.findAll(Sort.by("displayOrder")).stream()
				.map(CategoryResponse::from)
				.toList());
	}

	@GetMapping
	CategoryCollection list() {
		return collection;
	}

	/**
	 * An object at the top level, never a bare array (Zalando #110): a future field — a count, an
	 * icon set, a translation — can be added alongside {@code items} without breaking every client.
	 */
	record CategoryCollection(List<CategoryResponse> items) {
	}

	/** Serialized snake_case ({@code name_pl}, {@code display_order}) by the global Jackson strategy. */
	record CategoryResponse(String code, String namePl, int displayOrder) {

		static CategoryResponse from(Category category) {
			return new CategoryResponse(category.getCode(), category.getNamePl(), category.getDisplayOrder());
		}
	}
}
