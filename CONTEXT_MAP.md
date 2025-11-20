# Context Map - gChess

This document describes the relationships between bounded contexts in the gChess application, following Domain-Driven Design principles.

## Bounded Contexts

### Chess Context
**Purpose**: Manages chess games, rules, and gameplay

**Ubiquitous Language**:
- Game, ChessPosition, Move, PlayerSide
- ChessRules, CastlingRights, Piece, Position
- GameStatus (IN_PROGRESS, CHECK, CHECKMATE, STALEMATE, DRAW)

**Responsibilities**:
- Validate chess moves according to FIDE rules
- Manage game state and move history
- Calculate legal moves using bitboard architecture
- Handle castling, en passant, pawn promotion
- Detect check situations

**Public API**:
- `POST /api/games` - Create new game (authenticated)
- `GET /api/games/{id}` - Get game state (public)
- `POST /api/games/{id}/moves` - Make a move (authenticated)

### User Context
**Purpose**: Manages user accounts, authentication, and security

**Ubiquitous Language**:
- User, Credentials, PasswordHasher
- Authentication, Registration, Login

**Responsibilities**:
- User registration with validation
- Password hashing with BCrypt
- User authentication
- JWT token generation and validation
- User profile management

**Public API**:
- `POST /api/auth/register` - Register new user (public)
- `POST /api/auth/login` - Authenticate user (public)
- `GET /api/users/{id}` - Get user profile (public)

### Matchmaking Context
**Purpose**: Pairs players together for chess games automatically

**Ubiquitous Language**:
- QueueEntry, Match, MatchmakingQueue
- MatchmakingStatus (WAITING, MATCHED), MatchmakingResult
- GameCreator (ACL port), TTL (Time To Live)

**Responsibilities**:
- Manage FIFO matchmaking queue
- Pair waiting players automatically
- Assign colors randomly (50/50 WHITE/BLACK)
- Create chess games automatically upon match
- Track match TTL (default: 5 minutes expiration)
- Validate player existence before queueing
- Cleanup expired matches

**Public API**:
- `POST /api/matchmaking/queue` - Join matchmaking queue (authenticated)
- `DELETE /api/matchmaking/queue` - Leave matchmaking queue (authenticated)
- `GET /api/matchmaking/status` - Get matchmaking status (authenticated)

### Shared Kernel
**Purpose**: Common value objects shared across all contexts

**Ubiquitous Language**:
- PlayerId, GameId

**Contents**:
- `PlayerId`: Value object wrapping ULID for player identification
- `GameId`: Value object wrapping ULID for game identification

**Why ULID?**
- Lexicographically sortable (time-ordered)
- URL-safe (no special characters)
- 128-bit uniqueness (like UUID)
- 26-character string representation

## Context Relationships

### Chess Context → User Context (Anti-Corruption Layer)

**Relationship Type**: Customer-Supplier with Anti-Corruption Layer

**Direction**: Chess context depends on User context (one-way)

**Integration Pattern**: Anti-Corruption Layer (ACL)

```
┌──────────────────────────────┐
│     Chess Context            │
│                              │
│  ┌────────────────────────┐  │
│  │  Domain                │  │
│  │  - PlayerExistence     │  │     Port defined in Chess domain
│  │    Checker (port)      │  │
│  └───────────┬────────────┘  │
│              │               │
│  ┌───────────┴────────────┐  │
│  │  Infrastructure        │  │
│  │  - UserContextPlayer   │  │     ACL Adapter in Chess infrastructure
│  │    Checker (adapter)   │──┼──┐
│  └────────────────────────┘  │  │
└──────────────────────────────┘  │
                                  │
                ┌─────────────────┘
                │
                ↓
┌──────────────────────────────┐
│     User Context             │
│                              │
│  ┌────────────────────────┐  │
│  │  Application           │  │
│  │  - GetUserUseCase      │  │     Called by Chess ACL
│  └────────────────────────┘  │
│                              │
└──────────────────────────────┘
```

**ACL Components**:

1. **Port (Chess Domain)**:
   - `PlayerExistenceChecker` interface
   - Defines what Chess context needs from the outside world
   - Domain remains isolated from User context

2. **Adapter (Chess Infrastructure)**:
   - `UserContextPlayerChecker` implementation
   - Translates Chess domain concepts to User context calls
   - Calls `GetUserUseCase` from User context
   - Fail-fast error handling strategy

**Validation Flow**:
```
1. CreateGameUseCase receives player IDs
2. Calls PlayerExistenceChecker.exists(playerId)
3. UserContextPlayerChecker (ACL) translates call
4. Invokes GetUserUseCase.execute(playerId)
5. Returns boolean result to Chess context
6. Chess context continues or fails based on result
```

**Error Handling Strategy**: Fail-Fast
- If User context is unavailable → Error propagated
- If player doesn't exist → Error returned
- No fallback or caching (consistency over availability)

### Matchmaking Context → Chess Context (Anti-Corruption Layer)

**Relationship Type**: Customer-Supplier with Anti-Corruption Layer

**Direction**: Matchmaking context depends on Chess context (one-way)

**Integration Pattern**: Anti-Corruption Layer (ACL)

```
┌──────────────────────────────┐
│  Matchmaking Context         │
│                              │
│  ┌────────────────────────┐  │
│  │  Domain                │  │
│  │  - GameCreator (port)  │  │     Port defined in Matchmaking domain
│  └───────────┬────────────┘  │
│              │               │
│  ┌───────────┴────────────┐  │
│  │  Infrastructure        │  │
│  │  - ChessContextGame    │  │     ACL Adapter in Matchmaking infrastructure
│  │    Creator (adapter)   │──┼──┐
│  └────────────────────────┘  │  │
└──────────────────────────────┘  │
                                  │
                ┌─────────────────┘
                │
                ↓
┌──────────────────────────────┐
│     Chess Context            │
│                              │
│  ┌────────────────────────┐  │
│  │  Application           │  │
│  │  - CreateGameUseCase   │  │     Called by Matchmaking ACL
│  └────────────────────────┘  │
│                              │
└──────────────────────────────┘
```

**ACL Components**:

1. **Port (Matchmaking Domain)**:
   - `GameCreator` interface
   - Defines what Matchmaking needs to create games
   - Domain remains isolated from Chess context

2. **Adapter (Matchmaking Infrastructure)**:
   - `ChessContextGameCreator` implementation
   - Translates Matchmaking domain calls to Chess context calls
   - Calls `CreateGameUseCase` from Chess context
   - Extracts `GameId` from created game

**Game Creation Flow**:
```
1. JoinMatchmakingUseCase finds a match
2. CreateGameFromMatchUseCase assigns colors randomly
3. Calls GameCreator.createGame(whitePlayerId, blackPlayerId)
4. ChessContextGameCreator (ACL) translates call
5. Invokes CreateGameUseCase.execute(white, black)
6. Returns GameId to Matchmaking context
7. Matchmaking saves Match with gameId
```

### Matchmaking Context → User Context (Port Reuse)

**Relationship Type**: Shared Port

**Direction**: Matchmaking reuses Chess's PlayerExistenceChecker port

**Integration Pattern**: Port Reuse via Dependency Injection

```
┌──────────────────────────────┐
│  Matchmaking Context         │
│                              │
│  ┌────────────────────────┐  │
│  │  Application           │  │
│  │  - Uses PlayerExist    │──┼──┐  Reuses port from Chess
│  │    enceChecker         │  │  │
│  └────────────────────────┘  │  │
└──────────────────────────────┘  │
                                  │
        ┌─────────────────────────┘
        │
        ↓
┌──────────────────────────────┐
│     Chess Context            │
│                              │
│  ┌────────────────────────┐  │
│  │  Domain Port           │  │
│  │  - PlayerExistence     │  │     Port defined in Chess
│  │    Checker             │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │  Infrastructure        │  │
│  │  - UserContextPlayer   │──┼──┐  Shared adapter (via DI)
│  │    Checker (ACL)       │  │  │
│  └────────────────────────┘  │  │
└──────────────────────────────┘  │
                                  │
                ┌─────────────────┘
                │
                ↓
┌──────────────────────────────┐
│     User Context             │
│                              │
│  ┌────────────────────────┐  │
│  │  Application           │  │
│  │  - GetUserUseCase      │  │     Called by shared ACL
│  └────────────────────────┘  │
└──────────────────────────────┘
```

**Port Reuse Rationale**:
- Both Chess and Matchmaking need to validate player existence
- Same contract (`PlayerExistenceChecker` interface)
- Avoids port duplication
- Maintains context isolation (depends on interface, not implementation)
- Acceptable trade-off for consistency

**Dependency Injection**:
- Koin provides same `UserContextPlayerChecker` instance
- Both contexts depend on the interface (port)
- Neither knows about the User context directly

### Shared Kernel Relationships

All three contexts depend on the Shared Kernel:

```
              ┌────────────────────────────────────────┐
              │         Shared Kernel                  │
              │   - PlayerId (ULID value object)       │
              │   - GameId (ULID value object)         │
              └────────────────────────────────────────┘
                    ↑            ↑            ↑
                    │            │            │
       ┌────────────┘            │            └────────────┐
       │                         │                         │
┌──────┴───────┐      ┌──────────┴───────┐      ┌─────────┴─────────┐
│Chess Context │      │Matchmaking       │      │   User Context    │
│              │      │Context           │      │                   │
│Uses:         │      │                  │      │  Uses:            │
│- GameId      │      │Uses:             │      │  - PlayerId       │
│- PlayerId    │      │- PlayerId        │      │                   │
│              │      │- GameId          │      │                   │
└──────────────┘      └──────────────────┘      └───────────────────┘
```

**Shared Kernel Rules**:
- Contains only value objects (no business logic)
- No external dependencies (framework-agnostic)
- Immutable by design
- Changes require coordination between all contexts

## Context Integration Patterns Summary

| From Context | To Context | Pattern | Direction | Why |
|--------------|------------|---------|-----------|-----|
| Chess | User | Anti-Corruption Layer | One-way | Chess needs to verify player existence without tight coupling |
| Matchmaking | Chess | Anti-Corruption Layer | One-way | Matchmaking needs to create games without tight coupling |
| Matchmaking | User | Port Reuse (via Chess) | Indirect | Reuses PlayerExistenceChecker port for player validation |
| Chess | Shared Kernel | Shared Kernel | Depends on | Common identifier types (GameId, PlayerId) |
| User | Shared Kernel | Shared Kernel | Depends on | Common identifier types (PlayerId) |
| Matchmaking | Shared Kernel | Shared Kernel | Depends on | Common identifier types (GameId, PlayerId) |

## Design Decisions

### Why Anti-Corruption Layer?

**Problem**: Chess context needs to validate that players exist before creating a game, but direct dependency on User context would create tight coupling.

**Solution**: Anti-Corruption Layer pattern
- Chess domain defines `PlayerExistenceChecker` port (what it needs)
- Infrastructure provides `UserContextPlayerChecker` adapter (how to get it)
- User context remains unaware of Chess context (one-way dependency)

**Benefits**:
- ✅ Contexts remain independently deployable
- ✅ User context can change without affecting Chess domain
- ✅ Clear boundary between contexts
- ✅ Easy to replace User context with external service later
- ✅ Testable (can mock PlayerExistenceChecker in Chess tests)

### Why Shared Kernel for IDs?

**Problem**: Both contexts need to identify players and games with the same type.

**Solution**: Shared Kernel with value objects
- Single source of truth for PlayerId and GameId
- Both contexts use the same ULID format
- Type safety across context boundaries

**Benefits**:
- ✅ Type safety (compiler prevents using GameId where PlayerId expected)
- ✅ Consistent identifier generation
- ✅ No translation needed at context boundaries
- ✅ Minimal shared surface (only value objects)

**Trade-off Accepted**:
- ⚠️ Changes to Shared Kernel affect both contexts
- ⚠️ Requires coordination for breaking changes
- ✅ Worth it for the type safety and consistency benefits

### Why Fail-Fast in ACL?

**Decision**: UserContextPlayerChecker propagates errors immediately without fallback.

**Rationale**:
- Data consistency is more important than availability
- Creating a game with non-existent players would violate invariants
- Simpler error handling (no stale data or cache invalidation)
- Clear failure modes for debugging

**Alternative Considered**: Optimistic caching
- Could cache player existence checks
- Rejected: Risk of stale data, cache invalidation complexity
- May revisit if performance becomes an issue

### Why Port Reuse in Matchmaking?

**Decision**: Matchmaking reuses `PlayerExistenceChecker` port from Chess context instead of defining its own.

**Rationale**:
- Same validation need: check if player exists
- Same contract: boolean result for playerId input
- Avoids port duplication (DRY principle)
- Maintains isolation: depends on interface, not implementation
- Simplifies dependency injection (single ACL adapter)

**Trade-offs Accepted**:
- ⚠️ Coupling to Chess domain port (interface dependency)
- ⚠️ If Chess changes port signature, Matchmaking affected
- ✅ Worth it: Unlikely to change, benefits outweigh risks
- ✅ Alternative: Could duplicate port in Matchmaking domain

**Why Not Separate Port?**:
- Would require duplicate port interface
- Would require duplicate ACL adapter (or adapter wrapping)
- Same User context integration regardless
- Added complexity for minimal isolation gain

### Why Random Color Assignment?

**Decision**: Matchmaking assigns WHITE/BLACK randomly (50/50) when creating matches.

**Rationale**:
- Simple and fair for MVP
- No ELO or skill-based matching yet
- Prevents first-mover advantage exploitation
- Easy to implement and test

**Future Enhancement**:
- Could use ELO ratings for fairness (higher ELO gets WHITE more often)
- Could use player preferences
- Could use tournament rules (alternating colors)

## Future Evolution

### Potential Context Extraction

**Game History Context** (future):
- Extract move history, game archives, PGN export
- Subscribe to Chess context events
- Read-only view of completed games
- Game replay functionality

**Tournament Context** (future):
- Organize tournaments (Swiss, Round-Robin, Knockout)
- Bracket management
- Prize pools and rankings
- Would coordinate with Matchmaking and Chess contexts

**Notification Context** (future):
- Real-time notifications (WebSocket)
- Email notifications for game invites
- Push notifications for mobile
- Event sourcing for notification history

**Rating Context** (future):
- ELO rating calculations
- Player statistics and analytics
- Leaderboards and rankings
- Could enhance Matchmaking with skill-based pairing

### Microservices Migration Path

Current: Monolithic modular architecture
- Bounded contexts in separate packages
- Shared Kernel for common types
- ACL pattern already in place

Future: Each context → separate microservice
- Replace ACL adapter with REST/gRPC client
- Move Shared Kernel to shared library
- Replace in-process calls with network calls
- No changes to domain layer required! 🎉

## References

- **Domain-Driven Design** by Eric Evans
- **Implementing Domain-Driven Design** by Vaughn Vernon
- **Context Mapping** pattern from DDD
- **Anti-Corruption Layer** pattern from DDD
