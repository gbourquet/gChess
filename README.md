# gChess

A high-performance chess engine built with Kotlin, featuring bitboard-based move generation and hexagonal architecture.

## Features

### Chess Engine
- ⚡ **Bitboard-based architecture** for efficient move generation and position evaluation
- ♟️ **Complete FIDE chess rules implementation**:
  - All piece movements (Pawn, Knight, Bishop, Rook, Queen, King)
  - Special moves: Castling (kingside & queenside), En passant, Pawn promotion
  - Check detection and pinned piece handling
  - Move validation ensuring king safety
- 📊 **FEN notation support** for position import/export
- 🎯 **Legal move generation** with full rule compliance

### API & Architecture
- 🌐 RESTful API for game operations
- 🏗️ Hexagonal architecture (ports and adapters)
- 🧪 Comprehensive test coverage (67+ tests)
- 💉 Dependency injection with Koin
- 📦 In-memory game storage

## Quick Start

### Prerequisites

- Java 21 or higher
- Gradle (wrapper included)

### Running the Application

```bash
./gradlew run
```

The server will start on `http://localhost:8080`

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

Run specific test class:
```bash
./gradlew unitTest --tests "com.gchess.domain.service.StandardChessRulesTest"
```

## API Usage

### Create a new game
```bash
curl -X POST http://localhost:8080/api/games
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "board": {...},
  "currentPlayer": "WHITE",
  "status": "IN_PROGRESS",
  "moveHistory": []
}
```

### Get game state
```bash
curl http://localhost:8080/api/games/{gameId}
```

### Make a move
```bash
# Simple pawn move
curl -X POST http://localhost:8080/api/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -d '{"from": "e2", "to": "e4"}'

# Pawn promotion to queen
curl -X POST http://localhost:8080/api/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -d '{"from": "e7", "to": "e8", "promotion": "QUEEN"}'

# Castling (just move the king 2 squares)
curl -X POST http://localhost:8080/api/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -d '{"from": "e1", "to": "g1"}'
```

## Chess Rules Implementation

| Feature | Status | Description |
|---------|--------|-------------|
| Basic Moves | ✅ | All piece types (Pawn, Knight, Bishop, Rook, Queen, King) |
| Pawn Promotion | ✅ | Promote to Queen, Rook, Bishop, or Knight |
| Castling | ✅ | Kingside and queenside for both colors |
| En Passant | ✅ | Special pawn capture |
| Check Detection | ✅ | Validates king safety |
| Pinned Pieces | ✅ | Pieces that cannot move without exposing king |
| FEN Support | ✅ | Import/export positions via Forsyth-Edwards Notation |
| Checkmate | ⏳ | Detection not yet implemented |
| Stalemate | ⏳ | Detection not yet implemented |

## Architecture

The project follows **hexagonal architecture** (ports and adapters) with Domain-Driven Design principles:

```
┌─────────────────────────────────────────────────┐
│           Infrastructure Layer                  │
│  (HTTP Routes, Repositories, Framework)         │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │        Application Layer                  │ │
│  │  (Use Cases: CreateGame, MakeMove, etc.) │ │
│  │                                           │ │
│  │  ┌─────────────────────────────────────┐ │ │
│  │  │       Domain Layer                  │ │ │
│  │  │  (Chess Rules, Game Logic, Models)  │ │ │
│  │  │                                     │ │ │
│  │  │  • ChessPosition (Bitboards)        │ │ │
│  │  │  • StandardChessRules (Service)     │ │ │
│  │  │  • CastlingRights (Value Object)    │ │ │
│  │  └─────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

### Key Components

**Domain Layer** (`com.gchess.domain`):
- `model/`: Core entities (Game, ChessPosition, Piece, Move, CastlingRights)
- `service/`: Domain services (ChessRules interface, StandardChessRules implementation)
- `port/`: Interfaces (GameRepository)

**Application Layer** (`com.gchess.application`):
- Use cases: CreateGameUseCase, GetGameUseCase, MakeMoveUseCase

**Infrastructure Layer** (`com.gchess.infrastructure`):
- Input adapters: GameRoutes (REST API)
- Output adapters: InMemoryGameRepository
- Configuration: Koin dependency injection

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
- **Dependency Injection**: Koin 3.5.3
- **Build Tool**: Gradle with Kotlin DSL
- **Testing**: Kotest, ArchUnit (architecture tests)
- **JVM**: Java 21

## Development

### Project Structure
```
src/
├── main/kotlin/com/gchess/
│   ├── domain/            # Core business logic
│   │   ├── model/         # Entities and value objects
│   │   ├── service/       # Domain services
│   │   └── port/          # Interface definitions
│   ├── application/       # Use cases
│   └── infrastructure/    # Adapters and config
├── unitTest/kotlin/       # Unit test suites
│   └── com/gchess/
│       ├── domain/        # Domain tests (models, services)
│       ├── application/   # Application tests (use cases)
│       └── infrastructure/# Infrastructure tests
└── architectureTest/kotlin/ # Architecture tests
    └── com/gchess/architecture/
        └── HexagonalArchitectureTest.kt
```

### Design Patterns Used
- **Domain Services**: ChessRules encapsulates complex business logic
- **Value Objects**: CastlingRights, Position, Move (immutable)
- **Repository Pattern**: Abstraction for data persistence
- **Use Case Pattern**: Each user action is a dedicated class
- **Dependency Inversion**: Domain defines interfaces, infrastructure implements

### Test Organization

Tests are organized into two separate source sets for clarity and focused execution:

**Unit Tests** (`src/unitTest/kotlin/`):
- Domain model tests (67+ tests)
- Chess rules implementation tests
- Use case tests
- Fast execution, run frequently during development

**Architecture Tests** (`src/architectureTest/kotlin/`):
- ArchUnit-based validation of hexagonal architecture
- Layer dependency rules
- Framework independence checks
- Naming convention enforcement
- Package structure validation

Run all tests with `./gradlew check` or run each category independently.

### Architecture Testing with ArchUnit

The project includes **automated architecture tests** using ArchUnit to enforce hexagonal architecture principles:

- **Layer Dependencies**: Domain layer has zero dependencies on infrastructure/application
- **Framework Independence**: Domain is free from Ktor, Koin, and serialization dependencies
- **Naming Conventions**: UseCase suffix, Repository suffix, consistent naming
- **Package Structure**: Proper organization of domain/application/infrastructure

These tests run automatically with `./gradlew check` and fail the build if architecture rules are violated, ensuring the codebase remains clean and maintainable.

### Running Tests with Coverage
```bash
./gradlew check jacocoTestReport
```

## Current Limitations

- Checkmate and stalemate detection not yet implemented
- In-memory storage only (games lost on restart)
- No authentication or player management
- No WebSocket support for real-time updates

## Future Enhancements

- [ ] Implement checkmate/stalemate detection
- [ ] Add persistent storage (database)
- [ ] Implement threefold repetition and fifty-move rule
- [ ] Add move history with algebraic notation (e.g., "Nf3", "O-O")
- [ ] WebSocket support for real-time games
- [ ] Player authentication and game sessions
- [ ] Chess clock/timer functionality
- [ ] Opening book and endgame tablebase integration

## Contributing

This is a private project. For questions or suggestions, please contact the maintainer.

## License

Private project - All rights reserved
