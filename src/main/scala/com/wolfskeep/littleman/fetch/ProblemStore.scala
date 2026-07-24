package com.wolfskeep.littleman.fetch

import java.nio.file.Path

/** An idempotent store for problem materials and `meta.json`.
  *
  * `writeMeta` merges fetcher-owned fields into `meta.json` while
  * preserving eval-owned fields and unknown keys verbatim. */
trait ProblemStore {
  def ensureProblemDir(slug: String): Path
  def readMeta(slug: String): Option[ProblemMeta]
  def writeMeta(slug: String, fields: FetcherFields): Unit
  def writeProblemBody(slug: String, details: ProblemDetails): Unit
  def hasBody(slug: String): Boolean
}