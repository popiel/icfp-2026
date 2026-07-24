package com.wolfskeep.littleman.model

/** A load-time parse/validation failure. `message` is a human-readable
  * description used both for testing and for the stderr detail line. */
final case class ParseError(message: String)