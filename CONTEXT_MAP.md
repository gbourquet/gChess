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

### Shared Kernel Relationships

Both Chess and User contexts depend on the Shared Kernel:

```
┌────────────────────────────────────────┐
│         Shared Kernel                  │
│   - PlayerId (ULID value object)       │
│   - GameId (ULID value object)         │
└────────────────────────────────────────┘
         ↑                        ↑
         │                        │
┌────────┴────────┐    ┌──────────┴───────┐
│  Chess Context  │    │   User Context   │
│                 │    │                  │
│  Uses:          │    │  Uses:           │
│  - GameId       │    │  - PlayerId      │
│  - PlayerId     │    │                  │
└─────────────────┘    └──────────────────┘
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
| Chess | Shared Kernel | Shared Kernel | Depends on | Common identifier types |
| User | Shared Kernel | Shared Kernel | Depends on | Common identifier types |

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

## Future Evolution

### Potential Context Extraction

**Game History Context** (future):
- Could extract move history, game archives, PGN export
- Would subscribe to Chess context events
- Read-only view of completed games

**Matchmaking Context** (future):
- Pair players for games
- ELO ratings, rankings
- Would coordinate between User and Chess contexts

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
