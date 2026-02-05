package pipeline

import cats.parse.{Parser, Parser0}
import cats.parse.Parser.*
import cats.data.NonEmptyList
import domain.*

/**
 * FEN (Forsyth-Edwards Notation) parser and generator.
 * Handles parsing FEN strings into World states and generating FEN from World states.
 */
object fen:

  /**
   * Parse a FEN string into a World state.
   */
  def parseFen(fenString: String): Either[NonEmptyList[ChessError], World] =
    fenString.trim.split(' ').toList match
      case boardStr :: colorStr :: castlingStr :: enPassantStr :: halfMoveStr :: fullMoveStr :: Nil =>
        for
          board <- parseBoard(boardStr)
          color <- parseActiveColor(colorStr)
          castling <- parseCastlingRights(castlingStr)
          enPassant <- parseEnPassant(enPassantStr)
          halfMove <- parseHalfMoveClock(halfMoveStr)
          fullMove <- parseFullMoveNumber(fullMoveStr)
        yield createWorld(board, color, castling, enPassant, halfMove, fullMove)
      case _ => Left(NonEmptyList.one(ChessError.ParsingFailure(fenString, "Invalid FEN format")))

  /**
   * Generate a FEN string from a World state.
   */
  def generateFen(world: World): String =
    val board = generateBoard(world)
    val color = generateActiveColor(world)
    val castling = generateCastlingRights(world)
    val enPassant = generateEnPassant(world)
    val halfMove = world.halfMoveClock.toString
    val fullMove = ((world.history.length / 2) + 1).toString
    s"$board $color $castling $enPassant $halfMove $fullMove"

  // Board parsing and generation

  private def parseBoard(boardStr: String): Either[NonEmptyList[ChessError], Vector[Option[Piece]]] =
    boardStr.split('/').toList match
      case ranks if ranks.length == 8 =>
        // FEN ranks are in reverse order (rank 8 to rank 1), but we store board as rank 0 to 7
        ranks.reverse.zipWithIndex.foldLeft(Right(Vector.empty[Option[Piece]]): Either[NonEmptyList[ChessError], Vector[Option[Piece]]]) {
          case (Right(acc), (rankStr, rankIndex)) =>
            parseRank(rankStr, rankIndex).map(acc ++ _)
          case (left, _) => left
        }
      case _ => Left(NonEmptyList.one(ChessError.ParsingFailure(boardStr, "Board must have 8 ranks")))

  private def parseRank(rankStr: String, rankIndex: Int): Either[NonEmptyList[ChessError], Vector[Option[Piece]]] =
    rankStr.foldLeft(Right(Vector.empty[Option[Piece]]): Either[NonEmptyList[ChessError], Vector[Option[Piece]]]) {
      case (Right(acc), char) if char.isDigit =>
        val emptyCount = char - '0'
        Right(acc ++ Vector.fill(emptyCount)(None))
      case (Right(acc), char) =>
        parsePiece(char) match
          case Some(piece) => Right(acc :+ Some(piece))
          case None => Left(NonEmptyList.one(ChessError.ParsingFailure(rankStr, s"Invalid piece character: $char")))
      case (left, _) => left
    }.flatMap { pieces =>
      if pieces.length == 8 then Right(pieces)
      else Left(NonEmptyList.one(ChessError.ParsingFailure(rankStr, s"Rank must have 8 squares, got ${pieces.length}")))
    }

  private def parsePiece(char: Char): Option[Piece] =
    val pieceType = char.toLower match
      case 'p' => Some(PieceType.Pawn)
      case 'n' => Some(PieceType.Knight)
      case 'b' => Some(PieceType.Bishop)
      case 'r' => Some(PieceType.Rook)
      case 'q' => Some(PieceType.Queen)
      case 'k' => Some(PieceType.King)
      case _ => None

    val color = if char.isUpper then Color.White else Color.Black

    pieceType.map(Piece(_, color))

  private def generateBoard(world: World): String =
    (0 until 8).map { rank =>
      val rankPieces = (0 until 8).map { file =>
        val squareIndex = (7 - rank) * 8 + file  // Reverse rank order (FEN starts from rank 8)
        world.board(squareIndex)
      }.toList

      // Compress consecutive empty squares
      rankPieces.foldLeft("" -> 0) { case ((acc, emptyCount), pieceOpt) =>
        pieceOpt match
          case Some(piece) =>
            val result = if emptyCount > 0 then acc + emptyCount.toString + piece.toFen else acc + piece.toFen
            (result, 0)
          case None =>
            (acc, emptyCount + 1)
      } match
        case (acc, emptyCount) =>
          if emptyCount > 0 then acc + emptyCount.toString else acc
    }.mkString("/")

  // Active color parsing and generation

  private def parseActiveColor(colorStr: String): Either[NonEmptyList[ChessError], Color] =
    colorStr match
      case "w" => Right(Color.White)
      case "b" => Right(Color.Black)
      case _ => Left(NonEmptyList.one(ChessError.ParsingFailure(colorStr, "Active color must be 'w' or 'b'")))

  private def generateActiveColor(world: World): String =
    world.turn.toFen.toString

  // Castling rights parsing and generation

  private def parseCastlingRights(castlingStr: String): Either[NonEmptyList[ChessError], CastlingRights] =
    if castlingStr == "-" then
      Right(CastlingRights.none)
    else
      val rights = castlingStr.toSet
      val validRights = Set('K', 'Q', 'k', 'q')

      if rights.subsetOf(validRights) then
        val whiteKingSide = rights.contains('K')
        val whiteQueenSide = rights.contains('Q')
        val blackKingSide = rights.contains('k')
        val blackQueenSide = rights.contains('q')

        var castlingValue: Byte = 0
        if whiteKingSide then castlingValue = (castlingValue | 1).toByte
        if whiteQueenSide then castlingValue = (castlingValue | 2).toByte
        if blackKingSide then castlingValue = (castlingValue | 4).toByte
        if blackQueenSide then castlingValue = (castlingValue | 8).toByte

        Right(castlingValue.asInstanceOf[CastlingRights])
      else
        Left(NonEmptyList.one(ChessError.ParsingFailure(castlingStr, "Invalid castling rights")))

  private def generateCastlingRights(world: World): String =
    val rights = List(
      if world.castlingRights.canWhiteKingSide then Some('K') else None,
      if world.castlingRights.canWhiteQueenSide then Some('Q') else None,
      if world.castlingRights.canBlackKingSide then Some('k') else None,
      if world.castlingRights.canBlackQueenSide then Some('q') else None
    ).flatten

    if rights.isEmpty then "-" else rights.mkString

  // En passant parsing and generation

  private def parseEnPassant(enPassantStr: String): Either[NonEmptyList[ChessError], Option[Square]] =
    if enPassantStr == "-" then
      Right(None)
    else
      Position.fromAlgebraic(enPassantStr).flatMap(_.toSquare) match
        case Some(square) => Right(Some(square))
        case None => Left(NonEmptyList.one(ChessError.ParsingFailure(enPassantStr, "Invalid en passant square")))

  private def generateEnPassant(world: World): String =
    world.enPassantTarget match
      case Some(square) => Position(square.x, square.y).toAlgebraic
      case None => "-"

  // Halfmove clock parsing

  private def parseHalfMoveClock(halfMoveStr: String): Either[NonEmptyList[ChessError], Int] =
    try
      val value = halfMoveStr.toInt
      if value >= 0 then Right(value)
      else Left(NonEmptyList.one(ChessError.ParsingFailure(halfMoveStr, "Halfmove clock must be non-negative")))
    catch
      case _: NumberFormatException =>
        Left(NonEmptyList.one(ChessError.ParsingFailure(halfMoveStr, "Invalid halfmove clock")))

  // Fullmove number parsing

  private def parseFullMoveNumber(fullMoveStr: String): Either[NonEmptyList[ChessError], Int] =
    try
      val value = fullMoveStr.toInt
      if value > 0 then Right(value)
      else Left(NonEmptyList.one(ChessError.ParsingFailure(fullMoveStr, "Fullmove number must be positive")))
    catch
      case _: NumberFormatException =>
        Left(NonEmptyList.one(ChessError.ParsingFailure(fullMoveStr, "Invalid fullmove number")))

  // World creation from parsed components

  private def createWorld(
    board: Vector[Option[Piece]],
    turn: Color,
    castlingRights: CastlingRights,
    enPassantTarget: Option[Square],
    halfMoveClock: Int,
    fullMoveNumber: Int
  ): World =
    // Create occupancy bitboards from board
    val whiteOccupied = Bitboard.fromSquares(
      board.zipWithIndex.collect {
        case (Some(piece), index) if piece.color == Color.White =>
          Square.fromIndex(index).get
      }: _*
    )

    val blackOccupied = Bitboard.fromSquares(
      board.zipWithIndex.collect {
        case (Some(piece), index) if piece.color == Color.Black =>
          Square.fromIndex(index).get
      }: _*
    )

    val occupancy = Map(Color.White -> whiteOccupied, Color.Black -> blackOccupied)

    World(
      board = board,
      occupancy = occupancy,
      history = Nil, // FEN doesn't include history
      turn = turn,
      castlingRights = castlingRights,
      enPassantTarget = enPassantTarget,
      halfMoveClock = halfMoveClock
    )