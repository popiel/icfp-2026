package com.wolfskeep.littleman.fetch

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters._

/** A filesystem-backed store for problem materials under `problemsDir`.
  *
  * Each problem lives at `problemsDir/<slug>/` and contains:
  * - `meta.json` — metadata (fetcher-owned + eval-owned fields, merged)
  * - `problem.json` — the raw API response (written by `writeProblemBody`)
  * - `description.md` (or `.json`), `io.json`, `scoring.json`,
  *   `publicTestData.json` — derived files
  *
  * `writeMeta` MERGES: it overwrites only fetcher-owned fields and
  * preserves eval-owned fields and unknown keys verbatim. */
final class FsProblemStore(problemsDir: Path) extends ProblemStore {

  def ensureProblemDir(slug: String): Path = {
    val p = problemsDir.resolve(slug)
    if (!Files.exists(p)) Files.createDirectories(p)
    p
  }

  def readMeta(slug: String): Option[ProblemMeta] = {
    val f = problemsDir.resolve(slug).resolve("meta.json")
    if (!Files.exists(f)) None
    else {
      val json = ujson.read(new String(Files.readAllBytes(f), "UTF-8"))
      Some(parseMeta(json))
    }
  }

  def writeMeta(slug: String, fields: FetcherFields): Unit = {
    val dir = ensureProblemDir(slug)
    val f = dir.resolve("meta.json")
    // read existing json (if any) to preserve eval/unknown fields
    val existing: ujson.Value =
      if (Files.exists(f)) ujson.read(new String(Files.readAllBytes(f), "UTF-8"))
      else ujson.Obj()

    // merge: set fetcher-owned fields, leave everything else untouched
    existing match {
      case obj: ujson.Obj =>
        obj("id") = ujson.Str(fields.id)
        obj("slug") = ujson.Str(fields.slug)
        obj("name") = ujson.Str(fields.name)
        obj("problemSetName") = ujson.Str(fields.problemSetName)
        obj("status") = ujson.Str(fields.status)
        obj("fetchedAt") = ujson.Str(fields.fetchedAt)
        obj("bodyFetchedAt") = fields.bodyFetchedAt.map(ujson.Str).getOrElse(ujson.Null)
        // initialize eval fields on first create only (don't overwrite existing)
        if (!obj.value.contains("bestScorePossible"))
          obj("bestScorePossible") = ujson.Null
        if (!obj.value.contains("candidateScores"))
          obj("candidateScores") = ujson.Arr()
        if (!obj.value.contains("lastSubmission"))
          obj("lastSubmission") = ujson.Null
        if (!obj.value.contains("frozen"))
          obj("frozen") = ujson.False
      case _ => () // existing wasn't an object; shouldn't happen
    }

    // atomic write
    val tmp = Files.createTempFile(dir, "meta", ".json.tmp")
    Files.write(tmp, ujson.write(existing, indent = 2).getBytes("UTF-8"))
    Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }

  def writeProblemBody(slug: String, details: ProblemDetails): Unit = {
    val dir = ensureProblemDir(slug)
    // build the full problem.json from the details fields
    val obj = ujson.Obj(
      "id" -> ujson.Str(details.id),
      "slug" -> ujson.Str(details.slug),
      "name" -> ujson.Str(details.name),
      "problemSetName" -> ujson.Str(details.problemSetName),
      "status" -> ujson.Str(details.status),
      "description" -> details.description,
      "io" -> details.io,
      "scoring" -> details.scoring,
      "publicTestData" -> details.publicTestData
    )
    Files.write(dir.resolve("problem.json"), ujson.write(obj, indent = 2).getBytes("UTF-8"))
    // derived files
    writeDerived(dir, "description", details.description)
    writeDerived(dir, "io", details.io)
    writeDerived(dir, "scoring", details.scoring)
    writeDerived(dir, "publicTestData", details.publicTestData)
  }

  def hasBody(slug: String): Boolean =
    Files.exists(problemsDir.resolve(slug).resolve("problem.json"))

  private def writeDerived(dir: Path, name: String, value: ujson.Value): Unit = {
    value match {
      case ujson.Str(s) =>
        Files.write(dir.resolve(s"$name.md"), s.getBytes("UTF-8"))
      case _ =>
        Files.write(dir.resolve(s"$name.json"), ujson.write(value, indent = 2).getBytes("UTF-8"))
    }
  }

  private def parseMeta(json: ujson.Value): ProblemMeta = json match {
    case obj: ujson.Obj =>
      ProblemMeta(
        id = obj("id").strOpt.getOrElse(""),
        slug = obj("slug").strOpt.getOrElse(""),
        name = obj("name").strOpt.getOrElse(""),
        problemSetName = obj("problemSetName").strOpt.getOrElse(""),
        status = obj("status").strOpt.getOrElse(""),
        fetchedAt = obj("fetchedAt").strOpt.getOrElse(""),
        bodyFetchedAt = obj("bodyFetchedAt") match {
          case ujson.Null => None
          case ujson.Str(s) => Some(s)
          case _ => None
        },
        bestScorePossible = obj.value.getOrElse("bestScorePossible", ujson.Null),
        candidateScores = obj.value.getOrElse("candidateScores", ujson.Arr()),
        lastSubmission = obj.value.getOrElse("lastSubmission", ujson.Null),
        frozen = obj.value.getOrElse("frozen", ujson.False)
      )
    case _ => throw new RuntimeException(s"invalid meta.json: expected object")
  }
}