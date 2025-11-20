# Plan d'implémentation Matchmaking : Découpage en Phases

## Vue d'ensemble

Implémentation progressive suivant l'architecture hexagonale : **Domain → Infrastructure (Driven) → Application → Infrastructure (Driver) → Configuration**

Chaque phase est testable indépendamment et construit sur la précédente.

---

## Phase 1 : Domain Layer (Foundation) 🏗️

**Objectif** : Créer les modèles et ports du domain (sans dépendances externes)

### Fichiers à créer :
1. `matchmaking/domain/model/QueueEntry.kt`
   - Data class avec `playerId: PlayerId` et `joinedAt: Instant`

2. `matchmaking/domain/model/Match.kt`
   - Data class avec `whitePlayerId`, `blackPlayerId`, `gameId`, `matchedAt`, `expiresAt`
   - Méthode `isExpired(): Boolean`

3. `matchmaking/domain/model/MatchmakingStatus.kt`
   - Enum : `WAITING`, `MATCHED`

4. `matchmaking/domain/port/MatchmakingQueue.kt`
   - Interface avec méthodes : `addPlayer()`, `removePlayer()`, `findMatch()`, `isPlayerInQueue()`, `getQueueSize()`

5. `matchmaking/domain/port/MatchRepository.kt`
   - Interface avec méthodes : `save()`, `findByPlayer()`, `delete()`, `deleteExpiredMatches()`

6. `matchmaking/domain/port/GameCreator.kt`
   - Interface avec méthode : `createGame(whitePlayerId, blackPlayerId): Result<GameId>`

### Tests à créer :
- `MatchTest.kt` : Tester `isExpired()` avec différents TTL

### Critère de succès :
✅ Tous les modèles domain compilent sans erreurs
✅ Tests unitaires des modèles passent
✅ Aucune dépendance externe (pure Kotlin + Shared Kernel)

---

## Phase 2 : Infrastructure - Repositories (Driven Adapters) 🗄️

**Objectif** : Implémenter les adapters de stockage en mémoire

### Fichiers à créer :
1. `matchmaking/infrastructure/adapter/driven/InMemoryMatchmakingQueue.kt`
   - Implémente `MatchmakingQueue`
   - Utilise `ConcurrentLinkedQueue` + `ConcurrentHashMap`
   - **ReentrantLock** pour protéger les sections critiques
   - Logique FIFO thread-safe

2. `matchmaking/infrastructure/adapter/driven/InMemoryMatchRepository.kt`
   - Implémente `MatchRepository`
   - `ConcurrentHashMap<PlayerId, Match>` (2 entrées par match)
   - TTL configurable (default: 5 minutes)
   - Méthode `deleteExpiredMatches()`

### Tests à créer :
- `InMemoryMatchmakingQueueTest.kt` :
  - Ajouter/retirer joueurs (FIFO)
  - `findMatch()` retourne les 2 premiers joueurs
  - Unicité des joueurs en queue
  - **Tests de concurrence** (multiples coroutines ajoutent simultanément)

- `InMemoryMatchRepositoryTest.kt` :
  - Sauvegarder/récupérer match par joueur
  - Les deux joueurs peuvent récupérer le même match
  - Supprimer match expiré
  - Supprimer uniquement les matches expirés (pas les valides)

### Critère de succès :
✅ Repositories fonctionnent correctement (tests unitaires passent)
✅ Thread-safety validée par tests concurrents
✅ Comportement FIFO confirmé

---

## Phase 3 : ACL - User Context (Réutilisation) 🔄

**Objectif** : Vérifier que l'ACL existant pour User est disponible

### Actions :
1. Vérifier que `UserContextPlayerChecker` existe dans :
   - `chess/infrastructure/adapter/driven/UserContextPlayerChecker.kt`

2. Si besoin, vérifier son wiring dans `KoinModule.kt`

### Critère de succès :
✅ `PlayerExistenceChecker` disponible et fonctionnel
✅ Pas de modifications nécessaires (déjà implémenté pour Chess)

---

## Phase 4 : ACL - Chess Context (Nouveau) ♟️

**Objectif** : Créer l'ACL pour appeler CreateGameUseCase depuis Matchmaking

### Fichiers à créer :
1. `matchmaking/infrastructure/adapter/driven/ChessContextGameCreator.kt`
   - Implémente `GameCreator` (port domain)
   - Injecte `CreateGameUseCase` (Chess context)
   - Convertit `Result<Game>` en `Result<GameId>`

### Tests à créer :
- `ChessContextGameCreatorTest.kt` :
  - Création réussie → retourne `GameId`
  - `CreateGameUseCase` échoue → propage l'erreur
  - Mock de `CreateGameUseCase`

### Critère de succès :
✅ ACL compile et tests passent
✅ Isolation maintenue (Matchmaking ne connaît pas Game)

---

## Phase 5 : Application Layer - Use Cases 🎯

**Objectif** : Implémenter la logique métier (orchestration)

### Ordre de création (par dépendances) :

#### 5.1 - CreateGameFromMatchUseCase
- **Dépendances** : `GameCreator`, `Random`
- **Logique** : Attribution aléatoire des couleurs (50/50) + appel ACL
- **Tests** : Mock Random avec seed fixe, vérifier distribution des couleurs

#### 5.2 - CleanupExpiredMatchesUseCase
- **Dépendances** : `MatchRepository`
- **Logique** : Appelle `deleteExpiredMatches()`
- **Tests** : Vérifier suppression des expirés uniquement

#### 5.3 - LeaveMatchmakingUseCase
- **Dépendances** : `MatchmakingQueue`
- **Logique** : Retirer joueur de la queue
- **Tests** : Succès si en queue, échec sinon

#### 5.4 - GetMatchStatusUseCase
- **Dépendances** : `MatchRepository`, `MatchmakingQueue`
- **Logique** : Cleanup puis recherche match ou position en queue
- **Tests** : Match trouvé, joueur en attente, joueur nulle part

#### 5.5 - JoinMatchmakingUseCase ⭐ (Le plus complexe)
- **Dépendances** : `MatchmakingQueue`, `MatchRepository`, `PlayerExistenceChecker`, `CreateGameFromMatchUseCase`
- **Logique** :
  1. Valider joueur existe (ACL User)
  2. Valider pas déjà en queue
  3. Valider pas déjà matché
  4. Ajouter à la queue (lock)
  5. Tenter `findMatch()`
  6. Si match → appeler `CreateGameFromMatchUseCase` automatiquement
  7. Sauvegarder Match dans repository
- **Tests** :
  - Joueur rejoint → WAITING
  - Match trouvé → MATCHED avec gameId
  - Joueur déjà en queue → erreur 409
  - Joueur déjà matché → erreur 409
  - Joueur inexistant → erreur

### Fichiers à créer :
1. `matchmaking/application/usecase/CreateGameFromMatchUseCase.kt`
2. `matchmaking/application/usecase/CleanupExpiredMatchesUseCase.kt`
3. `matchmaking/application/usecase/LeaveMatchmakingUseCase.kt`
4. `matchmaking/application/usecase/GetMatchStatusUseCase.kt`
5. `matchmaking/application/usecase/JoinMatchmakingUseCase.kt`

### Tests à créer :
- 1 fichier de test par use case (5 fichiers)

### Critère de succès :
✅ Tous les use cases testés et fonctionnels
✅ Logique métier correcte (validations, atomicité)
✅ Gestion d'erreurs complète

---

## Phase 6 : Infrastructure - Routes & DTOs (Driver Adapters) 🌐

**Objectif** : Exposer les use cases via API REST

### Fichiers à créer :
1. `matchmaking/infrastructure/adapter/driver/dto/MatchmakingStatusDTO.kt`
   - Data class sérialisable avec `@Serializable`
   - Champs : `status`, `queuePosition?`, `gameId?`, `yourColor?`

2. `matchmaking/infrastructure/adapter/driver/MatchmakingRoutes.kt`
   - Extension function : `Route.matchmakingRoutes()`
   - 3 endpoints :
     - `POST /api/matchmaking/queue` (JWT requis)
     - `DELETE /api/matchmaking/queue` (JWT requis)
     - `GET /api/matchmaking/status` (JWT requis)
   - Extraction `playerId` depuis JWT
   - Conversion domain models → DTOs

### Tests à créer :
- Pas de tests unitaires (les routes seront testées en E2E Phase 7)

### Critère de succès :
✅ Routes compilent sans erreur
✅ DTOs sérialisables correctement
✅ JWT authentication configurée

---

## Phase 7 : Configuration & Wiring 🔌

**Objectif** : Connecter tous les composants via Koin et enregistrer les routes

### Fichiers à modifier :

1. `infrastructure/config/KoinModule.kt`
   - Ajouter une section `// Matchmaking Context`
   - Wiring :
     ```kotlin
     // Repositories
     single<MatchmakingQueue> { InMemoryMatchmakingQueue() }
     single<MatchRepository> { InMemoryMatchRepository(ttlMinutes = 5) }

     // ACL
     single<GameCreator> { ChessContextGameCreator(get()) }

     // Use Cases
     single { CreateGameFromMatchUseCase(get()) }
     single { CleanupExpiredMatchesUseCase(get()) }
     single { LeaveMatchmakingUseCase(get()) }
     single { GetMatchStatusUseCase(get(), get()) }
     single { JoinMatchmakingUseCase(get(), get(), get(), get()) }
     ```

2. `Application.kt`
   - Importer `matchmakingRoutes`
   - Ajouter dans le bloc `routing { }` :
     ```kotlin
     matchmakingRoutes(
         joinUseCase = get(),
         leaveUseCase = get(),
         getStatusUseCase = get()
     )
     ```

### Tests à créer :
- Vérifier que l'application démarre sans erreur : `./gradlew run` (test manuel)

### Critère de succès :
✅ Application démarre sans erreur Koin
✅ Routes matchmaking disponibles
✅ Dépendances injectées correctement

---

## Phase 8 : Tests d'intégration E2E 🧪

**Objectif** : Valider le flux complet de bout en bout

### Fichiers à créer :
1. `integrationTest/kotlin/com/gchess/matchmaking/MatchmakingE2ETest.kt`
   - Test : Deux joueurs s'inscrivent, se matchent, partie créée
   - Flux :
     ```
     1. Register Player1
     2. Register Player2
     3. Login Player1 → JWT1
     4. Login Player2 → JWT2
     5. Player1 POST /queue → WAITING
     6. Player1 GET /status → WAITING
     7. Player2 POST /queue → MATCHED (avec gameId)
     8. Player1 GET /status → MATCHED (même gameId)
     9. Player1 GET /games/{gameId} → Partie existe
     10. Player2 GET /games/{gameId} → Partie existe
     ```

   - Test : Joueur déjà en queue → erreur 409
   - Test : Joueur déjà matché → erreur 409
   - Test : Joueur quitte la queue
   - Test : Match expire après TTL

### Critère de succès :
✅ Flux nominal fonctionne de bout en bout
✅ Cas limites gérés correctement
✅ JWT authentication fonctionne
✅ Game créée automatiquement lors du match

---

## Phase 9 : Architecture Tests 🏛️

**Objectif** : Valider que les règles d'architecture sont respectées

### Fichiers à créer/modifier :
1. `architectureTest/kotlin/com/gchess/BoundedContextTest.kt`
   - Ajouter tests pour Matchmaking context :
     - Domain Matchmaking ne dépend pas de User/Chess
     - Application Matchmaking ne dépend pas de User/Chess
     - Infrastructure peut appeler User/Chess (ACL)

### Tests à exécuter :
```bash
./gradlew architectureTest
```

### Critère de succès :
✅ Tous les tests d'architecture passent
✅ Isolation des contextes maintenue
✅ ACL correctement placé dans infrastructure

---

## Phase 10 : Documentation & Polish 📝

**Objectif** : Finaliser la feature avec documentation

### Actions :
1. Mettre à jour `CLAUDE.md` avec :
   - Description du Matchmaking context
   - Nouveaux endpoints API
   - Flux utilisateur

2. Mettre à jour `CONTEXT_MAP.md` avec :
   - Ajout du Matchmaking context
   - Relations ACL (Matchmaking → User, Matchmaking → Chess)

3. Créer/mettre à jour OpenAPI spec si existant

4. Test manuel complet :
   ```bash
   # Démarrer l'application
   ./gradlew run

   # Tester le flux avec curl ou Postman
   ```

### Critère de succès :
✅ Documentation à jour
✅ Tous les tests passent (`./gradlew check`)
✅ Feature utilisable manuellement

---

## Résumé : Ordre d'exécution

```
Phase 1: Domain Models & Ports                    [~30 min]
   ↓
Phase 2: Repositories (InMemory)                  [~1h]
   ↓
Phase 3: ACL User (vérification)                  [~5 min]
   ↓
Phase 4: ACL Chess (nouveau)                      [~30 min]
   ↓
Phase 5: Use Cases (5 fichiers)                   [~2h]
   ↓
Phase 6: Routes & DTOs                            [~45 min]
   ↓
Phase 7: Koin Wiring                              [~15 min]
   ↓
Phase 8: Tests E2E                                [~1h30]
   ↓
Phase 9: Architecture Tests                       [~20 min]
   ↓
Phase 10: Documentation                           [~30 min]
```

**Durée totale estimée** : ~7-8 heures

---

## Stratégie TDD recommandée

Pour chaque phase :
1. ✍️ Écrire les tests en premier (Red)
2. ✅ Implémenter le code minimal (Green)
3. ♻️ Refactorer si nécessaire (Refactor)
4. 🚀 Passer à la phase suivante

**Exception** : Phases 6-7 (Routes/Wiring) testées directement en E2E (Phase 8)

---

## Notes importantes

⚠️ **Ne pas sauter de phases** : Chaque phase dépend de la précédente

⚠️ **Tester au fur et à mesure** : Ne pas accumuler du code non testé

⚠️ **Commits fréquents** : Commit après chaque phase validée

✅ **Prêt à démarrer** : On peut commencer Phase 1 quand tu veux !
