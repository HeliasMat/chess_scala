Benchmarking instructions

Run the micro-benchmark to measure average move generation time on your machine.

Usage:

```bash
# Quick micro-benchmark (simple harness):
sbt "runMain bench.Benchmark 2000"

# JMH benchmark (recommended for accurate results):
sbt jmh:run -i 5 -wi 5 -f1 -t1
```

Optional: change the number `2000` to increase/decrease iterations.

This harness is intentionally small and measures raw `MoveGenerator.generateLegalMoves(World.initial)` performance.
Use a proper benchmarking tool (JMH) for more accurate measurements.
