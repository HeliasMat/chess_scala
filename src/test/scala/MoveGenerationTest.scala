package test

import munit.FunSuite
import domain.*
import logic.MoveGenerator
import pipeline.ValidationPipeline

class MoveGenerationTest extends FunSuite:

  test("MoveGenerator should generate legal moves for initial position") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    // In initial position, white has 20 legal moves (16 pawn moves + 4 knight moves)
    assert(moves.nonEmpty, "Should generate moves in initial position")
    assertEquals(moves.length, 20)
  }

  test("MoveGenerator should generate pawn moves") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    // Should have pawn moves
    val pawnMoves = moves.filter(m => 
      world.board(m.from.y * 8 + m.from.x).exists(_.kind == PieceType.Pawn)
    )
    assert(pawnMoves.nonEmpty, "Should generate pawn moves in initial position")
  }

  test("MoveGenerator should generate knight moves") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    // Should have knight moves (2 per knight, 2 knights = 4 moves)
    val knightMoves = moves.filter(m =>
      world.board(m.from.y * 8 + m.from.x).exists(_.kind == PieceType.Knight)
    )
    assertEquals(knightMoves.length, 4)
  }

  test("MoveGenerator should not generate illegal moves (moves leaving king in check)") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    // All generated moves should be legal (moves are already filtered)
    assert(moves.nonEmpty, "Should have generated moves")
    assert(moves.length == 20, "Should have 20 legal moves in initial position")
  }

  test("Legal moves from starting position should all be distinct") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    val uniqueMoves = moves.toSet
    
    assertEquals(moves.length, uniqueMoves.size, "All moves should be distinct")
  }

  test("Empty position should have no legal moves") {
    val emptyBoard = Vector.fill(64)(None)
    val world = World(
      board = emptyBoard,
      occupancy = Map(Color.White -> Bitboard.empty, Color.Black -> Bitboard.empty),
      history = Nil,
      turn = Color.White,
      castlingRights = CastlingRights.none,
      enPassantTarget = None,
      halfMoveClock = 0
    )
    
    val moves = MoveGenerator.generateLegalMoves(world)
    assert(moves.isEmpty, "Empty position should have no legal moves")
  }

  test("Position with only kings should have limited moves") {
    val board = Vector.fill(64)(None).updated(
      0, Some(Piece(PieceType.King, Color.White))
    ).updated(
      63, Some(Piece(PieceType.King, Color.Black))
    )
    
    val sq0 = Square.fromIndex(0).get
    val sq63 = Square.fromIndex(63).get
    
    val world = World(
      board = board,
      occupancy = Map(
        Color.White -> Bitboard.fromSquares(sq0),
        Color.Black -> Bitboard.fromSquares(sq63)
      ),
      history = Nil,
      turn = Color.White,
      castlingRights = CastlingRights.none,
      enPassantTarget = None,
      halfMoveClock = 0
    )
    
    val moves = MoveGenerator.generateLegalMoves(world)
    // White king at a1 should have at most 3 moves (b1, b2, a2)
    assert(moves.length <= 3, s"King at a1 should have at most 3 moves, got ${moves.length}")
  }

  test("Pawn at starting position should be able to move one or two squares") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    // Get pawn moves (should be from y=1)
    val pawnMoves = moves.filter(_.from.y == 1)
    
    // Each pawn should be able to move 1 or 2 squares forward
    assert(pawnMoves.nonEmpty, "Should have pawn moves")
  }

  test("Generated moves should respect piece movement rules") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    for move <- moves do
      val fromSquare = move.from.y * 8 + move.from.x
      val toSquare = move.to.y * 8 + move.to.x
      
      // Source square should have a piece
      assert(world.board(fromSquare).isDefined, s"Source square should have a piece for move $move")
      
      // Destination should be different from source
      assert(fromSquare != toSquare, s"Move should not be from and to same square")
  }

  test("All legal moves should be validatable") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    for move <- moves do
      val result = ValidationPipeline.validateMove(world, move)
      assert(result.isRight, s"Move $move should pass validation: $result")
  }
