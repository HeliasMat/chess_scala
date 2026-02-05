package test

import munit.FunSuite
import domain.*
import logic.*
import pipeline.*

class IntegrationTest extends FunSuite:

  test("Full pipeline: validation + state reduction should work") {
    val world = World.initial
    val move = MoveIntent(Position(4, 1), Position(4, 3), None)  // e2e4
    
    // Validate
    val validationResult = ValidationPipeline.validateMove(world, move)
    assert(validationResult.isRight, "Move e2e4 should be valid")
    
    // Apply
    val validated = validationResult.toOption.get
    val applyResult = StateReducer.applyMove(world, validated)
    assert(applyResult.isRight, "Move application should succeed")
    
    val (newWorld, event) = applyResult.toOption.get
    
    // Verify state changed
    assert(newWorld.turn == Color.Black, "Turn should switch to black")
    assert(newWorld.halfMoveClock == 0, "Halfmove clock should reset")
  }

  test("Move application should update board correctly") {
    val world = World.initial
    val move = MoveIntent(Position(4, 1), Position(4, 2), None)  // e2e3
    
    val validated = ValidationPipeline.validateMove(world, move).toOption.get
    val (newWorld, _) = StateReducer.applyMove(world, validated).toOption.get
    
    // Source square should be empty
    val fromIdx = 1 * 8 + 4
    assert(newWorld.board(fromIdx).isEmpty, "Source square should be empty")
    
    // Destination should have the pawn
    val toIdx = 2 * 8 + 4
    assert(newWorld.board(toIdx).contains(Piece(PieceType.Pawn, Color.White)), 
           "Destination should have white pawn")
  }

  test("Capture should remove captured piece") {
    val board = Vector.fill(64)(None)
      .updated(4, Some(Piece(PieceType.King, Color.White)))      // e1
      .updated(60, Some(Piece(PieceType.King, Color.Black)))     // e8
       .updated(18, Some(Piece(PieceType.Pawn, Color.White)))    // c3 (x=2, y=2)
       .updated(26, Some(Piece(PieceType.Pawn, Color.Black)))     // c4 (x=2, y=3)
    
    val sq4 = Square.fromIndex(4).get
    val sq18 = Square.fromIndex(18).get
    val sq26 = Square.fromIndex(26).get
    val sq60 = Square.fromIndex(60).get
    
    val world = World(
      board = board,
      occupancy = Map(
         Color.White -> Bitboard.fromSquares(sq4, sq18),
         Color.Black -> Bitboard.fromSquares(sq26, sq60)
      ),
      history = Nil,
      turn = Color.White,
      castlingRights = CastlingRights.none,
      enPassantTarget = None,
      halfMoveClock = 0
    )
    
    // White pawn at c3 captures black pawn at c4
    val move = MoveIntent(Position(2, 2), Position(2, 3), None)
    
    val validated = ValidationPipeline.validateMove(world, move).toOption.get
    val (newWorld, _) = StateReducer.applyMove(world, validated).toOption.get
    
    // c3 should be empty
     assert(newWorld.board(18).isEmpty, "c3 should be empty after move")
    
    // c4 should have white pawn
     assert(newWorld.board(26).contains(Piece(PieceType.Pawn, Color.White)))
  }

  test("Invalid move should fail validation") {
    val world = World.initial
    // Trying to move black piece with white to move
    val move = MoveIntent(Position(4, 6), Position(4, 4), None)  // e7e5 (black pawn)
    
    val result = ValidationPipeline.validateMove(world, move)
    assert(result.isLeft, "Moving opponent's piece should fail validation")
  }

  test("FEN roundtrip should preserve position") {
    val world = World.initial
    val fenStr = fen.generateFen(world)
    val parsed = fen.parseFen(fenStr).toOption.get
    
    assert(parsed.board == world.board, "FEN roundtrip should preserve board")
    assert(parsed.turn == world.turn, "FEN roundtrip should preserve turn")
    assert(parsed.castlingRights == world.castlingRights, "FEN roundtrip should preserve castling rights")
  }

  test("UCI move parsing should create valid moves") {
    val move = uci.moveToUci(MoveIntent(Position(4, 1), Position(4, 3), None))
    assert(move == "e2e4", "Should convert to correct UCI notation")
  }

  test("Position command should update game state") {
    val world = World.initial
    val posCmd = uci.UciCommand.Position(None, List("e2e4", "c7c5"))
    
    val result = uci.buildWorldFromPosition(posCmd)
    assert(result.isRight, "Position building should succeed")
    
    val finalWorld = result.toOption.get
    assert(finalWorld.turn == Color.White, "After two moves, it should be white's turn")
  }

  test("Complete move sequence should be valid") {
    var world = World.initial
    
    val moves = List(
      MoveIntent(Position(4, 1), Position(4, 3), None),  // e2e4
      MoveIntent(Position(4, 6), Position(4, 4), None)   // e7e5
    )
    
    for (move, idx) <- moves.zipWithIndex do
      val validated = ValidationPipeline.validateMove(world, move)
      assert(validated.isRight, s"Move $idx should be valid")
      
      val (newWorld, _) = StateReducer.applyMove(world, validated.toOption.get).toOption.get
      world = newWorld
    
    // After two moves, should be white's turn again
    assert(world.turn == Color.White)
  }

  test("Halfmove clock should increment on non-pawn, non-capture moves") {
    val board = Vector.fill(64)(None)
      .updated(4, Some(Piece(PieceType.King, Color.White)))
      .updated(60, Some(Piece(PieceType.King, Color.Black)))
    
    val sq4 = Square.fromIndex(4).get
    val sq60 = Square.fromIndex(60).get
    
    val world = World(
      board = board,
      occupancy = Map(
        Color.White -> Bitboard.fromSquares(sq4),
        Color.Black -> Bitboard.fromSquares(sq60)
      ),
      history = Nil,
      turn = Color.White,
      castlingRights = CastlingRights.none,
      enPassantTarget = None,
      halfMoveClock = 5
    )
    
    val move = MoveIntent(Position(4, 0), Position(5, 0), None)  // e1f1
    val validated = ValidationPipeline.validateMove(world, move).toOption.get
    val (newWorld, _) = StateReducer.applyMove(world, validated).toOption.get
    
    // Halfmove clock should increment
    assert(newWorld.halfMoveClock == 6, "Halfmove clock should increment")
  }

  test("Halfmove clock should reset on pawn move") {
    val board = Vector.fill(64)(None)
      .updated(10, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(4, Some(Piece(PieceType.King, Color.White)))
      .updated(60, Some(Piece(PieceType.King, Color.Black)))
    
    val sq4 = Square.fromIndex(4).get
    val sq10 = Square.fromIndex(10).get
    val sq60 = Square.fromIndex(60).get
    
    val world = World(
      board = board,
      occupancy = Map(
        Color.White -> Bitboard.fromSquares(sq4, sq10),
        Color.Black -> Bitboard.fromSquares(sq60)
      ),
      history = Nil,
      turn = Color.White,
      castlingRights = CastlingRights.none,
      enPassantTarget = None,
      halfMoveClock = 50  // Approaching 50-move rule
    )
    
    val move = MoveIntent(Position(2, 1), Position(2, 2), None)  // c2c3
    val validated = ValidationPipeline.validateMove(world, move).toOption.get
    val (newWorld, _) = StateReducer.applyMove(world, validated).toOption.get
    
    // Halfmove clock should reset
    assert(newWorld.halfMoveClock == 0, "Halfmove clock should reset after pawn move")
  }

  test("Move generation followed by validation should produce valid sequence") {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    
    assert(moves.nonEmpty, "Should generate moves")
    
    // All generated moves should validate successfully
    for move <- moves do
      val result = ValidationPipeline.validateMove(world, move)
      assert(result.isRight, s"Generated move $move should validate")
  }
