# gChess

A high-performance chess application built with Kotlin, featuring bitboard-based move generation, hexagonal architecture, and Domain-Driven Design with bounded contexts.

## Features

### Chess Engine
- ⚡ **Bitboard-based architecture** for efficient move generation and position evaluation
- ♟️ **Complete FIDE chess rules implementation**:
  - All piece movements (Pawn, Knight, Bishop, Rook, Queen, King)
  - Special moves: Castling (kingside & queenside), En passant, Pawn promotion
  - Check detection and pinned piece handling
  - Move validation ensuring king safety (including protected piece validation)
  - **Game-ending conditions**: Checkmate and Stalemate detection
  - **Draw rules**: Fifty-move rule, Threefold repetition, Insufficient material
- 📊 **FEN notation support** for position import/export
- 🎯 **Legal move generation** with full rule compliance

### User Management & Security
- 🔐 **JWT authentication** with Bearer tokens
- 👤 **User registration and login** with BCrypt password hashing
- 🛡️ **Protected endpoints** requiring authentication
- 🔑 **ULID-based identifiers** for users and games

### API & Architecture
- 🌐 **RESTful API** for game and user operations
- 🏗️ **Hexagonal architecture** (ports and adapters)
- 🎯 **Domain-Driven Design** with bounded contexts (Chess + User + Matchmaking)
- 🔄 **Anti-Corruption Layer** for context communication
- 🧪 **Comprehensive test coverage** (108+ unit tests, architecture tests, integration tests)
- 💉 **Dependency injection** with Koin
- 🗄️ **PostgreSQL persistence** with jOOQ and Liquibase migrations

## Quick Start

### Prerequisites

- Java 21 or higher
- Gradle (wrapper included)
- Docker (required for Testcontainers during build and tests)
- PostgreSQL 16+ (for production deployment)

### Database Setup

The application uses PostgreSQL for data persistence. You have two options:

#### Option 1: Development with Default Configuration (Recommended)

The application will attempt to connect to PostgreSQL using default development credentials:
```
URL: jdbc:postgresql://localhost:5432/gchess_dev
Username: gchess
Password: gchess
```

**Quick PostgreSQL setup with Docker:**
```bash
docker run -d \
  --name gchess-postgres \
  -e POSTGRES_DB=gchess_dev \
  -e POSTGRES_USER=gchess \
  -e POSTGRES_PASSWORD=gchess \
  -p 5432:5432 \
  postgres:16-alpine
```

Database migrations will run automatically on application startup via Liquibase.

#### Option 2: Custom Configuration with Environment Variables

Override default values with environment variables:

```bash
export DATABASE_URL="jdbc:postgresql://your-host:5432/your-database"
export DATABASE_USER="your-username"
export DATABASE_PASSWORD="your-password"
export DATABASE_POOL_SIZE=20  # Optional: connection pool size (default: 10)

# JWT secret (REQUIRED in production)
export JWT_SECRET="your-secure-random-secret-min-256-bits"
export JWT_VALIDITY_MS=86400000  # Optional: token validity in ms (default: 24h)

./gradlew run
```

**Generate a secure JWT secret:**
```bash
openssl rand -base64 32
```

### Running the Application

```bash
./gradlew run
```

The server will start on `http://localhost:8080`

On first startup, you'll see:
- Database connection established
- Liquibase migrations applied (creates tables: users, games, game_moves, matches)
- Server ready to accept requests

### Building

```bash
./gradlew build
```

### Testing

Run all tests:
```bash
./gradlew check
```

Run unit tests only:
```bash
./gradlew unitTest
```

Run architecture tests only:
```bash
./gradlew architectureTest
```

Run integration tests only:
```bash
./gradlew integrationTest
```

Run specific test class:
```bash
./gradlew unitTest --tests "com.gchess.domain.service.StandardChessRulesTest"
```

## API Usage

### User Management

#### Register a new user
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "SecurePassword123"
  }'
```

**Response:**
```json
{
  "id": "01HQZN2K3M4P5Q6R7S8T9V0W1X",
  "username": "alice",
  "email": "alice@example.com"
}
```

#### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePassword123"
  }'
```

**Response:**
```json
{
  "user": {
    "id": "01HQZN2K3M4P5Q6R7S8T9V0W1X",
    "username": "alice",
    "email": "alice@example.com"
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "message": "Login successful"
}
```

**Save the token** - you'll need it for authenticated requests!

### Game Operations (Require Authentication)

#### Create a new game
```bash
# Register two players first, then:
curl -X POST http://localhost:8080/api/games \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "whitePlayerId": "01HQZN2K3M4P5Q6R7S8T9V0W1X",
    "blackPlayerId": "01HQZN2K3M4P5Q6R7S8T9V0W2Y"
  }'
```

**Response:**
```json
{
  "id": "01HQZN3A4B5C6D7E8F9G0H1J2K",
  "whitePlayer": "01HQZN2K3M4P5Q6R7S8T9V0W1X",
  "blackPlayer": "01HQZN2K3M4P5Q6R7S8T9V0W2Y",
  "board": {},
  "currentSide": "WHITE",
  "currentPlayer": "01HQZN2K3M4P5Q6R7S8T9V0W1X",
  "status": "IN_PROGRESS",
  "moveHistory": []
}
```

#### Get game state (Public - No Auth Required)
```bash
curl http://localhost:8080/api/games/{gameId}
```

#### Make a move
```bash
# Player ID is extracted from JWT token
# Simple pawn move
curl -X POST http://localhost:8080/api/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"from": "e2", "to": "e4"}'

# Pawn promotion to queen
curl -X POST http://localhost:8080/api/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"from": "e7", "to": "e8", "promotion": "QUEEN"}'

# Castling (just move the king 2 squares)
curl -X POST http://localhost:8080/api/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"from": "e1", "to": "g1"}'
```

**Note:** The API validates that:
- Both players exist before creating a game
- It's the authenticated player's turn
- The move is legal according to chess rules

## Chess Rules Implementation

| Feature | Status | Description |
|---------|--------|-------------|
| **Basic Moves** | ✅ | All piece types (Pawn, Knight, Bishop, Rook, Queen, King) |
| **Pawn Promotion** | ✅ | Promote to Queen, Rook, Bishop, or Knight |
| **Castling** | ✅ | Kingside and queenside for both colors |
| **En Passant** | ✅ | Special pawn capture |
| **Check Detection** | ✅ | Validates king safety |
| **Pinned Pieces** | ✅ | Pieces that cannot move without exposing king |
| **Protected Pieces** | ✅ | King cannot capture protected pieces |
| **FEN Support** | ✅ | Import/export positions via Forsyth-Edwards Notation |
| **Checkmate** | ✅ | King in check with no legal moves |
| **Stalemate** | ✅ | King not in check with no legal moves |
| **Fifty-Move Rule** | ✅ | Draw after 50 moves without capture/pawn move |
| **Threefold Repetition** | ✅ | Draw when same position occurs 3 times |
| **Insufficient Material** | ✅ | Draw when checkmate is impossible (K vs K, K+B vs K, etc.) |

## Architecture

The project follows **Domain-Driven Design** with **bounded contexts**, implementing **hexagonal architecture** (ports and adapters) within each context:

```
                    ┌──────────────────────────────────────────┐
                    │         Shared Kernel                    │
                    │   (PlayerId, GameId - Value Objects)     │
                    └──────────────────────────────────────────┘
                              ↑        ↑        ↑
                              │        │        │
        ┌─────────────────────┘        │        └─────────────────────┐
        │                              │                              │
┌───────┴────────────┐    ┌───────────┴──────────┐    ┌─────────────┴──────┐
│  Chess Context     │    │  Matchmaking Context │    │   User Context     │
│ ┌────────────────┐ │    │ ┌──────────────────┐ │    │ ┌────────────────┐ │
│ │Infrastructure  │ │    │ │ Infrastructure   │ │    │ │Infrastructure  │ │
│ │• GameRoutes    │←┼────┼─│• MatchRoutes     │ │    │ │• AuthRoutes    │ │
│ │• PostgresGame  │ │    │ │• PostgresMatch   │ │    │ │• PostgresUser  │ │
│ │  Repository    │ │    │ │  Repository      │ │    │ │  Repository    │ │
│ │                │ │    │ │• GameCreator ACL─┼─┼───→│ │                │ │
│ │• PlayerChecker │ │    │ │• PlayerChecker ──┼─┼────┼→│• JwtConfig     │ │
│ │  ACL ──────────┼─┼────┼─┼→ (reused port)   │ │    │ │                │ │
│ └────────────────┘ │    │ └──────────────────┘ │    │ └────────────────┘ │
│ ┌────────────────┐ │    │ ┌──────────────────┐ │    │ ┌────────────────┐ │
│ │Application     │ │    │ │ Application      │ │    │ │Application     │ │
│ │• CreateGame    │ │    │ │• JoinMatchmaking │ │    │ │• RegisterUser  │ │
│ │• MakeMove      │ │    │ │• GetStatus       │ │    │ │• Login         │ │
│ │• GetGame       │ │    │ │• LeaveQueue      │ │    │ │• GetUser       │ │
│ └────────────────┘ │    │ └──────────────────┘ │    │ └────────────────┘ │
│ ┌────────────────┐ │    │ ┌──────────────────┐ │    │ ┌────────────────┐ │
│ │Domain          │ │    │ │ Domain           │ │    │ │Domain          │ │
│ │• Game          │ │    │ │• Match           │ │    │ │• User          │ │
│ │• ChessRules    │ │    │ │• QueueEntry      │ │    │ │• Credentials   │ │
│ │• ChessPosition │ │    │ │• Ports           │ │    │ │• Ports         │ │
│ └────────────────┘ │    │ └──────────────────┘ │    │ └────────────────┘ │
└────────────────────┘    └──────────────────────┘    └────────────────────┘

         ACL 1: Chess → User (Player validation)
         ACL 2: Matchmaking → Chess (Game creation)
         ACL 3: Matchmaking → User (Player validation, port reuse)
```

### Bounded Contexts

**Chess Context** (`com.gchess.chess`):
- **Domain**: Game, ChessPosition, ChessRules, PlayerSide
- **Application**: CreateGameUseCase, MakeMoveUseCase, GetGameUseCase
- **Infrastructure**: GameRoutes, PostgresGameRepository (jOOQ), UserContextPlayerChecker (ACL)
- **Purpose**: Manages chess games, rules, and gameplay

**User Context** (`com.gchess.user`):
- **Domain**: User, Credentials, PasswordHasher port
- **Application**: RegisterUserUseCase, LoginUseCase, GetUserUseCase
- **Infrastructure**: AuthRoutes, UserRoutes, PostgresUserRepository (jOOQ), BcryptPasswordHasher
- **Purpose**: Manages user accounts, authentication, and security

**Matchmaking Context** (`com.gchess.matchmaking`):
- **Domain**: QueueEntry, Match, MatchmakingStatus
- **Application**: JoinMatchmakingUseCase, GetMatchStatusUseCase, LeaveMatchmakingUseCase
- **Infrastructure**: MatchmakingRoutes, PostgresMatchRepository (jOOQ), InMemoryMatchmakingQueue
- **Purpose**: Manages player matchmaking, automatic game creation, and color assignment

**Shared Kernel** (`com.gchess.shared`):
- **Value Objects**: PlayerId, GameId (ULID-based)
- **Purpose**: Common concepts shared across contexts

### Anti-Corruption Layer (ACL)

The application uses multiple ACL adapters to maintain bounded context isolation:

**UserContextPlayerChecker** (Chess → User):
- Chess domain defines `PlayerExistenceChecker` port (interface)
- Infrastructure implements it by calling `GetUserUseCase` from User context
- Validates player existence before creating games or making moves
- Fail-fast strategy: errors propagate immediately

**ChessContextGameCreator** (Matchmaking → Chess):
- Matchmaking domain defines `GameCreator` port (interface)
- Infrastructure implements it by calling `CreateGameUseCase` from Chess context
- Creates games automatically when two players match
- Isolates matchmaking logic from chess game creation details

These ACLs maintain bounded context isolation while enabling cross-context communication.

See [CONTEXT_MAP.md](CONTEXT_MAP.md) for detailed context relationships.

### Bitboard Architecture

The chess engine uses **bitboards** for optimal performance:
- Each piece type/color stored as a 64-bit Long (1 bit per square)
- Fast move generation using bitwise operations
- Efficient occupancy and attack detection
- Memory layout: 12 bitboards (6 piece types × 2 colors) + metadata

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

## Technology Stack

- **Language**: Kotlin 1.9.22
- **Web Framework**: Ktor 2.3.7 (Netty engine)
- **Database**: PostgreSQL 16+ with HikariCP connection pooling
- **Database Access**: jOOQ 3.19.15 (type-safe SQL with Kotlin support)
- **Database Migrations**: Liquibase 4.29.2
- **Authentication**: JWT with auth0-jwt
- **Password Hashing**: BCrypt (jbcrypt 0.4)
- **Unique Identifiers**: ULID (Universally Unique Lexicographically Sortable Identifier)
- **Dependency Injection**: Koin 3.5.3
- **Build Tool**: Gradle with Kotlin DSL
- **Testing**: Kotest (unit/integration), ArchUnit (architecture), Testcontainers (integration tests)
- **JVM**: Java 21

## Development

### Project Structure
```
src/
├── main/kotlin/com/gchess/
│   ├── shared/                    # Shared Kernel
│   │   └── domain/model/          # PlayerId, GameId
│   ├── chess/                     # Chess Bounded Context
│   │   ├── domain/
│   │   │   ├── model/             # Game, ChessPosition, Move, etc.
│   │   │   ├── service/           # ChessRules
│   │   │   └── port/              # GameRepository, PlayerExistenceChecker
│   │   ├── application/usecase/   # CreateGame, MakeMove, GetGame
│   │   └── infrastructure/
│   │       └── adapter/
│   │           ├── driver/        # GameRoutes, DTOs
│   │           └── driven/        # PostgresGameRepository (jOOQ), UserContextPlayerChecker (ACL)
│   ├── user/                      # User Bounded Context
│   │   ├── domain/
│   │   │   ├── model/             # User, Credentials
│   │   │   └── port/              # UserRepository, PasswordHasher
│   │   ├── application/usecase/   # RegisterUser, Login, GetUser
│   │   └── infrastructure/
│   │       └── adapter/
│   │           ├── driver/        # AuthRoutes, UserRoutes, DTOs
│   │           └── driven/        # PostgresUserRepository (jOOQ), BcryptPasswordHasher
│   ├── matchmaking/               # Matchmaking Bounded Context
│   │   ├── domain/
│   │   │   ├── model/             # QueueEntry, Match
│   │   │   └── port/              # MatchRepository, MatchmakingQueue, GameCreator
│   │   ├── application/usecase/   # JoinMatchmaking, GetMatchStatus, LeaveMatchmaking
│   │   └── infrastructure/
│   │       └── adapter/
│   │           ├── driver/        # MatchmakingRoutes, DTOs
│   │           └── driven/        # PostgresMatchRepository, InMemoryMatchmakingQueue, ChessContextGameCreator (ACL)
│   └── infrastructure/
│       ├── config/                # Shared infrastructure (KoinModule, JwtConfig, DatabaseConfig)
│       └── persistence/jooq/      # Generated jOOQ code (Tables, Records)
├── main/resources/
│   ├── application.conf           # Application configuration
│   └── db/changelog/              # Liquibase database migrations
├── unitTest/kotlin/               # Unit tests
│   └── com/gchess/chess/
│       ├── domain/                # Chess domain tests
│       └── application/           # Chess use case tests
├── architectureTest/kotlin/       # Architecture tests
│   └── com/gchess/architecture/
│       ├── HexagonalArchitectureTest.kt    # Hexagonal architecture rules
│       └── BoundedContextTest.kt           # Context isolation rules
└── integrationTest/kotlin/        # E2E integration tests
    └── com/gchess/chess/integration/
        └── GameE2ETest.kt         # Full flow: Register → Login → Game → Moves
```

### Design Patterns Used
- **Bounded Contexts**: Chess and User contexts with clear boundaries
- **Shared Kernel**: Common value objects (PlayerId, GameId) shared across contexts
- **Anti-Corruption Layer**: UserContextPlayerChecker protects Chess context from User context changes
- **Hexagonal Architecture**: Ports and adapters within each bounded context
- **Domain Services**: ChessRules encapsulates complex business logic
- **Value Objects**: CastlingRights, Position, Move, PlayerId, GameId (immutable)
- **Repository Pattern**: Abstraction for data persistence
- **Use Case Pattern**: Each user action is a dedicated class
- **Dependency Inversion**: Domain defines interfaces, infrastructure implements
- **DTO Pattern**: Separation between domain models and API contracts

### Test Organization

Tests are organized into three separate source sets for clarity and focused execution:

**Unit Tests** (`src/unitTest/kotlin/`):
- Domain model tests (108+ tests)
- Chess rules implementation tests (move generation, checkmate, stalemate, draw rules)
- Use case tests
- Fast execution, run frequently during development

**Architecture Tests** (`src/architectureTest/kotlin/`):
- ArchUnit-based validation of hexagonal architecture and bounded contexts
- **Hexagonal Architecture Rules**:
  - Layer dependency rules (domain → application → infrastructure)
  - Framework independence checks
  - Naming convention enforcement
- **Bounded Context Isolation Rules**:
  - Chess domain cannot depend on User context
  - User domain cannot depend on Chess context
  - Only infrastructure can cross context boundaries (via ACL)
  - Shared Kernel accessible to all contexts
- Ensures architecture integrity and maintainability

**Integration Tests** (`src/integrationTest/kotlin/`):
- End-to-end API testing with Ktor test host
- **Testcontainers PostgreSQL**: Real database for integration tests (not H2/in-memory)
- Full authentication flow: Register → Login → JWT → Create Game → Make Moves
- Matchmaking flow: Join queue → Match → Automatic game creation
- Validates JWT authentication and authorization
- Game flow testing with turn validation
- Database persistence and retrieval testing
- Ensures DTOs properly serialize/deserialize domain models
- Verifies Anti-Corruption Layer works correctly across contexts
- Automatic database cleanup between tests (TRUNCATE)

Run all tests with `./gradlew check` or run each category independently.

### Architecture Testing with ArchUnit

The project includes **automated architecture tests** using ArchUnit to enforce both hexagonal architecture and bounded context isolation:

**Hexagonal Architecture Rules** (`HexagonalArchitectureTest.kt`):
- **Layer Dependencies**: Domain layer has zero dependencies on infrastructure/application
- **Framework Independence**: Domain is free from Ktor, Koin, and serialization dependencies
- **Naming Conventions**: UseCase suffix, Repository suffix, consistent naming
- **Package Structure**: Proper organization of domain/application/infrastructure

**Bounded Context Rules** (`BoundedContextTest.kt`):
- **Context Isolation**: Chess and User contexts are independent
- **ACL Enforcement**: Only infrastructure layer can cross context boundaries
- **Shared Kernel**: Value objects accessible to all contexts
- **No Cross-Context Dependencies**: Domain and application layers remain isolated

These tests run automatically with `./gradlew check` and fail the build if architecture rules are violated, ensuring the codebase remains clean, maintainable, and properly isolated.

### Running Tests with Coverage
```bash
./gradlew check jacocoTestReport
```

## Environment Variables

The application supports the following environment variables for configuration:

### Database Configuration

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/gchess_dev` | No |
| `DATABASE_USER` | Database username | `gchess` | No |
| `DATABASE_PASSWORD` | Database password | `gchess` | No |
| `DATABASE_POOL_SIZE` | HikariCP connection pool size | `10` | No |

### JWT Configuration

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `JWT_SECRET` | Secret key for JWT signing (min 256 bits) | Development default | **YES** (production) |
| `JWT_VALIDITY_MS` | Token validity in milliseconds | `86400000` (24h) | No |

### Server Configuration

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `PORT` | HTTP server port | `8080` | No |

**⚠️ Production Security Notes:**
- `JWT_SECRET` **MUST** be set to a secure random value in production
- Use `openssl rand -base64 32` to generate a strong secret
- Never commit secrets to version control
- Consider using a secrets management system (e.g., HashiCorp Vault, AWS Secrets Manager)

## Current Limitations

- No token refresh mechanism (JWT expires after 24 hours by default)
- No WebSocket support for real-time updates (matchmaking requires polling)
- Matchmaking uses FIFO only (no ELO/skill-based matching)
- No game history or replay functionality
- Draw by mutual agreement not yet implemented (requires player interaction/API endpoint)
- No rate limiting on API endpoints

## Future Enhancements

- [x] ~~Add persistent storage (database)~~ ✅ Completed (PostgreSQL + jOOQ + Liquibase)
- [ ] Add move history with algebraic notation (e.g., "Nf3", "O-O")
- [ ] Implement draw by mutual agreement
- [ ] WebSocket support for real-time games
- [ ] Chess clock/timer functionality
- [ ] Game replay and analysis features
- [ ] ELO rating system for matchmaking
- [ ] Opening book and endgame tablebase integration
- [ ] Move suggestion and hints functionality
- [ ] API rate limiting and throttling

## Contributing

Contributions are welcome! This project is open source and follows Domain-Driven Design principles.

### How to contribute

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes following the existing architecture patterns
4. Ensure all tests pass (`./gradlew check`)
5. Commit your changes with descriptive messages
6. Push to your branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Architecture Guidelines

- Follow hexagonal architecture (ports and adapters)
- Respect bounded context isolation
- Domain layer must remain framework-agnostic
- Add tests for new features (unit + integration)
- Run architecture tests to validate compliance

See [CLAUDE.md](CLAUDE.md) and [CONTEXT_MAP.md](CONTEXT_MAP.md) for detailed architecture documentation.

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

### What this means

✅ **You are free to:**
- Use this software for any purpose (personal, commercial, educational)
- Modify and adapt the code
- Distribute copies
- Sublicense and sell copies

⚠️ **Conditions:**
- Include the original copyright notice and license in any copies
- The software is provided "as is" without warranty

**TL;DR**: Do whatever you want with this code, just keep the license notice. 🎉
