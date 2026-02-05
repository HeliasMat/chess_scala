import cats.effect.{IO, IOApp}
import fs2.io.stdin
import fs2.text
import pipeline.uci
import domain.World
import logic.MoveGenerator

/**
 * Main entry point for the Pure Functional Chess Engine.
 * Implements the UCI protocol interface using fs2 streams.
 */
object Main extends IOApp.Simple:

  // Current game state (mutable reference for demonstration)
  private var currentWorld = World.initial

  /**
   * The core engine processing pipe.
   * Transforms UCI commands into responses.
   */
  def enginePipe: fs2.Pipe[IO, String, String] = stream =>
    stream
      .filter(_.trim.nonEmpty)  // Skip empty lines
      .map(processCommand)
      .flatMap(fs2.Stream.emits(_))  // Flatten multiple responses

  /**
   * Process a single UCI command and return response(s).
   */
  def processCommand(input: String): List[String] =
    uci.parseCommand(input) match
      case Right(cmd) =>
        cmd match
          case uci.UciCommand.Uci =>
            List(
              uci.formatResponse(uci.UciResponse.Id),
              uci.formatResponse(uci.UciResponse.UciOk)
            )
          case uci.UciCommand.IsReady =>
            List(uci.formatResponse(uci.UciResponse.ReadyOk))
          case uci.UciCommand.SetOption(name, value) =>
            // Ignore options for now
            List()
          case posCmd @ uci.UciCommand.Position(_, _) =>
            uci.buildWorldFromPosition(posCmd) match
              case Right(world) =>
                currentWorld = world
                List()  // Position command produces no output
              case Left(err) =>
                // Silently ignore invalid positions
                List()
          case goCmd @ uci.UciCommand.Go(_, _, _, _, _, _, _, depth, _, _, _, infinite) =>
            val searchDepth = depth.getOrElse(4)
            try
              // Simple synchronous search (blocking)
              val moves = MoveGenerator.generateLegalMoves(currentWorld)
              if moves.isEmpty then
                List(uci.formatResponse(uci.UciResponse.BestMove("(none)")))
              else
                val bestMove = moves.head  // For now, just pick first move
                val uciMove = uci.moveToUci(bestMove)
                List(uci.formatResponse(uci.UciResponse.BestMove(uciMove)))
            catch
              case e: Exception =>
                List(uci.formatResponse(uci.UciResponse.BestMove("(none)")))
          case uci.UciCommand.Stop =>
            List()  // Stopping not yet implemented
          case uci.UciCommand.Quit() =>
            System.exit(0)
            List()

      case Left(err) =>
        // Unknown command - output nothing per UCI spec
        List()

  /**
   * Main IO loop - connects stdin/stdout to the engine.
   */
  val run: IO[Unit] =
    stdin[IO](bufSize = 4096)
      .through(text.utf8.decode)
      .through(text.lines)
      .through(enginePipe)
      .intersperse("\n")
      .through(text.utf8.encode)
      .through(fs2.io.stdout)
      .compile
      .drain
