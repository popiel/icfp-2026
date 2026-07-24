package com.wolfskeep.littleman.fetch

import java.nio.file.{Files, Path, Paths}
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

/** CLI entry for the problem-fetcher.
  *
  * Usage: `FetchMain [--once|--watch] [--interval 5m] [--base-url URL]
  * [--problems-dir problems]`
  *
  * Default: `--once`, base url `https://icfpcontest2026.com/api/v1`,
  * problems dir `problems`. No API key is read. */
object FetchMain {

  val DefaultBaseUrl: String = "https://icfpcontest2026.com/api/v1"
  val DefaultPollMillis: Long = 5 * 60 * 1000L

  def main(args: Array[String]): Unit = {
    sys.exit(run(args))
  }

  /** Run with default API + store. Returns an exit code (0=ok, 1=fatal). */
  def run(args: Array[String]): Int = {
    val opts = parseArgs(args)
    val api  = new JdkContestApi(opts.baseUrl)
    val store = new FsProblemStore(Paths.get(opts.problemsDir))
    run(args, api, store, opts)
  }

  /** Run with injected API + store (test seam). Returns an exit code. */
  def run(args: Array[String], api: ProblemApi, store: ProblemStore): Int = {
    run(args, api, store, parseArgs(args))
  }

  private def run(args: Array[String], api: ProblemApi, store: ProblemStore, opts: Options): Int = {
    val clock = () => Instant.now().toString
    val fetcher = new ProblemFetcher(api, store, clock)
    val report = fetcher.fetchOnce()
    report match {
      case r if r.fatalError.isDefined =>
        System.err.println(s"fatal: ${r.fatalError.get.code} ${r.fatalError.get.message}")
        1
      case r =>
        println(s"fetched ${r.fetched.size}, skipped ${r.skipped.size}, errors ${r.errors.size}")
        if (opts.watch) {
          new PollLoop(fetcher, opts.intervalMillis).run()
        }
        0
    }
  }

  case class Options(
    watch: Boolean = false,
    intervalMillis: Long = DefaultPollMillis,
    baseUrl: String = DefaultBaseUrl,
    problemsDir: String = "problems"
  )

  def parseArgs(args: Array[String]): Options = {
    var opts = Options()
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--watch"  => opts = opts.copy(watch = true)
        case "--once"   => opts = opts.copy(watch = false)
        case "--interval" if i + 1 < args.length =>
          i += 1; opts = opts.copy(intervalMillis = parseInterval(args(i)))
        case "--base-url" if i + 1 < args.length =>
          i += 1; opts = opts.copy(baseUrl = args(i))
        case "--problems-dir" if i + 1 < args.length =>
          i += 1; opts = opts.copy(problemsDir = args(i))
        case other =>
          System.err.println(s"unknown arg: $other")
      }
      i += 1
    }
    opts
  }

  def parseInterval(s: String): Long = {
    val (num, unit) = s.span(_.isDigit)
    val n = num.toLongOption.getOrElse(5L)
    unit match {
      case "s" | "" => n * 1000L
      case "m"      => n * 60 * 1000L
      case "h"      => n * 60 * 60 * 1000L
      case _        => n * 1000L
    }
  }
}