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
import com.thedariusz.todoai.ai.memory.AiMemory;
import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.goal.Goal;
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
			new DescribedPredicate<>("map a user_id column") {
				@Override
				public boolean test(JavaClass type) {
					return type.getAllFields().stream().anyMatch(UserOwnedConventionTest::isUserIdColumn);
				}
			};

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
	 */
	@Test
	void theRuleRejectsAnEntityThatCarriesUserIdWithoutTheMarker() {
		JavaClasses violator = new ClassFileImporter().importClasses(LeakyEntity.class);

		assertThatThrownBy(() -> PER_USER_ENTITIES_ARE_USER_OWNED.check(violator))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("LeakyEntity");
	}

	/** Both of today's per-user aggregates are matched by the rule, not skipped by it. */
	@Test
	void bothPerUserAggregatesAreCoveredByTheRule() {
		JavaClasses aggregates = new ClassFileImporter().importClasses(AiMemory.class, Goal.class);

		assertThat(aggregates).allMatch(MAP_A_USER_ID_COLUMN);
		PER_USER_ENTITIES_ARE_USER_OWNED.check(aggregates);
	}

	@Test
	void perUserAggregatesAreReadThroughUserScopedFinders() throws NoSuchMethodException {
		// The scoped-access convention: per-user reads go through a user-scoped finder — including the
		// by-id one, so a client-supplied aggregate id can never reach a row it does not own.
		assertThat(AiMemoryRepository.class.getMethod("findByUserId", UUID.class).getReturnType())
				.isEqualTo(java.util.Optional.class);
		Method scopedById = GoalRepository.class.getMethod("findByIdAndUserId", UUID.class, UUID.class);
		assertThat(scopedById.getReturnType()).isEqualTo(java.util.Optional.class);
	}
}
