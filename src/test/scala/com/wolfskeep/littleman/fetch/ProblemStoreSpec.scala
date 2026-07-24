package com.wolfskeep.littleman.fetch

import java.nio.file.{Files, Path}
import com.wolfskeep.littleman.fetch.{FsProblemStore, ProblemMeta, ProblemDetails, FetcherFields}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll}
import upickle.default._

class ProblemStoreSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  private var tmpDir: Path = _
  private var store: FsProblemStore = _

  override def beforeEach(): Unit = {
    tmpDir = Files.createTempDirectory("fetch-test")
    store = new FsProblemStore(tmpDir)
  }

  override def afterEach(): Unit = {
    deleteRecursively(tmpDir)
  }

  private def deleteRecursively(p: Path): Unit = {
    if (Files.isDirectory(p)) {
      Files.list(p).forEach(deleteRecursively)
    }
    Files.deleteIfExists(p)
  }

  "FsProblemStore" should {
    "ensureProblemDir creates the directory if absent" in {
      val p = store.ensureProblemDir("my-problem")
      Files.isDirectory(p) shouldBe true
    }

    "ensureProblemDir is idempotent" in {
      val p1 = store.ensureProblemDir("my-problem")
      val p2 = store.ensureProblemDir("my-problem")
      p1 shouldBe p2
    }

    "ensureProblemDir preserves pre-existing solution.man in the dir" in {
      val p = store.ensureProblemDir("triangle")
      val sol = p.resolve("solution.man")
      Files.write(sol, "hello".getBytes("UTF-8"))
      store.ensureProblemDir("triangle")
      Files.exists(sol) shouldBe true
      new String(Files.readAllBytes(sol), "UTF-8") shouldBe "hello"
    }

    "writeMeta initializes eval fields on first create" in {
      store.writeMeta("p", FetcherFields(
        id = "42", slug = "p", name = "P", problemSetName = "set1",
        status = "released", fetchedAt = "T1", bodyFetchedAt = None))
      val meta = store.readMeta("p").get
      meta.id shouldBe "42"
      meta.slug shouldBe "p"
      meta.status shouldBe "released"
      meta.fetchedAt shouldBe "T1"
      meta.bodyFetchedAt shouldBe None
      meta.bestScorePossible shouldBe ujson.Null
      meta.candidateScores shouldBe ujson.Arr()
      meta.lastSubmission shouldBe ujson.Null
      meta.frozen shouldBe ujson.False
    }

    "writeMeta preserves eval fields and unknown keys on overwrite" in {
      // seed meta with eval fields and an unknown sentinel key
      val seed =
        """{
          "id":"old","slug":"p","name":"old","problemSetName":"s","status":"practice",
          "fetchedAt":"T0","bodyFetchedAt":"T9",
          "bestScorePossible":123,
          "candidateScores":[{"score":50,"candidateSha":"abc","timestamp":"x"}],
          "lastSubmission":{"candidate":"abc","timestamp":"x"},
          "frozen":true,
          "customFutureField":"survive"
        }"""
      val p = store.ensureProblemDir("p")
      Files.write(p.resolve("meta.json"), seed.getBytes("UTF-8"))

      // overwrite fetcher-owned fields
      store.writeMeta("p", FetcherFields(
        id = "42", slug = "p", name = "NewName", problemSetName = "s",
        status = "released", fetchedAt = "T2", bodyFetchedAt = Some("T2")))

      val meta = store.readMeta("p").get
      // fetcher-owned fields updated
      meta.id shouldBe "42"
      meta.name shouldBe "NewName"
      meta.status shouldBe "released"
      meta.fetchedAt shouldBe "T2"
      meta.bodyFetchedAt shouldBe Some("T2")
      // eval fields preserved
      meta.bestScorePossible shouldBe ujson.Num(123)
      meta.candidateScores shouldBe ujson.Arr(ujson.Obj("score" -> ujson.Num(50), "candidateSha" -> ujson.Str("abc"), "timestamp" -> ujson.Str("x")))
      meta.lastSubmission shouldBe ujson.Obj("candidate" -> ujson.Str("abc"), "timestamp" -> ujson.Str("x"))
      meta.frozen shouldBe ujson.True

      // unknown keys preserved
      val raw = new String(Files.readAllBytes(p.resolve("meta.json")), "UTF-8")
      raw should include("customFutureField")
      raw should include("survive")
    }

    "writeProblemBody writes problem.json and derived files" in {
      val p = store.ensureProblemDir("p")
      val details = ProblemDetails(
        id = "1", slug = "p", name = "P", problemSetName = "s", status = "released",
        description = ujson.Str("do the thing"),
        io = ujson.Obj("in" -> ujson.Num(1)),
        scoring = ujson.Obj("type" -> ujson.Str("ticks")),
        publicTestData = ujson.Arr(ujson.Num(1), ujson.Num(2))
      )
      store.writeProblemBody("p", details)
      Files.exists(p.resolve("problem.json")) shouldBe true
      Files.exists(p.resolve("description.md")) shouldBe true
      Files.exists(p.resolve("io.json")) shouldBe true
      Files.exists(p.resolve("scoring.json")) shouldBe true
      Files.exists(p.resolve("publicTestData.json")) shouldBe true
      new String(Files.readAllBytes(p.resolve("description.md")), "UTF-8") shouldBe "do the thing"
      read[ujson.Value](new String(Files.readAllBytes(p.resolve("io.json")), "UTF-8")) shouldBe ujson.Obj("in" -> ujson.Num(1))
    }

    "hasBody returns false when no problem.json exists" in {
      store.ensureProblemDir("p")
      store.hasBody("p") shouldBe false
    }

    "hasBody returns true after writeProblemBody" in {
      val details = ProblemDetails("1","p","P","s","released",ujson.Null,ujson.Null,ujson.Null,ujson.Null)
      store.writeProblemBody("p", details)
      store.hasBody("p") shouldBe true
    }
  }
}