# The server is the only authority on Clocks

Clients cannot be trusted with their own time. The server computes each Clock from the time at which it *received* each Move (`game_moves.received_at`) and ignores any time reported by the client. There is no server-side timer: a Timeout is detected when a Move is attempted after the Clock has run out, or when the waiting Player sends a Timeout claim, which the server checks against its own computation.

Each Side's first Move is free, and a Player's Clock only starts after their own first Move, the same rule as Lichess and chess.com.

## Consequences

- A Game in which the Player to move goes silent and the opponent never claims stays in progress indefinitely.
- Network latency counts against the Player who is moving, since only the time of receipt is known.
