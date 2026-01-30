package domain

enum Color:
  case White, Black

  def opposite: Color = this match
    case White => Black
    case Black => White

  def toFen: Char = this match
    case White => 'w'
    case Black => 'b'

enum PieceType:
  case Pawn, Knight, Bishop, Rook, Queen, King

  def toFen: Char = this match
    case Pawn => 'p'
    case Knight => 'n'
    case Bishop => 'b'
    case Rook => 'r'
    case Queen => 'q'
    case King => 'k'

  def value: Int = this match
    case Pawn => 100
    case Knight => 320
    case Bishop => 330
    case Rook => 500
    case Queen => 900
    case King => 0 // King has no material value in evaluation

case class Piece(kind: PieceType, color: Color):
  def toFen: Char =
    val base = kind.toFen
    if color == Color.White then base.toUpper else base

  def value: Int = kind.value

  override def toString: String = s"${color.toString.head}${kind.toString}"

case class Position(x: Int, y: Int):
  require(x >= 0 && x < 8, s"x must be between 0 and 7, got $x")
  require(y >= 0 && y < 8, s"y must be between 0 and 7, got $y")

  def toSquare: Option[Square] = Square.from(x, y)

  def +(other: Position): Position = Position(x + other.x, y + other.y)
  def -(other: Position): Position = Position(x - other.x, y - other.y)

  def isValid: Boolean = x >= 0 && x < 8 && y >= 0 && y < 8

  def toAlgebraic: String =
    val file = ('a' + x).toChar
    val rank = ('1' + y).toChar
    s"$file$rank"

  override def toString: String = s"($x,$y)"

object Position:
  def fromAlgebraic(algebraic: String): Option[Position] =
    if algebraic.length == 2 then
      val file = algebraic.charAt(0)
      val rank = algebraic.charAt(1)
      if file >= 'a' && file <= 'h' && rank >= '1' && rank <= '8' then
        Some(Position(file - 'a', rank - '1'))
      else None
    else None

  val all: List[Position] =
    (for
      y <- 0 until 8
      x <- 0 until 8
    yield Position(x, y)).toList

case class MoveIntent(
  from: Position,
  to: Position,
  promotion: Option[PieceType]
)

case class World(
  // High-level representation for UI/Logic
  board: Vector[Option[Piece]],

  // Low-level representation for Move Gen (Bitboards)
  occupancy: Map[Color, Bitboard],

  // Game History & Rules
  history: List[MoveEvent],
  turn: Color,
  castlingRights: CastlingRights,
  enPassantTarget: Option[Square],
  halfMoveClock: Int // For 50-move rule
)

object World:
  // Initial chess position
  val initial: World =
    val initialBoard = Vector.fill(64)(None)
      // Place pawns
      .updated(8, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(9, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(10, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(11, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(12, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(13, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(14, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(15, Some(Piece(PieceType.Pawn, Color.White)))
      .updated(48, Some(Piece(PieceType.Pawn, Color.Black)))
      .updated(49, Some(Piece(PieceType.Pawn, Color.Black)))
      .updated(50, Some(Piece(PieceType.Pawn, Color.Black)))
      .updated(51, Some(Piece(PieceType.Pawn, Color.Black)))
      .updated(52, Some(Piece(PieceType.Pawn, Color.Black)))
      .updated(53, Some(Piece(PieceType.Pawn, Color.Black)))
      .updated(54, Some(Piece(PieceType.Pawn, Color.Black)))
      .updated(55, Some(Piece(PieceType.Pawn, Color.Black)))
      // Place other pieces
      .updated(0, Some(Piece(PieceType.Rook, Color.White)))
      .updated(1, Some(Piece(PieceType.Knight, Color.White)))
      .updated(2, Some(Piece(PieceType.Bishop, Color.White)))
      .updated(3, Some(Piece(PieceType.Queen, Color.White)))
      .updated(4, Some(Piece(PieceType.King, Color.White)))
      .updated(5, Some(Piece(PieceType.Bishop, Color.White)))
      .updated(6, Some(Piece(PieceType.Knight, Color.White)))
      .updated(7, Some(Piece(PieceType.Rook, Color.White)))
      .updated(56, Some(Piece(PieceType.Rook, Color.Black)))
      .updated(57, Some(Piece(PieceType.Knight, Color.Black)))
      .updated(58, Some(Piece(PieceType.Bishop, Color.Black)))
      .updated(59, Some(Piece(PieceType.Queen, Color.Black)))
      .updated(60, Some(Piece(PieceType.King, Color.Black)))
      .updated(61, Some(Piece(PieceType.Bishop, Color.Black)))
      .updated(62, Some(Piece(PieceType.Knight, Color.Black)))
      .updated(63, Some(Piece(PieceType.Rook, Color.Black)))

    val initialOccupancy = Map(
      Color.White -> Bitboard.fromSquares(
        (0 to 15).map(i => Square.fromIndex(i).get): _*
      ),
      Color.Black -> Bitboard.fromSquares(
        (48 to 63).map(i => Square.fromIndex(i).get): _*
      )
    )

    World(
      board = initialBoard,
      occupancy = initialOccupancy,
      history = Nil,
      turn = Color.White,
      castlingRights = CastlingRights.all,
      enPassantTarget = None,
      halfMoveClock = 0
    )

  extension (w: World)
    def pieceAt(square: Square): Option[Piece] = w.board(square.index)

    def isOccupied(square: Square): Boolean = w.pieceAt(square).isDefined

    def isOccupiedBy(square: Square, color: Color): Boolean =
      w.pieceAt(square).exists(_.color == color)

    def kingPosition(color: Color): Option[Square] =
      w.board.zipWithIndex.collectFirst {
        case (Some(Piece(PieceType.King, c)), idx) if c == color => Square.fromIndex(idx).get
      }

    def toFen: String =
      // This will be implemented in Phase 5
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"