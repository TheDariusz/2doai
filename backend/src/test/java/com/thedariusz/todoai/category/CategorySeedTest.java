package com.thedariusz.todoai.category;

import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Autowired
	JdbcTemplate jdbc;

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

	/**
	 * V11 added the column without a constraint, so nothing in the schema guaranteed the 11 rows
	 * were actually seeded. This is what does.
	 */
	@Test
	void everyEnglishNameIsNonBlank() {
		assertThat(categories.findAll())
				.isNotEmpty()
				.allSatisfy(category -> assertThat(category.getNameEn()).isNotBlank());
	}

	/**
	 * The test above proves the eleven rows were seeded; this proves nothing can take a label away
	 * again. Without the constraint a row missing its English name is an error nowhere — it
	 * serializes as {@code "name": null} and reaches the user as a nav link with no text on it, the
	 * one failure the frontend cannot tell apart from a domain that is genuinely called nothing.
	 *
	 * <p>Asserted by writing rather than by reading {@code information_schema}, so what is under test
	 * is the rule and not the spelling of the DDL that states it.
	 */
	@Test
	@Transactional
	void refusesToLeaveACategoryWithoutItsEnglishName() {
		assertThatThrownBy(
				() -> jdbc.update("UPDATE category SET name_en = NULL WHERE code = 'HEALTH'"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
