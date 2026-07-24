package com.wolfskeep.littleman.fetch

import java.nio.file.{Files, Path}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import upickle.default._

/** Tests [[ProblemFetcher]] logic using [[FakeApi]] + [[FsProblemStore]] in
  * a temp directory — no network. */
class ProblemApiSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  import ProblemApiSpec._

  private var tmpDir: Path = _
  private var store: FsProblemStore = _
  private var api: FakeApi = _

  override def beforeEach(): Unit = {
    tmpDir = Files.createTempDirectory("fetch-api-test")
    store = new FsProblemStore(tmpDir)
    api = new FakeApi()
  }

  override def afterEach(): Unit = {
    deleteRecursively(tmpDir)
  }

  private def deleteRecursively(p: Path): Unit = {
    if (Files.isDirectory(p)) Files.list(p).forEach(deleteRecursively)
    Files.deleteIfExists(p)
  }

  "ProblemFetcher" should {
    "create dirs, problem.json, derived files, and meta.json on first pass" in {
      api.problems = Vector(
        ContestProblem("1", "alpha", "Alpha", "set1", "released"),
        ContestProblem("2", "beta", "Beta", "set1", "released")
      )
      api.details = Map(
        "alpha" -> mkDetails("1", "alpha", "Alpha", "released"),
        "beta"  -> mkDetails("2", "beta",  "Beta",  "released")
      )
      val fetcher = new ProblemFetcher(api, store, () => "T1")
      val report = fetcher.fetchOnce()

      report.fetched shouldBe Vector("alpha", "beta")
      report.skipped shouldBe empty
      report.errors shouldBe empty
      store.hasBody("alpha") shouldBe true
      store.hasBody("beta") shouldBe true

      val meta = store.readMeta("alpha").get
      meta.id shouldBe "1"
      meta.status shouldBe "released"
      meta.fetchedAt shouldBe "T1"
      meta.bodyFetchedAt shouldBe Some("T1")
      meta.candidateScores shouldBe ujson.Arr()
      meta.frozen shouldBe ujson.False
    }

    "skip fetchProblem on a second pass when status is unchanged" in {
      api.problems = Vector(ContestProblem("1", "alpha", "Alpha", "set1", "released"))
      api.details = Map("alpha" -> mkDetails("1", "alpha", "Alpha", "released"))
      val fetcher = new ProblemFetcher(api, store, () => "T1")
      fetcher.fetchOnce()

      api.calls = 0 // reset call counter
      api.fetchCalls = 0
      val fetcher2 = new ProblemFetcher(api, store, () => "T2")
      val report = fetcher2.fetchOnce()

      report.fetched shouldBe empty
      report.skipped shouldBe Vector("alpha")
      api.fetchCalls shouldBe 0 // no full fetch
      val meta = store.readMeta("alpha").get
      meta.fetchedAt shouldBe "T2"     // advanced
      meta.bodyFetchedAt shouldBe Some("T1") // unchanged
    }

    "re-fetch when status changes" in {
      api.problems = Vector(ContestProblem("1", "alpha", "Alpha", "set1", "released"))
      api.details = Map("alpha" -> mkDetails("1", "alpha", "Alpha", "released"))
      val fetcher = new ProblemFetcher(api, store, () => "T1")
      fetcher.fetchOnce()

      // change status
      api.problems = Vector(ContestProblem("1", "alpha", "Alpha", "set1", "closed"))
      api.details = Map("alpha" -> mkDetails("1", "alpha", "Alpha", "closed"))
      api.fetchCalls = 0
      val fetcher2 = new ProblemFetcher(api, store, () => "T2")
      val report = fetcher2.fetchOnce()

      report.fetched shouldBe Vector("alpha")
      report.skipped shouldBe empty
      api.fetchCalls shouldBe 1
      val meta = store.readMeta("alpha").get
      meta.status shouldBe "closed"
      meta.bodyFetchedAt shouldBe Some("T2")
    }

    "preserve eval fields and unknown keys on re-fetch" in {
      api.problems = Vector(ContestProblem("1", "alpha", "Alpha", "set1", "released"))
      api.details = Map("alpha" -> mkDetails("1", "alpha", "Alpha", "released"))
      val fetcher = new ProblemFetcher(api, store, () => "T1")
      fetcher.fetchOnce()

      // seed eval fields and unknown key via raw meta.json
      val seed = """{
        "id":"1","slug":"alpha","name":"Alpha","problemSetName":"set1","status":"released",
        "fetchedAt":"T1","bodyFetchedAt":"T1",
        "bestScorePossible":99,
        "candidateScores":[{"score":50,"candidateSha":"abc","timestamp":"x"}],
        "lastSubmission":{"candidate":"abc","timestamp":"x"},
        "frozen":true,
        "futureField":"survive"
      }"""
      Files.write(tmpDir.resolve("alpha").resolve("meta.json"), seed.getBytes("UTF-8"))

      // re-fetch (status unchanged -> skip body, but meta still re-written)
      val fetcher2 = new ProblemFetcher(api, store, () => "T2")
      fetcher2.fetchOnce()

      val meta = store.readMeta("alpha").get
      meta.bestScorePossible shouldBe ujson.Num(99)
      meta.candidateScores shouldBe ujson.Arr(ujson.Obj("score" -> ujson.Num(50), "candidateSha" -> ujson.Str("abc"), "timestamp" -> ujson.Str("x")))
      meta.frozen shouldBe ujson.True
      meta.fetchedAt shouldBe "T2"
      val raw = new String(Files.readAllBytes(tmpDir.resolve("alpha").resolve("meta.json")), "UTF-8")
      raw should include("futureField")
      raw should include("survive")
    }

    "fetch practice problems identically (no special-casing)" in {
      api.problems = Vector(ContestProblem("3", "prac", "Prac", "set1", "practice"))
      api.details = Map("prac" -> mkDetails("3", "prac", "Prac", "practice"))
      val fetcher = new ProblemFetcher(api, store, () => "T1")
      val report = fetcher.fetchOnce()
      report.fetched shouldBe Vector("prac")
      store.hasBody("prac") shouldBe true
      store.readMeta("prac").get.status shouldBe "practice"
    }

    "continue past a per-problem fetch error" in {
      api.problems = Vector(
        ContestProblem("1", "alpha", "Alpha", "set1", "released"),
        ContestProblem("2", "beta", "Beta", "set1", "released")
      )
      api.details = Map("alpha" -> mkDetails("1", "alpha", "Alpha", "released"))
      // beta not in details -> FakeApi returns 404
      val fetcher = new ProblemFetcher(api, store, () => "T1")
      val report = fetcher.fetchOnce()
      report.fetched shouldBe Vector("alpha")
      report.errors should have size 1
      report.errors.head._1 shouldBe "beta"
      store.hasBody("alpha") shouldBe true
      store.hasBody("beta") shouldBe false
    }

    "report a fatal error when listProblems fails" in {
      api.listResult = Left(ApiError(500, "server_error", "oops"))
      val fetcher = new ProblemFetcher(api, store, () => "T1")
      val report = fetcher.fetchOnce()
      report.fatalError shouldBe defined
      report.fatalError.get.code shouldBe "server_error"
    }
  }
}

object ProblemApiSpec {
  def mkDetails(id: String, slug: String, name: String, status: String): ProblemDetails =
    ProblemDetails(id, slug, name, "set1", status,
      ujson.Str("desc"), ujson.Null, ujson.Null, ujson.Arr())

  /** A fake API with mutable state for testing. */
  class FakeApi extends ProblemApi {
    var problems: Vector[ContestProblem] = Vector.empty
    var details: Map[String, ProblemDetails] = Map.empty
    var listResult: Either[ApiError, Vector[ContestProblem]] = Right(Vector.empty)
    var calls: Int = 0
    var fetchCalls: Int = 0

    def listProblems(): Either[ApiError, Vector[ContestProblem]] = {
      calls += 1
      listResult match {
        case Left(_) => listResult
        case Right(_) => Right(problems)
      }
    }
    def fetchProblem(slug: String): Either[ApiError, ProblemDetails] = {
      fetchCalls += 1
      details.get(slug) match {
        case Some(d) => Right(d)
        case None => Left(ApiError(404, "not_found", s"no problem $slug"))
      }
    }
  }
}