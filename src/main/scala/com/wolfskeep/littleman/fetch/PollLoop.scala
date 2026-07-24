package com.wolfskeep.littleman.fetch

/** Runs `fetchOnce()` on a schedule until interrupted. */
final class PollLoop(fetcher: ProblemFetcher, intervalMillis: Long) {
  def run(): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(() => {}))
    while (!Thread.interrupted()) {
      fetcher.fetchOnce()
      Thread.sleep(intervalMillis)
    }
  }
}