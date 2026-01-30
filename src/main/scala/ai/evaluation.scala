package ai

import domain._

// Static evaluation function for chess positions
object Evaluation:

  // Piece values in centipawns (1/100th of a pawn)
  private val PAWN_VALUE = 100
  private val KNIGHT_VALUE = 320
  private val BISHOP_VALUE = 330
  private val ROOK_VALUE = 500
  private val QUEEN_VALUE = 900
  private val KING_VALUE = 20000 // High value to ensure king safety

  // Piece-square tables for positional evaluation
  // Values are in centipawns, positive for white, negative for black
  private val PAWN_TABLE = Array(
    0,   0,   0,   0,   0,   0,   0,   0,
    50,  50,  50,  50,  50,  50,  50,  50,
    10,  10,  20,  30,  30,  20,  10,  10,
    5,   5,   10,  25,  25,  10,  5,   5,
    0,   0,   0,   20,  20,  0,   0,   0,
    5,   -5,  -10, 0,   0,   -10, -5,  5,
    5,   10,  10,  -20, -20, 10,  10,  5,
    0,   0,   0,   0,   0,   0,   0,   0
  )

  private val KNIGHT_TABLE = Array(
    -50, -40, -30, -30, -30, -30, -40, -50,
    -40, -20, 0,   0,   0,   0,   -20, -40,
    -30, 0,   10,  15,  15,  10,  0,   -30,
    -30, 5,   15,  20,  20,  15,  5,   -30,
    -30, 0,   15,  20,  20,  15,  0,   -30,
    -30, 5,   10,  15,  15,  10,  5,   -30,
    -40, -20, 0,   5,   5,   0,   -20, -40,
    -50, -40, -30, -30, -30, -30, -40, -50
  )

  private val BISHOP_TABLE = Array(
    -20, -10, -10, -10, -10, -10, -10, -20,
    -10, 0,   0,   0,   0,   0,   0,   -10,
    -10, 0,   5,   10,  10,  5,   0,   -10,
    -10, 5,   5,   10,  10,  5,   5,   -10,
    -10, 0,   10,  10,  10,  10,  0,   -10,
    -10, 10,  10,  10,  10,  10,  10,  -10,
    -10, 5,   0,   0,   0,   0,   5,   -10,
    -20, -10, -10, -10, -10, -10, -10, -20
  )

  private val ROOK_TABLE = Array(
    0,  0,  0,  0,  0,  0,  0,  0,
    5,  10, 10, 10, 10, 10, 10, 5,
    -5, 0,  0,  0,  0,  0,  0,  -5,
    -5, 0,  0,  0,  0,  0,  0,  -5,
    -5, 0,  0,  0,  0,  0,  0,  -5,
    -5, 0,  0,  0,  0,  0,  0,  -5,
    -5, 0,  0,  0,  0,  0,  0,  -5,
    0,  0,  0,  5,  5,  0,  0,  0
  )

  private val QUEEN_TABLE = Array(
    -20, -10, -10, -5, -5, -10, -10, -20,
    -10, 0,   0,   0,  0,  0,   0,   -10,
    -10, 0,   5,   5,  5,  5,   0,   -10,
    -5,  0,   5,   5,  5,  5,   0,   -5,
    0,   0,   5,   5,  5,  5,   0,   -5,
    -10, 5,   5,   5,  5,  5,   0,   -10,
    -10, 0,   5,   0,  0,  0,   0,   -10,
    -20, -10, -10, -5, -5, -10, -10, -20
  )

  private val KING_MIDDLE_TABLE = Array(
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -20, -30, -30, -40, -40, -30, -30, -20,
    -10, -20, -20, -20, -20, -20, -20, -10,
    20,  20,  0,   0,   0,   0,   20,  20,
    20,  30,  10,  0,   0,   10,  30,  20
  )

  private val KING_END_TABLE = Array(
    -50, -40, -30, -20, -20, -30, -40, -50,
    -30, -20, -10, 0,   0,   -10, -20, -30,
    -30, -10, 20,  30,  30,  20,  -10, -30,
    -30, -10, 30,  40,  40,  30,  -10, -30,
    -30, -10, 30,  40,  40,  30,  -10, -30,
    -30, -10, 20,  30,  30,  20,  -10, -30,
    -30, -30, 0,   0,   0,   0,   -30, -30,
    -50, -30, -30, -30, -30, -30, -30, -50
  )

  // Main evaluation function - returns score in centipawns from white's perspective
  def evaluate(world: World): Int =
    val material = evaluateMaterial(world)
    val positional = evaluatePositional(world)
    val mobility = evaluateMobility(world)
    val kingSafety = evaluateKingSafety(world)

    material + positional + mobility + kingSafety

  // Evaluate material balance
  private def evaluateMaterial(world: World): Int =
    world.board.zipWithIndex.foldLeft(0) { case (score, (pieceOpt, squareIndex)) =>
      pieceOpt match
        case Some(piece) =>
          val pieceVal = getPieceValue(piece.kind)
          val squareBonus = pieceSquareValue(piece, squareIndex, world)
          val colorMultiplier = if piece.color == Color.White then 1 else -1
          score + (pieceVal + squareBonus) * colorMultiplier
        case None => score
    }

  // Get the base material value of a piece
  private def getPieceValue(pieceType: PieceType): Int = pieceType match
    case PieceType.Pawn => PAWN_VALUE
    case PieceType.Knight => KNIGHT_VALUE
    case PieceType.Bishop => BISHOP_VALUE
    case PieceType.Rook => ROOK_VALUE
    case PieceType.Queen => QUEEN_VALUE
    case PieceType.King => KING_VALUE

  // Get piece-square table value for a piece at a square
  private def pieceSquareValue(piece: Piece, squareIndex: Int, world: World): Int =
    val tableIndex = if piece.color == Color.White then squareIndex else (63 - squareIndex)
    val tableValue = piece.kind match
      case PieceType.Pawn => PAWN_TABLE(tableIndex)
      case PieceType.Knight => KNIGHT_TABLE(tableIndex)
      case PieceType.Bishop => BISHOP_TABLE(tableIndex)
      case PieceType.Rook => ROOK_TABLE(tableIndex)
      case PieceType.Queen => QUEEN_TABLE(tableIndex)
      case PieceType.King =>
        // Use different king tables for middle/end game
        val endGame = isEndGamePosition(world)
        if endGame then KING_END_TABLE(tableIndex) else KING_MIDDLE_TABLE(tableIndex)

    tableValue

  // Evaluate positional factors
  private def evaluatePositional(world: World): Int =
    // For now, piece-square tables handle most positional evaluation
    // Could add more factors like pawn structure, piece coordination, etc.
    0

  // Evaluate piece mobility (number of legal moves)
  private def evaluateMobility(world: World): Int =
    import logic.MoveGenerator
    val legalMoves = MoveGenerator.generateLegalMoves(world)
    // Each legal move is worth about 1 centipawn
    legalMoves.length

  // Evaluate king safety
  private def evaluateKingSafety(world: World): Int =
    // Basic king safety - penalize exposed kings
    var safetyScore = 0

    for color <- List(Color.White, Color.Black) do
      world.kingPosition(color).foreach { kingSquare =>
        // Check if king is in check
        if logic.MoveGenerator.isInCheck(world, color) then
          safetyScore += (if color == Color.White then -50 else 50)

        // Check pawn shield
        val pawnShieldBonus = evaluatePawnShield(world, kingSquare, color)
        safetyScore += (if color == Color.White then pawnShieldBonus else -pawnShieldBonus)
      }

    safetyScore

  // Evaluate pawn shield around the king
  private def evaluatePawnShield(world: World, kingSquare: Square, color: Color): Int =
    val direction = if color == Color.White then 1 else -1
    var shieldScore = 0

    // Check pawns in front of king
    for dx <- -1 to 1 do
      val shieldSquare = Square.from(kingSquare.x + dx, kingSquare.y + direction)
      shieldSquare.foreach { sq =>
        world.pieceAt(sq) match
          case Some(Piece(PieceType.Pawn, c)) if c == color =>
            shieldScore += 10 // Bonus for pawn shield
          case _ => ()
      }

    shieldScore

  // Determine if the position is in endgame
  private def isEndGamePosition(world: World): Boolean =
    // Simple heuristic: endgame if both sides have few pieces
    val whitePieces = world.board.count(_.exists(_.color == Color.White))
    val blackPieces = world.board.count(_.exists(_.color == Color.Black))

    // Consider it endgame if each side has 12 or fewer pieces (including pawns)
    whitePieces <= 12 && blackPieces <= 12

  // Check if a position is a draw
  def isDraw(world: World): Boolean =
    // Insufficient material
    val whitePieces = world.board.filter(_.exists(_.color == Color.White))
    val blackPieces = world.board.filter(_.exists(_.color == Color.Black))

    // King vs King
    if whitePieces.length == 1 && blackPieces.length == 1 then
      true
    // King and minor piece vs King
    else if (whitePieces.length == 2 && blackPieces.length == 1) ||
            (whitePieces.length == 1 && blackPieces.length == 2) then
      val minorPieces = world.board.filter(piece =>
        piece.exists(p => p.kind == PieceType.Knight || p.kind == PieceType.Bishop)
      )
      minorPieces.length == 1
    else
      false

  // Check if a position is a checkmate
  def isCheckmate(world: World): Boolean =
    import logic.MoveGenerator
    val inCheck = MoveGenerator.isInCheck(world, world.turn)
    val noLegalMoves = MoveGenerator.generateLegalMoves(world).isEmpty
    inCheck && noLegalMoves

  // Check if a position is a stalemate
  def isStalemate(world: World): Boolean =
    import logic.MoveGenerator
    val notInCheck = !MoveGenerator.isInCheck(world, world.turn)
    val noLegalMoves = MoveGenerator.generateLegalMoves(world).isEmpty
    notInCheck && noLegalMoves