package domain

// Event hierarchy for move history
sealed trait MoveEvent

case class MoveExecuted(
  from: Square,
  to: Square,
  piece: Piece,
  capturedPiece: Option[Piece],
  promotion: Option[PieceType],
  castlingRightsBefore: CastlingRights,
  enPassantTargetBefore: Option[Square]
) extends MoveEvent

case class CastlingExecuted(
  kingFrom: Square,
  kingTo: Square,
  rookFrom: Square,
  rookTo: Square,
  side: CastlingSide
) extends MoveEvent

enum CastlingSide:
  case KingSide, QueenSide

case class EnPassantExecuted(
  pawnFrom: Square,
  pawnTo: Square,
  capturedPawnSquare: Square
) extends MoveEvent

case class PromotionExecuted(
  from: Square,
  to: Square,
  promotedTo: PieceType
) extends MoveEvent