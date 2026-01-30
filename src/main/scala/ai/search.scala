package ai

import cats.effect.IO
import cats.syntax.all.*
import domain.*
import logic.*
import pipeline.*

/**
 * AI search algorithms for chess move selection.
 * Implements minimax with alpha-beta pruning for efficient game tree exploration.
 */
object search:

  /**
   * Result of a search operation containing the best move and its evaluation.
   */
  case class SearchResult(move: Option[MoveIntent], evaluation: Double, depth: Int)

  /**
   * Configuration for search parameters.
   */
  case class SearchConfig(
    maxDepth: Int = 4,
    useAlphaBeta: Boolean = true,
    quiescenceDepth: Int = 2,
    useTranspositionTable: Boolean = true,
    tableSize: Int = 1000000
  )

  /**
   * Performs minimax search with alpha-beta pruning to find the best move.
   *
   * @param world Current game state
   * @param config Search configuration
   * @return IO containing the best move and its evaluation
   */
  def findBestMove(world: World, config: SearchConfig = SearchConfig()): IO[SearchResult] =
    for
      table <- if config.useTranspositionTable then transposition.createTable(config.tableSize) else IO.pure(null)
      moves <- generateLegalMoves(world)
      result <- if moves.isEmpty then
        // No legal moves - game is over
        IO.pure(SearchResult(None, Evaluation.evaluate(world).toDouble, 0))
      else
        // Evaluate all moves and find the best one
        evaluateMoves(world, moves, config, table).map { moveEvals =>
          val bestEval = moveEvals.maxBy(_._2)._2
          val bestMove = moveEvals.find(_._2 == bestEval).get._1
          SearchResult(Some(bestMove), bestEval, config.maxDepth)
        }
    yield result

  /**
   * Generates all legal moves for the current position.
   */
  private def generateLegalMoves(world: World): IO[List[MoveIntent]] =
    IO {
      // Generate all possible moves for the current player
      MoveGenerator.generateLegalMoves(world)
    }

  private def evaluateMoves(
    world: World,
    moves: List[MoveIntent],
    config: SearchConfig,
    table: transposition.TranspositionTable | Null
  ): IO[List[(MoveIntent, Double)]] =
    moves.parTraverse { move =>
      for
        validatedMove <- ValidationPipeline.validateMove(world, move) match
          case Right(vm) => IO.pure(vm)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Invalid move: $move, errors: ${errors.toList.mkString(", ")}"))
        newWorld <- StateReducer.applyMove(world, validatedMove) match
          case Right((nw, _)) => IO.pure(nw)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Failed to apply move: $move, errors: ${errors.toList.mkString(", ")}"))
        eval <- minimax(newWorld, config.maxDepth - 1, Double.NegativeInfinity, Double.PositiveInfinity, false, config, table)
      yield (move, eval)
    }

  /**
   * Minimax algorithm with alpha-beta pruning.
   *
   * @param world Current position
   * @param depth Remaining search depth
   * @param alpha Alpha value for pruning
   * @param beta Beta value for pruning
   * @param maximizing True if maximizing player (current player), false for minimizing
   * @param config Search configuration
   * @param table Transposition table (null if disabled)
   * @return Evaluation of the position
   */
  private def minimax(
    world: World,
    depth: Int,
    alpha: Double,
    beta: Double,
    maximizing: Boolean,
    config: SearchConfig,
    table: transposition.TranspositionTable | Null
  ): IO[Double] =
    // Check transposition table first
    val hashOpt = if table != null then Some(transposition.computeHash(world)) else None
    val tableLookup = hashOpt match
      case Some(hash) => transposition.probeTable(table, hash, depth, alpha, beta)
      case None => IO.pure(None)

    tableLookup.flatMap {
      case Some(cachedEval) => IO.pure(cachedEval)
      case None =>
        // Not in table, compute normally
        val result = if depth <= 0 then
          // Use quiescence search for unstable positions
          quiescenceSearch(world, config.quiescenceDepth, alpha, beta, maximizing, table)
        else
          for
            moves <- generateLegalMoves(world)
            res <- if moves.isEmpty then
              // Terminal position - evaluate directly
              IO.pure(Evaluation.evaluate(world).toDouble)
            else if maximizing then
              // Maximizing player
              maximize(world, moves, depth, alpha, beta, config, table)
            else
              // Minimizing player
              minimize(world, moves, depth, alpha, beta, config, table)
          yield res

        // Store result in transposition table
        result.flatMap { eval =>
          (table, hashOpt) match
            case (t, Some(h)) if t != null =>
              val flag = if eval <= alpha then transposition.EntryFlag.UpperBound
                        else if eval >= beta then transposition.EntryFlag.LowerBound
                        else transposition.EntryFlag.Exact
              transposition.storeInTable(t, h, eval, depth, flag, None).as(eval)
            case _ => IO.pure(eval)
        }
    }

  private def maximize(
    world: World,
    moves: List[MoveIntent],
    depth: Int,
    alpha: Double,
    beta: Double,
    config: SearchConfig,
    table: transposition.TranspositionTable | Null
  ): IO[Double] =
    moves.foldLeftM(alpha) { (currentAlpha, move) =>
      for
        validatedMove <- ValidationPipeline.validateMove(world, move) match
          case Right(vm) => IO.pure(vm)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Invalid move: $move, errors: ${errors.toList.mkString(", ")}"))
        newWorld <- StateReducer.applyMove(world, validatedMove) match
          case Right((nw, _)) => IO.pure(nw)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Failed to apply move: $move, errors: ${errors.toList.mkString(", ")}"))
        eval <- minimax(newWorld, depth - 1, currentAlpha, beta, false, config, table)
        newAlpha = math.max(currentAlpha, eval)
        _ <- IO.whenA(newAlpha >= beta && config.useAlphaBeta)(IO.unit) // Beta cutoff
      yield newAlpha
    }

  private def minimize(
    world: World,
    moves: List[MoveIntent],
    depth: Int,
    alpha: Double,
    beta: Double,
    config: SearchConfig,
    table: transposition.TranspositionTable | Null
  ): IO[Double] =
    moves.foldLeftM(beta) { (currentBeta, move) =>
      for
        validatedMove <- ValidationPipeline.validateMove(world, move) match
          case Right(vm) => IO.pure(vm)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Invalid move: $move, errors: ${errors.toList.mkString(", ")}"))
        newWorld <- StateReducer.applyMove(world, validatedMove) match
          case Right((nw, _)) => IO.pure(nw)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Failed to apply move: $move, errors: ${errors.toList.mkString(", ")}"))
        eval <- minimax(newWorld, depth - 1, alpha, currentBeta, true, config, table)
        newBeta = math.min(currentBeta, eval)
        _ <- IO.whenA(alpha >= newBeta && config.useAlphaBeta)(IO.unit) // Alpha cutoff
      yield newBeta
    }

  /**
   * Quiescence search to avoid horizon effect.
   * Only searches captures and checks in unstable positions.
   */
  private def quiescenceSearch(
    world: World,
    depth: Int,
    alpha: Double,
    beta: Double,
    maximizing: Boolean,
    table: transposition.TranspositionTable | Null
  ): IO[Double] =
    // First, evaluate the current position
    val standPat = Evaluation.evaluate(world).toDouble

    // If we're in a quiet position or at max quiescence depth, return static evaluation
    if depth <= 0 then
      IO.pure(standPat)
    else
      // Check if we should continue searching (captures available, in check, etc.)
      for
        captures <- generateCaptures(world)
        result <- if captures.isEmpty then
          IO.pure(standPat) // No captures, position is quiet
        else if maximizing then
          quiesceMax(world, captures, depth, math.max(alpha, standPat), beta, table)
        else
          quiesceMin(world, captures, depth, alpha, math.min(beta, standPat), table)
      yield result

  /**
   * Generate capture moves for quiescence search.
   */
  private def generateCaptures(world: World): IO[List[MoveIntent]] =
    IO {
      val allMoves = MoveGenerator.generateLegalMoves(world)
      // Filter to captures only (moves that capture enemy pieces)
      allMoves.filter { move =>
        // Check if destination square has an enemy piece
        val destSquare = move.to.toSquare.get
        world.isOccupiedBy(destSquare, world.turn.opposite)
      }
    }

  private def quiesceMax(
    world: World,
    captures: List[MoveIntent],
    depth: Int,
    alpha: Double,
    beta: Double,
    table: transposition.TranspositionTable | Null
  ): IO[Double] =
    captures.foldLeftM(alpha) { (currentAlpha, capture) =>
      for
        validatedMove <- ValidationPipeline.validateMove(world, capture) match
          case Right(vm) => IO.pure(vm)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Invalid capture: $capture, errors: ${errors.toList.mkString(", ")}"))
        newWorld <- StateReducer.applyMove(world, validatedMove) match
          case Right((nw, _)) => IO.pure(nw)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Failed to apply capture: $capture, errors: ${errors.toList.mkString(", ")}"))
        eval <- quiescenceSearch(newWorld, depth - 1, currentAlpha, beta, false, table)
        newAlpha = math.max(currentAlpha, eval)
        _ <- IO.whenA(newAlpha >= beta)(IO.unit) // Beta cutoff
      yield newAlpha
    }

  private def quiesceMin(
    world: World,
    captures: List[MoveIntent],
    depth: Int,
    alpha: Double,
    beta: Double,
    table: transposition.TranspositionTable | Null
  ): IO[Double] =
    captures.foldLeftM(beta) { (currentBeta, capture) =>
      for
        validatedMove <- ValidationPipeline.validateMove(world, capture) match
          case Right(vm) => IO.pure(vm)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Invalid capture: $capture, errors: ${errors.toList.mkString(", ")}"))
        newWorld <- StateReducer.applyMove(world, validatedMove) match
          case Right((nw, _)) => IO.pure(nw)
          case Left(errors) => IO.raiseError(new RuntimeException(s"Failed to apply capture: $capture, errors: ${errors.toList.mkString(", ")}"))
        eval <- quiescenceSearch(newWorld, depth - 1, alpha, currentBeta, true, table)
        newBeta = math.min(currentBeta, eval)
        _ <- IO.whenA(alpha >= newBeta)(IO.unit) // Alpha cutoff
      yield newBeta
    }