package com.thedariusz.todoai.security;

import java.lang.reflect.Method;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import archfixture.LeakyEntity;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.thedariusz.todoai.goal.GoalRepository;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the per-user isolation convention S-01 established: every per-user aggregate implements
 * {@link UserOwned} and is read through a {@link CurrentUser}-scoped finder, never by a
 * client-supplied id alone.
 *
 * <p>S-02 adds the second per-user entity, which is the trigger this class's predecessor named for
 * promoting the hand-rolled N = 1 check into a <b>structural</b> rule: the check no longer enumerates
 * the aggregates it knows about, it derives them from the schema mapping. A third aggregate that
 * carries {@code user_id} and forgets the marker now fails without anyone remembering to add it here
 * — which is the whole point, since the marker is what {@code PerUserDataDeleter} coverage and the
 * future RLS policies key off.
 */
class UserOwnedConventionTest {

	/**
	 * The structural definition of "per-user": the class maps a {@code user_id} column. Either
	 * spelling counts — an explicit {@code @Column(name = "user_id")} or a bare {@code userId} field
	 * that Hibernate's snake_case strategy maps to the same column.
	 */
	private static final DescribedPredicate<JavaClass> MAP_A_USER_ID_COLUMN =
			DescribedPredicate.describe("map a user_id column",
					type -> type.getAllFields().stream().anyMatch(UserOwnedConventionTest::isUserIdColumn));

	private static final ArchRule PER_USER_ENTITIES_ARE_USER_OWNED = classes()
			.that().areAnnotatedWith(Entity.class)
			.and(MAP_A_USER_ID_COLUMN)
			.should().implement(UserOwned.class)
			.because("the UserOwned marker is what scoped finders, FR-019 deleters and future RLS "
					+ "policies key off — an unmarked per-user entity leaks across accounts silently");

	private static boolean isUserIdColumn(JavaField field) {
		String column = field.tryGetAnnotationOfType(Column.class)
				.map(Column::name)
				.filter(StringUtils::isNotBlank)
				.orElse(field.getName());
		return "user_id".equals(column) || "userId".equals(column);
	}

	@Test
	void everyEntityMappingAUserIdColumnImplementsUserOwned() {
		JavaClasses production = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.thedariusz.todoai");

		PER_USER_ENTITIES_ARE_USER_OWNED.check(production);
	}

	/**
	 * The rule has teeth — asserted, not assumed. Without this, a predicate that silently matched
	 * nothing (a renamed column, an annotation lookup that quietly returns empty) would keep passing
	 * forever while guarding nothing. {@link LeakyEntity} deliberately violates the convention and
	 * lives outside the {@code com.thedariusz.todoai} package so Hibernate's entity scan never sees it.
	 *
	 * <p>Nothing here re-enumerates today's aggregates for the rule above's sake: ArchUnit's
	 * {@code archRule.failOnEmptyShould} defaults to true, so a predicate matching zero classes
	 * already fails it. (The repository-surface test below does name {@code GoalRepository}, because
	 * it asserts a property of one interface rather than a rule over the whole package.)
	 */
	@Test
	void theRuleRejectsAnEntityThatCarriesUserIdWithoutTheMarker() {
		JavaClasses violator = new ClassFileImporter().importClasses(LeakyEntity.class);

		assertThatThrownBy(() -> PER_USER_ENTITIES_ARE_USER_OWNED.check(violator))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("LeakyEntity");
	}

	/**
	 * The scoped-access convention, asserted as a <b>structural</b> property rather than by naming
	 * methods that happen to exist. The predecessor of this test checked that
	 * {@code findByIdAndUserId} was declared — which proves nothing about what callers use, since
	 * {@code JpaRepository}'s inherited {@code findById} compiles just as readily and skips the
	 * ownership check entirely. {@link GoalRepository} therefore extends the bare
	 * {@code Repository} marker, and this pins that decision: the unscoped finders must not be on
	 * the surface at all.
	 *
	 * <p>{@code AiMemoryRepository} still extends {@code JpaRepository} and is deliberately not
	 * covered yet — narrowing it is its own change. Its reads go through {@code findByUserId} today.
	 */
	@Test
	void userOwnedRepositoriesDoNotPublishUnscopedFinders() throws NoSuchMethodException {
		assertThat(GoalRepository.class.getMethods())
				.extracting(Method::getName)
				.as("an unscoped finder on a UserOwned repository is a cross-account read waiting to "
						+ "happen — the next caller reaches for the one that compiles")
				.doesNotContain("findById", "findAll", "getReferenceById", "existsById", "deleteById");

		// And the scoped by-id read that replaces it is present (getMethod throws if it is not).
		assertThat(GoalRepository.class.getMethod("findByIdAndUserId", UUID.class, UUID.class))
				.isNotNull();
	}
}
