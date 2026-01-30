package domain

// 0-63 index, represented as a Byte at runtime for memory efficiency
opaque type Square = Byte

object Square:
  def from(x: Int, y: Int): Option[Square] =
    if x >= 0 && x < 8 && y >= 0 && y < 8 then
      Some((y * 8 + x).toByte)
    else
      None

  def fromIndex(index: Int): Option[Square] =
    if index >= 0 && index < 64 then Some(index.toByte) else None

  extension (s: Square)
    def x: Int = s % 8
    def y: Int = s / 8
    def index: Int = s.toInt

    def isValid: Boolean = s >= 0 && s < 64

    def +(other: Square): Square = ((s + other) % 64).toByte
    def -(other: Square): Square = ((s - other + 64) % 64).toByte

    def toString: String = s"(${s.x},${s.y})"

// 64-bit Long for bitwise operations (zero runtime overhead)
opaque type Bitboard = Long

object Bitboard:
  val empty: Bitboard = 0L
  val full: Bitboard = -1L

  def fromSquares(squares: Square*): Bitboard =
    squares.foldLeft(empty)((bb, sq) => bb | (1L << sq))

  extension (b: Bitboard)
    def contains(square: Square): Boolean = (b & (1L << square)) != 0

    def add(square: Square): Bitboard = b | (1L << square)
    def remove(square: Square): Bitboard = b & ~(1L << square)
    def toggle(square: Square): Bitboard = b ^ (1L << square)

    def union(other: Bitboard): Bitboard = b | other
    def intersection(other: Bitboard): Bitboard = b & other
    def difference(other: Bitboard): Bitboard = b & ~other
    def complement: Bitboard = ~b

    def isEmpty: Boolean = b == 0L
    def nonEmpty: Boolean = b != 0L
    def count: Int = java.lang.Long.bitCount(b)

    def squares: List[Square] =
      (0 until 64).collect {
        case i if contains(i.toByte) => i.toByte
      }.toList

    def toString: String =
      val binary = b.toBinaryString.reverse.padTo(64, '0').reverse
      binary.grouped(8).mkString("\n")

// Bitmask for Castling Rights (0000 to 1111)
opaque type CastlingRights = Byte

object CastlingRights:
  val none: CastlingRights = 0.toByte
  val whiteKingSide: CastlingRights = 1.toByte
  val whiteQueenSide: CastlingRights = 2.toByte
  val blackKingSide: CastlingRights = 4.toByte
  val blackQueenSide: CastlingRights = 8.toByte

  val all: CastlingRights = 15.toByte // 1111 in binary

  extension (cr: CastlingRights)
    def canWhiteKingSide: Boolean = (cr & whiteKingSide) != 0
    def canWhiteQueenSide: Boolean = (cr & whiteQueenSide) != 0
    def canBlackKingSide: Boolean = (cr & blackKingSide) != 0
    def canBlackQueenSide: Boolean = (cr & blackQueenSide) != 0

    def removeWhiteKingSide: CastlingRights = (cr & ~whiteKingSide).toByte
    def removeWhiteQueenSide: CastlingRights = (cr & ~whiteQueenSide).toByte
    def removeBlackKingSide: CastlingRights = (cr & ~blackKingSide).toByte
    def removeBlackQueenSide: CastlingRights = (cr & ~blackQueenSide).toByte

    def hasAny: Boolean = cr != none

    def toString: String =
      val rights = List(
        if canWhiteKingSide then "K" else "",
        if canWhiteQueenSide then "Q" else "",
        if canBlackKingSide then "k" else "",
        if canBlackQueenSide then "q" else ""
      ).mkString
      if rights.isEmpty then "-" else rights