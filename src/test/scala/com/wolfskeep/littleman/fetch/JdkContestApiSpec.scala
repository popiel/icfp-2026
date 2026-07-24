package com.wolfskeep.littleman.fetch

import java.net.{InetSocketAddress, URI}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

/** Tests [[JdkContestApi]] against an in-process `HttpServer` stub —
  * exercises the real `java.net.http.HttpClient` and JSON parsing without
  * touching the live contest server. */
class JdkContestApiSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private var server: HttpServer = _
  private var api: JdkContestApi = _
  private var handler: StubHandler = _

  override def beforeAll(): Unit = {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    handler = new StubHandler
    server.createContext("/", handler)
    server.start()
    val port = server.getAddress.getPort
    api = new JdkContestApi(s"http://localhost:$port/api/v1")
  }

  override def afterAll(): Unit = {
    server.stop(0)
  }

  "JdkContestApi" should {
    "listProblems parses a JSON array into Vector[ContestProblem]" in {
      handler.response = """[{"id":"1","slug":"alpha","name":"Alpha","problemSetName":"s1","status":"released"},
                            {"id":"2","slug":"beta","name":"Beta","problemSetName":"s1","status":"practice"}]"""
      handler.status = 200
      val result = api.listProblems()
      result shouldBe 'right
      result.toOption.get should have size 2
      result.toOption.get.head shouldBe ContestProblem("1", "alpha", "Alpha", "s1", "released")
      result.toOption.get(1).status shouldBe "practice"
    }

    "fetchProblem parses the JSON object into ProblemDetails with publicTestData" in {
      handler.response = """{"id":"3","slug":"gam","name":"Gamma","problemSetName":"s1","status":"released",
                            "description":"do it","io":{"in":5},"scoring":{"type":"ticks"},"publicTestData":[1,2,3]}"""
      handler.status = 200
      val result = api.fetchProblem("gam")
      result shouldBe 'right
      val d = result.toOption.get
      d.id shouldBe "3"
      d.slug shouldBe "gam"
      d.name shouldBe "Gamma"
      d.status shouldBe "released"
      d.description shouldBe ujson.Str("do it")
      d.publicTestData shouldBe ujson.Arr(ujson.Num(1), ujson.Num(2), ujson.Num(3))
    }

    "return Left(ApiError) on a 404 with error body" in {
      handler.response = """{"error":{"code":"not_found","message":"no such problem"}}"""
      handler.status = 404
      val result = api.fetchProblem("missing")
      result shouldBe 'left
      val err = result.left.get
      err.http shouldBe 404
      err.code shouldBe "not_found"
      err.message shouldBe "no such problem"
    }

    "return Left(ApiError) on a 500 server error" in {
      handler.response = """{"error":{"code":"server_error","message":"kaboom"}}"""
      handler.status = 500
      val result = api.listProblems()
      result shouldBe 'left
      result.left.get.http shouldBe 500
      result.left.get.code shouldBe "server_error"
    }
  }

  /** A simple handler that serves a fixed response body and status for all
    * requests, and records the path for assertions. */
  private class StubHandler extends HttpHandler {
    var response: String = ""
    var status: Int = 200
    var lastPath: String = ""

    def handle(exch: HttpExchange): Unit = {
      lastPath = exch.getRequestURI.getPath
      val body = response.getBytes("UTF-8")
      exch.sendResponseHeaders(status, body.length)
      exch.getResponseBody.write(body)
      exch.getResponseBody.close()
    }
  }
}