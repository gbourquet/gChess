# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

gChess is a chess application built with Kotlin following Domain-Driven Design with bounded contexts. It uses Ktor as the web framework, Koin for dependency injection, JWT for authentication, and implements hexagonal architecture (ports and adapters) within each bounded context.

## Technology Stack

- **Language**: Kotlin 1.9.22
- **Web Framework**: Ktor 2.3.7 (Netty engine)
- **Database**: PostgreSQL 16+ with HikariCP connection pooling
- **Database Access**: jOOQ 3.19.6 (type-safe SQL with Kotlin support)
- **Database Migrations**: Liquibase 4.26.0 (automated schema management)
- **Authentication**: JWT (JSON Web Tokens) with auth0-jwt
- **Password Hashing**: BCrypt (jbcrypt 0.4)
- **Unique Identifiers**: ULID (Universally Unique Lexicographically Sortable Identifier)
- **Dependency Injection**: Koin 3.5.3
- **Build Tool**: Gradle (Kotlin DSL)
- **Testing**: Kotest (unit/integration), ArchUnit (architecture), Testcontainers (database integration tests)
- **JVM**: Java 21

## Build and Development Commands

### Database Setup (Required)

The application requires PostgreSQL. Use Docker Compose for local development:

```bash
cd docker
docker-compose up -d
```

This starts PostgreSQL on `localhost:5432` with default credentials. Data persists in `docker/data/`.

Alternatively, set environment variables to connect to your own PostgreSQL instance:
- `DATABASE_URL` (default: `jdbc:postgresql://localhost:5432/gchess_dev`)
- `DATABASE_USER` (default: `gchess`)
- `DATABASE_PASSWORD` (default: `gchess`)

### Build the project
```bash
./gradlew build
```

### Run the application
```bash
./gradlew run
```

Note: Database migrations run automatically on startup via Liquibase.

### Run unit tests
```bash
./gradlew unitTest
```

### Run architecture tests
```bash
./gradlew architectureTest
```

### Run integration tests
```bash
./gradlew integrationTest
```

### Run all tests
```bash
./gradlew check
```

### Run a specific test class
```bash
./gradlew unitTest --tests "com.gchess.chess.domain.model.ChessPositionTest"
```

### Clean build artifacts
```bash
./gradlew clean
```

### Regenerate jOOQ classes from database schema
```bash
./gradlew generateJooq
```

This task:
- Starts a Testcontainers PostgreSQL instance
- Runs Liquibase migrations to create schema
- Generates type-safe jOOQ classes in `build/generated-sources/jooq/`
- Classes are automatically included in compilation

Note: jOOQ generation is automatic during build, but you can run this manually after schema changes.

## Architecture

### Domain-Driven Design with Bounded Contexts

The codebase follows **Domain-Driven Design** with clearly separated **bounded contexts**, each implementing **hexagonal architecture** (ports and adapters):

#### Chess Bounded Context (`com.gchess.chess`)

**Domain Layer** (`com.gchess.chess.domain`):
- `model/`: Core business entities
  - `Game`: Chess game aggregate
    - **Invariants**:
      - `whitePlayer` always controls `PlayerSide.WHITE`
      - `blackPlayer` always controls `PlayerSide.BLACK`
      - `currentPlayer` is derived from `currentSide` (calculated property - no duplication)
      - Players cannot change sides during a game
  - `ChessPosition`: Bitboard-based position representation
  - `Move`, `Position`, `Piece`, `PlayerSide`, `PieceType`, `GameStatus`, `CastlingRights`
- `port/`: Interfaces defining contracts
  - `GameRepository`: Game persistence
  - `PlayerExistenceChecker`: **ACL port** for player validation (Anti-Corruption Layer)
- `service/`: Domain services
  - `ChessRules`: Interface defining chess rules
  - `StandardChessRules`: FIDE-compliant implementation using bitboard techniques
- No dependencies on external frameworks or User context - pure business logic

**Application Layer** (`com.gchess.chess.application`):
- `usecase/`: Application use cases orchestrating domain logic
  - `CreateGameUseCase`: Creates a new chess game
    - Validates both players exist via `PlayerExistenceChecker` (ACL)
    - Validates players are different
    - Returns `Result<Game>` for error handling
  - `GetGameUseCase`: Retrieves a game by ID
  - `MakeMoveUseCase`: Validates player turn and executes a move
    - Validates player exists via `PlayerExistenceChecker`
    - Validates it's the player's turn using `Game.isPlayerTurn()`
    - Validates move is legal via `ChessRules`
- Depends only on Chess domain layer and Shared Kernel

**Infrastructure Layer** (`com.gchess.chess.infrastructure`):
- `adapter/driver/`: Entry points (REST API routes)
  - `GameRoutes.kt`: HTTP endpoints for game operations
    - **Protected routes** (require JWT authentication):
      - `POST /api/games` - Create new game
      - `POST /api/games/{id}/moves` - Make a move (playerId extracted from JWT)
    - **Public routes**:
      - `GET /api/games/{id}` - Get game state
  - `dto/`: Data Transfer Objects for JSON serialization (GameDTO, ChessPositionDTO, MoveDTO)
- `adapter/driven/`: External integrations
  - `PostgresGameRepository`: PostgreSQL implementation using jOOQ for type-safe SQL
    - Persists games and move history with transactions
    - Uses FEN notation for board state serialization
    - Wraps jOOQ calls in `withContext(Dispatchers.IO)` for coroutines
  - `UserContextPlayerChecker`: **Anti-Corruption Layer** adapter
    - Implements `PlayerExistenceChecker` port
    - Calls `GetUserUseCase` from User context
    - Fail-fast strategy: propagates errors immediately
    - Maintains bounded context isolation

#### User Bounded Context (`com.gchess.user`)

**Domain Layer** (`com.gchess.user.domain`):
- `model/`: User entities and value objects
  - `User`: User aggregate with id, username, email, passwordHash
    - Business rule validations (username length, email format)
    - `withMaskedPassword()` method for safe display
  - `Credentials`: Value object for authentication
    - Overrides `toString()` to mask password
- `port/`: Interfaces defining contracts
  - `UserRepository`: User persistence with query methods (by username, email, ID)
  - `PasswordHasher`: Password hashing abstraction (decouples domain from BCrypt)
- No dependencies on external frameworks or Chess context - pure business logic

**Application Layer** (`com.gchess.user.application`):
- `usecase/`: User management use cases
  - `RegisterUserUseCase`: User registration
    - Validates username/email uniqueness
    - Enforces password strength (minimum 8 characters)
    - Generates ULID for PlayerId
    - Returns `Result<User>` for error handling
  - `LoginUseCase`: User authentication
    - Validates credentials via `PasswordHasher.verify()`
    - Returns `Result<User>` (success) or failure
  - `GetUserUseCase`: Retrieve user by ID
- Depends only on User domain layer and Shared Kernel

**Infrastructure Layer** (`com.gchess.user.infrastructure`):
- `adapter/driver/`: Entry points (REST API routes)
  - `AuthRoutes.kt`: Authentication endpoints (**public**, no auth required)
    - `POST /api/auth/register` - User registration
    - `POST /api/auth/login` - Login (generates JWT token)
  - `UserRoutes.kt`: User profile endpoints
    - `GET /api/users/{id}` - Get user profile
  - `dto/`: DTOs (UserDTO, LoginRequest, RegisterRequest, LoginResponse with JWT token)
- `adapter/driven/`: External integrations
  - `PostgresUserRepository`: PostgreSQL user storage using jOOQ
    - Type-safe SQL queries via jOOQ DSL
    - Indexed lookups by username, email, and ID
    - Connection pooling via HikariCP
  - `BcryptPasswordHasher`: BCrypt implementation of PasswordHasher
    - Configurable work factor (default: 12)

#### Matchmaking Bounded Context (`com.gchess.matchmaking`)

**Domain Layer** (`com.gchess.matchmaking.domain`):
- `model/`: Core matchmaking entities
  - `QueueEntry`: Value object for players in matchmaking queue
    - `playerId: PlayerId` - Player identifier
    - `joinedAt: Instant` - Timestamp when player joined (FIFO ordering)
  - `Match`: Entity representing successful match with TTL
    - `whitePlayerId: PlayerId` - Player assigned white pieces
    - `blackPlayerId: PlayerId` - Player assigned black pieces
    - `gameId: GameId` - Created game identifier
    - `matchedAt: Instant` - When match was created
    - `expiresAt: Instant` - When match expires (default: 5 minutes TTL)
    - `isExpired()` method for TTL validation
  - `MatchmakingStatus`: Enum (WAITING, MATCHED)
- `port/`: Interfaces defining contracts
  - `MatchmakingQueue`: FIFO queue operations (add, remove, findMatch)
  - `MatchRepository`: Match persistence (save, find, delete expired)
  - `GameCreator`: **ACL port** for Chess context game creation
  - Reuses `PlayerExistenceChecker` from Chess context (port sharing)
- No dependencies on external frameworks or other contexts - pure business logic

**Application Layer** (`com.gchess.matchmaking.application`):
- `usecase/`: Matchmaking orchestration use cases
  - `JoinMatchmakingUseCase`: Main matchmaking orchestrator
    - Validates player exists via `PlayerExistenceChecker` (ACL)
    - Checks player not already in queue or matched
    - Adds to queue, attempts to find match
    - If match found: creates game automatically, assigns colors randomly (50/50)
    - Returns `MatchmakingResult` (WAITING or MATCHED with game details)
  - `GetMatchStatusUseCase`: Check player's matchmaking status
    - Returns WAITING (with queue position), MATCHED (with gameId + color), or NOT_FOUND
  - `LeaveMatchmakingUseCase`: Remove player from queue
    - Returns boolean indicating if player was removed
  - `CreateGameFromMatchUseCase`: Create game with random color assignment
    - Uses `GameCreator` port (ACL to Chess context)
    - 50/50 random assignment of WHITE/BLACK
  - `CleanupExpiredMatchesUseCase`: Remove expired matches (TTL enforcement)
- `MatchmakingResult`: Sealed class for result types (NotFound, Waiting, Matched)
- Depends only on Matchmaking domain, shared ports, and Shared Kernel

**Infrastructure Layer** (`com.gchess.matchmaking.infrastructure`):
- `adapter/driver/`: Entry points (REST API routes)
  - `MatchmakingRoutes.kt`: Matchmaking endpoints (**all require JWT authentication**)
    - `POST /api/matchmaking/queue` - Join matchmaking queue
      - Returns WAITING (solo) or MATCHED (if opponent found)
      - Automatic game creation on match
    - `DELETE /api/matchmaking/queue` - Leave matchmaking queue
    - `GET /api/matchmaking/status` - Poll for matchmaking status
      - Clients should poll every 2-3 seconds for match updates
  - `dto/`: DTOs (MatchmakingStatusDTO with status, queuePosition, gameId, yourColor)
- `adapter/driven/`: External integrations and ACL
  - `InMemoryMatchmakingQueue`: Thread-safe FIFO queue (remains in-memory for performance)
    - Uses `ConcurrentLinkedQueue` for queue + `ConcurrentHashMap` for index
    - `ReentrantLock` for race condition protection
    - In-memory design optimal for volatile, short-lived queue data
  - `PostgresMatchRepository`: PostgreSQL match storage using jOOQ
    - Type-safe SQL queries via jOOQ DSL
    - Indexed lookups by player ID
    - TTL enforcement with automatic expiration
  - `ChessContextGameCreator`: **Anti-Corruption Layer** adapter
    - Implements `GameCreator` port
    - Calls `CreateGameUseCase` from Chess context
    - Translates between contexts (GameId extraction)
  - Reuses `UserContextPlayerChecker` via dependency injection

#### Shared Kernel (`com.gchess.shared`)

- `domain/model/`: Common value objects shared across contexts
  - `PlayerId`: ULID-based player identifier (value class)
  - `GameId`: ULID-based game identifier (value class)
- **Why ULID?**
  - Lexicographically sortable (time-ordered)
  - URL-safe (26 characters, no special characters)
  - 128-bit uniqueness like UUID
  - Better for distributed systems
- Framework-agnostic, immutable value classes
- Minimal shared surface to avoid coupling

#### Infrastructure Configuration (`com.gchess.infrastructure.config`)

- `DatabaseConfig.kt`: PostgreSQL database configuration
  - HikariCP connection pooling (configurable pool size)
  - Liquibase migrations on startup
  - jOOQ DSLContext for type-safe queries
  - Configuration via environment variables (DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
- `KoinModule.kt`: Dependency injection wiring for all contexts
  - **Database Layer**: DataSource, DSLContext (shared across contexts)
  - **Chess Context** dependencies (PostgreSQL repositories, services, use cases)
  - **User Context** dependencies (PostgreSQL repositories, services, use cases)
  - **Matchmaking Context** dependencies (in-memory queue, PostgreSQL repositories, use cases)
  - **Anti-Corruption Layer** wiring:
    - `PlayerExistenceChecker` → `UserContextPlayerChecker` (Chess → User)
    - `GameCreator` → `ChessContextGameCreator` (Matchmaking → Chess)
- `JwtConfig.kt`: JWT token generation and validation
  - HMAC256 algorithm
  - 24-hour token validity (configurable)
  - Embeds `playerId` claim in token
  - Configuration via environment variables (JWT_SECRET, JWT_VALIDITY_MS)

### Key Design Patterns

- **Bounded Contexts**: Three contexts (Chess, User, Matchmaking) with clear boundaries (see CONTEXT_MAP.md)
- **Shared Kernel**: Common value objects (PlayerId, GameId) shared across all contexts
- **Anti-Corruption Layer (ACL)**: Multiple ACL adapters protect context isolation
  - `UserContextPlayerChecker`: Chess → User (player validation)
  - `ChessContextGameCreator`: Matchmaking → Chess (game creation)
  - Domain defines ports (what it needs), infrastructure provides adapters (how to get it)
  - Fail-fast strategy for consistency
- **Port Reuse**: Matchmaking reuses `PlayerExistenceChecker` port from Chess domain
  - Acceptable trade-off: avoids port duplication while maintaining isolation
  - Both contexts validate players through the same contract
- **Hexagonal Architecture**: Ports and adapters within each bounded context
- **Domain Services**: Business logic that doesn't belong to a single entity (e.g., ChessRules)
- **Value Objects**: Immutable objects defined by their attributes (e.g., CastlingRights, Position, Move, QueueEntry, Match)
- **Repository Pattern**: Abstraction for data persistence
- **Use Case Pattern**: Each user action is a dedicated class
- **Dependency Inversion**: Domain defines interfaces; infrastructure implements
- **DTO Pattern**: Separation between domain models (pure) and API contracts (serializable)
- **Immutability**: Domain models are immutable data classes
- **Encapsulation**: Domain concepts encapsulated in dedicated classes with business methods
- **Thread Safety**: Concurrent data structures (ConcurrentHashMap, ConcurrentLinkedQueue) + ReentrantLock
- **TTL Pattern**: Time-bound entities (Match with expiration) for resource cleanup

### Data Flow

```
HTTP Request (with JWT)
  → Authentication (JWT validation)
  → GameRoutes (Input Adapter)
  → Use Case
  → Domain Logic
  → ACL (if cross-context call needed)
  → Repository (Output Adapter)
```

## Domain Model

### Chess Context

- **Game**: Complete game state including ChessPosition, players, current side, status, and move history
  - `id: GameId` - Unique game identifier (ULID)
  - `whitePlayer: PlayerId` - White player identifier
  - `blackPlayer: PlayerId` - Black player identifier
  - `currentSide: PlayerSide` - Which side is to move (WHITE or BLACK)
  - `currentPlayer: PlayerId` - Calculated property (derived from currentSide)
  - `status: GameStatus` - IN_PROGRESS, CHECK, CHECKMATE, STALEMATE, DRAW
  - `moveHistory: List<Move>` - All moves played
- **Position**: Chess board position (file: 0-7, rank: 0-7), supports algebraic notation (e.g., "e4")
- **Piece**: Chess piece with type, color, and movement tracking
- **ChessPosition**: Bitboard-based chess position representation using 64-bit integers for efficient board state and move generation. Supports FEN notation import/export, castling rights, en passant tracking, and halfmove/fullmove counters
- **CastlingRights**: Value Object encapsulating castling availability for both players (kingside and queenside). Provides domain methods like `canCastleKingside(color)` and FEN conversion
- **Move**: Represents a chess move from one position to another, with optional promotion
- **PlayerSide**: WHITE or BLACK (board side, distinct from player identity)
- **PieceType**: PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING
- **GameStatus**: IN_PROGRESS, CHECK, CHECKMATE, STALEMATE, DRAW

### User Context

- **User**: User aggregate
  - `id: PlayerId` - Unique user identifier (ULID)
  - `username: String` - Unique username (min 3 chars)
  - `email: String` - Unique email
  - `passwordHash: String` - BCrypt hashed password (never exposed in DTOs)
- **Credentials**: Username and password for authentication

### Matchmaking Context

- **QueueEntry**: Value object for queue management
  - `playerId: PlayerId` - Player in queue
  - `joinedAt: Instant` - Join timestamp (for FIFO ordering)
- **Match**: Entity representing a successful match
  - `whitePlayerId: PlayerId` - Player assigned WHITE
  - `blackPlayerId: PlayerId` - Player assigned BLACK
  - `gameId: GameId` - Created game identifier
  - `matchedAt: Instant` - Match creation time
  - `expiresAt: Instant` - Expiration time (TTL)
  - `isExpired(): Boolean` - Check if match has expired
  - **Default TTL**: 5 minutes
- **MatchmakingStatus**: WAITING, MATCHED (enum)
- **MatchmakingResult**: Sealed class for use case results
  - `NotFound`: Player not in queue or matched
  - `Waiting(queuePosition: Int)`: Player in queue at position
  - `Matched(gameId: GameId, yourColor: PlayerSide)`: Match found with game details
- **PlayerSide**: WHITE or BLACK (reused from Chess context for color assignment)

### Shared Kernel

- **PlayerId**: ULID-based identifier for players (value class)
- **GameId**: ULID-based identifier for games (value class)

## Bitboard Architecture

The chess engine uses bitboards for efficient position representation and move generation:

- **Bitboard Representation**: Each piece type/color combination is stored as a 64-bit Long where each bit represents a square (bit 0 = a1, bit 63 = h8)
- **Efficient Operations**: Bitwise operations enable fast move generation, occupancy checks, and attack detection
- **Memory Layout**: ChessPosition contains 12 bitboards (6 piece types × 2 colors) plus metadata (castling rights, en passant, etc.)
- **FEN Support**: Full support for Forsyth-Edwards Notation (FEN) for position import/export via `String.toChessPosition()` and `ChessPosition.toFen()`
- **Move Generation**: The `ChessPosition.getLegalMoves()` method delegates to `StandardChessRules` for efficient legal move generation

## API Endpoints

The application exposes a REST API on port 8080:

### User Management (Public - No Authentication Required)

- `POST /api/auth/register` - Register new user
  - Request body: `{"username": "alice", "email": "alice@example.com", "password": "SecurePass123"}`
  - Response: UserDTO with id, username, email
- `POST /api/auth/login` - Authenticate user
  - Request body: `{"username": "alice", "password": "SecurePass123"}`
  - Response: `{"user": {...}, "token": "eyJhbGci...", "message": "Login successful"}`
- `GET /api/users/{id}` - Get user profile

### Game Operations

- `POST /api/games` - Create a new game (**Requires JWT Authentication**)
  - Request header: `Authorization: Bearer <JWT_TOKEN>`
  - Request body: `{"whitePlayerId": "01HQZN...", "blackPlayerId": "01HQZN..."}`
  - Validates both players exist before creating game (via ACL)
- `GET /api/games/{id}` - Get game state by ID (Public)
- `POST /api/games/{id}/moves` - Make a move (**Requires JWT Authentication**)
  - Request header: `Authorization: Bearer <JWT_TOKEN>`
  - Player ID extracted from JWT token
  - Request body: `{"from": "e2", "to": "e4"}` or `{"from": "e7", "to": "e8", "promotion": "QUEEN"}`
  - Validates it's the authenticated player's turn

### Matchmaking Operations (All Require JWT Authentication)

- `POST /api/matchmaking/queue` - Join matchmaking queue
  - Request header: `Authorization: Bearer <JWT_TOKEN>`
  - Player ID extracted from JWT token
  - No request body needed
  - Response:
    - If no opponent: `{"status": "WAITING", "queuePosition": 1}`
    - If opponent found: `{"status": "MATCHED", "gameId": "01HQZN...", "yourColor": "WHITE"}`
  - Automatic game creation when two players match
  - Random color assignment (50/50)
  - Returns 409 Conflict if already in queue or matched
- `GET /api/matchmaking/status` - Get current matchmaking status
  - Request header: `Authorization: Bearer <JWT_TOKEN>`
  - Response:
    - `{"status": "NOT_FOUND"}` - Not in queue or matched
    - `{"status": "WAITING", "queuePosition": 1}` - In queue
    - `{"status": "MATCHED", "gameId": "01HQZN...", "yourColor": "BLACK"}` - Matched
  - Poll this endpoint every 2-3 seconds to check for match
- `DELETE /api/matchmaking/queue` - Leave matchmaking queue
  - Request header: `Authorization: Bearer <JWT_TOKEN>`
  - Response: `{"removed": true}` if removed, `{"removed": false}` if not in queue
  - Cannot leave once matched (game already created)

## Chess Rules Implementation

The `StandardChessRules` domain service implements:

### Move Generation & Validation
- ✅ Complete move generation for all piece types (Pawn, Knight, Bishop, Rook, Queen, King)
- ✅ En passant captures
- ✅ Pawn promotion (Queen, Rook, Bishop, Knight)
- ✅ Check detection
- ✅ Pinned pieces (pieces that cannot move without exposing king to check)
- ✅ King cannot capture protected pieces (validates destination square threats after removing both king and captured piece)
- ✅ Castling (kingside and queenside for both colors)
  - King and rook must not have moved (castling rights tracked)
  - No pieces between king and rook
  - King not in check, doesn't pass through check, doesn't end in check

### Game-Ending Conditions
- ✅ **Checkmate detection** (`isCheckmate()`)
  - King in check AND no legal moves available
- ✅ **Stalemate detection** (`isStalemate()`)
  - King NOT in check AND no legal moves available

### Draw Rules
- ✅ **Fifty-move rule** (`isFiftyMoveRule()`)
  - Draw when 50 consecutive moves (100 half-moves) without pawn move or capture
  - Uses `halfmoveClock` from position (automatically reset on capture/pawn move)
- ✅ **Threefold repetition** (`isThreefoldRepetition()`)
  - Draw when same position occurs 3 times
  - Compares piece placement, side to move, castling rights, and en passant (excludes move counters)
- ✅ **Insufficient material** (`isInsufficientMaterial()`)
  - King vs King
  - King + Bishop vs King
  - King + Knight vs King
  - King + Bishop vs King + Bishop (same color bishops only)

## Anti-Corruption Layer (ACL)

The ACL pattern protects the Chess context from changes in the User context:

### How It Works

1. **Port Definition** (Chess Domain):
   ```kotlin
   interface PlayerExistenceChecker {
       suspend fun exists(playerId: PlayerId): Boolean
   }
   ```
   - Chess domain defines what it needs
   - No knowledge of User context

2. **Adapter Implementation** (Chess Infrastructure):
   ```kotlin
   class UserContextPlayerChecker(
       private val getUserUseCase: GetUserUseCase
   ) : PlayerExistenceChecker {
       override suspend fun exists(playerId: PlayerId): Boolean {
           val user = getUserUseCase.execute(playerId)
           return user != null
       }
   }
   ```
   - Translates Chess needs into User context calls
   - Fail-fast strategy: propagates errors immediately

3. **Usage** (Chess Application):
   ```kotlin
   class CreateGameUseCase(
       private val gameRepository: GameRepository,
       private val playerExistenceChecker: PlayerExistenceChecker // ACL port
   ) {
       suspend fun execute(whitePlayerId: PlayerId, blackPlayerId: PlayerId): Result<Game> {
           // Validate players exist via ACL
           if (!playerExistenceChecker.exists(whitePlayerId)) {
               return Result.failure(Exception("White player does not exist"))
           }
           // ...
       }
   }
   ```

### Benefits

- ✅ Chess context remains independent of User context
- ✅ User context can change without affecting Chess domain
- ✅ Clear boundary between contexts
- ✅ Easy to replace User context with external service later
- ✅ Testable (can mock PlayerExistenceChecker in Chess tests)

## Security

### JWT Authentication

- **Token Generation**: On successful login, JWT token is generated with:
  - Issuer: "gchess"
  - Audience: "gchess-users"
  - Claim: `playerId` (ULID of authenticated user)
  - Expiration: 24 hours
  - Algorithm: HMAC256
- **Token Validation**: Ktor's JWT authentication plugin validates:
  - Signature (HMAC256)
  - Issuer and audience
  - Expiration time
  - Presence of `playerId` claim
- **Protected Routes**: Chess game operations require valid JWT in `Authorization: Bearer <token>` header
- **Player ID Extraction**: `playerId` is extracted from validated JWT token (cannot be spoofed)

### Password Hashing

- BCrypt with work factor 12
- Automatic salt generation
- Passwords never stored or logged in plain text
- User.withMaskedPassword() for safe display
- Credentials.toString() override masks password

## Current Limitations

- Matchmaking queue is in-memory only - queue state lost on server restart (games/users/matches persist)
- No token refresh mechanism (JWT tokens expire after configured validity period)
- No WebSocket support for real-time updates (matchmaking requires polling)
  - Clients must poll `GET /api/matchmaking/status` every 2-3 seconds
  - Ideal: WebSocket push notifications when match is found
- Matchmaking is simple FIFO (no ELO/skill-based matching)
- No reconnection handling if client disconnects while in queue
- Match TTL cleanup is passive (no background job, cleanup on next operation)
- Draw by mutual agreement not yet implemented (requires player interaction/API endpoint)

## Architecture Testing

The project uses **ArchUnit** to enforce architecture rules automatically.

### Hexagonal Architecture Tests (`HexagonalArchitectureTest.kt`)

**Layer Dependency Rules**:
- ✅ Domain layer has **no dependencies** on application or infrastructure layers
- ✅ Domain layer is **framework-agnostic** (no Ktor, Koin, or serialization dependencies)
- ✅ Application layer depends **only on domain** (and standard Kotlin libraries)
- ✅ Infrastructure can access application and domain
- ✅ Dependencies flow **inward** toward the domain

**Naming Conventions**:
- ✅ Use cases end with `UseCase`
- ✅ Repository interfaces end with `Repository`
- ✅ Domain services end with `Rules` or `Service`

**Package Organization**:
- ✅ Domain services are interfaces or implementations of domain interfaces
- ✅ Ports (repository interfaces) reside in `domain.port` package
- ✅ Adapters (implementations) reside in `infrastructure` layer
- ✅ Domain models (entities, value objects) reside in `domain.model` package

### Bounded Context Isolation Tests (`BoundedContextTest.kt`)

**Context Isolation** (29 tests total):
- ✅ Chess domain cannot depend on User or Matchmaking contexts
- ✅ Chess application cannot depend on User or Matchmaking contexts
- ✅ User domain cannot depend on Chess or Matchmaking contexts
- ✅ User application cannot depend on Chess or Matchmaking contexts
- ✅ Matchmaking domain cannot depend on Chess or User contexts
- ✅ Matchmaking application cannot depend on Chess or User contexts (except shared ports)

**ACL Enforcement**:
- ✅ Only infrastructure layer can cross context boundaries
- ✅ ACL adapters reside in infrastructure layer:
  - `UserContextPlayerChecker` (Chess → User)
  - `ChessContextGameCreator` (Matchmaking → Chess)
- ✅ Domain and application layers remain isolated

**Port Reuse**:
- ✅ Matchmaking reuses `PlayerExistenceChecker` port from Chess domain
- ✅ Acceptable trade-off to avoid port duplication while maintaining isolation

**Shared Kernel**:
- ✅ Shared Kernel contains only value objects (no services/use cases)
- ✅ Shared Kernel has no external framework dependencies
- ✅ All three contexts can depend on Shared Kernel

Run architecture tests:
```bash
./gradlew architectureTest
```

## Integration Testing

The project includes **end-to-end integration tests** that verify the full application stack from HTTP request to response:

### Test Organization
- **Location**: `src/integrationTest/kotlin/`
- **Framework**: Ktor Test Host with Kotest assertions
- **Database**: Testcontainers with real PostgreSQL 16 (alpine image)
  - Singleton container shared across all tests
  - Automatic Liquibase migrations on startup
  - Database cleanup between tests (TRUNCATE CASCADE)
  - Full jOOQ type-safe queries tested end-to-end
- **Scope**: Full API testing including authentication, authorization, persistence, and domain logic

### What Integration Tests Cover
- ✅ Complete authentication flow: Register → Login → JWT token
- ✅ Game creation with player validation (ACL)
- ✅ Move execution with turn validation
- ✅ **Matchmaking flow**: Join queue → Match → Game creation
  - Single player: WAITING status with queue position
  - Two players: Automatic MATCHED status with game creation
  - Random color assignment (WHITE/BLACK)
  - Queue operations: join, leave, status polling
  - Error handling: already in queue, unauthorized access
- ✅ JWT authentication and authorization on all endpoints
- ✅ DTO serialization/deserialization
- ✅ HTTP status codes and error responses (200, 401, 409, etc.)
- ✅ Full integration of all layers (infrastructure → application → domain → ACL)
- ✅ Cross-context communication via ACL (Matchmaking → Chess → User)

Run integration tests:
```bash
./gradlew integrationTest
```

## API Changes and Migration Notes

### Breaking Changes from Previous Versions

**Game DTO**:
- **Before**: `currentPlayer: "WHITE"` (board side)
- **After**:
  - `currentPlayer: "01HQZN..."` (PlayerId - identity of player whose turn it is)
  - `currentSide: "WHITE"` (PlayerSide - which board side is to move)
- **Migration**: Clients must update to use `currentSide` for board side, `currentPlayer` for player identity

**Game Creation**:
- **Before**: `POST /api/games` (no body, auto-generated players)
- **After**: `POST /api/games` with `{"whitePlayerId": "...", "blackPlayerId": "..."}` (requires JWT authentication)
- **Migration**: Clients must register users first, then create games with player IDs

**Making Moves**:
- **Before**: Move request body only contained move coordinates
- **After**: Requires JWT authentication header; player ID extracted from token
- **Migration**: Clients must login and include JWT token in Authorization header

## Development Notes

- The project uses Kotlin data classes for immutability
- Bitboard-based chess engine for efficient move generation and position evaluation
- All domain logic is in pure Kotlin with no framework dependencies
- Bounded contexts maintain strict isolation (validated by ArchUnit tests)
- **Database**: PostgreSQL with jOOQ for type-safe queries and Liquibase for migrations
  - Development: Use Docker Compose (`docker/docker-compose.yml`) for local PostgreSQL
  - Testing: Testcontainers provides isolated PostgreSQL instances
  - Production: Configure via environment variables (DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
- Koin is used for dependency injection - see `KoinModule.kt` for wiring
- The application entry point is `com.gchess.Application.kt`
- Ktor runs on Netty engine listening on port 8080
- JWT and database configuration via environment variables (see `application.conf`)
- ChessRules domain service follows Domain-Driven Design principles (domain service pattern)
- See [CONTEXT_MAP.md](CONTEXT_MAP.md) for detailed context relationship documentation
