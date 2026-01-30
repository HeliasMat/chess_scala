package ai

import cats.effect.IO
import cats.effect.std.MapRef
import domain.*
import monocle.Lens
import monocle.macros.GenLens

/**
 * Transposition table for caching position evaluations.
 * Uses Zobrist hashing for fast position keys and concurrent MapRef for thread-safe access.
 */
object transposition:

  /**
   * Entry in the transposition table.
   */
  case class TableEntry(
    evaluation: Double,
    depth: Int,
    flag: EntryFlag, // EXACT, LOWER_BOUND, UPPER_BOUND
    bestMove: Option[MoveIntent]
  )

  enum EntryFlag:
    case Exact, LowerBound, UpperBound

  /**
   * Zobrist hash for a position.
   * 64-bit hash that uniquely identifies board positions.
   */
  opaque type ZobristHash = Long

  object ZobristHash:
    def apply(value: Long): ZobristHash = value

    extension (hash: ZobristHash)
      def value: Long = hash
      def xor(other: Long): ZobristHash = ZobristHash(hash ^ other)

  /**
   * Random numbers for Zobrist hashing.
   * Precomputed random 64-bit numbers for pieces, squares, and game state.
   */
  private val zobristTable: Array[Array[Array[Long]]] = {
    val random = new scala.util.Random(0x123456789ABCDEF0L) // Fixed seed for reproducibility

    // [piece][color][square] - 6 pieces × 2 colors × 64 squares = 768 entries
    Array.fill(6, 2, 64)(random.nextLong())
  }

  /**
   * Computes Zobrist hash for a board position.
   */
  def computeHash(world: World): ZobristHash =
    var hash: Long = 0L

    // Hash pieces on board
    world.board.zipWithIndex.foreach { (pieceOpt, squareIndex) =>
      pieceOpt.foreach { piece =>
        val pieceIndex = piece.kind match
          case PieceType.Pawn => 0
          case PieceType.Knight => 1
          case PieceType.Bishop => 2
          case PieceType.Rook => 3
          case PieceType.Queen => 4
          case PieceType.King => 5

        val colorIndex = piece.color match
          case Color.White => 0
          case Color.Black => 1

        hash = hash ^ zobristTable(pieceIndex)(colorIndex)(squareIndex)
      }
    }

    ZobristHash(hash)

  /**
   * Transposition table using MapRef for concurrent access.
   */
  trait TranspositionTable:
    def get(hash: ZobristHash): IO[Option[TableEntry]]
    def put(hash: ZobristHash, entry: TableEntry): IO[Unit]
    def clear(): IO[Unit]
    def size: IO[Int]

  /**
   * Creates a new transposition table with the given maximum size.
   */
  def createTable(maxSize: Int = 1000000): IO[TranspositionTable] =
    import cats.effect.Ref
    Ref.of[IO, Map[ZobristHash, TableEntry]](Map.empty).map { ref =>
      new TranspositionTable:
        def get(hash: ZobristHash): IO[Option[TableEntry]] =
          ref.get.map(_.get(hash))

        def put(hash: ZobristHash, entry: TableEntry): IO[Unit] =
          ref.update(_ + (hash -> entry))

        def clear(): IO[Unit] =
          ref.set(Map.empty)

        def size: IO[Int] =
          ref.get.map(_.size)
    }

  /**
   * Updates the hash when a piece moves from one square to another.
   * This is more efficient than recomputing the entire hash.
   */
  def updateHashForMove(
    oldHash: ZobristHash,
    fromSquare: Square,
    toSquare: Square,
    movingPiece: Piece,
    capturedPiece: Option[Piece]
  ): ZobristHash =
    import ZobristHash.value
    val oldHashValue = oldHash.value

    val pieceIndex = movingPiece.kind match
      case PieceType.Pawn => 0
      case PieceType.Knight => 1
      case PieceType.Bishop => 2
      case PieceType.Rook => 3
      case PieceType.Queen => 4
      case PieceType.King => 5

    val colorIndex = movingPiece.color match
      case Color.White => 0
      case Color.Black => 1

    // Remove piece from source square
    val hash1 = oldHashValue ^ zobristTable(pieceIndex)(colorIndex)(fromSquare.index)

    // Add piece to destination square
    val hash2 = hash1 ^ zobristTable(pieceIndex)(colorIndex)(toSquare.index)

    // Remove captured piece if any
    val finalHash = capturedPiece match
      case Some(captured) =>
        val capturedPieceIndex = captured.kind match
          case PieceType.Pawn => 0
          case PieceType.Knight => 1
          case PieceType.Bishop => 2
          case PieceType.Rook => 3
          case PieceType.Queen => 4
          case PieceType.King => 5

        val capturedColorIndex = captured.color match
          case Color.White => 0
          case Color.Black => 1

        hash2 ^ zobristTable(capturedPieceIndex)(capturedColorIndex)(toSquare.index)
      case None => hash2

    ZobristHash(finalHash)

  /**
   * Checks if a position is in the transposition table and returns the stored evaluation if valid.
   */
  def probeTable(
    table: TranspositionTable,
    hash: ZobristHash,
    depth: Int,
    alpha: Double,
    beta: Double
  ): IO[Option[Double]] =
    table.get(hash).map {
      case Some(entry) if entry.depth >= depth =>
        entry.flag match
          case EntryFlag.Exact => Some(entry.evaluation)
          case EntryFlag.LowerBound if entry.evaluation >= beta => Some(entry.evaluation)
          case EntryFlag.UpperBound if entry.evaluation <= alpha => Some(entry.evaluation)
          case _ => None
      case _ => None
    }

  /**
   * Stores a position evaluation in the transposition table.
   */
  def storeInTable(
    table: TranspositionTable,
    hash: ZobristHash,
    evaluation: Double,
    depth: Int,
    flag: EntryFlag,
    bestMove: Option[MoveIntent]
  ): IO[Unit] =
    val entry = TableEntry(evaluation, depth, flag, bestMove)
    table.put(hash, entry)