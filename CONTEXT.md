# gChess back

Server of an online chess platform: users register, get paired through a queue and play timed or untimed games against each other in real time, while others may watch.

## Language

### Identity

**User**:
A person with a permanent account on the platform, identified by a **UserId**.
_Avoid_: Account, member

**Player**:
A User's participation in one specific Game, on one Side, identified by a **PlayerId** that exists only for that Game.
_Avoid_: Participant, user (when talking about a Game)

**Side**:
White or Black: which pieces a Player controls.
_Avoid_: Color, team

**Spectator**:
Someone watching a Game without being one of its two Players.
_Avoid_: Observer, viewer

### Games

**Game**:
One chess game between exactly two Players, from the initial position to its Outcome.
_Avoid_: Match (reserved for Matchmaking), party

**Square**:
One of the 64 cells of the board, named by file and rank (e.g. e4).
_Avoid_: Position, cell, case

**Position**:
The full state of the board at a given moment: where every piece stands, whose Side is to move, castling and en-passant rights.
_Avoid_: Board, board state, FEN (FEN is one notation of a Position)

**Move**:
A piece going from one Square to another, optionally with a promotion piece.
_Avoid_: Ply, turn

**Outcome**:
How a finished Game ended: Checkmate, Stalemate, Draw, Resignation or Timeout. Checkmate, Resignation and Timeout have a winning Side; Stalemate and Draw do not.
_Avoid_: Result, end state

**Draw**:
A finished Game with no winner, reached by agreement, fifty-move rule, threefold repetition, insufficient material, or a Timeout against an opponent who cannot checkmate.
_Avoid_: Tie

**Draw offer**:
A proposal by one Side to end the Game as a Draw, pending until the opponent accepts or rejects it.
_Avoid_: Draw request, draw proposal

**Resignation**:
A Player conceding the Game, which the opponent wins.
_Avoid_: Forfeit, abandon

### Time

**Time control**:
The clock rules of a Game: a base time per Player plus an increment added after each of their Moves (Fischer). A base time of zero means the Game is **untimed**.
_Avoid_: Cadence, time format

**Clock**:
The time a Player has left in a timed Game.
_Avoid_: Timer, remaining time

**Free move**:
Each Side's first Move, which consumes no Clock time; a Player's Clock only starts running after their own first Move.
_Avoid_: Grace move

**Timeout**:
A Player's Clock reaching zero: they lose the Game, unless the opponent no longer has the material to checkmate, in which case it is a Draw.
_Avoid_: Flag fall, time forfeit

**Timeout claim**:
A request by the Player waiting for the opponent's move to have the opponent declared out of time.
_Avoid_: Flag claim

### Matchmaking

**Queue**:
The list of Users waiting for an opponent, each with the Time control they want.
_Avoid_: Lobby, waiting list

**Match**:
Two Users from the Queue who want the same Time control, paired in order of arrival; their Sides are assigned at random and a Game is created for them.
_Avoid_: Pairing; never use Match to mean a Game
