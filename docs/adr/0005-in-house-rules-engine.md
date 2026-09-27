# The chess rules engine is written in-house

Move generation and game-ending detection (bitboards, FIDE rules, threefold repetition via Zobrist hashing) are implemented in the Chess domain instead of relying on an existing chess library. It is a deliberate learning investment, and it keeps the domain free to evolve (variants, custom rules) without being tied to a third-party model.

## Consequences

- Correctness of the rules is our responsibility; the unit tests on `StandardChessRules` are the safety net.
- Do not replace it with a library without revisiting this decision.
