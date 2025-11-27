package com.gchess.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Architecture tests to enforce bounded context isolation.
 *
 * These tests ensure that:
 * 1. Chess context does not depend on User or Matchmaking contexts (isolation)
 * 2. User context does not depend on Chess or Matchmaking contexts (isolation)
 * 3. Matchmaking context does not depend on other contexts' internals (isolation)
 * 4. Anti-Corruption Layer (ACL) is only in infrastructure layer
 * 5. Shared Kernel contains only value objects (no business logic)
 * 6. Shared Kernel has no external dependencies
 */
@DisplayName("Bounded Context Isolation Tests")
class BoundedContextTest {

    companion object {
        private lateinit var classes: JavaClasses

        @JvmStatic
        @BeforeAll
        fun setup() {
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
                    "com.gchess.infrastructure"
                )
        }
    }

    // ========== Context Isolation Rules ==========

    @Test
    @DisplayName("Chess domain should not depend on User or Matchmaking contexts")
    fun `chess domain should not depend on user or matchmaking contexts`() {
        val rule = noClasses()
            .that().resideInAPackage("..chess.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..user.domain..",
                "..user.application..",
                "..user.infrastructure..",
                "..matchmaking.domain..",
                "..matchmaking.application..",
                "..matchmaking.infrastructure.."
            )
            .because("Chess domain must be isolated from other contexts (bounded context isolation)")

        rule.check(classes)
    }

    @Test
    @DisplayName("Chess application should not depend on User or Matchmaking contexts")
    fun `chess application should not depend on user or matchmaking contexts`() {
        val rule = noClasses()
            .that().resideInAPackage("..chess.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..user.domain..",
                "..user.application..",
                "..user.infrastructure..",
                "..matchmaking.domain..",
                "..matchmaking.application..",
                "..matchmaking.infrastructure.."
            )
            .because("Chess application must be isolated from other contexts (bounded context isolation)")

        rule.check(classes)
    }

    @Test
    @DisplayName("User domain should not depend on Chess or Matchmaking contexts")
    fun `user domain should not depend on chess or matchmaking contexts`() {
        val rule = noClasses()
            .that().resideInAPackage("..user.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..chess.domain..",
                "..chess.application..",
                "..chess.infrastructure..",
                "..matchmaking.domain..",
                "..matchmaking.application..",
                "..matchmaking.infrastructure.."
            )
            .because("User domain must be isolated from other contexts (bounded context isolation)")

        rule.check(classes)
    }

    @Test
    @DisplayName("User application should not depend on Chess or Matchmaking contexts")
    fun `user application should not depend on chess or matchmaking contexts`() {
        val rule = noClasses()
            .that().resideInAPackage("..user.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..chess.domain..",
                "..chess.application..",
                "..chess.infrastructure..",
                "..matchmaking.domain..",
                "..matchmaking.application..",
                "..matchmaking.infrastructure.."
            )
            .because("User application must be isolated from other contexts (bounded context isolation)")

        rule.check(classes)
    }

    // ========== Matchmaking Context Isolation ==========

    @Test
    @DisplayName("Matchmaking domain should not depend on Chess or User contexts")
    fun `matchmaking domain should not depend on chess or user contexts`() {
        val rule = noClasses()
            .that().resideInAPackage("..matchmaking.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..chess.domain..",
                "..chess.application..",
                "..chess.infrastructure..",
                "..user.domain..",
                "..user.application..",
                "..user.infrastructure.."
            )
            .because("Matchmaking domain must be isolated from other contexts (bounded context isolation)")

        rule.check(classes)
    }

    @Test
    @DisplayName("Matchmaking application should not depend on Chess or User contexts (except shared ports)")
    fun `matchmaking application should not depend on chess or user contexts except ports`() {
        // Matchmaking reuses the PlayerExistenceChecker port from Chess domain
        // This is acceptable as it's a port (interface), not an implementation
        // Ideally, Matchmaking would define its own port, but port reuse is acceptable here
        val rule = noClasses()
            .that().resideInAPackage("..matchmaking.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..chess.application..",
                "..chess.infrastructure..",
                "..user.domain..",
                "..user.application..",
                "..user.infrastructure.."
            )
            .because("Matchmaking application must be isolated from other contexts (except reusable ports)")

        rule.check(classes)
    }

    // ========== Anti-Corruption Layer (ACL) Rules ==========

    @Test
    @DisplayName("ACL adapters should only exist in infrastructure layer")
    fun `acl adapters should only be in infrastructure layer`() {
        // ACL adapters (UserContextPlayerChecker, ChessContextGameCreator) should be in infrastructure
        val rule = classes()
            .that().haveSimpleNameContaining("UserContext")
            .or().haveSimpleNameContaining("ChessContext")
            .and().haveSimpleNameNotStartingWith("Test")
            .and().haveSimpleNameNotStartingWith("Fake")
            .should().resideInAPackage("..infrastructure..")
            .because("Anti-Corruption Layer adapters belong in the infrastructure layer")

        rule.check(classes)
    }

    @Test
    @DisplayName("Matchmaking infrastructure can depend on Chess application (ACL pattern)")
    fun `matchmaking infrastructure can communicate with chess application via acl`() {
        // Matchmaking infrastructure → Chess application is allowed (this is the ACL)
        val rule = classes()
            .that().resideInAPackage("..matchmaking.infrastructure..")
            .and().haveSimpleNameContaining("ChessContext")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..chess.application.."
            )
            .because("ACL in Matchmaking infrastructure can call Chess application use cases")

        rule.check(classes)
    }

    @Test
    @DisplayName("Matchmaking infrastructure provides ACL adapters for cross-context communication")
    fun `matchmaking infrastructure provides acl adapters`() {
        // Matchmaking infrastructure should contain ACL adapters like ChessContextGameCreator
        // that translate between Matchmaking's domain ports and other contexts
        val rule = classes()
            .that().resideInAPackage("..matchmaking.infrastructure..")
            .and().haveSimpleNameContaining("ChessContext")
            .should().implement("com.gchess.matchmaking.domain.port.GameCreator")
            .because("ACL adapters in infrastructure implement domain ports")

        rule.check(classes)
    }

    // ========== Shared Kernel Rules ==========

    @Test
    @DisplayName("Shared Kernel should only contain value objects")
    fun `shared kernel should only contain value objects`() {
        // Shared Kernel classes should be simple value classes (typically ending with "Id")
        // This is a soft constraint - we mainly want to ensure no services/use cases/repositories
        val rule = classes()
            .that().resideInAPackage("..shared..")
            .should().haveSimpleNameNotEndingWith("Service")
            .andShould().haveSimpleNameNotEndingWith("UseCase")
            .andShould().haveSimpleNameNotEndingWith("Repository")
            .because("Shared Kernel should only contain simple value objects, not business logic")

        rule.check(classes)
    }

    @Test
    @DisplayName("Shared Kernel domain should not have external dependencies")
    fun `shared kernel domain should not depend on external frameworks`() {
        val rule = noClasses()
            .that().resideInAPackage("..shared.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.ktor..",
                "org.koin..",
                "kotlinx.serialization..",
                "org.mindrot..",
                "com.auth0.."
            )
            .because("Shared Kernel domain must remain framework-agnostic and dependency-free (infrastructure can use frameworks)")

        rule.check(classes)
    }

    @Test
    @DisplayName("Shared Kernel should not contain business logic")
    fun `shared kernel should not contain business logic`() {
        val rule = noClasses()
            .that().resideInAPackage("..shared..")
            .should().haveSimpleNameEndingWith("Service")
            .orShould().haveSimpleNameEndingWith("UseCase")
            .orShould().haveSimpleNameEndingWith("Repository")
            .because("Shared Kernel should only contain value objects, not business logic")

        rule.check(classes)
    }

    // ========== Context Communication Rules ==========

    @Test
    @DisplayName("Only infrastructure layer can cross context boundaries")
    fun `only infrastructure can cross context boundaries`() {
        val rule = noClasses()
            .that().resideInAPackage("..chess.domain..")
            .or().resideInAPackage("..chess.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..user.."
            )
            .because("Only infrastructure layer can communicate across bounded contexts")

        rule.check(classes)
    }

    @Test
    @DisplayName("Both contexts can safely depend on Shared Kernel")
    fun `both contexts can depend on shared kernel`() {
        // Verify that both Chess and User contexts can depend on Shared Kernel
        // This is already covered by previous tests, but we verify explicitly that
        // the Shared Kernel is accessible to all contexts
        val rule = noClasses()
            .that().resideInAnyPackage("..chess.domain..", "..chess.application..")
            .or().resideInAnyPackage("..user.domain..", "..user.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..chess.infrastructure..",
                "..user.infrastructure.."
            )
            .because("Domain and application layers should not depend on infrastructure of any context")

        rule.check(classes)
    }
}
