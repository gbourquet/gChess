# Plan d'implémentation : Système de Matchmaking Simple (V1 MVP)

## Objectifs
- File d'attente **First-Come First-Served**
- Attribution **aléatoire** des couleurs (50/50)
- **Pas de système de rating** (ajout ultérieur possible)
- Stockage **en mémoire** (InMemory)

---

## 1. Créer un nouveau Bounded Context : **Matchmaking**

### 1.1 Domain Layer (`com.gchess.matchmaking.domain`)

**Nouveaux modèles** :
- `QueueEntry` (Value Object) : Représente un joueur en attente
  - `playerId: PlayerId`
  - `joinedAt: Instant` (timestamp de l'entrée en queue)

- `Match` (Entity) : Représente un appariement réussi
  - `whitePlayerId: PlayerId`
  - `blackPlayerId: PlayerId`
  - `gameId: GameId` (ID de la partie créée automatiquement)
  - `matchedAt: Instant`
  - `expiresAt: Instant` (TTL: matchedAt + 5 minutes par défaut)
  - `isExpired(): Boolean` (vérifie si Instant.now() > expiresAt)

**Ports** :
- `MatchmakingQueue` (interface) : Opérations sur la queue
  - `addPlayer(playerId: PlayerId): QueueEntry`
  - `removePlayer(playerId: PlayerId): Boolean`
  - `findMatch(): Pair<QueueEntry, QueueEntry>?` (retourne null si < 2 joueurs, retire les joueurs de la queue)
  - `isPlayerInQueue(playerId: PlayerId): Boolean`
  - `getQueueSize(): Int`

- `MatchRepository` (interface) : Gestion des matches créés
  - `save(match: Match)`
  - `findByPlayer(playerId: PlayerId): Match?`
  - `delete(playerId: PlayerId)`
  - `deleteExpiredMatches()` (supprime matches > TTL)

### 1.2 Application Layer (`com.gchess.matchmaking.application.usecase`)

**Use Cases** :
- `JoinMatchmakingUseCase`
  - Valide que le joueur existe (via ACL → User context)
  - Valide que le joueur n'est pas déjà en queue
  - Valide que le joueur n'a pas déjà un match actif (via MatchRepository)
  - **Utilise un lock** pour éviter les race conditions
  - Ajoute à la queue
  - Tente de trouver un match immédiatement
  - **Si match trouvé** :
    - Appelle `CreateGameFromMatchUseCase` automatiquement
    - Les deux joueurs sont retirés de la queue
    - Le Match est sauvegardé dans MatchRepository
  - Retourne : `Result<MatchmakingStatus>` (WAITING ou MATCHED avec gameId)

- `LeaveMatchmakingUseCase`
  - **Utilise un lock** pour éviter les race conditions
  - Retire le joueur de la queue
  - Retourne succès/échec (si joueur pas en queue)

- `GetMatchStatusUseCase` (renommé depuis CheckMatchStatusUseCase)
  - Vérifie si un joueur a été matché (via MatchRepository)
  - Supprime les matches expirés avant la recherche
  - Retourne le Match si trouvé (avec gameId et couleur assignée)
  - Retourne null si joueur en attente ou si match expiré

- `CreateGameFromMatchUseCase`
  - **Usage interne** : appelé automatiquement par JoinMatchmakingUseCase
  - Attribue aléatoirement les couleurs (50/50)
  - Appelle `CreateGameUseCase` du Chess context (via ACL)
  - Crée un objet `Match` avec le gameId généré
  - Retourne `Result<Match>`

- `CleanupExpiredMatchesUseCase` (nouveau)
  - Supprime les matches expirés (> TTL)
  - Appelé périodiquement (scheduler ou endpoint admin)

### 1.3 Infrastructure Layer (`com.gchess.matchmaking.infrastructure`)

**Adapters (Driven)** :
- `InMemoryMatchmakingQueue` : Implémentation en mémoire
  - `ConcurrentLinkedQueue<QueueEntry>` (FIFO thread-safe)
  - `ConcurrentHashMap<PlayerId, QueueEntry>` (index pour recherches rapides)
  - **Mutex/Lock** : `ReentrantLock` pour protéger les opérations critiques
  - Logique FIFO (premier entré = premier servi)
  - Atomicité des opérations `addPlayer()` et `findMatch()`

- `InMemoryMatchRepository` (nouveau) : Stockage des matches
  - `ConcurrentHashMap<PlayerId, Match>` (index par joueur : 2 entrées par match)
  - TTL par défaut : 5 minutes (configurable)
  - Méthode `deleteExpiredMatches()` pour cleanup

**Adapters (Driver)** :
- `MatchmakingRoutes` : Endpoints REST
  - `POST /api/matchmaking/queue` - Rejoindre la queue (JWT requis)
    - Retourne immédiatement : `{ "status": "WAITING" }` ou `{ "status": "MATCHED", "gameId": "...", "yourColor": "WHITE" }`
  - `DELETE /api/matchmaking/queue` - Quitter la queue (JWT requis)
  - `GET /api/matchmaking/status` - Vérifier son statut (JWT requis)
    - Retourne : `{ "status": "WAITING", "queuePosition": 1 }` ou `{ "status": "MATCHED", "gameId": "...", "yourColor": "BLACK" }`
    - Nettoie les matches expirés avant de répondre

**DTOs** :
- `MatchmakingStatusDTO` :
  - `status: MatchmakingStatus` (enum: WAITING, MATCHED)
  - `queuePosition: Int?` (si WAITING)
  - `gameId: String?` (si MATCHED)
  - `yourColor: String?` (si MATCHED : "WHITE" ou "BLACK")

**ACL Adapters** :
- `UserContextPlayerChecker` (réutiliser l'existant)
- `ChessContextGameCreator` (nouveau) : Port + Adapter
  - **Port** (domain) : `GameCreator` interface
    - `createGame(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<GameId>`
  - **Adapter** (infrastructure) : Appelle `CreateGameUseCase` du Chess context

---

## 2. Chess Context : Aucune modification nécessaire

- ✅ `CreateGameUseCase` reste inchangé
- ✅ Matchmaking utilise `CreateGameUseCase` via ACL (`ChessContextGameCreator`)
- ✅ Isolation des contextes préservée

---

## 3. Flux Utilisateur

### Scénario nominal :

1. **Player 1 rejoint** :
   ```
   POST /api/matchmaking/queue (JWT Player1)
   → Lock acquis
   → Player 1 validé (existe, pas déjà en queue/match)
   → Player 1 ajouté à la queue
   → findMatch() → null (< 2 joueurs)
   → Lock relâché
   → Réponse : { "status": "WAITING", "queuePosition": 1 }
   ```

2. **Player 1 vérifie** (polling) :
   ```
   GET /api/matchmaking/status (JWT Player1)
   → Cleanup des matches expirés
   → Recherche dans MatchRepository : null
   → Recherche dans Queue : trouvé
   → Réponse : { "status": "WAITING", "queuePosition": 1 }
   ```

3. **Player 2 rejoint** :
   ```
   POST /api/matchmaking/queue (JWT Player2)
   → Lock acquis
   → Player 2 validé
   → Player 2 ajouté à la queue
   → findMatch() → (Player1, Player2) ✅
   → Player 1 et Player 2 retirés de la queue
   → CreateGameFromMatchUseCase :
       ├─ Attribution aléatoire : Player1=WHITE, Player2=BLACK
       ├─ Appel CreateGameUseCase (via ACL)
       └─ gameId créé : "01HQZN..."
   → Match créé : { whitePlayerId, blackPlayerId, gameId, matchedAt, expiresAt }
   → Match sauvegardé dans MatchRepository (2 entrées : 1 par joueur)
   → Lock relâché
   → Réponse : { "status": "MATCHED", "gameId": "01HQZN...", "yourColor": "BLACK" }
   ```

4. **Player 1 vérifie à nouveau** :
   ```
   GET /api/matchmaking/status (JWT Player1)
   → Cleanup des matches expirés
   → Recherche dans MatchRepository : Match trouvé
   → Réponse : { "status": "MATCHED", "gameId": "01HQZN...", "yourColor": "WHITE" }
   ```

5. **Les deux joueurs rejoignent la partie** :
   ```
   GET /api/games/01HQZN...
   → Partie démarre
   ```

6. **Après 5 minutes (TTL)** :
   ```
   GET /api/matchmaking/status (JWT Player1)
   → Cleanup : Match expiré, supprimé
   → Réponse : { "status": "WAITING", "queuePosition": 0 } (joueur n'est plus en queue)
   ```

### Gestion des cas limites :
- **Joueur quitte avant match** : `DELETE /api/matchmaking/queue` → Succès
- **Joueur déjà en queue** : Erreur 409 "Already in queue"
- **Joueur déjà matché** : Erreur 409 "Already matched" (vérification via MatchRepository)
- **Match expiré** : Supprimé automatiquement lors de `GET /status` ou cleanup périodique
- **Race condition (2 joueurs rejoignent simultanément)** :
  - Lock empêche l'ajout concurrent
  - Un seul thread peut exécuter `addPlayer()` + `findMatch()` à la fois
- **Player 1 quitte pendant que Player 2 rejoint** :
  - Lock garantit l'atomicité : soit Player 1 est retiré avant le match, soit après
  - Si retiré avant : Player 2 reste en queue
  - Si retiré après : Match déjà créé, pas d'impact

---

## 4. Modifications dans build.gradle.kts

**Nouveaux source sets** (optionnel, pour cohérence) :
- Pas nécessaire si on ajoute dans `src/main/kotlin/com/gchess/matchmaking/`

---

## 5. Tests (TDD)

### Tests unitaires (`src/unitTest/`) :

**Domain** :
- `MatchTest` :
  - `isExpired()` retourne true si expiré
  - `isExpired()` retourne false si dans le TTL

**Repositories** :
- `InMemoryMatchmakingQueueTest` :
  - Ajouter/retirer joueurs
  - Trouver un match FIFO
  - Vérifier unicité des joueurs en queue
  - Race conditions (tests concurrents avec coroutines)

- `InMemoryMatchRepositoryTest` :
  - Sauvegarder/récupérer match par joueur
  - Supprimer match expiré
  - Les deux joueurs peuvent récupérer le même match

**Use Cases** :
- `JoinMatchmakingUseCaseTest` :
  - Joueur rejoint avec succès → WAITING
  - Match trouvé immédiatement → MATCHED avec gameId
  - Joueur déjà en queue → erreur 409
  - Joueur déjà matché → erreur 409
  - Joueur inexistant → erreur

- `CreateGameFromMatchUseCaseTest` :
  - Attribution aléatoire des couleurs (mock Random avec seed fixe)
  - Création réussie de la partie
  - GameCreator retourne échec → propagation erreur

- `CleanupExpiredMatchesUseCaseTest` :
  - Supprime uniquement les matches expirés
  - Conserve les matches valides

### Tests d'intégration (`src/integrationTest/`) :
- `MatchmakingE2ETest` : Flux complet
  - Deux joueurs s'inscrivent → Login → Matchmaking → Partie créée

---

## 6. Architecture : Diagramme de contextes

```
┌─────────────────────────────────────────────────────────┐
│                    Shared Kernel                        │
│              (PlayerId, GameId - ULID)                  │
└─────────────────────────────────────────────────────────┘
    ↑              ↑                            ↑
    │              │                            │
┌───┴────┐    ┌───┴──────────┐         ┌──────┴──────┐
│  User  │    │ Matchmaking  │         │    Chess    │
│Context │←ACL┤   Context    ├ACL→     │   Context   │
│        │    │              │         │             │
│• User  │    │• QueueEntry  │         │• Game       │
│        │    │• Match       │         │• ChessRules │
└────────┘    │• Queue Mgmt  │         │             │
              └──────────────┘         └─────────────┘
```

**ACL Interactions** :
- Matchmaking → User : Vérifier que le joueur existe
- Matchmaking → Chess : Créer une partie à partir d'un Match

---

## 7. Estimation des fichiers à créer/modifier

### Nouveaux fichiers (≈16) :

**Domain Layer** :
1. `matchmaking/domain/model/QueueEntry.kt`
2. `matchmaking/domain/model/Match.kt`
3. `matchmaking/domain/model/MatchmakingStatus.kt` (enum: WAITING, MATCHED)
4. `matchmaking/domain/port/MatchmakingQueue.kt`
5. `matchmaking/domain/port/MatchRepository.kt` ✨ (nouveau)
6. `matchmaking/domain/port/GameCreator.kt` ✨ (ACL port)

**Application Layer** :
7. `matchmaking/application/usecase/JoinMatchmakingUseCase.kt`
8. `matchmaking/application/usecase/LeaveMatchmakingUseCase.kt`
9. `matchmaking/application/usecase/GetMatchStatusUseCase.kt` (renommé)
10. `matchmaking/application/usecase/CreateGameFromMatchUseCase.kt`
11. `matchmaking/application/usecase/CleanupExpiredMatchesUseCase.kt` ✨ (nouveau)

**Infrastructure Layer** :
12. `matchmaking/infrastructure/adapter/driven/InMemoryMatchmakingQueue.kt`
13. `matchmaking/infrastructure/adapter/driven/InMemoryMatchRepository.kt` ✨ (nouveau)
14. `matchmaking/infrastructure/adapter/driven/ChessContextGameCreator.kt` (ACL adapter)
15. `matchmaking/infrastructure/adapter/driver/MatchmakingRoutes.kt`
16. `matchmaking/infrastructure/adapter/driver/dto/MatchmakingStatusDTO.kt`

**Tests** :
17. Tests unitaires (domain + use cases)
18. Tests d'intégration E2E

### Fichiers à modifier (≈2) :
1. `infrastructure/config/KoinModule.kt` : Wiring Matchmaking dependencies
   - `single<MatchmakingQueue> { InMemoryMatchmakingQueue() }`
   - `single<MatchRepository> { InMemoryMatchRepository(ttlMinutes = 5) }`
   - `single<GameCreator> { ChessContextGameCreator(get()) }`
   - `single { JoinMatchmakingUseCase(...) }`
2. `Application.kt` : Enregistrer MatchmakingRoutes

---

## 8. Détails d'implémentation : Race Conditions

### Stratégie de synchronisation

**Option choisie** : `ReentrantLock` dans `InMemoryMatchmakingQueue`

```kotlin
class InMemoryMatchmakingQueue : MatchmakingQueue {
    private val lock = ReentrantLock()
    private val queue = ConcurrentLinkedQueue<QueueEntry>()
    private val indexByPlayer = ConcurrentHashMap<PlayerId, QueueEntry>()

    override suspend fun addPlayer(playerId: PlayerId): QueueEntry = withContext(Dispatchers.IO) {
        lock.withLock {
            // Opérations atomiques ici
        }
    }
}
```

**Sections critiques** :
- `addPlayer()` + `findMatch()` : doivent être atomiques
- `removePlayer()` : doit être synchronisé

**Pourquoi pas ConcurrentHashMap seul ?**
- `findMatch()` modifie la queue (retire 2 joueurs) → besoin d'atomicité
- `addPlayer()` suivi de `findMatch()` doivent être une transaction

---

## 9. Points d'extension futurs (hors scope V1)

- Système de rating (ELO/Glicko-2)
- Règles de matchmaking avancées (éviter rematches)
- Temps de jeu (Bullet/Blitz/Rapide/Classique)
- WebSocket pour notifications temps réel (au lieu de polling)
- Persistance (PostgreSQL) pour historique
- Matchmaking par préférences
- Abandon de queue automatique après timeout

---

## Résumé : Approche MVP

✅ **Simple** : File FIFO, pas de rating

✅ **Isolation** : Nouveau bounded context Matchmaking

✅ **Testable** : TDD avec tests unitaires et intégration

✅ **Extensible** : Architecture permettant ajout de fonctionnalités

✅ **Cohérent** : Respecte hexagonal architecture + DDD

✅ **Thread-safe** : Protection contre les race conditions avec locks

✅ **Robuste** : Gestion TTL et cleanup automatique des matches expirés
