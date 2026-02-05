package bench

import org.openjdk.jmh.runner.{Runner => JmhRunnerImpl}
import org.openjdk.jmh.runner.options.{OptionsBuilder, VerboseMode}

object JmhRunner:
  def main(args: Array[String]): Unit =
    val include = args.headOption.getOrElse("bench.JmhMoveGen")
    val iterations = args.lift(1).map(_.toInt).getOrElse(3)

    val opts = new OptionsBuilder()
      .include(include)
      .warmupIterations(1)
      .measurementIterations(iterations)
      .forks(1)
      .threads(1)
      .verbosity(VerboseMode.NORMAL)
      .build()

    val runner = new JmhRunnerImpl(opts)
    val res = runner.run()
    println(s"JMH run completed, result entries: ${res.size()}")
