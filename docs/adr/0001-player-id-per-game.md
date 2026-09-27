# A PlayerId per Game instead of (UserId, GameId)

A Player is the participation of one User in one Game. Rather than identifying it by the composite key (UserId, GameId), each Player gets its own PlayerId, generated when the Game is created and stored with it. Every per-game reference (WebSocket game connections, moves, timeout claims) uses a single identifier, and the Chess context never needs to handle UserIds.

## Consequences

- A User playing several Games at once has one PlayerId per Game; game WebSocket connections are indexed by PlayerId, matchmaking ones by UserId.
- Going from a PlayerId back to a User goes through the Game, which holds both.
