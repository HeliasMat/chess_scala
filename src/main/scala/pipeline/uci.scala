package pipeline

import cats.effect.IO
import cats.parse.{Parser, Parser0, Numbers}
import cats.parse.Parser.*
import domain.*
import logic.*
import ai.*

/**
 * UCI (Universal Chess Interface) protocol implementation.
 * Handles parsing UCI commands and generating UCI responses.
 */
object uci:

  // ===== UCI Commands =====

  sealed trait UciCommand
  object UciCommand:
    case object Uci extends UciCommand
    case object IsReady extends UciCommand
    case class SetOption(name: String, value: Option[String]) extends UciCommand
    case class Position(fen: Option[String], moves: List[String]) extends UciCommand
    case class Go(
      searchMoves: Option[List[String]] = None,
      ponder: Boolean = false,
      wtime: Option[Long] = None,
      btime: Option[Long] = None,
      winc: Option[Long] = None,
      binc: Option[Long] = None,
      movesToGo: Option[Int] = None,
      depth: Option[Int] = None,
      nodes: Option[Long] = None,
      mate: Option[Int] = None,
      moveTime: Option[Long] = None,
      infinite: Boolean = false
    ) extends UciCommand
    case class Quit() extends UciCommand
    case object Stop extends UciCommand

  // ===== UCI Responses =====

  sealed trait UciResponse
  object UciResponse:
    case object Id extends UciResponse
    case object UciOk extends UciResponse
    case object ReadyOk extends UciResponse
    case class BestMove(move: String, ponder: Option[String] = None) extends UciResponse
    case class Info(
      depth: Option[Int] = None,
      seldepth: Option[Int] = None,
      time: Option[Long] = None,
      nodes: Option[Long] = None,
      pv: List[String] = List(),
      score: Option[String] = None,
      currmove: Option[String] = None,
      currmovenumber: Option[Int] = None,
      hashfull: Option[Int] = None,
      nps: Option[Long] = None
    ) extends UciResponse

  // ===== Parsing =====

  /**
   * Parse a UCI command from input string.
   */
  def parseCommand(input: String): Either[String, UciCommand] =
    val trimmed = input.trim
    val tokens = trimmed.split("\\s+").toList

    tokens match
      case "uci" :: _ => Right(UciCommand.Uci)
      case "isready" :: _ => Right(UciCommand.IsReady)
      case "setoption" :: rest =>
        parseSetOption(rest) match
          case Right(cmd) => Right(cmd)
          case Left(err) => Left(err)
      case "position" :: rest =>
        parsePosition(rest) match
          case Right(cmd) => Right(cmd)
          case Left(err) => Left(err)
      case "go" :: rest =>
        parseGo(rest) match
          case Right(cmd) => Right(cmd)
          case Left(err) => Left(err)
      case "stop" :: _ => Right(UciCommand.Stop)
      case "quit" :: _ => Right(UciCommand.Quit())
      case _ => Left(s"Unknown UCI command: $trimmed")

  private def parseSetOption(tokens: List[String]): Either[String, UciCommand.SetOption] =
    tokens match
      case "name" :: name :: rest =>
        val value = rest match
          case "value" :: v :: _ => Some(v)
          case _ => None
        Right(UciCommand.SetOption(name, value))
      case _ => Left("Invalid setoption syntax")

  private def parsePosition(tokens: List[String]): Either[String, UciCommand.Position] =
    tokens match
      case "fen" :: fenTokens =>
        val (fenParts, movesRest) = fenTokens.span(_ != "moves")
        val fen = if fenParts.nonEmpty then Some(fenParts.mkString(" ")) else None
        val moves = movesRest match
          case "moves" :: moveList => moveList
          case _ => List()
        Right(UciCommand.Position(fen, moves))
      case "startpos" :: rest =>
        val moves = rest match
          case "moves" :: moveList => moveList
          case _ => List()
        Right(UciCommand.Position(None, moves))
      case _ => Left("Invalid position syntax")

  private def parseGo(tokens: List[String]): Either[String, UciCommand.Go] =
    var searchMoves: Option[List[String]] = None
    var ponder = false
    var wtime: Option[Long] = None
    var btime: Option[Long] = None
    var winc: Option[Long] = None
    var binc: Option[Long] = None
    var movesToGo: Option[Int] = None
    var depth: Option[Int] = None
    var nodes: Option[Long] = None
    var mate: Option[Int] = None
    var moveTime: Option[Long] = None
    var infinite = false

    def parseGoTokens(remaining: List[String]): Unit =
      remaining match
        case Nil => ()
        case "searchmoves" :: rest =>
          val (moves, next) = rest.span(!isGoKeyword(_))
          searchMoves = Some(moves)
          parseGoTokens(next)
        case "ponder" :: rest =>
          ponder = true
          parseGoTokens(rest)
        case "wtime" :: value :: rest =>
          wtime = value.toLongOption
          parseGoTokens(rest)
        case "btime" :: value :: rest =>
          btime = value.toLongOption
          parseGoTokens(rest)
        case "winc" :: value :: rest =>
          winc = value.toLongOption
          parseGoTokens(rest)
        case "binc" :: value :: rest =>
          binc = value.toLongOption
          parseGoTokens(rest)
        case "movestogo" :: value :: rest =>
          movesToGo = value.toIntOption
          parseGoTokens(rest)
        case "depth" :: value :: rest =>
          depth = value.toIntOption
          parseGoTokens(rest)
        case "nodes" :: value :: rest =>
          nodes = value.toLongOption
          parseGoTokens(rest)
        case "mate" :: value :: rest =>
          mate = value.toIntOption
          parseGoTokens(rest)
        case "movetime" :: value :: rest =>
          moveTime = value.toLongOption
          parseGoTokens(rest)
        case "infinite" :: rest =>
          infinite = true
          parseGoTokens(rest)
        case _ :: rest =>
          parseGoTokens(rest)

    parseGoTokens(tokens)
    Right(UciCommand.Go(
      searchMoves = searchMoves,
      ponder = ponder,
      wtime = wtime,
      btime = btime,
      winc = winc,
      binc = binc,
      movesToGo = movesToGo,
      depth = depth,
      nodes = nodes,
      mate = mate,
      moveTime = moveTime,
      infinite = infinite
    ))

  private def isGoKeyword(token: String): Boolean =
    Set("searchmoves", "ponder", "wtime", "btime", "winc", "binc", "movestogo",
        "depth", "nodes", "mate", "movetime", "infinite").contains(token)

  // ===== Format =====

  /**
   * Format a UCI response to output string.
   */
  def formatResponse(response: UciResponse): String =
    response match
      case UciResponse.Id =>
        "id name Pure Chess Engine\nid author Scala"
      case UciResponse.UciOk =>
        "uciok"
      case UciResponse.ReadyOk =>
        "readyok"
      case UciResponse.BestMove(move, ponder) =>
        val ponderStr = ponder.map(p => s" ponder $p").getOrElse("")
        s"bestmove $move$ponderStr"
      case UciResponse.Info(depth, seldepth, time, nodes, pv, score, currmove, currmovenumber, hashfull, nps) =>
        val parts = scala.collection.mutable.ListBuffer[String]()
        depth.foreach(d => parts += s"depth $d")
        seldepth.foreach(sd => parts += s"seldepth $sd")
        time.foreach(t => parts += s"time $t")
        nodes.foreach(n => parts += s"nodes $n")
        if pv.nonEmpty then parts += s"pv ${pv.mkString(" ")}"
        score.foreach(s => parts += s"score $s")
        currmove.foreach(cm => parts += s"currmove $cm")
        currmovenumber.foreach(cmn => parts += s"currmovenumber $cmn")
        hashfull.foreach(hf => parts += s"hashfull $hf")
        nps.foreach(n => parts += s"nps $n")
        "info " + parts.mkString(" ")

  // ===== Position Management =====

  /**
   * Build a World from UCI position command.
   */
  def buildWorldFromPosition(posCmd: UciCommand.Position): Either[String, World] =
    val initialWorld = posCmd.fen match
      case Some(fenStr) =>
        fen.parseFen(fenStr).left.map(_.head.toString).toOption.getOrElse(World.initial)
      case None =>
        World.initial

    // Apply moves if any
    posCmd.moves.foldLeft(Right(initialWorld): Either[String, World]) { (acc, moveStr) =>
      acc.flatMap { world =>
        applyUciMove(world, moveStr)
      }
    }

  /**
   * Apply a UCI move (algebraic notation like "e2e4") to a World.
   */
  def applyUciMove(world: World, moveStr: String): Either[String, World] =
    if moveStr.length < 4 then
      Left(s"Invalid move format: $moveStr")
    else
      val fromFile = moveStr.charAt(0).asDigit - 1
      val fromRank = moveStr.charAt(1).asDigit - 1
      val toFile = moveStr.charAt(2).asDigit - 1
      val toRank = moveStr.charAt(3).asDigit - 1

      // Handle promotion if present (e.g., "e7e8q")
      val promotion = if moveStr.length > 4 then
        moveStr.charAt(4).toLower match
          case 'q' => Some(PieceType.Queen)
          case 'r' => Some(PieceType.Rook)
          case 'b' => Some(PieceType.Bishop)
          case 'n' => Some(PieceType.Knight)
          case _ => None
      else
        None

      // Validate coordinates
      if fromFile < 0 || fromFile > 7 || fromRank < 0 || fromRank > 7 ||
         toFile < 0 || toFile > 7 || toRank < 0 || toRank > 7 then
        Left(s"Invalid coordinates in move: $moveStr")
      else
        // Convert file/rank to board index
        val fromIndex = fromRank * 8 + fromFile
        val toIndex = toRank * 8 + toFile

        val intent = MoveIntent(
          from = Position(fromFile, fromRank),
          to = Position(toFile, toRank),
          promotion = promotion
        )

        // Validate and apply move
        (for
          validatedMove <- ValidationPipeline.validateMove(world, intent)
          result <- StateReducer.applyMove(world, validatedMove)
          newWorld = result._1
        yield newWorld).left.map(_.head.toString)

  /**
   * Convert a MoveIntent to UCI notation.
   */
  def moveToUci(move: MoveIntent): String =
    val from = s"${('a' + move.from.x).toChar}${move.from.y + 1}"
    val to = s"${('a' + move.to.x).toChar}${move.to.y + 1}"
    val promotion = move.promotion.map(pt =>
      pt match
        case PieceType.Queen => "q"
        case PieceType.Rook => "r"
        case PieceType.Bishop => "b"
        case PieceType.Knight => "n"
        case _ => ""
    ).getOrElse("")
    s"$from$to$promotion"
