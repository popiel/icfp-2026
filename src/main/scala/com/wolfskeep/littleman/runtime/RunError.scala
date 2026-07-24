package com.wolfskeep.littleman.runtime

/** A fatal runtime error code, emitted on stdout after the partial output. */
sealed trait RunError { def code: String }
object RunError {
  case object Wall       extends RunError { val code = "wall" }
  case object BadOp      extends RunError { val code = "bad-op" }
  case object NoPipe     extends RunError { val code = "no-pipe" }
  case object DispAddr   extends RunError { val code = "display-addr" }
  case object DispData   extends RunError { val code = "display-data" }
  case object DispSwap   extends RunError { val code = "display-swap" }
  case object BadLiteral extends RunError { val code = "bad-literal" }

  def fromCode(code: String): RunError = code match {
    case "wall"          => Wall
    case "bad-op"        => BadOp
    case "no-pipe"       => NoPipe
    case "display-addr"  => DispAddr
    case "display-data"  => DispData
    case "display-swap"  => DispSwap
    case "bad-literal"   => BadLiteral
    case _               => BadOp
  }
}