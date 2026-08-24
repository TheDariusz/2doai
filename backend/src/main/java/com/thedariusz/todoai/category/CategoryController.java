package com.thedariusz.todoai.category;

import java.time.Duration;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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

	/**
	 * Cacheable, and it says so (Zalando #227): eleven rows a Flyway seed owns, which cannot change
	 * under a running process — the SPA refetches them on every load and an hour of browser cache
	 * saves that round-trip outright. {@code private} because the collection is served behind the
	 * session cookie: identical for every user it may be, but a shared cache holding a response from
	 * an authenticated request is how one user's response reaches another.
	 *
	 * <p><b>The day a second language is seeded, this header needs {@code Vary: Accept-Language}</b>
	 * — and this field needs to stop being one collection built once at startup. A cache keyed on the
	 * URL alone would hand the Polish response to a caller who asked for English, which is the one
	 * bug caching introduces that no test of a single-language build can see.
	 *
	 * <p>Set here rather than in {@code SecurityConfig} on purpose. Spring Security's
	 * {@code CacheControlHeadersWriter} defaults every response to {@code no-store} and skips writing
	 * only when the header is <em>already</em> present — a controller runs before the filter chain
	 * writes on commit, so this one wins, and it wins for this endpoint alone.
	 */
	@GetMapping
	ResponseEntity<CategoryCollection> list() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
				.body(collection);
	}

	/**
	 * An object at the top level, never a bare array (Zalando #110): a future field — a count, an
	 * icon set, a translation — can be added alongside {@code items} without breaking every client.
	 */
	record CategoryCollection(List<CategoryResponse> items) {
	}

	/**
	 * Serialized snake_case ({@code display_order}) by the global Jackson strategy.
	 *
	 * <p>{@code name}, not {@code name_pl} (Zalando #244): the column keeps its language because it
	 * stores exactly one, but the <em>wire</em> field names the role — "the label for this caller" —
	 * and the server picks which language fills it. Today there is only Polish to pick. When English
	 * lands, an {@code Accept-Language} header changes what this field contains and no client has to
	 * change at all; a {@code name_pl}/{@code name_en} pair would instead make every client choose,
	 * and renaming {@code name_pl} at that point would be the breaking change (#106) this avoids
	 * while there is exactly one client to move.
	 */
	record CategoryResponse(String code, String name, int displayOrder) {

		static CategoryResponse from(Category category) {
			return new CategoryResponse(category.getCode(), category.getNamePl(), category.getDisplayOrder());
		}
	}
}
