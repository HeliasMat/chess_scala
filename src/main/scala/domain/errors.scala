package domain

// Error hierarchy for chess domain
sealed trait ChessError

object ChessError:
  // Logical Errors
  case object PathBlocked extends ChessError
  case object TargetOccupiedByFriendly extends ChessError
  case object KingInCheck extends ChessError
  case object InvalidCastlingRights extends ChessError
  case object InvalidEnPassantTarget extends ChessError
  case object InvalidPromotion extends ChessError

  // Move Validation Errors
  case object InvalidMoveGeometry extends ChessError
  case object PieceCannotMoveThisWay extends ChessError
  case object KingWouldBeInCheck extends ChessError

  // Syntax Errors
  case class ParsingFailure(input: String, reason: String) extends ChessError
  case class InvalidPosition(position: String) extends ChessError

  // System Errors (should be rare in pure code)
  case class UnknownState(msg: String) extends ChessError