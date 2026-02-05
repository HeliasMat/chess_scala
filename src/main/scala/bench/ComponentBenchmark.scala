package bench

import domain.*
import logic.*

object ComponentBenchmark:
  def makeWorld(pieceType: PieceType, positions: List[Position]): World =
    // Start with empty board
    val empty = Vector.fill(64)(None)

    // Place kings so world is valid
    val withKings = empty
      .updated(Square.fromIndex(4).get.index, Some(Piece(PieceType.King, Color.White)))
      .updated(Square.fromIndex(60).get.index, Some(Piece(PieceType.King, Color.Black)))

    // Place pieces
    val board = positions.foldLeft(withKings) { (b, pos) =>
      b.updated(pos.toSquare.get.index, Some(Piece(pieceType, Color.White)))
    }

    val allySquares = positions.map(_.toSquare.get)
    val occupancy = Map(
      Color.White -> Bitboard.fromSquares((allySquares :+ Square.fromIndex(4).get): _*),
      Color.Black -> Bitboard.fromSquares(Square.fromIndex(60).get)
    )

    World(
      board = board,
      occupancy = occupancy,
      history = Nil,
      turn = Color.White,
      castlingRights = CastlingRights.none,
      enPassantTarget = None,
      halfMoveClock = 0
    )

  def runScenario(name: String, world: World, iterations: Int): Unit =
    // Warmup
    for _ <- 1 to 50 do MoveGenerator.generateLegalMoves(world)

    val t0 = System.nanoTime()
    for _ <- 1 to iterations do MoveGenerator.generateLegalMoves(world)
    val t1 = System.nanoTime()
    val avgNs = (t1 - t0).toDouble / iterations
    println(f"$name: avg ${avgNs/1e3}%.3f µs (${avgNs}%.0f ns) over $iterations runs")

  def main(args: Array[String]): Unit =
    val iterations = args.headOption.map(_.toInt).getOrElse(1000)

    // Prepare positions: spread pieces across the board
    val allSquares = (for y <- 0 until 8; x <- 0 until 8 yield Position(x, y)).toList
    val knightPositions = allSquares.filter(p => (p.x + p.y) % 2 == 0).take(20)
    val bishopPositions = allSquares.filter(p => (p.x + p.y) % 2 == 0).take(10)
    val rookPositions = allSquares.filter(p => p.y == 3).take(8)
    val queenPositions = List(Position(3,3))
    val pawnPositions = (0 until 8).map(x => Position(x,1)).toList

    val knightWorld = makeWorld(PieceType.Knight, knightPositions)
    val bishopWorld = makeWorld(PieceType.Bishop, bishopPositions)
    val rookWorld = makeWorld(PieceType.Rook, rookPositions)
    val queenWorld = makeWorld(PieceType.Queen, queenPositions)
    val pawnWorld = makeWorld(PieceType.Pawn, pawnPositions)
    val initialWorld = World.initial

    println(s"Component benchmarking with $iterations iterations")
    runScenario("Knights", knightWorld, iterations)
    runScenario("Bishops", bishopWorld, iterations)
    runScenario("Rooks", rookWorld, iterations)
    runScenario("Queens", queenWorld, iterations)
    runScenario("Pawns", pawnWorld, iterations)
    runScenario("InitialPosition", initialWorld, iterations)
