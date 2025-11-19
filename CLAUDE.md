# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

gChess is a chess application built with Kotlin following Domain-Driven Design with bounded contexts. It uses Ktor as the web framework, Koin for dependency injection, JWT for authentication, and implements hexagonal architecture (ports and adapters) within each bounded context.

## Technology Stack

- **Language**: Kotlin 1.9.22
- **Web Framework**: Ktor 2.3.7
- **Authentication**: JWT (JSON Web Tokens) with auth0-jwt
- **Password Hashing**: BCrypt (jbcrypt 0.4)
- **Unique Identifiers**: ULID (Universally Unique Lexicographically Sortable Identifier)
- **Dependency Injection**: Koin 3.5.3
- **Build Tool**: Gradle (Kotlin DSL)
- **Testing**: Kotest (unit/integration), ArchUnit (architecture)
- **JVM**: Java 21

## Build and Development Commands

### Build the project
```bash
./gradlew build
```

### Run the application
```bash
./gradlew run
```

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
  - `InMemoryGameRepository`: In-memory implementation of GameRepository
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
  - `InMemoryUserRepository`: In-memory user storage
    - Indexes for fast lookups (username, email)
    - Thread-safe with ConcurrentHashMap
  - `BcryptPasswordHasher`: BCrypt implementation of PasswordHasher
    - Configurable work factor (default: 12)

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

- `KoinModule.kt`: Dependency injection wiring for all contexts
  - **Chess Context** dependencies
  - **User Context** dependencies
  - **Anti-Corruption Layer** wiring: `PlayerExistenceChecker` → `UserContextPlayerChecker`
- `JwtConfig.kt`: JWT token generation and validation
  - HMAC256 algorithm
  - 24-hour token validity
  - Embeds `playerId` claim in token
  - **TODO Production**: Move secret to environment variables

### Key Design Patterns

- **Bounded Contexts**: Chess and User contexts with clear boundaries (see CONTEXT_MAP.md)
- **Shared Kernel**: Common value objects (PlayerId, GameId) shared across contexts
- **Anti-Corruption Layer (ACL)**: UserContextPlayerChecker protects Chess context from User context changes
  - Chess domain defines `PlayerExistenceChecker` port (what it needs)
  - Infrastructure provides adapter (how to get it from User context)
  - Fail-fast strategy for consistency
- **Hexagonal Architecture**: Ports and adapters within each bounded context
- **Domain Services**: Business logic that doesn't belong to a single entity (e.g., ChessRules)
- **Value Objects**: Immutable objects defined by their attributes (e.g., CastlingRights, Position, Move, PlayerId, GameId)
- **Repository Pattern**: Abstraction for data persistence
- **Use Case Pattern**: Each user action is a dedicated class
- **Dependency Inversion**: Domain defines interfaces; infrastructure implements
- **DTO Pattern**: Separation between domain models (pure) and API contracts (serializable)
- **Immutability**: Domain models are immutable data classes
- **Encapsulation**: Domain concepts encapsulated in dedicated classes with business methods

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

## Chess Rules Implementation

The `StandardChessRules` domain service implements:
- ✅ Complete move generation for all piece types (Pawn, Knight, Bishop, Rook, Queen, King)
- ✅ En passant captures
- ✅ Pawn promotion (Queen, Rook, Bishop, Knight)
- ✅ Check detection
- ✅ Pinned pieces (pieces that cannot move without exposing king to check)
- ✅ Castling (kingside and queenside for both colors)
  - King and rook must not have moved (castling rights tracked)
  - No pieces between king and rook
  - King not in check, doesn't pass through check, doesn't end in check
- ⏳ Checkmate detection (TODO - interface defined but not implemented)
- ⏳ Stalemate detection (TODO - interface defined but not implemented)

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

- Checkmate and stalemate detection not yet implemented (interface exists)
- In-memory storage only - games and users are lost on server restart
- JWT secret stored in code (should be in environment variables for production)
- No token refresh mechanism
- No WebSocket support for real-time updates (though Ktor WebSocket dependency is included)

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

**Context Isolation**:
- ✅ Chess domain cannot depend on User context
- ✅ Chess application cannot depend on User context
- ✅ User domain cannot depend on Chess context
- ✅ User application cannot depend on Chess context

**ACL Enforcement**:
- ✅ Only infrastructure layer can cross context boundaries
- ✅ ACL adapter (UserContextPlayerChecker) can call User application
- ✅ Domain and application layers remain isolated

**Shared Kernel**:
- ✅ Shared Kernel contains only value objects (no services/use cases)
- ✅ Shared Kernel has no external framework dependencies
- ✅ Both contexts can depend on Shared Kernel

Run architecture tests:
```bash
./gradlew architectureTest
```

## Integration Testing

The project includes **end-to-end integration tests** that verify the full application stack from HTTP request to response:

### Test Organization
- **Location**: `src/integrationTest/kotlin/`
- **Framework**: Ktor Test Host with Kotest assertions
- **Scope**: Full API testing including authentication, authorization, and domain logic

### What Integration Tests Cover
- ✅ Complete authentication flow: Register → Login → JWT token
- ✅ Game creation with player validation (ACL)
- ✅ Move execution with turn validation
- ✅ JWT authentication and authorization
- ✅ DTO serialization/deserialization
- ✅ HTTP status codes and error responses
- ✅ Full integration of all layers (infrastructure → application → domain → ACL)

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
- Koin is used for dependency injection - see `KoinModule.kt` for wiring
- The application entry point is `com.gchess.Application.kt`
- Ktor runs on Netty engine listening on port 8080
- JWT secret should be moved to environment variables for production
- ChessRules domain service follows Domain-Driven Design principles (domain service pattern)
- See [CONTEXT_MAP.md](CONTEXT_MAP.md) for detailed context relationship documentation
