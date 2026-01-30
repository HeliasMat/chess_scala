package logic

import domain._

// Move generation system - generates all legal moves for a given position
object MoveGenerator:

  // Generate all legal moves for the current player
  def generateLegalMoves(world: World): List[MoveIntent] =
    val pseudoLegal = generatePseudoLegalMoves(world)
    // Filter out moves that would leave king in check
    pseudoLegal.filter(move => !wouldBeInCheck(world, move))

  // Generate pseudo-legal moves (not checking for king safety)
  private def generatePseudoLegalMoves(world: World): List[MoveIntent] =
    val color = world.turn
    val occupied = BitboardOps.getAllOccupied(world)
    val enemyOccupied = BitboardOps.getOccupiedByColor(world, color.opposite)

    // Generate moves for all pieces of the current color
    world.board.zipWithIndex.collect {
      case (Some(piece), squareIndex) if piece.color == color =>
        val square = Square.fromIndex(squareIndex).get
        piece.kind match
          case PieceType.Pawn => generatePawnMoves(world, square, piece)
          case PieceType.Knight => generateKnightMoves(world, square, occupied, enemyOccupied)
          case PieceType.Bishop => generateBishopMoves(world, square, occupied, enemyOccupied)
          case PieceType.Rook => generateRookMoves(world, square, occupied, enemyOccupied)
          case PieceType.Queen => generateQueenMoves(world, square, occupied, enemyOccupied)
          case PieceType.King => generateKingMoves(world, square, occupied, enemyOccupied)
    }.flatten.toList

  private def generatePawnMoves(world: World, square: Square, piece: Piece): List[MoveIntent] =
    val color = piece.color
    val direction = if color == Color.White then 1 else -1
    val startRank = if color == Color.White then 1 else 6
    val promotionRank = if color == Color.White then 7 else 0

    var moves = List[MoveIntent]()

    val oneStep = Position(square.x, square.y + direction)
    if oneStep.isValid && !world.isOccupied(oneStep.toSquare.get) then
      if oneStep.y == promotionRank then
        // Promotion moves
        moves = moves ++ List(
          MoveIntent(square.toPosition, oneStep, Some(PieceType.Queen)),
          MoveIntent(square.toPosition, oneStep, Some(PieceType.Rook)),
          MoveIntent(square.toPosition, oneStep, Some(PieceType.Bishop)),
          MoveIntent(square.toPosition, oneStep, Some(PieceType.Knight))
        )
      else
        moves = MoveIntent(square.toPosition, oneStep, None) :: moves

      // Two-step move from starting position
      if square.y == startRank then
        val twoStep = Position(square.x, square.y + 2 * direction)
        if twoStep.isValid && !world.isOccupied(twoStep.toSquare.get) then
          moves = MoveIntent(square.toPosition, twoStep, None) :: moves

    // Captures
    for dx <- List(-1, 1) do
      val capturePos = Position(square.x + dx, square.y + direction)
      if capturePos.isValid then
        val captureSquare = capturePos.toSquare.get
        // Regular capture
        if world.isOccupiedBy(captureSquare, color.opposite) then
          if capturePos.y == promotionRank then
            moves = moves ++ List(
              MoveIntent(square.toPosition, capturePos, Some(PieceType.Queen)),
              MoveIntent(square.toPosition, capturePos, Some(PieceType.Rook)),
              MoveIntent(square.toPosition, capturePos, Some(PieceType.Bishop)),
              MoveIntent(square.toPosition, capturePos, Some(PieceType.Knight))
            )
          else
            moves = MoveIntent(square.toPosition, capturePos, None) :: moves
        // En passant capture
        else if world.enPassantTarget.contains(captureSquare) then
          moves = MoveIntent(square.toPosition, capturePos, None) :: moves

    moves

  private def generateKnightMoves(world: World, square: Square, occupied: Bitboard, enemyOccupied: Bitboard): List[MoveIntent] =
    val attacks = BitboardOps.getKnightAttacks(square)
    val validMoves = attacks.difference(world.occupancy(world.turn))
    validMoves.squares.map(target =>
      MoveIntent(square.toPosition, target.toPosition, None)
    )

  private def generateBishopMoves(world: World, square: Square, occupied: Bitboard, enemyOccupied: Bitboard): List[MoveIntent] =
    val attacks = BitboardOps.getBishopAttacks(square, occupied)
    attacks.squares.map(target =>
      MoveIntent(square.toPosition, target.toPosition, None)
    )

  private def generateRookMoves(world: World, square: Square, occupied: Bitboard, enemyOccupied: Bitboard): List[MoveIntent] =
    val attacks = BitboardOps.getRookAttacks(square, occupied)
    attacks.squares.map(target =>
      MoveIntent(square.toPosition, target.toPosition, None)
    )

  private def generateQueenMoves(world: World, square: Square, occupied: Bitboard, enemyOccupied: Bitboard): List[MoveIntent] =
    val attacks = BitboardOps.getQueenAttacks(square, occupied)
    attacks.squares.map(target =>
      MoveIntent(square.toPosition, target.toPosition, None)
    )

  private def generateKingMoves(world: World, square: Square, occupied: Bitboard, enemyOccupied: Bitboard): List[MoveIntent] =
    var moves = List[MoveIntent]()

    // Regular king moves
    val attacks = BitboardOps.getKingAttacks(square)
    val validMoves = attacks.difference(world.occupancy(world.turn))
    moves = moves ++ validMoves.squares.map(target =>
      MoveIntent(square.toPosition, target.toPosition, None)
    )

    // Castling moves
    if world.turn == Color.White then
      // Kingside castling
      if world.castlingRights.canWhiteKingSide &&
         !world.isOccupied(Position(5, 0).toSquare.get) &&
         !world.isOccupied(Position(6, 0).toSquare.get) &&
         world.pieceAt(Position(7, 0).toSquare.get).contains(Piece(PieceType.Rook, Color.White)) then
        moves = MoveIntent(square.toPosition, Position(6, 0), None) :: moves

      // Queenside castling
      if world.castlingRights.canWhiteQueenSide &&
         !world.isOccupied(Position(3, 0).toSquare.get) &&
         !world.isOccupied(Position(2, 0).toSquare.get) &&
         !world.isOccupied(Position(1, 0).toSquare.get) &&
         world.pieceAt(Position(0, 0).toSquare.get).contains(Piece(PieceType.Rook, Color.White)) then
        moves = MoveIntent(square.toPosition, Position(2, 0), None) :: moves
    else
      // Kingside castling for black
      if world.castlingRights.canBlackKingSide &&
         !world.isOccupied(Position(5, 7).toSquare.get) &&
         !world.isOccupied(Position(6, 7).toSquare.get) &&
         world.pieceAt(Position(7, 7).toSquare.get).contains(Piece(PieceType.Rook, Color.Black)) then
        moves = MoveIntent(square.toPosition, Position(6, 7), None) :: moves

      // Queenside castling for black
      if world.castlingRights.canBlackQueenSide &&
         !world.isOccupied(Position(3, 7).toSquare.get) &&
         !world.isOccupied(Position(2, 7).toSquare.get) &&
         !world.isOccupied(Position(1, 7).toSquare.get) &&
         world.pieceAt(Position(0, 7).toSquare.get).contains(Piece(PieceType.Rook, Color.Black)) then
        moves = MoveIntent(square.toPosition, Position(2, 7), None) :: moves

    moves

  // Check if a move would leave the king in check
  private def wouldBeInCheck(world: World, move: MoveIntent): Boolean =
    // Create a temporary world with the move applied
    val tempWorld = applyMove(world, move)
    isInCheck(tempWorld, world.turn)

  // Apply a move to create a temporary world (for check detection)
  private def applyMove(world: World, move: MoveIntent): World =
    val fromSquare = move.from.toSquare.get
    val toSquare = move.to.toSquare.get
    val piece = world.pieceAt(fromSquare).get

    // Simple move application (doesn't handle all special cases for check detection)
    val newBoard = world.board
      .updated(fromSquare.index, None)
      .updated(toSquare.index, Some(piece))

    val newOccupancy = world.occupancy.updated(
      world.turn,
      world.occupancy(world.turn).remove(fromSquare).add(toSquare)
    ).updated(
      world.turn.opposite,
      if world.isOccupiedBy(toSquare, world.turn.opposite) then
        world.occupancy(world.turn.opposite).remove(toSquare)
      else
        world.occupancy(world.turn.opposite)
    )

    world.copy(
      board = newBoard,
      occupancy = newOccupancy,
      turn = world.turn.opposite
    )

  // Check if a color is currently in check
  def isInCheck(world: World, color: Color): Boolean =
    world.kingPosition(color).exists(kingSquare =>
      // Check if any enemy piece attacks the king
      val enemyColor = color.opposite
      world.board.zipWithIndex.exists {
        case (Some(piece), squareIndex) if piece.color == enemyColor =>
          val square = Square.fromIndex(squareIndex).get
          attacksSquare(world, square, kingSquare)
        case _ => false
      }
    )

  // Check if a piece on 'from' attacks 'to' square
  private def attacksSquare(world: World, from: Square, to: Square): Boolean =
    world.pieceAt(from) match
      case Some(piece) =>
        val occupied = BitboardOps.getAllOccupied(world)
        piece.kind match
          case PieceType.Pawn =>
            val attacks = BitboardOps.getPawnAttacks(from, piece.color)
            attacks.contains(to)
          case PieceType.Knight =>
            val attacks = BitboardOps.getKnightAttacks(from)
            attacks.contains(to)
          case PieceType.Bishop =>
            val attacks = BitboardOps.getBishopAttacks(from, occupied)
            attacks.contains(to)
          case PieceType.Rook =>
            val attacks = BitboardOps.getRookAttacks(from, occupied)
            attacks.contains(to)
          case PieceType.Queen =>
            val attacks = BitboardOps.getQueenAttacks(from, occupied)
            attacks.contains(to)
          case PieceType.King =>
            val attacks = BitboardOps.getKingAttacks(from)
            attacks.contains(to)
      case None => false

  extension (s: Square)
    def toPosition: Position = Position(s.x, s.y)