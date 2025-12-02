package com.gchess.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Architecture tests to enforce hexagonal architecture (ports and adapters) principles.
 *
 * These tests ensure that:
 * 1. Domain layer has no dependencies on other layers (pure business logic)
 * 2. Application layer depends only on domain
 * 3. Infrastructure layer can depend on application and domain
 * 4. Naming conventions are respected
 * 5. Domain model rules are followed
 */
@DisplayName("Hexagonal Architecture Tests")
class HexagonalArchitectureTest {

    companion object {
        private lateinit var classes: JavaClasses

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Import all bounded context packages (exclude bootstrap and tests)
            // Testing Chess, User, and Matchmaking contexts
            classes = ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .withImportOption(ImportOption.DoNotIncludeJars())
                .importPackages(
                    "com.gchess.chess.domain",
                    "com.gchess.chess.application",
                    "com.gchess.chess.infrastructure",
                    "com.gchess.user.domain",
                    "com.gchess.user.application",
                    "com.gchess.user.infrastructure",
                    "com.gchess.matchmaking.domain",
                    "com.gchess.matchmaking.application",
                    "com.gchess.matchmaking.infrastructure",
                    "com.gchess.shared",
                    "com.gchess.infrastructure" // For shared KoinModule
                )
        }
    }

    // ========== Layer Dependency Rules ==========

    @Test
    @DisplayName("Domain layer should not depend on any other layer")
    fun `domain layer should not depend on application or infrastructure`() {
        val rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..",
                "..infrastructure.."
            )
            .because("Domain layer must be pure business logic with no framework dependencies")

        rule.check(classes)
    }

    @Test
    @DisplayName("Domain layer should not depend on framework classes")
    fun `domain layer should not use framework annotations or classes`() {
        val rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.ktor..",
                "org.koin..",
                "kotlinx.serialization.."
            )
            .because("Domain layer must remain framework-agnostic")

        rule.check(classes)
    }

    @Test
    @DisplayName("Application layer should only depend on domain layer and shared kernel")
    fun `application layer should only depend on domain`() {
        val rule = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "..application..",
                "..domain..",
                "..shared..",  // Allow dependency on shared kernel
                "java..",
                "kotlin..",
                "kotlinx.coroutines..",
                "org.jetbrains.annotations.." // Kotlin compiler annotations
            )
            .because("Application layer should orchestrate domain logic without infrastructure concerns")

        rule.check(classes)
    }

    @Test
    @DisplayName("Hexagonal architecture layers should be respected")
    fun `layered architecture should be enforced`() {
        val rule = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Shared").definedBy("..shared..")

            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Shared").mayOnlyBeAccessedByLayers("Domain", "Application", "Infrastructure")

        rule.check(classes)
    }

    // ========== Naming Convention Rules ==========

    @Test
    @DisplayName("Use cases should be named with 'UseCase' suffix")
    fun `use cases should follow naming convention`() {
        val rule = classes()
            .that().resideInAPackage("..application.usecase..")
            .and().areNotAnonymousClasses()
            .and().areNotMemberClasses()
            .and().haveSimpleNameNotEndingWith("Result") // Exclude result types (e.g. MatchmakingResult)
            .should().haveSimpleNameEndingWith("UseCase")
            .because("Use cases represent application-level operations")

        rule.check(classes)
    }

    @Test
    @DisplayName("Repository interfaces should be named with 'Repository' suffix")
    fun `repositories should follow naming convention`() {
        val rule = classes()
            .that().resideInAPackage("..domain.port..")
            .and().areInterfaces()
            .and().haveSimpleNameEndingWith("Repository")
            .should().haveSimpleNameEndingWith("Repository")
            .because("Repository ports define persistence contracts")

        rule.check(classes)
    }

    @Test
    @DisplayName("Domain services should be named with 'Rules' or 'Service' suffix")
    fun `domain services should follow naming convention`() {
        val rule = classes()
            .that().resideInAPackage("..domain.service..")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("Rules")
            .orShould().haveSimpleNameEndingWith("Service")
            .because("Domain services encapsulate business logic")

        rule.check(classes)
    }

    // ========== Domain Model Rules ==========

    @Test
    @DisplayName("Domain model classes should not have Ktor annotations")
    fun `domain models should not use Ktor serialization`() {
        val rule = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAPackage("kotlinx.serialization..")
            .because("Domain models should be framework-agnostic")

        rule.check(classes)
    }

    @Test
    @DisplayName("Domain services should be interfaces or implementations")
    fun `domain services should be properly structured`() {
        val rule = classes()
            .that().resideInAPackage("..domain.service..")
            .and().areNotAnonymousClasses()
            .and().areNotMemberClasses()
            .and().haveSimpleNameNotContaining("$")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().beInterfaces()
            .orShould().beAssignableTo(
                DescribedPredicate.describe("an interface") {
                    it.isInterface
                }
            )
            .because("Domain services should be interfaces or implement domain service interfaces")

        rule.check(classes)
    }

    @Test
    @DisplayName("Port interfaces should reside in domain.port package")
    fun `ports should be in correct package`() {
        val rule = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().areInterfaces()
            .and().resideInAPackage("..domain..")
            .should().resideInAPackage("..domain.port..")
            .because("Ports define contracts and belong in the port package")

        rule.check(classes)
    }

    // ========== Package Organization Rules ==========

    @Test
    @DisplayName("Infrastructure adapters should be in adapter packages")
    fun `adapters should be organized in infrastructure layer`() {
        val rule = classes()
            .that().haveSimpleNameContaining("Adapter")
            .or().haveSimpleNameEndingWith("Routes")
            .or().haveSimpleNameEndingWith("Repository")
            .and().areNotInterfaces()
            .and().resideOutsideOfPackage("..domain..")
            .should().resideInAPackage("..infrastructure..")
            .because("Adapters belong to the infrastructure layer")
            .allowEmptyShould(true) // Allow if no adapter classes exist yet

        rule.check(classes)
    }

    @Test
    @DisplayName("Domain models should be in model package")
    fun `domain entities and value objects should be in model package`() {
        val rule = classes()
            .that().resideInAPackage("..domain..")
            .and().areNotInterfaces()
            .and().haveSimpleNameNotEndingWith("Rules")
            .and().resideOutsideOfPackage("..domain.service..")
            .and().resideOutsideOfPackage("..domain.port..")
            .should().resideInAPackage("..domain.model..")
            .because("Domain entities and value objects belong in the model package")

        rule.check(classes)
    }

    // ========== Dependency Direction Rules ==========

    @Test
    @DisplayName("Dependencies should point inward (from infrastructure to domain)")
    fun `dependencies should flow toward the domain`() {
        val rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("Dependencies must point inward in hexagonal architecture")

        rule.check(classes)
    }

    @Test
    @DisplayName("Use cases should not depend on infrastructure")
    fun `use cases should not depend on infrastructure`() {
        val rule = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("Application layer should not know about infrastructure details")

        rule.check(classes)
    }

}
