# Detailed Roadmap for Pure Functional Event-Sourced Chess Engine (Scala 3)

## Overview
This roadmap outlines the development phases for building a high-performance, pure functional chess engine in Scala 3. The project emphasizes immutability, event sourcing, and functional programming principles. Each phase includes specific tasks, deliverables, and estimated effort.

## Phase 1: Project Initialization (1-2 weeks)
**Goal:** Set up the development environment and project structure.

### 1.1 Sbt Project Structure Creation
**Objective:** Establish the build system with all necessary dependencies.

#### Prerequisites:
- sbt 1.9+ installed
- Java 17+ installed
- Basic familiarity with sbt commands

#### Steps:
1. Create the root project directory: `chess_functional_programming`
2. Initialize a new Scala 3 project using sbt template: `sbt new scala/scala3.g8`
3. Rename the generated project to match the desired name
4. Create `build.sbt` file with the following content:
   - Set `scalaVersion := "3.3.1"`
   - Add all library dependencies (cats-core, cats-effect, fs2, monocle, cats-parse, scalacheck)
   - Configure compiler options for strict mode (-Werror, -deprecation, etc.)
5. Create `project/plugins.sbt` if needed for additional plugins
6. Run `sbt compile` to verify the build works

#### Deliverables:
- `build.sbt` file with complete dependency list
- Successful compilation of empty project

### 1.2 Directory Structure Establishment
**Objective:** Create the standard sbt directory layout matching the specification.

#### Prerequisites:
- Project directory created
- Basic understanding of Scala package structure

#### Steps:
1. Create `src/main/scala/` directory
2. Under `src/main/scala/`, create subdirectories:
   - `domain/` for models and types
   - `logic/` for core business logic
   - `pipeline/` for orchestration
   - `ai/` for artificial intelligence components
3. Create `src/test/scala/` directory
4. Under `src/test/scala/`, create `properties/` subdirectory for ScalaCheck tests
5. Create `src/main/resources/` for any static resources (if needed)
6. Create `.gitignore` file with Scala/sbt ignores:
   - `target/`
   - `.bsp/`
   - `.metals/`
   - `.vscode/`
   - `*.class`
7. Verify directory structure matches the specification exactly

#### Deliverables:
- Complete directory tree as specified
- `.gitignore` file configured for Scala development

### 1.3 Environment Setup and Verification
**Objective:** Ensure all development tools are properly configured.

#### Prerequisites:
- All directories created
- sbt project compilable

#### Steps:
1. Verify Scala 3.3.1 installation: `scala -version`
2. Test sbt functionality: `sbt about`
3. Run initial compilation: `sbt compile`
4. Resolve all dependencies: `sbt update`
5. Set up IDE:
   - For VS Code: Install Metals extension, import project
   - For IntelliJ IDEA: Import as sbt project
6. Configure IDE settings for Scala 3 (if needed)
7. Run a simple test to ensure environment works

#### Deliverables:
- Verified Scala/sbt installation
- IDE configured and project imported
- Successful dependency resolution

### Phase 1 Deliverables Summary:
- Functional sbt build with all dependencies
- Complete directory structure
- Verified development environment

## Phase 2: Domain Modeling (2-3 weeks)
**Goal:** Define the core algebraic data types and immutable data structures.

### 2.1 Primitive Types and Opaque Types Implementation
**Objective:** Create zero-overhead abstractions for performance-critical data.

#### Prerequisites:
- Basic Scala 3 syntax knowledge
- Understanding of opaque types

#### Steps:
1. Create `domain/bitboards.scala` file
2. Define `Square` opaque type:
   - Companion object with `from(x: Int, y: Int): Option[Square]`
   - Validation to ensure 0-63 range
   - Conversion methods to/from Int
3. Define `Bitboard` opaque type:
   - Backed by `Long`
   - Extension methods: `contains(square: Square): Boolean`, `union`, `intersection`, `complement`
   - Bitwise operations using JVM primitives
4. Define `CastlingRights` opaque type:
   - Backed by `Byte`
   - Bitmask operations for KQkq rights
5. Add comprehensive tests for all operations

#### Deliverables:
- `Square`, `Bitboard`, `CastlingRights` types with full functionality
- Unit tests for all operations

### 2.2 Core Algebraic Data Types Definition
**Objective:** Define the fundamental domain entities.

#### Prerequisites:
- Opaque types implemented
- Understanding of Scala 3 enums and case classes

#### Steps:
1. Create `domain/models.scala` file
2. Define `Color` enum:
   - Cases: `White`, `Black`
   - Method: `opposite: Color`
3. Define `PieceType` enum:
   - Cases: `Pawn`, `Knight`, `Bishop`, `Rook`, `Queen`, `King`
4. Define `Piece` case class:
   - Fields: `kind: PieceType`, `color: Color`
5. Define `Position` case class:
   - Fields: `x: Int`, `y: Int`
   - Validation in companion object (0-7 range)
   - Conversion to/from `Square`
6. Add equality and hash methods where needed

#### Deliverables:
- Complete ADT definitions
- Type-safe piece and position representations

### 2.3 World Aggregate Definition
**Objective:** Create the single source of truth for game state.

#### Prerequisites:
- All basic ADTs defined
- Understanding of immutable data structures

#### Steps:
1. Continue in `domain/models.scala` or create separate file
2. Define `World` case class with fields:
   - `board: Vector[Option[Piece]]` (64 elements)
   - `occupancy: Map[Color, Bitboard]`
   - `history: List[MoveEvent]`
   - `turn: Color`
   - `castlingRights: CastlingRights`
   - `enPassantTarget: Option[Square]`
   - `halfMoveClock: Int`
3. Implement companion object with:
   - `initial: World` (starting chess position)
   - Utility methods for inspection
4. Ensure immutability and structural sharing

#### Deliverables:
- Complete `World` aggregate
- Initial game state setup

### 2.4 Event and Intent Types Definition
**Objective:** Define types for user input and state changes.

#### Prerequisites:
- World aggregate defined
- Understanding of event sourcing

#### Steps:
1. Create `domain/events.scala` file
2. Define `MoveIntent` case class:
   - Fields: `from: Position`, `to: Position`, `promotion: Option[PieceType]`
3. Define event hierarchy:
   - Sealed trait `ChessEvent`
   - Case classes for different events (MoveExecuted, Capture, etc.)
4. Create `domain/errors.scala` file
5. Define `ChessError` sealed trait with all error cases:
   - Logical errors: `PathBlocked`, `TargetOccupiedByFriendly`, etc.
   - Syntax errors: `ParsingFailure`
   - System errors: `UnknownState`

#### Deliverables:
- Complete event and intent type system
- Comprehensive error hierarchy

### Phase 2 Deliverables Summary:
- Full domain model implementation
- Type-safe, immutable data structures
- Error handling framework

## Phase 3: Core Logic Implementation (4-5 weeks)
**Goal:** Implement the pure functional update cycle and move validation.

### 3.1 Bitboard Operations Implementation
**Objective:** Create efficient bit-level representations for piece movements.

#### Prerequisites:
- Bitboard types defined
- Knowledge of chess piece movement rules

#### Steps:
1. Create `logic/bitboardops.scala` file
2. Implement piece-specific bitboard generation:
   - Pawn attacks, moves
   - Knight attacks (lookup table)
   - Bishop/Queen sliding moves
   - Rook/Queen sliding moves
   - King attacks
3. Implement sliding piece algorithms:
   - Choose between Kogge-Stone or magic bitboards
   - Precompute attack tables if using magic bitboards
4. Implement occupancy calculations:
   - All pieces occupancy
   - Color-specific occupancy
5. Add utility functions for bit manipulation

#### Deliverables:
- Complete bitboard operation library
- Efficient piece movement calculations

### 3.2 Move Generation System
**Objective:** Generate all legal moves for a given position.

#### Prerequisites:
- Bitboard operations implemented
- Understanding of chess rules

#### Steps:
1. Create `logic/movegen.scala` file
2. Implement `MoveGenerator` object with methods:
   - `generatePawnMoves(world: World): List[MoveIntent]`
   - `generateKnightMoves(world: World): List[MoveIntent]`
   - Similar for all piece types
3. Implement legal move filtering:
   - Generate pseudo-legal moves
   - Filter out moves that leave king in check
4. Add special moves:
   - Castling logic with rights checking
   - En passant capture detection
   - Pawn promotion handling
5. Combine all generators into `generateAllMoves(world: World)`

#### Deliverables:
- Complete move generation for all scenarios
- Legal move filtering

### 3.3 Validation Pipeline Implementation
**Objective:** Create the railway-oriented validation system.

#### Prerequisites:
- Move generation working
- Understanding of Either/Validated

#### Steps:
1. Create `logic/validators.scala` file
2. Implement `GeometryValidator`:
   - Check basic move geometry (straight/diagonal)
   - Validate piece-specific movement patterns
3. Implement `PathValidator`:
   - Check path clearance for sliding pieces
   - Handle blocking pieces
4. Implement `RuleValidator`:
   - Check game rules (own king not in check after move)
   - Validate special move conditions
5. Create composition functions using Either/Validated
6. Implement `validateMove(world: World, intent: MoveIntent): ValidationResult`

#### Deliverables:
- Complete validation pipeline
- Composable validation functions

### 3.4 State Reducer Implementation
**Objective:** Create the pure state transition function.

#### Prerequisites:
- Validation pipeline complete
- Monocle library available

#### Steps:
1. Create `logic/reducers.scala` file
2. Implement `Reducer` object with `apply` method:
   - Input: `(world: World, intent: MoveIntent)`
   - Output: `Either[ChessError, World]`
3. Use Monocle lenses for immutable updates:
   - Board updates (piece movement)
   - Bitboard updates (XOR operations)
   - Metadata updates (turn, castling rights, en passant)
4. Handle all state transitions:
   - Piece capture and removal
   - Castling rights updates
   - En passant target setting/resetting
   - Half-move clock management
5. Implement event sourcing:
   - Create appropriate event from move
   - Append to history list

#### Deliverables:
- Complete state transition function
- Immutable world updates
- Event-sourced history

### Phase 3 Deliverables Summary:
- Full move generation and validation
- Pure state transitions
- Event-sourced updates

## Phase 4: AI Implementation (3-4 weeks)
**Goal:** Build the parallel minimax search with evaluation.

### 4.1 Static Evaluation Function
**Objective:** Create position evaluation heuristics.

#### Prerequisites:
- World representation complete
- Basic chess knowledge

#### Steps:
1. Create `ai/eval.scala` file
2. Implement piece-square tables:
   - Arrays for each piece type and square
   - Middlegame and endgame tables
3. Add positional evaluation:
   - Piece mobility
   - King safety
   - Pawn structure
4. Implement material balance:
   - Piece values (pawn=100, etc.)
   - Material counting functions
5. Combine into `evaluate(world: World): Double`

#### Deliverables:
- Comprehensive evaluation function
- Tunable evaluation parameters

### 4.2 Search Algorithm Implementation
**Objective:** Implement the core search logic.

#### Prerequisites:
- Evaluation function complete
- Understanding of minimax algorithm

#### Steps:
1. Create `ai/search.scala` file
2. Implement basic minimax:
   - Recursive function with depth parameter
   - Max/min player alternation
3. Add alpha-beta pruning:
   - Alpha/beta parameter passing
   - Pruning logic
4. Implement quiescence search:
   - Extend search in unstable positions
   - Only consider captures and checks
5. Add iterative deepening:
   - Search progressively deeper
   - Time management

#### Deliverables:
- Efficient search algorithm
- Alpha-beta with quiescence

### 4.3 Parallelization Implementation
**Objective:** Add concurrent move exploration.

#### Prerequisites:
- Search algorithm working
- Cats Effect knowledge

#### Steps:
1. Modify search to use Cats Effect IO
2. Implement parallel move evaluation:
   - Use `parTraverse` for concurrent exploration
   - Scatter-gather pattern
3. Add fiber-based parallelism:
   - Launch concurrent fibers for each move
   - Collect results asynchronously
4. Handle concurrency correctly:
   - Ensure thread safety (though data is immutable)
   - Proper resource management

#### Deliverables:
- Parallel search implementation
- Concurrent move evaluation

### 4.4 Transposition Table Implementation
**Objective:** Add position caching for efficiency.

#### Prerequisites:
- Search algorithm implemented
- Understanding of Zobrist hashing

#### Steps:
1. Create `ai/transposition.scala` file
2. Implement Zobrist hashing:
   - Random 64-bit numbers for pieces/squares
   - Incremental hash updates in reducer
3. Create immutable transposition table:
   - Use `Ref[Map[Long, Evaluation]]` or similar
   - Concurrent-safe operations
4. Integrate with search:
   - Check table before evaluation
   - Store results after evaluation
5. Add table management:
   - Size limits and eviction policy

#### Deliverables:
- Efficient position hashing
- Concurrent transposition table
- Improved search performance

### Phase 4 Deliverables Summary:
- Complete AI system
- Parallel search with evaluation
- Position caching

## Phase 5: Parsing and IO Integration (2-3 weeks)
**Goal:** Connect the pure engine to external protocols.

### 5.1 FEN Parser Implementation
**Objective:** Parse and generate FEN strings.

#### Prerequisites:
- World representation complete
- Cats Parse knowledge

#### Steps:
1. Create `pipeline/fen.scala` file
2. Implement FEN parsing using Cats Parse:
   - Parse board position (piece placement)
   - Parse active color
   - Parse castling rights
   - Parse en passant target
   - Parse halfmove clock and fullmove number
3. Handle all FEN components:
   - Piece symbols (PNBRQKpnbrqk)
   - Empty squares (1-8)
   - Ranks separated by /
4. Add FEN generation:
   - Convert World to FEN string
5. Error handling for invalid FEN

#### Deliverables:
- Complete FEN parser/generator
- Robust error handling

### 5.2 UCI Protocol Implementation
**Objective:** Implement Universal Chess Interface.

#### Prerequisites:
- FEN parser complete
- Understanding of UCI protocol

#### Steps:
1. Create `pipeline/uci.scala` file
2. Implement command parsing:
   - `uci` command response
   - `isready` command
   - `position` command with FEN/startpos
   - `go` command for search initiation
3. Add position setup:
   - Parse move sequences
   - Apply moves to world
4. Implement response formatting:
   - `bestmove` responses
   - `info` strings for search progress
5. Handle UCI options if needed

#### Deliverables:
- Complete UCI command handling
- Proper protocol compliance

### 5.3 IO Pipeline Implementation
**Objective:** Create the impure boundary with stdin/stdout.

#### Prerequisites:
- UCI protocol implemented
- FS2 knowledge

#### Steps:
1. Create `Main.scala` file
2. Implement FS2 stream processing:
   - Read from stdin with utf8 decoding
   - Parse lines into commands
   - Process commands with pure engine
3. Create `IOApp.Simple` main class:
   - Wire streams together
   - Handle errors gracefully
4. Add logging and error handling:
   - Log invalid commands
   - Handle parsing errors
5. Test stdin/stdout interaction

#### Deliverables:
- Complete IO integration
- Runnable chess engine
- Error-resilient stream processing

### Phase 5 Deliverables Summary:
- UCI-compatible engine
- FEN support
- Stream-based IO

## Phase 6: Testing and Validation (2-3 weeks)
**Goal:** Ensure correctness through comprehensive testing.

### 6.1 Unit Testing Implementation
**Objective:** Test individual components.

#### Prerequisites:
- All components implemented
- ScalaTest or similar testing framework

#### Steps:
1. Create unit test files in `src/test/scala/`
2. Test validators:
   - Geometry validation edge cases
   - Path clearance scenarios
   - Rule compliance checks
3. Test move generators:
   - All piece types
   - Special moves (castling, en passant)
   - Edge positions
4. Test reducer:
   - State transitions
   - Event sourcing
5. Add test utilities:
   - World creation helpers
   - Common test positions

#### Deliverables:
- Comprehensive unit test suite
- Edge case coverage

### 6.2 Property-Based Testing Implementation
**Objective:** Prove laws with generated test cases.

#### Prerequisites:
- ScalaCheck configured
- Understanding of property testing

#### Steps:
1. Create `properties/` test files
2. Define generators:
   - `Gen[World]` for random positions
   - `Gen[MoveIntent]` for random moves
3. Implement laws:
   - Reversibility: unmake(make(world, move)) == world
   - Validity: legal moves don't leave king in check
   - Determinism: evaluation is consistent
4. Add move generation properties:
   - All legal moves are valid
   - No invalid moves are generated
5. Run extensive property checks

#### Deliverables:
- ScalaCheck property suite
- Proven correctness laws

### 6.3 Integration Testing Implementation
**Objective:** Test complete system interactions.

#### Prerequisites:
- Unit and property tests passing
- Full engine implemented

#### Steps:
1. Create integration test files
2. Test complete pipeline:
   - Intent -> Validation -> Reducer -> New World
   - End-to-end move processing
3. Test AI integration:
   - Search produces valid moves
   - Evaluation is reasonable
4. Test UCI protocol:
   - Command parsing and responses
   - Position setup and move application
5. Add performance tests:
   - Move generation speed
   - Search depth benchmarks

#### Deliverables:
- Complete integration test suite
- Validated system interactions

### Phase 6 Deliverables Summary:
- High-coverage test suite
- Property-proven correctness
- Integration validation

## Phase 7: Optimization and Performance Tuning (2-3 weeks)
**Goal:** Achieve competitive performance through optimization.

### 7.1 Performance Profiling Setup
**Objective:** Identify performance bottlenecks.

#### Prerequisites:
- Working engine
- Profiling tools available

#### Steps:
1. Set up benchmarking framework
2. Profile move generation:
   - Measure generation speed for different positions
   - Identify slow piece types
3. Profile search algorithm:
   - Time per depth
   - Node visitation rates
4. Profile bitboard operations:
   - JVM performance of bitwise operations
   - Memory allocation patterns
5. Establish baseline metrics

#### Deliverables:
- Performance baseline measurements
- Identified bottlenecks

### 7.2 Algorithm Optimization
**Objective:** Improve algorithmic efficiency.

#### Prerequisites:
- Profiling data available
- Algorithm knowledge

#### Steps:
1. Optimize bitboard operations:
   - Use JVM intrinsics for Long operations
   - Minimize object creation
2. Improve search pruning:
   - Better move ordering
   - Enhanced alpha-beta cutoffs
3. Enhance evaluation accuracy:
   - Tune piece-square tables
   - Add more evaluation terms
4. Optimize data structures:
   - Better cache locality
   - Reduced memory footprint

#### Deliverables:
- Optimized algorithms
- Improved performance metrics

### 7.3 Memory Optimization
**Objective:** Reduce memory usage and improve efficiency.

#### Prerequisites:
- Algorithm optimizations complete
- Memory profiling tools

#### Steps:
1. Optimize Vector updates:
   - Ensure structural sharing is effective
   - Minimize unnecessary allocations
2. Tune transposition table:
   - Optimal size for memory constraints
   - Efficient eviction policies
3. Minimize object allocation:
   - Reuse objects where possible
   - Avoid boxing of primitives
4. Profile and optimize garbage collection pressure

#### Deliverables:
- Memory-efficient implementation
- Reduced GC overhead
- Maintained performance

### Phase 7 Deliverables Summary:
- Competitive performance achieved
- Optimized algorithms and data structures
- Memory-efficient operation

## Phase 8: Documentation and Finalization (1-2 weeks)
**Goal:** Complete the project with documentation and polish.

### 8.1 Code Documentation Implementation
**Objective:** Document the codebase comprehensively.

#### Prerequisites:
- All code implemented
- Understanding of Scaladoc

#### Steps:
1. Add Scaladoc comments to all public APIs:
   - Classes, traits, objects
   - Methods and functions
   - Complex algorithms
2. Document data structures:
   - Bitboard representations
   - World aggregate fields
3. Create usage examples:
   - Code snippets for common operations
   - Integration examples
4. Document design decisions:
   - Why certain patterns were chosen
   - Performance trade-offs

#### Deliverables:
- Fully documented codebase
- API documentation

### 8.2 Project Documentation Creation
**Objective:** Create user-facing documentation.

#### Prerequisites:
- Code documentation complete
- Project understanding

#### Steps:
1. Write comprehensive README.md:
   - Project overview and philosophy
   - Setup and installation instructions
   - Usage examples
   - Architecture explanation
2. Document architecture decisions:
   - Why functional programming
   - Event sourcing benefits
   - Performance considerations
3. Add performance benchmarks:
   - Move generation speeds
   - Search depths achieved
   - Comparisons with other engines
4. Create troubleshooting guide

#### Deliverables:
- User-friendly README
- Architecture documentation
- Performance comparisons

### 8.3 Final Polish and Packaging
**Objective:** Prepare for production release.

#### Prerequisites:
- All tests passing
- Documentation complete

#### Steps:
1. Code cleanup and refactoring:
   - Remove debug code
   - Improve naming consistency
   - Optimize imports
2. Final testing:
   - Run full test suite
   - Integration testing
   - Performance validation
3. Package preparation:
   - Create assembly JAR
   - Add startup scripts
   - Prepare for distribution
4. Final review and bug fixes

#### Deliverables:
- Production-ready engine
- Distribution package
- Clean, maintainable codebase

### Phase 8 Deliverables Summary:
- Fully documented and polished project
- Production-ready distribution
- Maintainable codebase

## Risk Mitigation
- **Technical Risks:** Regular code reviews and pair programming for complex FP concepts
- **Performance Risks:** Early benchmarking and profiling to ensure JVM optimization
- **Scope Risks:** Incremental development with working milestones
- **Dependency Risks:** Pin library versions and maintain compatibility

## Success Criteria
- **Functional:** Complete UCI-compatible chess engine
- **Correct:** Passes all property-based tests and standard chess test suites
- **Performative:** Competitive move generation and search speeds
- **Maintainable:** Well-documented, type-safe, and modular codebase
- **Pure:** Zero mutable state or side effects in core logic

## Timeline Summary
- **Total Duration:** 16-26 weeks (4-6 months)
- **Key Milestones:** Domain complete (Week 5), Core logic complete (Week 10), AI complete (Week 14), Full engine (Week 18)
- **Team Size:** 1-2 developers with Scala/FP experience
- **Tools:** sbt, Scala 3.3.1, VS Code/IntelliJ, Git

This roadmap provides a structured path to building a sophisticated functional chess engine while maintaining architectural purity and performance goals.