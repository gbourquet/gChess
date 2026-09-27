# Bounded contexts isolated behind anti-corruption layers

Chess, User and Matchmaking live in one deployable but are kept as isolated as if they were separate services, so that one can be extracted into a microservice when scaling requires it. A context reaches another only through a port it declares itself, implemented by an adapter in `infrastructure/`. Only primitives and shared-kernel identifiers cross a boundary: Matchmaking sends a time control as two integers, not the Chess `TimeControl`, and Chess receives a username, never a `User`. ArchUnit tests (`architectureTest`) fail the build on any direct dependency.

## Consequences

- Some things are duplicated across contexts (e.g. time-control parameters in Matchmaking and Chess) instead of shared.
- Extracting a context means replacing its ACL adapters with remote calls; the domain and application layers stay unchanged.
