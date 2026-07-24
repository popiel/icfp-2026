package com.wolfskeep.littleman.io

/** A sink for the program's output integers. The [[com.wolfskeep.littleman.runtime.Simulator]]
  * calls `emit` for each value the program produces (in order). */
trait OutputSink {
  def emit(value: Long): Unit
}

/** An [[OutputSink]] backed by a `java.io.PrintStream`, writing space-
  * separated integers. The caller is responsible for any trailing newline. */
final class PrintStreamSink(out: java.io.PrintStream) extends OutputSink {
  private var first = true
  def emit(value: Long): Unit = {
    if (first) { out.print(value); first = false }
    else out.print(" " + value)
  }
  /** Flush the underlying stream. */
  def flush(): Unit = out.flush()
}

/** An [[OutputSink]] that collects emitted values into a Vector, for testing. */
final class CollectingSink extends OutputSink {
  val values: scala.collection.mutable.ArrayBuffer[Long] =
    scala.collection.mutable.ArrayBuffer.empty
  def emit(value: Long): Unit = values += value
  def toVector: Vector[Long] = values.toVector
}