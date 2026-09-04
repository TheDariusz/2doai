package com.thedariusz.todoai.category;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.thedariusz.todoai.user.AppLanguage;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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
	 * Read once at startup, not per request, and rendered once per language. {@code CategorySyncCheck}
	 * fails boot if the table drifts from the {@code LifeDomain} enum, so these rows cannot change
	 * under a running process — and every query avoided is idle time Neon can autosuspend through.
	 *
	 * <p>Two languages means two immutable collections, not a query with a language argument: eleven
	 * rows twice is nothing to hold, and the alternative would trade the property this field exists
	 * for.
	 */
	private final Map<AppLanguage, CategoryCollection> collections = new EnumMap<>(AppLanguage.class);

	public CategoryController(CategoryRepository categories) {
		List<Category> rows = categories.findAll(Sort.by("displayOrder"));
		for (AppLanguage language : AppLanguage.values()) {
			collections.put(language, new CategoryCollection(rows.stream()
					.map(row -> CategoryResponse.from(row, language))
					.toList()));
		}
	}

	/**
	 * Cacheable, and it says so (Zalando #227): eleven rows a Flyway seed owns, which cannot change
	 * under a running process — the SPA refetches them on every load and an hour of browser cache
	 * saves that round-trip outright. {@code private} because the collection is served behind the
	 * session cookie: identical for every user it may be, but a shared cache holding a response from
	 * an authenticated request is how one user's response reaches another.
	 *
	 * <p>{@code Vary: Accept-Language} is what makes that cache safe now that the body depends on a
	 * request header: keyed on the URL alone, a cache would hand the Polish response to a caller who
	 * asked for English. It ships in the same commit as the second language for that reason, and
	 * {@code CategoryApiTest} asserts the header rather than only the body.
	 *
	 * <p>Set here rather than in {@code SecurityConfig} on purpose. Spring Security's
	 * {@code CacheControlHeadersWriter} defaults every response to {@code no-store} and skips writing
	 * only when the header is <em>already</em> present — a controller runs before the filter chain
	 * writes on commit, so this one wins, and it wins for this endpoint alone.
	 */
	@GetMapping
	ResponseEntity<CategoryCollection> list(Locale locale) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
				.varyBy(HttpHeaders.ACCEPT_LANGUAGE)
				.body(collections.get(AppLanguage.of(locale)));
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
	 * <p>{@code name}, not {@code name_pl} (Zalando #244): the columns keep their language because
	 * each stores exactly one, but the <em>wire</em> field names the role — "the label for this
	 * caller" — and the server picks which column fills it from {@code Accept-Language}. That is what
	 * let the second language land without a client changing at all; a {@code name_pl}/{@code name_en}
	 * pair would instead have made every client choose, and renaming {@code name_pl} now would be the
	 * breaking change (#106) this spelling avoided while there was exactly one client to move.
	 */
	record CategoryResponse(String code, String name, int displayOrder) {

		static CategoryResponse from(Category category, AppLanguage language) {
			// A switch expression, not a ternary: it is exhaustive, so a third language would be a
			// compile error here instead of silently reading as English.
			String name = switch (language) {
				case PL -> category.getNamePl();
				case EN -> category.getNameEn();
			};
			return new CategoryResponse(category.getCode(), name, category.getDisplayOrder());
		}
	}
}
