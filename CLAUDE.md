# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

gChess is a chess game application built with Kotlin, using Ktor as the web framework and Koin for dependency injection. The project follows hexagonal architecture (ports and adapters) principles to maintain clean separation of concerns.

## Technology Stack

- **Language**: Kotlin 1.9.22
- **Web Framework**: Ktor 2.3.7
- **Dependency Injection**: Koin 3.5.3
- **Build Tool**: Gradle (Kotlin DSL)
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

### Run all tests
```bash
./gradlew check
```

### Run a specific test class
```bash
./gradlew unitTest --tests "com.gchess.domain.model.ChessPositionTest"
```

### Clean build artifacts
```bash
./gradlew clean
```

## Architecture

### Hexagonal Architecture (Ports and Adapters)

The codebase is organized following hexagonal architecture principles:

**Domain Layer** (`com.gchess.domain`):
- `model/`: Core business entities (Game, ChessPosition, Piece, Move, Position, Color, PieceType, GameStatus)
- `port/`: Interfaces defining contracts (GameRepository)
- `service/`: Domain services encapsulating business logic
  - `ChessRules`: Interface defining chess rules (move generation, validation, check detection, checkmate/stalemate)
  - `StandardChessRules`: FIDE-compliant implementation using bitboard techniques for efficient move generation
- No dependencies on external frameworks - pure business logic

**Application Layer** (`com.gchess.application`):
- `usecase/`: Application use cases orchestrating domain logic
  - `CreateGameUseCase`: Creates a new chess game
  - `GetGameUseCase`: Retrieves a game by ID
  - `MakeMoveUseCase`: Validates and executes a move
- Depends only on domain layer

**Infrastructure Layer** (`com.gchess.infrastructure`):
- `adapter/input/`: Entry points (REST API routes)
  - `GameRoutes.kt`: HTTP endpoints for game operations
- `adapter/output/`: External integrations and implementations
  - `InMemoryGameRepository`: In-memory implementation of GameRepository
- `config/`: Framework configuration
  - `KoinModule.kt`: Dependency injection wiring

### Key Design Patterns

- **Domain Services**: Business logic that doesn't belong to a single entity (e.g., ChessRules)
- **Value Objects**: Immutable objects defined by their attributes (e.g., CastlingRights, Position, Move)
- **Dependency Inversion**: Domain layer defines interfaces; infrastructure provides implementations
- **Use Case Pattern**: Each user action is represented by a dedicated use case class
- **Repository Pattern**: Data persistence abstraction through GameRepository interface
- **Immutability**: Domain models are immutable data classes
- **Encapsulation**: Domain concepts like castling rights are encapsulated in dedicated classes with business methods

### Data Flow

```
HTTP Request → GameRoutes (Input Adapter) → Use Case → Domain Logic → Repository (Output Adapter)
```

## Domain Model

- **Position**: Chess board position (file: 0-7, rank: 0-7), supports algebraic notation (e.g., "e4")
- **Piece**: Chess piece with type, color, and movement tracking
- **ChessPosition**: Bitboard-based chess position representation using 64-bit integers for efficient board state and move generation. Supports FEN notation import/export, castling rights, en passant tracking, and halfmove/fullmove counters
- **CastlingRights**: Value Object encapsulating castling availability for both players (kingside and queenside). Provides domain methods like `canCastleKingside(color)` and FEN conversion
- **Move**: Represents a chess move from one position to another, with optional promotion
- **Game**: Complete game state including ChessPosition, current player, status, and move history
- **Color**: WHITE or BLACK
- **PieceType**: PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING
- **GameStatus**: IN_PROGRESS, CHECK, CHECKMATE, STALEMATE, DRAW

## Bitboard Architecture

The chess engine uses bitboards for efficient position representation and move generation:

- **Bitboard Representation**: Each piece type/color combination is stored as a 64-bit Long where each bit represents a square (bit 0 = a1, bit 63 = h8)
- **Efficient Operations**: Bitwise operations enable fast move generation, occupancy checks, and attack detection
- **Memory Layout**: ChessPosition contains 12 bitboards (6 piece types × 2 colors) plus metadata (castling rights, en passant, etc.)
- **FEN Support**: Full support for Forsyth-Edwards Notation (FEN) for position import/export via `String.toChessPosition()` and `ChessPosition.toFen()`
- **Move Generation**: The `ChessPosition.getLegalMoves()` method delegates to `StandardChessRules` for efficient legal move generation

## API Endpoints

The application exposes a REST API on port 8080:

- `POST /api/games` - Create a new game
- `GET /api/games/{id}` - Get game state by ID
- `POST /api/games/{id}/moves` - Make a move
  - Request body: `{"from": "e2", "to": "e4"}`

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

## Current Limitations

- Checkmate and stalemate detection not yet implemented (interface exists)
- In-memory storage only - games are lost on server restart
- No authentication or player management
- No WebSocket support for real-time updates (though Ktor WebSocket dependency is included)

## Architecture Testing

The project uses **ArchUnit** to enforce hexagonal architecture rules automatically. These tests ensure:

### Layer Dependency Rules
- ✅ Domain layer has **no dependencies** on application or infrastructure layers
- ✅ Domain layer is **framework-agnostic** (no Ktor, Koin, or serialization dependencies)
- ✅ Application layer depends **only on domain** (and standard Kotlin libraries)
- ✅ Infrastructure can access application and domain
- ✅ Dependencies flow **inward** toward the domain

### Naming Conventions
- ✅ Use cases end with `UseCase`
- ✅ Repository interfaces end with `Repository`
- ✅ Domain services end with `Rules` or `Service`

### Package Organization
- ✅ Domain services are interfaces or implementations of domain interfaces
- ✅ Ports (repository interfaces) reside in `domain.port` package
- ✅ Adapters (implementations) reside in `infrastructure` layer
- ✅ Domain models (entities, value objects) reside in `domain.model` package

Run architecture tests:
```bash
./gradlew architectureTest
```

## Development Notes

- The project uses Kotlin data classes for immutability
- Bitboard-based chess engine for efficient move generation and position evaluation
- All domain logic is in pure Kotlin with no framework dependencies
- Koin is used for dependency injection - see `KoinModule.kt` for wiring
- The application entry point is `com.gchess.Application.kt`
- Ktor runs on Netty engine listening on port 8080
- ChessRules domain service follows Domain-Driven Design principles (domain service pattern)
