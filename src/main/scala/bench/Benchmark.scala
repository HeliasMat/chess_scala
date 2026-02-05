package bench

import domain.*
import logic.*

object Benchmark:
  def main(args: Array[String]): Unit =
    val iterations = args.headOption.map(_.toInt).getOrElse(2000)
    val warmup = 100

    println(s"Benchmark: move generation - warmup=$warmup iterations=$iterations")

    // Warmup
    for _ <- 1 to warmup do
      val _ = MoveGenerator.generateLegalMoves(World.initial)

    // Timed runs
    val t0 = System.nanoTime()
    for _ <- 1 to iterations do
      val _ = MoveGenerator.generateLegalMoves(World.initial)
    val t1 = System.nanoTime()

    val totalNs = t1 - t0
    val avgNs = totalNs.toDouble / iterations
    println(f"Total time: ${totalNs/1e6}%.2f ms for $iterations runs")
    println(f"Average per call: ${avgNs/1e3}%.3f µs (${avgNs}%.0f ns)")

    // Quick sanity: print number of moves in initial position
    val moves = MoveGenerator.generateLegalMoves(World.initial)
    println(s"Moves in initial position: ${moves.length}")
