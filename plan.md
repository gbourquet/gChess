# Plan d'implémentation: Architecture multi-contextes User + Chess

## Approche retenue

Packages séparés avec Shared Kernel (monolithe modulaire) :
- Facilite la séparation future en microservices
- Garde la simplicité d'un monolithe pour le développement
- Séparation claire des bounded contexts

## Considérations importantes

### Breaking Changes assumés
- L'API REST change de format (currentPlayer: Color → PlayerId + currentSide: PlayerSide)
- Pas de versioning pour la V1 - migration directe acceptée
- Les DTOs seront incompatibles avec l'ancienne version

### Principes de design
- **Pas de duplication d'invariants**: currentPlayer dérivé de currentSide (propriété calculée)
- **Fail-fast dans ACL**: Erreurs du contexte User propagées immédiatement
- **Tests d'architecture stricts**: ArchUnit valide l'isolation des contextes

### Points de vigilance
- Phase 2.1 (réorganisation) = phase la plus risquée, exécuter tests après chaque sous-étape
- Les tests d'architecture actuels devront être adaptés pour multi-contexts
- Shared Kernel minimal (uniquement value objects, pas de logique métier)

## Phase 1: Préparation et Refactoring (découplage Color)

### 1.1 Créer le Shared Kernel

- Créer src/main/kotlin/com/gchess/shared/domain/model/
- Créer PlayerId.kt (value class inline wrappant ULID)
- Créer GameId.kt (value class inline wrappant String ULID pour compatibilité)

### 1.2 Renommer Color → PlayerSide dans le contexte Chess

- Renommer enum Color → PlayerSide (WHITE, BLACK = côté échiquier)
- Mise à jour de toutes les références dans:
    - ChessPosition.kt
    - Piece.kt
    - StandardChessRules.kt
    - Tous les tests unitaires

### 1.3 Refactorer Game pour utiliser PlayerId

- Modifier Game.kt:
    - Ajouter whitePlayer: PlayerId, blackPlayer: PlayerId
    - Remplacer currentPlayer: Color par currentSide: PlayerSide
    - Ajouter propriété calculée `currentPlayer: PlayerId` dérivée de currentSide
      ```kotlin
      val currentPlayer: PlayerId
          get() = if (currentSide == PlayerSide.WHITE) whitePlayer else blackPlayer
      ```
    - **Rationale**: Évite la duplication - la relation whitePlayer↔WHITE est un invariant
- Adapter la logique de switch de tour (toggle currentSide)
- Mettre à jour isPlayerTurn() pour accepter PlayerId

## Phase 2: Créer le contexte User

**Création du nouveau contexte avant de toucher l'existant (approche moins risquée)**

### 2.1 Structure et domaine User

- Créer structure src/main/kotlin/com/gchess/user/
- Implémenter le domaine User:
    - user/domain/model/User.kt (id: PlayerId, username, email, passwordHash)
    - user/domain/model/Credentials.kt (username, password)
    - user/domain/port/UserRepository.kt
    - user/domain/port/PasswordHasher.kt

### 2.2 Application layer User

- Implémenter les use cases:
    - user/application/usecase/RegisterUserUseCase.kt
    - user/application/usecase/LoginUseCase.kt
    - user/application/usecase/GetUserUseCase.kt

### 2.3 Infrastructure User

- Implémenter les adapters driven:
    - user/infrastructure/adapter/driven/InMemoryUserRepository.kt
    - user/infrastructure/adapter/driven/BcryptPasswordHasher.kt
- Implémenter les adapters driver:
    - user/infrastructure/adapter/driver/UserRoutes.kt
    - user/infrastructure/adapter/driver/AuthRoutes.kt
    - user/infrastructure/adapter/driver/dto/UserDTO.kt
    - user/infrastructure/adapter/driver/dto/LoginRequest.kt
    - user/infrastructure/adapter/driver/dto/RegisterRequest.kt

### 2.4 Wiring User context

- Mettre à jour KoinModule.kt pour injecter les dépendances User
- Mettre à jour Application.kt pour configurer les routes User
- Exécuter `./gradlew build` pour valider

## Phase 3: Réorganiser Chess dans son bounded context

**⚠️ Phase la plus risquée - casse tous les imports et tests**

Subdiviser en étapes pour limiter les risques:

### 3.1 Créer la nouvelle structure
- Créer src/main/kotlin/com/gchess/chess/ (vide)
- Créer chess/domain/, chess/application/, chess/infrastructure/

### 3.2 Déplacer le domaine
- Déplacer com.gchess.domain → com.gchess.chess.domain
- Mettre à jour imports dans domain uniquement
- Exécuter `./gradlew unitTest` pour valider

### 3.3 Déplacer l'application
- Déplacer com.gchess.application → com.gchess.chess.application
- Mettre à jour imports
- Exécuter `./gradlew unitTest`

### 3.4 Déplacer l'infrastructure
- Déplacer com.gchess.infrastructure → com.gchess.chess.infrastructure
- Mettre à jour KoinModule.kt, Application.kt
- Exécuter `./gradlew build`

### 3.5 Mettre à jour les tests
- Déplacer tests unitaires vers chess/
- Déplacer tests d'architecture vers racine (ils testent tous les contexts)
- Déplacer tests d'intégration vers chess/
- Mettre à jour packages dans HexagonalArchitectureTest.kt
- Exécuter `./gradlew check`

## Phase 4: Anti-Corruption Layer (Chess → User)

### 4.1 Créer le port PlayerExistenceChecker

- Dans chess/domain/port/PlayerExistenceChecker.kt
- Interface pour vérifier l'existence des joueurs

### 4.2 Implémenter l'adapter UserContextPlayerChecker

- Dans chess/infrastructure/adapter/driven/
- Implémente PlayerExistenceChecker en appelant GetUserUseCase
- **Gestion d'erreurs**: Définir la stratégie si User context échoue
    - Option 1: Propager l'erreur (fail-fast)
    - Option 2: Fallback pessimiste (considérer joueur inexistant si timeout)
    - **Recommandation**: Fail-fast pour la V1 (simplicité)

### 4.3 Mettre à jour les use cases Chess

- CreateGameUseCase: accepter whitePlayerId + blackPlayerId, valider via PlayerExistenceChecker
- MakeMoveUseCase: accepter playerId, vérifier que c'est son tour
- Adapter GameRoutes pour passer le playerId

## Phase 5: Authentification

### 5.1 Ajouter dépendances JWT

- Ajouter io.ktor:ktor-server-auth-jwt dans build.gradle.kts
- Ajouter bibliothèque de hashing (bcrypt)

### 5.2 Configurer JWT dans Application.kt

- Install Authentication plugin
- Configuration JWT avec secret et validation

### 5.3 Protéger les routes Chess

- Utiliser authenticate { } sur GameRoutes
- Extraire playerId du token JWT
- Passer aux use cases

### 5.4 Implémenter LoginResponse avec token

- LoginResponse.kt (token: String, userId: PlayerId)
- Générer JWT dans LoginUseCase

## Phase 6: Tests et Documentation

### 6.1 Tests d'architecture ArchUnit

**Mettre à jour HexagonalArchitectureTest.kt:**
- Adapter les packages: com.gchess.chess.domain, com.gchess.user.domain, com.gchess.shared

**Ajouter BoundedContextTest.kt (nouveau):**
- Test: chess.domain ne doit pas dépendre de user (isolation contexts)
- Test: user.domain ne doit pas dépendre de chess (isolation contexts)
- Test: ACL uniquement dans infrastructure (chess.infrastructure → user.application OK)
- Test: shared ne contient que des value objects (pas de dépendances externes)
- Test: shared ne contient pas de logique métier (classes simples uniquement)

### 6.2 Tests d'intégration E2E

- Scénario: Register → Login → CreateGame → MakeMove
- Vérifier authentification JWT fonctionne
- Vérifier validation des joueurs

### 6.3 Mise à jour documentation

- README.md: nouvelle architecture, bounded contexts
- CLAUDE.md: structure user/chess/, ACL pattern, invariants du domaine
    - Documenter invariants Game:
        - whitePlayer joue toujours PlayerSide.WHITE
        - blackPlayer joue toujours PlayerSide.BLACK
        - currentPlayer est dérivé de currentSide (pas de duplication)
        - On ne peut pas changer de côté en cours de partie
- Créer CONTEXT_MAP.md: diagramme des relations entre contextes
- **Note sur compatibilité API**: L'API GameDTO change
    - Avant: `currentPlayer: "WHITE"` (côté échiquier)
    - Après: `currentPlayer: "ulid-du-joueur"` (identité joueur) + `currentSide: "WHITE"`
    - Migration: Breaking change assumé (pas de versioning pour V1)

## Fichiers principaux à créer/modifier

### Nouveaux:

**Shared Kernel:**
- shared/domain/model/PlayerId.kt
- shared/domain/model/GameId.kt

**User Context:**
- user/domain/model/User.kt
- user/domain/model/Credentials.kt
- user/domain/port/UserRepository.kt
- user/domain/port/PasswordHasher.kt
- user/application/usecase/RegisterUserUseCase.kt
- user/application/usecase/LoginUseCase.kt
- user/application/usecase/GetUserUseCase.kt
- user/infrastructure/adapter/driven/InMemoryUserRepository.kt
- user/infrastructure/adapter/driven/BcryptPasswordHasher.kt
- user/infrastructure/adapter/driver/AuthRoutes.kt
- user/infrastructure/adapter/driver/UserRoutes.kt
- user/infrastructure/adapter/driver/dto/UserDTO.kt (et autres DTOs)

**Chess Context (ACL):**
- chess/domain/port/PlayerExistenceChecker.kt
- chess/infrastructure/adapter/driven/UserContextPlayerChecker.kt

**Tests:**
- architectureTest/BoundedContextTest.kt

**Documentation:**
- CONTEXT_MAP.md

### Modifiés:

- Tous les fichiers actuels (déplacement dans chess/)
- Color.kt → PlayerSide.kt
- Game.kt (ajout whitePlayer, blackPlayer)
- CreateGameUseCase.kt
- MakeMoveUseCase.kt
- GameRoutes.kt (ajout auth)
- Application.kt (ajout auth JWT)
- build.gradle.kts (dépendances JWT)

## Ordre d'exécution

Le plan suit l'approche **moins risquée** : créer le nouveau contexte (User) avant de réorganiser l'existant (Chess).

1. **Phase 1** - Préparation et Refactoring (découplage Color)
2. **Phase 2** - Créer le contexte User (nouveau code, pas de risque)
3. **Phase 3** - Réorganiser Chess dans son bounded context (migration du code existant)
4. **Phase 4** - Anti-Corruption Layer (connecter les deux contextes)
5. **Phase 5** - Authentification (sécuriser l'application)
6. **Phase 6** - Tests et Documentation (valider et documenter)

**Rationale**: Créer le contexte User d'abord réduit le blast radius. Si la réorganisation Chess (Phase 3) échoue, User reste fonctionnel. Les deux contexts peuvent être développés/testés en parallèle.

Chaque phase est indépendante et testable. On peut s'arrêter après chaque phase si besoin.