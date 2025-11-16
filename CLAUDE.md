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

### Run tests
```bash
./gradlew test
```

### Run a specific test class
```bash
./gradlew test --tests "com.gchess.domain.model.BoardTest"
```

### Clean build artifacts
```bash
./gradlew clean
```

## Architecture

### Hexagonal Architecture (Ports and Adapters)

The codebase is organized following hexagonal architecture principles:

**Domain Layer** (`com.gchess.domain`):
- `model/`: Core business entities (Game, Board, Piece, Move, Position, Color, PieceType, GameStatus)
- `port/`: Interfaces defining contracts (GameRepository, MoveValidator)
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
  - `BasicMoveValidator`: Basic move validation (TODO: implement full chess rules)
- `config/`: Framework configuration
  - `KoinModule.kt`: Dependency injection wiring

### Key Design Patterns

- **Dependency Inversion**: Domain layer defines interfaces; infrastructure provides implementations
- **Use Case Pattern**: Each user action is represented by a dedicated use case class
- **Repository Pattern**: Data persistence abstraction through GameRepository interface
- **Immutability**: Domain models are immutable data classes

### Data Flow

```
HTTP Request → GameRoutes (Input Adapter) → Use Case → Domain Logic → Repository (Output Adapter)
```

## Domain Model

- **Position**: Chess board position (file: 0-7, rank: 0-7), supports algebraic notation (e.g., "e4")
- **Piece**: Chess piece with type, color, and movement tracking
- **Board**: 8x8 chess board with piece positioning
- **Move**: Represents a chess move from one position to another, with optional promotion
- **Game**: Complete game state including board, current player, status, and move history
- **Color**: WHITE or BLACK
- **PieceType**: PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING
- **GameStatus**: IN_PROGRESS, CHECK, CHECKMATE, STALEMATE, DRAW

## API Endpoints

The application exposes a REST API on port 8080:

- `POST /api/games` - Create a new game
- `GET /api/games/{id}` - Get game state by ID
- `POST /api/games/{id}/moves` - Make a move
  - Request body: `{"from": "e2", "to": "e4"}`

## Current Limitations

- Move validation is basic - full chess rules (en passant, castling, check/checkmate detection) are not yet implemented
- In-memory storage only - games are lost on server restart
- No authentication or player management
- No WebSocket support for real-time updates (though Ktor WebSocket dependency is included)

## Development Notes

- The project uses Kotlin data classes for immutability
- All domain logic is in pure Kotlin with no framework dependencies
- Koin is used for dependency injection - see `KoinModule.kt` for wiring
- The application entry point is `com.gchess.Application.kt`
- Ktor runs on Netty engine listening on port 8080
