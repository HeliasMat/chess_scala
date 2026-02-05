package test

import domain.*
import pipeline.fen

class FenParserTest extends munit.FunSuite:

  test("FEN parser should parse initial position correctly") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val result = fen.parseFen(initialFen)

    assert(result.isRight, s"Failed to parse initial FEN: $result")

    val world = result.toOption.get

    // Check that the board has the correct pieces
    assert(world.board(0).contains(Piece(PieceType.Rook, Color.White)))
    assert(world.board(4).contains(Piece(PieceType.King, Color.White)))
    assert(world.board(63).contains(Piece(PieceType.Rook, Color.Black)))

    // Check turn
    assert(world.turn == Color.White)

    // Check castling rights
    assert(world.castlingRights.canWhiteKingSide)
    assert(world.castlingRights.canWhiteQueenSide)
    assert(world.castlingRights.canBlackKingSide)
    assert(world.castlingRights.canBlackQueenSide)

    // Check en passant
    assert(world.enPassantTarget.isEmpty)

    // Check clocks
    assert(world.halfMoveClock == 0)
  }

  test("FEN parser should generate correct FEN for initial position") {
    val generatedFen = fen.generateFen(World.initial)
    val expectedFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    assert(generatedFen == expectedFen, s"Generated FEN '$generatedFen' does not match expected '$expectedFen'")
  }

  test("FEN parser round-trip should preserve position") {
    val initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val result = fen.parseFen(initialFen)

    assert(result.isRight)

    val world = result.toOption.get
    val generatedFen = fen.generateFen(world)

    assert(generatedFen == initialFen, s"Round-trip failed: original '$initialFen' vs generated '$generatedFen'")
  }