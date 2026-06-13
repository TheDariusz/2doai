package com.thedariusz.todoai.category;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Fail-fast drift guard. At startup, asserts the seeded {@code category} codes
 * exactly match the {@link LifeDomain} enum, so a seed/enum mismatch (a missing,
 * extra, or renamed code) fails the boot instead of surfacing later as a bad AI
 * classification.
 */
@Component
class CategorySyncCheck implements ApplicationRunner {

	private final CategoryRepository categories;

	CategorySyncCheck(CategoryRepository categories) {
		this.categories = categories;
	}

	@Override
	public void run(ApplicationArguments args) {
		Set<String> dbCodes = categories.findAll().stream()
				.map(Category::getCode)
				.collect(Collectors.toCollection(TreeSet::new));
		Set<String> enumCodes = Arrays.stream(LifeDomain.values())
				.map(Enum::name)
				.collect(Collectors.toCollection(TreeSet::new));
		if (!dbCodes.equals(enumCodes)) {
			throw new IllegalStateException(
					"category table is out of sync with the LifeDomain enum — "
							+ "db codes=" + dbCodes + ", enum codes=" + enumCodes);
		}
	}
}
