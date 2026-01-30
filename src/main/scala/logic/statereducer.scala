package logic

import domain._
import monocle.Lens
import monocle.macros.GenLens
import cats.data.NonEmptyList
import MoveGenerator.toPosition

// State reducer - applies validated moves to world state using event sourcing
object StateReducer:

  // Apply a validated move to the world state, producing events and new state
  def applyMove(world: World, validatedMove: pipeline.ValidationPipeline.ValidatedMove): Either[NonEmptyList[ChessError], (World, MoveEvent)] =
    val geometry = validatedMove.geometry
    val moveIntent = MoveIntent(geometry.from.toPosition, geometry.to.toPosition, geometry.promotion)

    // Determine the type of move and create appropriate event
    val (newWorld, event) = if geometry.isCastling then
      applyCastling(world, geometry)
    else if geometry.isEnPassant then
      applyEnPassant(world, geometry)
    else if geometry.promotion.isDefined then
      applyPromotion(world, geometry)
    else
      applyRegularMove(world, geometry)

    Right((newWorld, event))

  private def applyRegularMove(world: World, geometry: pipeline.GeometryValidator.ValidatedGeometry): (World, MoveEvent) =
    val piece = geometry.piece
    val fromSquare = geometry.from
    val toSquare = geometry.to

    // Update board
    val newBoard = world.board
      .updated(fromSquare.index, None)
      .updated(toSquare.index, Some(piece))

    // Update occupancy bitboards
    val newOccupancy = world.occupancy
      .updated(world.turn, world.occupancy(world.turn).remove(fromSquare).add(toSquare))
      .updated(
        world.turn.opposite,
        if geometry.isCapture then
          world.occupancy(world.turn.opposite).remove(toSquare)
        else
          world.occupancy(world.turn.opposite)
      )

    // Update turn and other state
    val newWorld = world.copy(
      board = newBoard,
      occupancy = newOccupancy,
      turn = world.turn.opposite,
      enPassantTarget = None, // Clear en passant target
      halfMoveClock = if geometry.isCapture || piece.kind == PieceType.Pawn then 0 else world.halfMoveClock + 1
    )

    val event = MoveExecuted(
      from = fromSquare,
      to = toSquare,
      piece = piece,
      capturedPiece = if geometry.isCapture then world.pieceAt(toSquare) else None,
      promotion = geometry.promotion,
      castlingRightsBefore = world.castlingRights,
      enPassantTargetBefore = world.enPassantTarget
    )

    (newWorld, event)

  private def applyCastling(world: World, geometry: pipeline.GeometryValidator.ValidatedGeometry): (World, MoveEvent) =
    val (kingFrom, kingTo, rookFrom, rookTo) = getCastlingSquares(world.turn, geometry.to.x > geometry.from.x)

    val king = geometry.piece
    val rook = world.pieceAt(rookFrom).get // Should be safe due to validation

    // Move king and rook
    val newBoard = world.board
      .updated(kingFrom.index, None)
      .updated(kingTo.index, Some(king))
      .updated(rookFrom.index, None)
      .updated(rookTo.index, Some(rook))

    // Update occupancy
    val newOccupancy = world.occupancy
      .updated(world.turn, world.occupancy(world.turn).remove(kingFrom).add(kingTo))
      // Rook movement doesn't change occupancy since it's still the same color

    // Update castling rights
    val newCastlingRights = updateCastlingRights(world.castlingRights, world.turn)

    val newWorld = world.copy(
      board = newBoard,
      occupancy = newOccupancy,
      castlingRights = newCastlingRights,
      turn = world.turn.opposite,
      enPassantTarget = None,
      halfMoveClock = world.halfMoveClock + 1
    )

    val event = CastlingExecuted(
      kingFrom = kingFrom,
      kingTo = kingTo,
      rookFrom = rookFrom,
      rookTo = rookTo,
      side = if geometry.to.x > geometry.from.x then CastlingSide.KingSide else CastlingSide.QueenSide
    )

    (newWorld, event)

  private def applyEnPassant(world: World, geometry: pipeline.GeometryValidator.ValidatedGeometry): (World, MoveEvent) =
    val piece = geometry.piece
    val fromSquare = geometry.from
    val toSquare = geometry.to

    // Remove the captured pawn (which is on a different square than the destination)
    val capturedPawnSquare = Square.from(toSquare.x, fromSquare.y).get
    val capturedPawn = world.pieceAt(capturedPawnSquare).get

    // Update board
    val newBoard = world.board
      .updated(fromSquare.index, None)
      .updated(toSquare.index, Some(piece))
      .updated(capturedPawnSquare.index, None)

    // Update occupancy
    val newOccupancy = world.occupancy
      .updated(world.turn, world.occupancy(world.turn).remove(fromSquare).add(toSquare))
      .updated(world.turn.opposite, world.occupancy(world.turn.opposite).remove(capturedPawnSquare))

    val newWorld = world.copy(
      board = newBoard,
      occupancy = newOccupancy,
      turn = world.turn.opposite,
      enPassantTarget = None,
      halfMoveClock = 0 // Pawn capture resets halfmove clock
    )

    val event = EnPassantExecuted(
      pawnFrom = fromSquare,
      pawnTo = toSquare,
      capturedPawnSquare = capturedPawnSquare
    )

    (newWorld, event)

  private def applyPromotion(world: World, geometry: pipeline.GeometryValidator.ValidatedGeometry): (World, MoveEvent) =
    val pawn = geometry.piece
    val promotedPiece = Piece(geometry.promotion.get, world.turn)
    val fromSquare = geometry.from
    val toSquare = geometry.to

    // Update board
    val newBoard = world.board
      .updated(fromSquare.index, None)
      .updated(toSquare.index, Some(promotedPiece))

    // Update occupancy (same as regular move)
    val newOccupancy = world.occupancy
      .updated(world.turn, world.occupancy(world.turn).remove(fromSquare).add(toSquare))
      .updated(
        world.turn.opposite,
        if geometry.isCapture then
          world.occupancy(world.turn.opposite).remove(toSquare)
        else
          world.occupancy(world.turn.opposite)
      )

    val newWorld = world.copy(
      board = newBoard,
      occupancy = newOccupancy,
      turn = world.turn.opposite,
      enPassantTarget = None,
      halfMoveClock = 0 // Promotion resets halfmove clock
    )

    val event = PromotionExecuted(
      from = fromSquare,
      to = toSquare,
      promotedTo = geometry.promotion.get
    )

    (newWorld, event)

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

  private def updateCastlingRights(rights: CastlingRights, color: Color): CastlingRights =
    color match
      case Color.White =>
        rights.removeWhiteKingSide.removeWhiteQueenSide
      case Color.Black =>
        rights.removeBlackKingSide.removeBlackQueenSide

  // Set en passant target for double pawn moves (called after move validation)
  def setEnPassantTarget(world: World, from: Square, to: Square, piece: Piece): Option[Square] =
    if piece.kind == PieceType.Pawn && math.abs(from.y - to.y) == 2 then
      val enPassantY = (from.y + to.y) / 2
      Some(Square.from(from.x, enPassantY).get)
    else
      None

// Monocle lenses for immutable World updates
object WorldLenses:
  val board: Lens[World, Vector[Option[Piece]]] = GenLens[World](_.board)
  val occupancy: Lens[World, Map[Color, Bitboard]] = GenLens[World](_.occupancy)
  val turn: Lens[World, Color] = GenLens[World](_.turn)
  val castlingRights: Lens[World, CastlingRights] = GenLens[World](_.castlingRights)
  val enPassantTarget: Lens[World, Option[Square]] = GenLens[World](_.enPassantTarget)
  val halfMoveClock: Lens[World, Int] = GenLens[World](_.halfMoveClock)

  // Convenience lenses for occupancy by color
  def occupancy(color: Color): Lens[World, Bitboard] =
    occupancy.andThen(Lens[Map[Color, Bitboard], Bitboard](
      _.getOrElse(color, Bitboard.empty)
    )(bb => map => map.updated(color, bb)))

  // Lens for specific board square
  def boardSquare(square: Square): Lens[World, Option[Piece]] =
    board.andThen(Lens[Vector[Option[Piece]], Option[Piece]](
      _.lift(square.index).flatten
    )(piece => board => board.updated(square.index, piece)))

  extension (s: Square)
    def toPosition: Position = Position(s.x, s.y)