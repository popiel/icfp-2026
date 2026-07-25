package com.wolfskeep.littleman

import java.io.{File, PrintWriter}
import scala.io.Source

object EncodeHistory {

  def main(args: Array[String]): Unit = {
    val inputPath  = args.headOption.getOrElse("problems/history-lesson/icfp-history.txt")
    val outputPath = if (args.length > 1) args(1) else "problems/history-lesson/icfp-history.nums"

    val raw  = Source.fromFile(inputPath).mkString
    val text = raw.stripSuffix("\n").stripSuffix("\r")

    val groupSize = 9
    val numbers   = new scala.collection.mutable.ArrayBuffer[String]
    var i = 0
    while (i < text.length) {
      var value = 0L
      var power = 1L
      var j = 0
      while (j < groupSize) {
        val ch    = if (i + j < text.length) text.charAt(i + j) else 31.toChar
        val digit = ch.toInt - 31
        value += digit.toLong * power
        power *= 92L
        j += 1
      }
      numbers += s"`${"%018d".format(value)}`s"
      i += groupSize
    }

    val result = new StringBuilder
    var idx = 0
    var lineNum = 0

    // first group: 3
    val firstGroup = numbers.slice(idx, idx + 3).mkString(" ")
    result.append(if (lineNum % 2 == 0) firstGroup else firstGroup.reverse)
    result.append("\n")
    idx += 3
    lineNum += 1

    // middle groups: 4
    while (idx + 4 <= numbers.length - 2) {
      val grp = numbers.slice(idx, idx + 4).mkString(" ")
      result.append(if (lineNum % 2 == 0) grp else grp.reverse)
      result.append("\n")
      idx += 4
      lineNum += 1
    }

    // last group: 2
    val lastGroup = numbers.slice(idx, idx + 2).mkString(" ")
    result.append(if (lineNum % 2 == 0) lastGroup else lastGroup.reverse)
    result.append("\n")

    val out = new PrintWriter(new File(outputPath))
    try out.write(result.toString())
    finally out.close()
  }
}
