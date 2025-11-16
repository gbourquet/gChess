# gChess

A chess game application built with Kotlin, Ktor, and hexagonal architecture.

## Features

- Create and manage chess games
- Make moves with algebraic notation
- RESTful API for game operations
- Clean architecture with hexagonal design

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

```bash
./gradlew test
```

## API Usage

### Create a new game
```bash
curl -X POST http://localhost:8080/api/games
```

### Get game state
```bash
curl http://localhost:8080/api/games/{gameId}
```

### Make a move
```bash
curl -X POST http://localhost:8080/api/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -d '{"from": "e2", "to": "e4"}'
```

## Architecture

The project follows hexagonal architecture (ports and adapters):

- **Domain**: Pure business logic (chess rules, game state)
- **Application**: Use cases and orchestration
- **Infrastructure**: Adapters for HTTP, persistence, etc.

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

## Technology Stack

- Kotlin 1.9.22
- Ktor 2.3.7
- Koin 3.5.3
- Gradle with Kotlin DSL

## License

Private project
