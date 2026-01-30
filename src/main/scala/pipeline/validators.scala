package pipeline

import domain._
import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits._

// Type alias for validation results
type ValidationResult[A] = Either[NonEmptyList[ChessError], A]

// Validation pipeline for chess moves
object ValidationPipeline:

  // Main validation function - combines all validators
  def validateMove(world: World, move: MoveIntent): ValidationResult[ValidatedMove] =
    for
      geometry <- GeometryValidator.validate(world, move)
      path <- PathValidator.validate(world, geometry)
      rules <- RuleValidator.validate(world, geometry)
    yield ValidatedMove(geometry, path, rules)

  // Represents a fully validated move ready for execution
  case class ValidatedMove(
    geometry: GeometryValidator.ValidatedGeometry,
    path: PathValidator.ValidatedPath,
    rules: RuleValidator.ValidatedRules
  )

// Geometry validator - validates basic move structure and piece ownership
object GeometryValidator:

  case class ValidatedGeometry(
    from: Square,
    to: Square,
    piece: Piece,
    isCapture: Boolean,
    isEnPassant: Boolean,
    isCastling: Boolean,
    promotion: Option[PieceType]
  )

  def validate(world: World, move: MoveIntent): ValidationResult[ValidatedGeometry] =
    for
      fromSquare <- validateSourceSquare(move.from)
      toSquare <- validateDestinationSquare(move.to)
      piece <- validatePieceOwnership(world, fromSquare, world.turn)
      _ <- validateDestinationNotOccupiedByOwn(world, toSquare, world.turn)
      isCapture = world.isOccupiedBy(toSquare, world.turn.opposite)
      isEnPassant = validateEnPassant(world, fromSquare, toSquare, piece)
      isCastling = validateCastling(world, fromSquare, toSquare, piece)
      _ <- validatePromotion(move.promotion, piece, toSquare)
    yield ValidatedGeometry(
      fromSquare,
      toSquare,
      piece,
      isCapture,
      isEnPassant,
      isCastling,
      move.promotion
    )

  private def validateSourceSquare(pos: Position): ValidationResult[Square] =
    pos.toSquare.toRight(NonEmptyList.one(ChessError.InvalidMoveGeometry))

  private def validateDestinationSquare(pos: Position): ValidationResult[Square] =
    pos.toSquare.toRight(NonEmptyList.one(ChessError.InvalidMoveGeometry))

  private def validatePieceOwnership(world: World, square: Square, color: Color): ValidationResult[Piece] =
    world.pieceAt(square) match
      case Some(piece) if piece.color == color => Right(piece)
      case Some(_) => Left(NonEmptyList.one(ChessError.PieceCannotMoveThisWay))
      case None => Left(NonEmptyList.one(ChessError.InvalidMoveGeometry))

  private def validateDestinationNotOccupiedByOwn(world: World, square: Square, color: Color): ValidationResult[Unit] =
    if world.isOccupiedBy(square, color) then
      Left(NonEmptyList.one(ChessError.TargetOccupiedByFriendly))
    else
      Right(())

  private def validateEnPassant(world: World, from: Square, to: Square, piece: Piece): Boolean =
    piece.kind == PieceType.Pawn &&
    world.enPassantTarget.contains(to) &&
    math.abs(from.x - to.x) == 1 &&
    math.abs(from.y - to.y) == 1

  private def validateCastling(world: World, from: Square, to: Square, piece: Piece): Boolean =
    piece.kind == PieceType.King &&
    math.abs(from.x - to.x) == 2 &&
    from.y == to.y &&
    ((world.turn == Color.White && from.y == 0) || (world.turn == Color.Black && from.y == 7))

  private def validatePromotion(promotion: Option[PieceType], piece: Piece, to: Square): ValidationResult[Unit] =
    promotion match
      case Some(_) =>
        if piece.kind == PieceType.Pawn then
          if to.y == 0 || to.y == 7 then
            Right(())
          else
            Left(NonEmptyList.one(ChessError.InvalidPromotion))
        else
          Left(NonEmptyList.one(ChessError.InvalidPromotion))
      case None =>
        if piece.kind == PieceType.Pawn && (to.y == 0 || to.y == 7) then
          Left(NonEmptyList.one(ChessError.InvalidPromotion))
        else
          Right(())

// Path validator - validates path clearance for sliding pieces
object PathValidator:

  import logic.BitboardOps

  case class ValidatedPath(
    isPathClear: Boolean,
    attackedSquares: Bitboard
  )

  def validate(world: World, geometry: GeometryValidator.ValidatedGeometry): ValidationResult[ValidatedPath] =
    if geometry.isCastling || geometry.isEnPassant then
      // Special moves don't need path validation in the same way
      Right(ValidatedPath(true, Bitboard.empty))
    else
      val pathClear = isPathClear(world, geometry.from, geometry.to, geometry.piece)
      val attackedSquares = getAttackedSquares(world, geometry.from, geometry.piece)

      if pathClear then
        Right(ValidatedPath(true, attackedSquares))
      else
        Left(NonEmptyList.one(ChessError.PathBlocked))

  private def isPathClear(world: World, from: Square, to: Square, piece: Piece): Boolean =
    piece.kind match
      case PieceType.Knight | PieceType.King =>
        // Knights and kings don't have paths to block
        true
      case PieceType.Pawn =>
        // Pawn moves don't have intermediate squares to check
        true
      case PieceType.Bishop =>
        BitboardOps.isPathClear(from, to, world.occupancy(world.turn).union(world.occupancy(world.turn.opposite)))
      case PieceType.Rook =>
        BitboardOps.isPathClear(from, to, world.occupancy(world.turn).union(world.occupancy(world.turn.opposite)))
      case PieceType.Queen =>
        BitboardOps.isPathClear(from, to, world.occupancy(world.turn).union(world.occupancy(world.turn.opposite)))

  private def getAttackedSquares(world: World, from: Square, piece: Piece): Bitboard =
    val occupied = world.occupancy(Color.White).union(world.occupancy(Color.Black))
    piece.kind match
      case PieceType.Pawn => BitboardOps.getPawnAttacks(from, piece.color)
      case PieceType.Knight => BitboardOps.getKnightAttacks(from)
      case PieceType.Bishop => BitboardOps.getBishopAttacks(from, occupied)
      case PieceType.Rook => BitboardOps.getRookAttacks(from, occupied)
      case PieceType.Queen => BitboardOps.getQueenAttacks(from, occupied)
      case PieceType.King => BitboardOps.getKingAttacks(from)

// Rule validator - validates chess-specific rules
object RuleValidator:

  import logic.MoveGenerator

  case class ValidatedRules(
    isLegal: Boolean,
    wouldBeInCheck: Boolean,
    specialMoveValid: Boolean
  )

  def validate(world: World, geometry: GeometryValidator.ValidatedGeometry): ValidationResult[ValidatedRules] =
    for
      _ <- validateNotInCheckAfterMove(world, geometry)
      _ <- validateCastlingRights(world, geometry)
      _ <- validateEnPassantRights(world, geometry)
    yield ValidatedRules(
      isLegal = true,
      wouldBeInCheck = false,
      specialMoveValid = true
    )

  private def validateNotInCheckAfterMove(world: World, geometry: GeometryValidator.ValidatedGeometry): ValidationResult[Unit] =
    // Create a temporary world with the move applied to check if it would leave king in check
    val tempWorld = applyMove(world, geometry)
    if MoveGenerator.isInCheck(tempWorld, world.turn) then
      Left(NonEmptyList.one(ChessError.KingWouldBeInCheck))
    else
      Right(())

  private def validateCastlingRights(world: World, geometry: GeometryValidator.ValidatedGeometry): ValidationResult[Unit] =
    if geometry.isCastling then
      val (kingFrom, kingTo, rookFrom, rookTo) = getCastlingSquares(world.turn, geometry.to.x > geometry.from.x)

      // Check castling rights
      val hasRights = (world.turn == Color.White && geometry.to.x > geometry.from.x && world.castlingRights.canWhiteKingSide) ||
                     (world.turn == Color.White && geometry.to.x < geometry.from.x && world.castlingRights.canWhiteQueenSide) ||
                     (world.turn == Color.Black && geometry.to.x > geometry.from.x && world.castlingRights.canBlackKingSide) ||
                     (world.turn == Color.Black && geometry.to.x < geometry.from.x && world.castlingRights.canBlackQueenSide)

      if !hasRights then
        Left(NonEmptyList.one(ChessError.InvalidCastlingRights))
      else if world.isOccupied(kingFrom) || world.isOccupied(kingTo) || world.isOccupied(rookFrom) then
        Left(NonEmptyList.one(ChessError.InvalidCastlingRights))
      else if world.isOccupied(rookTo) then
        Left(NonEmptyList.one(ChessError.InvalidCastlingRights))
      else
        Right(())
    else
      Right(())

  private def validateEnPassantRights(world: World, geometry: GeometryValidator.ValidatedGeometry): ValidationResult[Unit] =
    if geometry.isEnPassant then
      if world.enPassantTarget.contains(geometry.to) then
        Right(())
      else
        Left(NonEmptyList.one(ChessError.InvalidEnPassantTarget))
    else
      Right(())

  private def getCastlingSquares(color: Color, kingSide: Boolean): (Square, Square, Square, Square) =
    color match
      case Color.White =>
        if kingSide then
          (Square.fromIndex(4).get, Square.fromIndex(6).get, Square.fromIndex(7).get, Square.fromIndex(5).get)
        else
          (Square.fromIndex(4).get, Square.fromIndex(2).get, Square.fromIndex(0).get, Square.fromIndex(3).get)
      case Color.Black =>
        if kingSide then
          (Square.fromIndex(60).get, Square.fromIndex(62).get, Square.fromIndex(63).get, Square.fromIndex(61).get)
        else
          (Square.fromIndex(60).get, Square.fromIndex(58).get, Square.fromIndex(56).get, Square.fromIndex(59).get)

  // Helper to apply a move for validation purposes
  private def applyMove(world: World, geometry: GeometryValidator.ValidatedGeometry): World =
    val newBoard = world.board
      .updated(geometry.from.index, None)
      .updated(geometry.to.index, Some(geometry.piece))

    val newOccupancy = world.occupancy.updated(
      world.turn,
      world.occupancy(world.turn).remove(geometry.from).add(geometry.to)
    ).updated(
      world.turn.opposite,
      if geometry.isCapture then
        world.occupancy(world.turn.opposite).remove(geometry.to)
      else
        world.occupancy(world.turn.opposite)
    )

    world.copy(
      board = newBoard,
      occupancy = newOccupancy,
      turn = world.turn.opposite
    )