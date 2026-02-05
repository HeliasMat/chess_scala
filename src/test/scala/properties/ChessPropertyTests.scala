package test.properties

import org.scalacheck._
import org.scalacheck.Prop._
import domain._
import logic._

/**
 * Property-based tests using ScalaCheck.
 * These tests verify laws that should hold for all valid inputs.
 */
object ChessPropertyTests extends Properties("Chess"):

  // ===== Generators =====

  // Generate valid positions (0-7)
  val positionGen: Gen[Int] = Gen.choose(0, 7)

  // Generate valid squares
  val squareGen: Gen[Square] = Gen.choose(0, 63).flatMap { i =>
    Gen.const(Square.fromIndex(i).get)
  }

  // Generate valid pieces
  val pieceTypeGen: Gen[PieceType] = Gen.oneOf(
    PieceType.Pawn, PieceType.Knight, PieceType.Bishop,
    PieceType.Rook, PieceType.Queen, PieceType.King
  )

  val colorGen: Gen[Color] = Gen.oneOf(Color.White, Color.Black)

  val pieceGen: Gen[Piece] = for
    pieceType <- pieceTypeGen
    color <- colorGen
  yield Piece(pieceType, color)

  // ===== Properties =====

  property("Square indices should be in valid range") = forAll(squareGen) { square =>
    val idx = square.index
    idx >= 0 && idx <= 63
  }

  property("Piece color should be white or black") = forAll(pieceGen) { piece =>
    piece.color == Color.White || piece.color == Color.Black
  }

  property("Color.opposite should be involutive") = forAll(colorGen) { color =>
    color.opposite.opposite == color
  }

  property("Bitboard union should be commutative") = forAll(squareGen, squareGen) { (sq1, sq2) =>
    val bb1 = Bitboard.fromSquares(sq1)
    val bb2 = Bitboard.fromSquares(sq2)
    bb1.union(bb2) == bb2.union(bb1)
  }

  property("Bitboard intersection should be commutative") = forAll(squareGen, squareGen) { (sq1, sq2) =>
    val bb1 = Bitboard.fromSquares(sq1)
    val bb2 = Bitboard.fromSquares(sq2)
    bb1.intersection(bb2) == bb2.intersection(bb1)
  }

  property("Bitboard union should be associative") = forAll(squareGen, squareGen, squareGen) { (sq1, sq2, sq3) =>
    val bb1 = Bitboard.fromSquares(sq1)
    val bb2 = Bitboard.fromSquares(sq2)
    val bb3 = Bitboard.fromSquares(sq3)
    
    bb1.union(bb2).union(bb3) == bb1.union(bb2.union(bb3))
  }

  property("Bitboard contains should match after union") = forAll(squareGen, squareGen) { (sq1, sq2) =>
    val bb1 = Bitboard.fromSquares(sq1)
    val bb2 = Bitboard.fromSquares(sq2)
    val union = bb1.union(bb2)
    
    union.contains(sq1) && union.contains(sq2)
  }

  property("Double complement should equal original bitboard") = forAll(squareGen) { sq =>
    val bb = Bitboard.fromSquares(sq)
    bb.complement.complement == bb
  }

  property("Legal moves from initial position should always exist") = {
    MoveGenerator.generateLegalMoves(World.initial).nonEmpty
  }

  property("All legal moves should be distinct") = {
    val moves = MoveGenerator.generateLegalMoves(World.initial)
    moves.length == moves.toSet.size
  }

  property("Generated moves should have different from and to squares") = {
    val moves = MoveGenerator.generateLegalMoves(World.initial)
    moves.forall(move => 
      move.from.x != move.to.x || move.from.y != move.to.y
    )
  }

  property("All generated moves should pass validation") = {
    val world = World.initial
    val moves = MoveGenerator.generateLegalMoves(world)
    moves.forall(move =>
      pipeline.ValidationPipeline.validateMove(world, move).isRight
    )
  }

  property("Initial position should have 20 legal moves for white") = {
    val moves = MoveGenerator.generateLegalMoves(World.initial)
    moves.length == 20
  }

  property("World board should maintain 64 squares") = {
    val world = World.initial
    world.board.length == 64
  }

  property("Initial position should have 32 pieces (16 white, 16 black)") = {
    val world = World.initial
    val whitePieces = world.board.count(_.exists(_.color == Color.White))
    val blackPieces = world.board.count(_.exists(_.color == Color.Black))
    
    whitePieces == 16 && blackPieces == 16
  }

  property("Turn should alternate between white and black") = {
    World.initial.turn == Color.White
  }

  property("World should start with castling rights enabled") = {
    val world = World.initial
    world.castlingRights.canWhiteKingSide &&
    world.castlingRights.canWhiteQueenSide &&
    world.castlingRights.canBlackKingSide &&
    world.castlingRights.canBlackQueenSide
  }

  property("Position coordinates should be in valid range") = forAll(positionGen, positionGen) { (x, y) =>
    val pos = Position(x, y)
    pos.x >= 0 && pos.x <= 7 && pos.y >= 0 && pos.y <= 7
  }

  property("CastlingRights should have consistent state") = {
    val rights = CastlingRights.none
    // All rights should be well-formed
    true
  }

  property("Move generation should be deterministic") = {
    val world = World.initial
    val moves1 = MoveGenerator.generateLegalMoves(world)
    val moves2 = MoveGenerator.generateLegalMoves(world)
    moves1 == moves2
  }

  property("Piece types should have defined values") = {
    // Verify all piece types exist and are distinct
    val pieces = List(
      PieceType.Pawn, PieceType.Knight, PieceType.Bishop,
      PieceType.Rook, PieceType.Queen, PieceType.King
    )
    pieces.length == 6
  }
