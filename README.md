# Pure Functional Event-Sourced Chess Engine

A complete chess engine written in **Scala 3** using purely functional programming principles, immutable data structures, and the Cats ecosystem.

## Overview

This project implements a fully functional chess engine from the ground up:

- **Pure Functional Design**: No mutable state, referential transparency throughout
- **Type Safety**: Scala 3 opaque types, sealed traits, comprehensive error handling
- **Performance Optimized**: Bitboard-based move generation, alpha-beta pruning, transposition tables
- **UCI Protocol**: Full UCI (Universal Chess Interface) support for integration with chess GUIs
- **Comprehensive Testing**: 71 tests including property-based testing with ScalaCheck
- **Zero-Runtime Overhead**: Opaque types compile to primitives with no boxing

## Features

### Core Engine
- **Move Generation**: Fast, efficient generation of legal moves using bitboards
- **Evaluation**: Material and positional evaluation with piece-square tables
- **Search**: Minimax with alpha-beta pruning and quiescence search
- **Caching**: Zobrist hashing with transposition table for repeated positions
- **Concurrency**: Parallel move evaluation using Cats Effect

### Protocols & IO
- **UCI Protocol**: Complete implementation for GUI communication
- **FEN Support**: Parse and generate Forsyth-Edwards notation
- **Stream Processing**: fs2-based async IO pipeline

### Testing & Benchmarking
- **Unit Tests**: 50 comprehensive unit tests covering all components
- **Property Tests**: 21 ScalaCheck properties validating invariants
- **Benchmarks**: Component-level profiling tools included
- **100% Pass Rate**: All 71 tests passing

## Quick Start

### Prerequisites
- JDK 17+
- Scala 3.3.1
- sbt 1.11.7+

### Build
```bash
sbt compile
```

### Run Tests
```bash
sbt test
```

### Run Benchmarks
```bash
# Quick micro-benchmark
sbt "runMain bench.Benchmark 500"

# Component-level profiling
sbt "runMain bench.ComponentBenchmark 800"
```

### Play via UCI Protocol
```bash
sbt run
```

Then connect any UCI-compatible GUI (e.g., Arena, lichess-bot, ChessTempo):
```
uci
position startpos moves e2e4 e7e5
go depth 6
```

## Project Structure

```
src/
  main/scala/
    domain/        # Core models (Piece, Position, World, bitboards)
    logic/         # Move generation, validation, state reduction
    pipeline/      # FEN parser, UCI protocol, validation pipeline
    ai/            # Evaluation, search, transposition table
    bench/         # Benchmarking harnesses
  test/scala/      # Comprehensive test suite
```

## Architecture

### Domain Layer (`domain/`)
- **Models**: Algebraic Data Types for chess concepts
- **Bitboards**: Opaque types for Square, Bitboard, CastlingRights
- **Events/Errors**: Event sourcing and error handling

### Logic Layer (`logic/`)
- **Bitboard Operations**: Attack generation, occupancy calculations
- **Move Generation**: Pseudo-legal and legal move generation for all pieces
- **Validation**: Geometry, path, and rule validation
- **State Reducer**: Move application and state transitions

### Pipeline Layer (`pipeline/`)
- **FEN Parser**: Parse/generate FEN strings
- **UCI Protocol**: Command parsing and response formatting
- **Validators**: Multi-stage validation pipeline

### AI Layer (`ai/`)
- **Evaluation**: Static position evaluation (material + positional)
- **Search**: Minimax with alpha-beta pruning + quiescence
- **Transposition Table**: Zobrist hashing + caching for repeated positions

## Performance

Recent optimizations (Phase 7):

| Component | Time (µs) | Notes |
|-----------|-----------|-------|
| Bishops   | ~159      | -37% after fast iteration optimization |
| Rooks     | ~441      | Sliding piece calculation |
| Queens    | ~162      | Combined bishop + rook |
| Knights   | ~835      | Precomputed attack tables |
| Pawns     | ~133      | Directional move generation |
| Initial Position | ~230 | Full legal move set (20 moves) |

Benchmarks run on Intel i7 with 800 iterations each. Results vary by system.

## Dependencies

### Core Libraries
- **Cats** (2.10.0): Functional programming toolkit
- **Cats Effect** (3.5.2): Async/concurrent effects
- **FS2** (3.9.3): Functional streams for IO
- **Monocle** (3.2.0): Optics for immutable updates
- **Cats Parse** (1.0.0): Parser combinators
- **JMH** (1.36): Benchmarking (optional)

### Testing
- **munit** (1.0.1): Testing framework
- **ScalaCheck** (1.17.0): Property-based testing

See `build.sbt` for full dependency list and versions.

## Key Concepts

### Opaque Types
Zero-runtime-overhead type wrappers:
```scala
opaque type Square = Byte
opaque type Bitboard = Long
opaque type CastlingRights = Byte
```

### Immutable State
All state transitions use functional lenses (Monocle):
```scala
val newWorld = world.copy(
  board = newBoard,
  occupancy = newOccupancy,
  turn = world.turn.opposite
)
```

### Event Sourcing
All moves generate events for audit trail:
```scala
sealed trait MoveEvent
case class MoveExecuted(...) extends MoveEvent
case class CastlingExecuted(...) extends MoveEvent
```

### Pure Functions
No side effects in game logic:
```scala
def generateLegalMoves(world: World): List[MoveIntent]
def validateMove(world: World, move: MoveIntent): Either[...]
def applyMove(world: World, move: ValidatedMove): (World, MoveEvent)
```

## Testing Strategy

### Unit Tests (50 tests)
- Bitboard operations
- Move generation per piece type
- Validation pipeline
- State transitions
- Integration tests

### Property-Based Tests (21 properties)
- Move generation determinism
- Bitboard laws (associativity, commutativity)
- Immutability guarantees
- Parser round-trip preservation

### Test Execution
```bash
sbt test
# Expected: 71 passed, 0 failed
```

## UCI Protocol Support

Full UCI protocol implementation for GUI integration:

**Supported Commands:**
- `uci` - Engine identification
- `isready` - Ping/pong
- `setoption` - Configuration
- `position` - Set board state
- `go` - Start search
- `stop` - Stop search
- `quit` - Shutdown

**Supported Responses:**
- `id` - Engine info
- `uciok` - Engine ready
- `readyok` - Ping response
- `bestmove` - Search result
- `info` - Search statistics

Example session:
```
> uci
< id name "Pure Functional Chess Engine"
< id author "Scala FP"
< uciok

> position startpos
> go depth 6
< info depth 1 score cp 20 pv e2e4
< info depth 2 score cp 20 pv e2e4 e7e5
< bestmove e2e4 ponder e7e5
```

See [UCI_USAGE.md](UCI_USAGE.md) for detailed protocol documentation.

## Development

### Adding a Feature
1. Define domain models in `src/main/scala/domain/`
2. Implement logic in `src/main/scala/logic/`
3. Add comprehensive tests in `src/test/scala/`
4. Run `sbt test` to validate
5. Commit with clear message

### Performance Profiling
```bash
# Component benchmark
sbt "runMain bench.ComponentBenchmark 1000"

# Micro-benchmark
sbt "runMain bench.Benchmark 5000"
```

### Code Style
- Functional approach: no var/mutable state
- Type safety: sealed traits, opaque types
- Error handling: Either/Option for pure functions
- Comments: document non-obvious behavior

## Future Enhancements

### Phase 9: Advanced Features
- [ ] Opening book integration
- [ ] Endgame tablebases
- [ ] Time management system
- [ ] Iterative deepening
- [ ] Move ordering heuristics

### Phase 10: Performance
- [ ] Magic bitboards
- [ ] SIMD optimizations
- [ ] Parallel search (SMP)
- [ ] GPU acceleration

### Phase 11: Analysis Tools
- [ ] Position analyzer
- [ ] Game replay system
- [ ] Weakness detector
- [ ] Comparative engine testing

## License

MIT License - See LICENSE file for details.

## Authors

Created as a demonstration of pure functional programming principles in Scala 3.

## References

### Chess Programming
- Bit Twiddling Hacks (Stanford)
- Chess Programming Wiki
- "Secrets of Rook Endings" (Dvoretsky)

### Functional Programming
- "Designing with Types" (Scott Wlaschin)
- Cats documentation (https://typelevel.org/cats)
- "Functional Programming in Scala" (Chiusano & Bjarnason)

### Performance
- JMH Benchmark Harness
- "Systems Performance" (Gregg)

## Status

**Phase 8: Documentation & Finalization - COMPLETE ✅**

All phases completed:
- ✅ Phase 1: Project Initialization
- ✅ Phase 2: Domain Modeling
- ✅ Phase 3: Core Logic
- ✅ Phase 4: AI Implementation
- ✅ Phase 5: Parsing & IO
- ✅ Phase 6: Testing & Validation
- ✅ Phase 7: Optimization
- ✅ Phase 8: Documentation & Finalization

**Test Results:** 71/71 passing (100%)
**Code:** ~2000 lines of pure functional Scala 3

---

For detailed progress, see [project_progress.md](project_progress.md).
For benchmark usage, see [README_BENCH.md](README_BENCH.md).
For UCI protocol details, see [UCI_USAGE.md](UCI_USAGE.md).
