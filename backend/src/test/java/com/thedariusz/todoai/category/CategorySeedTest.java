package com.thedariusz.todoai.category;

import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Flyway migrations seed exactly the 11 expected domains and that the
 * {@code category} table and the {@link LifeDomain} enum agree. Boots the full
 * context against a real Postgres (Testcontainers) with migrations applied.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CategorySeedTest {

	@Autowired
	CategoryRepository categories;

	@Test
	void seedsExactlyElevenDomains() {
		assertThat(categories.count()).isEqualTo(11);
	}

	@Test
	void codesMatchLifeDomainEnum() {
		Set<String> dbCodes = categories.findAll().stream()
				.map(Category::getCode)
				.collect(Collectors.toSet());
		Set<String> enumCodes = Arrays.stream(LifeDomain.values())
				.map(Enum::name)
				.collect(Collectors.toSet());
		assertThat(dbCodes).isEqualTo(enumCodes);
	}

	@Test
	void displayOrderIsOneToElevenAndUnique() {
		List<Integer> orders = categories.findAll().stream()
				.map(Category::getDisplayOrder)
				.sorted()
				.toList();
		assertThat(orders).containsExactlyElementsOf(
				IntStream.rangeClosed(1, 11).boxed().toList());
	}

	@Test
	void everyPolishNameIsNonBlank() {
		assertThat(categories.findAll())
				.isNotEmpty()
				.allSatisfy(category -> assertThat(category.getNamePl()).isNotBlank());
	}
}
