package bench

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit
import domain.*
import logic.*

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
class JmhMoveGen:

  @Setup(Level.Trial)
  def setup(): Unit =
    // Warmup handled by JMH
    ()

  @Benchmark
  def generateInitialMoves(): Int =
    val moves = MoveGenerator.generateLegalMoves(World.initial)
    moves.length // return size to avoid dead-code elimination
