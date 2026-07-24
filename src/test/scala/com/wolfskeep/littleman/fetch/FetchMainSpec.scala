package com.wolfskeep.littleman.fetch

import java.nio.file.{Files, Path}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

class FetchMainSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  import ProblemApiSpec._

  private var tmpDir: Path = _
  private var store: FsProblemStore = _
  private var api: FakeApi = _

  override def beforeEach(): Unit = {
    tmpDir = Files.createTempDirectory("fetch-main-test")
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

  "FetchMain.parseInterval" should {
    "parse '5m' as 5 minutes" in { FetchMain.parseInterval("5m") shouldBe 300000L }
    "parse '30s' as 30 seconds" in { FetchMain.parseInterval("30s") shouldBe 30000L }
    "parse '90s' as 90 seconds" in { FetchMain.parseInterval("90s") shouldBe 90000L }
    "parse '1h' as 1 hour" in { FetchMain.parseInterval("1h") shouldBe 3600000L }
  }

  "FetchMain.parseArgs" should {
    "default to --once with default URL" in {
      val o = FetchMain.parseArgs(Array.empty)
      o.watch shouldBe false
      o.baseUrl shouldBe FetchMain.DefaultBaseUrl
      o.problemsDir shouldBe "problems"
    }
    "parse --watch and --interval" in {
      val o = FetchMain.parseArgs(Array("--watch", "--interval", "2m"))
      o.watch shouldBe true
      o.intervalMillis shouldBe 120000L
    }
    "parse --base-url and --problems-dir" in {
      val o = FetchMain.parseArgs(Array("--base-url", "http://x", "--problems-dir", "/tmp/p"))
      o.baseUrl shouldBe "http://x"
      o.problemsDir shouldBe "/tmp/p"
    }
  }

  "FetchMain.run" should {
    "exit 0 on a successful --once pass with a fake API" in {
      api.problems = Vector(ContestProblem("1", "alpha", "Alpha", "s1", "released"))
      api.details = Map("alpha" -> ProblemApiSpec.mkDetails("1", "alpha", "Alpha", "released"))
      val code = FetchMain.run(Array("--once"), api, store)
      code shouldBe 0
      store.hasBody("alpha") shouldBe true
    }

    "exit 1 on a fatal listProblems error" in {
      api.listResult = Left(ApiError(500, "server_error", "oops"))
      val code = FetchMain.run(Array("--once"), api, store)
      code shouldBe 1
    }
  }
}