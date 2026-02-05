# UCI Protocol Usage Guide

The **Universal Chess Interface (UCI)** is the standard protocol for chess engines. This document explains how to use the Pure Functional Chess Engine via UCI.

## Protocol Overview

UCI is a text-based protocol where:
- **GUI** sends commands to the engine
- **Engine** responds with info and best move
- Communication happens via stdin/stdout
- All commands are ASCII text ending with newline

## Starting the Engine

```bash
sbt run
```

Or after building:
```bash
java -cp target/scala-3.3.1/classes main
```

The engine will wait for UCI commands from stdin.

## Engine Information

When you send the `uci` command:

```
> uci
< id name "Pure Functional Chess Engine"
< id author "Scala 3 FP"
< option name Depth type spin default 4 min 1 max 20
< uciok
```

**Response Fields:**
- `id name`: Engine identification
- `id author`: Author attribution
- `option`: Configurable parameters (future enhancement)
- `uciok`: Ready to receive commands

## Commands Reference

### 1. `uci` - Engine Identification

**Purpose:** Initialize the engine and request identification

**Syntax:**
```
uci
```

**Example:**
```
> uci
< id name "Pure Functional Chess Engine"
< id author "Scala 3 FP"
< uciok
```

**Response:**
- `id name <string>`: Engine name
- `id author <string>`: Creator/author
- `option`: Configuration options (optional)
- `uciok`: Handshake complete

---

### 2. `isready` - Ping/Pong

**Purpose:** Check if engine is responsive

**Syntax:**
```
isready
```

**Example:**
```
> isready
< readyok
```

**Response:**
- `readyok`: Engine is responsive

**Use Case:** GUI can verify engine responsiveness without blocking

---

### 3. `position` - Set Board State

**Purpose:** Set up the board position for analysis or play

**Syntax:**
```
position startpos [moves <move1> <move2> ...]
position fen <fenstring> [moves <move1> <move2> ...]
```

**Parameters:**
- `startpos`: Standard starting position
- `fen <fenstring>`: Custom position in FEN notation
- `moves`: Moves to apply from the base position (optional)

**Examples:**

**Starting Position:**
```
> position startpos
```

**After 1.e4:**
```
> position startpos moves e2e4
```

**After 1.e4 c5 (Sicilian Defense):**
```
> position startpos moves e2e4 c7c5
```

**Custom Position:**
```
> position fen rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2 moves d2d4
```

**FEN Components:**
```
rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
                                              │  │     │ │ │ │
                                              │  │     │ │ │ └─ Fullmove number
                                              │  │     │ │ └─── Halfmove clock
                                              │  │     │ └───── En passant target
                                              │  │     └─────── Castling rights
                                              │  └───────────── Active color
                                              └──────────────── Piece placement
```

---

### 4. `go` - Start Search

**Purpose:** Start engine search and return best move

**Syntax:**
```
go [depth <d>] [wtime <t>] [btime <t>] [movestogo <m>]
```

**Parameters:**
- `depth <d>`: Fixed search depth (default: 4, range: 1-20)
- `wtime <t>`: White's remaining time in milliseconds (optional)
- `btime <t>`: Black's remaining time in milliseconds (optional)
- `movestogo <m>`: Moves until next time control (optional)
- No parameters: Uses default depth 4

**Examples:**

**Depth-Limited Search (6 plies):**
```
> go depth 6
< info depth 1 score cp 20 pv e2e4
< info depth 2 score cp 20 pv e2e4 e7e5
< info depth 3 score cp 35 pv e2e4 c7c5 g1f3
< info depth 4 score cp 20 pv e2e4 e7e5 g1f3 b8c6
< info depth 5 score cp 15 pv e2e4 e7e5 g1f3 g8f6 f1c4
< info depth 6 score cp 25 pv e2e4 e7e5 g1f3 b8c6 f1b5 a7a6
< bestmove e2e4 ponder e7e5
```

**Time-Based Search (White has 5 minutes, Black has 3):**
```
> go wtime 300000 btime 180000 movestogo 40
< bestmove e2e4 ponder e7e5
```

**Quick Search (Depth 2):**
```
> go depth 2
< info depth 1 score cp 20 pv e2e4
< info depth 2 score cp 20 pv e2e4 e7e5
< bestmove e2e4 ponder e7e5
```

**Response Format:**

```
info [depth <d>] [seldepth <d>] [score <s>] [nodes <n>] [time <t>] [pv <move1> <move2> ...]
```

**Info Fields:**
- `depth <d>`: Current search depth
- `score cp <n>`: Score in centipawns (100cp = 1 pawn value)
  - Positive: White advantage
  - Negative: Black advantage
- `score mate <n>`: Mate in N moves (positive N means white mates)
- `pv <moves>`: Principal variation (best line found)
- `nodes <n>`: Nodes searched
- `time <t>`: Milliseconds elapsed
- `nps <n>`: Nodes per second

**Final Response:**
```
bestmove <move> [ponder <move>]
```

- `bestmove`: The recommended move in UCI notation
- `ponder`: Optional predicted opponent response (for GUI optimization)

---

### 5. `stop` - Stop Search

**Purpose:** Halt the current search and return best move found so far

**Syntax:**
```
stop
```

**Example:**
```
> position startpos
> go depth 20
< info depth 1 score cp 20 pv e2e4
< info depth 2 score cp 20 pv e2e4 e7e5
> stop
< bestmove e2e4 ponder e7e5
```

**Behavior:**
- Returns immediately with best move found
- Completes current depth evaluation before returning
- Returns in legal UCI format

---

### 6. `quit` - Shutdown

**Purpose:** Terminate the engine

**Syntax:**
```
quit
```

**Example:**
```
> quit
```

**Behavior:**
- Engine shuts down cleanly
- All resources released
- Process exits with code 0

---

## Complete Game Example

Here's a complete example of a game session:

```
> uci
< id name "Pure Functional Chess Engine"
< id author "Scala 3 FP"
< uciok

> isready
< readyok

> position startpos
> go depth 4
< info depth 1 score cp 20 pv e2e4
< info depth 2 score cp 20 pv e2e4 e7e5
< info depth 3 score cp 35 pv e2e4 c7c5 g1f3
< info depth 4 score cp 20 pv e2e4 e7e5 g1f3 b8c6
< bestmove e2e4 ponder e7e5

> position startpos moves e2e4 c7c5
> go depth 4
< info depth 1 score cp 30 pv g1f3
< info depth 2 score cp 28 pv g1f3 d7d6
< info depth 3 score cp 42 pv g1f3 g8f6 d2d4
< info depth 4 score cp 35 pv g1f3 g8f6 d2d4 c5d4
< bestmove g1f3 ponder g8f6

> position startpos moves e2e4 c7c5 g1f3 d7d6
> go depth 5
< info depth 1 score cp 40 pv d2d4
< info depth 2 score cp 38 pv d2d4 c5d4
< info depth 3 score cp 45 pv d2d4 c5d4 g1f3 wait...
< info depth 4 score cp 42 pv d2d4 c5d4 f3d4 g8f6
< info depth 5 score cp 48 pv d2d4 c5d4 f3d4 g8f6 b1c3
< bestmove d2d4 ponder c5d4

> quit
```

## Move Notation

The engine uses **standard algebraic notation (SAN)** converted to **UCI format**:

```
e2e4   → Pawn from e2 to e4
g1f3   → Knight from g1 to f3
e8g8   → King from e8 to g8 (kingside castling)
e8c8   → King from e8 to c8 (queenside castling)
e7e8q  → Pawn from e7 to e8 promotes to Queen
e7e8n  → Pawn from e7 to e8 promotes to Knight
```

**Promotion Codes:**
- `q`: Queen
- `r`: Rook
- `b`: Bishop
- `n`: Knight

## Score Interpretation

**Centipawn (cp) Scores:**
```
0      → Equal position
50     → White slight advantage (~0.5 pawn)
100    → White advantage (1 pawn)
200    → White significant advantage (2 pawns)
300+   → White winning
-100   → Black advantage (1 pawn)
-200   → Black significant advantage (2 pawns)
```

**Mate Scores:**
```
mate 3   → White mates in 3 moves
mate -2  → Black mates in 2 moves
mate 1   → Checkmate next move
```

## Using with Chess GUIs

### Arena Chess GUI
1. Tools → Engines → New
2. Select engine executable (sbt run)
3. Protocol: UCI
4. Configure options as needed
5. Play → Human vs Computer

### UCI2XBoard Bridge
```bash
# If using xboard/WinBoard that needs UCI2XBoard:
uci2wb.exe pure-functional-chess
```

### ChessTempo
1. Upload engine executable
2. Select UCI protocol
3. Test with depth 4-6 for faster games
4. Play via web interface

### Lichess API Bot
```python
import chess
import subprocess

engine = subprocess.Popen(
    ['sbt', 'run'],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    text=True
)

def get_move(board):
    engine.stdin.write(f"position fen {board.fen()}\n")
    engine.stdin.write("go depth 6\n")
    engine.stdin.flush()
    # Parse bestmove response
    ...
```

## Troubleshooting

### Engine hangs on `go` command
- Ensure `position` command was sent first
- Check that move list is valid (no illegal moves)
- Try reducing depth (go depth 2) to verify responsiveness
- Send `stop` to interrupt

### Invalid FEN string
- Verify FEN syntax: piece placement / active color / castling / en passant / halfmove / fullmove
- Check piece placement uses standard notation (p,n,b,r,q,k for black; uppercase for white)
- Test with standard startpos first

### Bad move response
- Verify moves are in legal UCI format (e.g., e2e4, not e4)
- Check that position was set before go command
- Ensure no illegal moves in move list
- Try: `position startpos moves e2e4` then `go depth 2`

### Engine too slow
- Reduce search depth: `go depth 3` or `go depth 4`
- Use time limits if iterative deepening implemented: `go wtime 30000 btime 30000`
- Monitor CPU usage (should be near 100% during search)

## Advanced Options

### Future Enhancements
The engine framework supports configuration via:
```
setoption name <optionname> value <optionvalue>
```

**Planned Options:**
- `Depth`: Default search depth (current: 4, range: 1-20)
- `HashSize`: Transposition table size in MB (future)
- `Threads`: Parallel search threads (future)
- `OpeningBook`: Enable/disable opening book (future)

## Performance Notes

**Search Speed:**
- ~500k-1M nodes per second on modern hardware
- ~230 microseconds for full initial position move generation
- Quiescence search reduces tactical blunders
- Transposition table caches ~50% of evaluations

**Memory Usage:**
- Base engine: ~100 MB
- Transposition table: 16 MB default
- Move generation: Zero-allocation on search hit

**Time Management:**
- Default depth 4: ~100 ms response
- Depth 5: ~500 ms response
- Depth 6: ~2-3 seconds response
- Scales non-linearly (branching factor ~35)

See [project_progress.md](project_progress.md) for detailed benchmarking results.

## References

- [UCI Protocol Specification](http://www.shredderchess.com/chess-features/uci-protocol.html)
- [Arena Chess GUI](http://www.playwitharena.com/)
- [Chess Programming Wiki - UCI](https://www.chessprogramming.org/UCI)

## Examples Included

See the test suite for comprehensive examples:
- `src/test/scala/FenParserTest.scala` - FEN parsing examples
- `src/test/scala/properties/` - Roundtrip preservation tests
- `src/test/scala/IntegrationTest.scala` - Full pipeline examples

---

For more information, see [README.md](README.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
