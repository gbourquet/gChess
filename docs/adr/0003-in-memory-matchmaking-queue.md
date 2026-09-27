# The matchmaking Queue is kept in memory

Users and Games are stored in PostgreSQL, but the Queue is not. It is hot data that has no value after a restart: matchmaking is the start of a Game, and it either succeeds or fails; a half-done Match cannot be resumed. Persisting it would add writes and recovery logic for no benefit.

## Consequences

- A restart empties the Queue; waiting Users must join again (their WebSocket is dropped anyway).
- The Queue lives in one process, so running several back instances would need a shared queue. This is the first thing to revisit when scaling out (see ADR-0002).
