package com.wolfskeep.littleman.fetch

import java.net.{URI, http}

/** A [[ProblemApi]] backed by the JDK 11+ `java.net.http.HttpClient`.
  *
  * List and fetch are unauthenticated (per `docs/api-help.md`). This class
  * is designed to be reusable by the later submission portion, which will
  * add an `apiKey` parameter and POST/poll methods. */
final class JdkContestApi(baseUrl: String) extends ProblemApi {
  private val client = http.HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofSeconds(10))
    .build()

  def listProblems(): Either[ApiError, Vector[ContestProblem]] = {
    val uri = URI.create(baseUrl + "/public/problems")
    sendRequest(uri) match {
      case Left(e) => Left(e)
      case Right(body) =>
        try {
          val arr = ujson.read(body).arr
          Right(arr.map(parseProblem).toVector)
        } catch {
          case e: Exception => Left(ApiError(200, "parse_error", e.getMessage))
        }
    }
  }

  def fetchProblem(slug: String): Either[ApiError, ProblemDetails] = {
    val uri = URI.create(baseUrl + s"/public/problems/$slug")
    sendRequest(uri) match {
      case Left(e) => Left(e)
      case Right(body) =>
        try {
          val obj = ujson.read(body).obj
          Right(parseDetails(obj))
        } catch {
          case e: Exception => Left(ApiError(200, "parse_error", e.getMessage))
        }
    }
  }

  private def sendRequest(uri: URI): Either[ApiError, String] = {
    val request = http.HttpRequest.newBuilder()
      .uri(uri)
      .timeout(java.time.Duration.ofSeconds(10))
      .GET()
      .build()
    try {
      val response = client.send(request, http.HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        Right(response.body())
      } else {
        parseError(response.statusCode(), response.body())
      }
    } catch {
      case e: Exception =>
        Left(ApiError(0, "network_error", e.getMessage))
    }
  }

  private def parseError(http: Int, body: String): Left[ApiError, Nothing] = {
    try {
      val obj = ujson.read(body).obj
      val err = obj("error").obj
      Left(ApiError(http, err("code").str, err("message").str))
    } catch {
      case _: Exception =>
        Left(ApiError(http, "unknown_error", body))
    }
  }

  private def parseProblem(json: ujson.Value): ContestProblem = json match {
    case o: ujson.Obj =>
      ContestProblem(
        id = o("id").str,
        slug = o("slug").str,
        name = o("name").str,
        problemSetName = o("problemSetName").str,
        status = o("status").str
      )
    case _ => throw new RuntimeException("expected object")
  }

  private def parseDetails(obj: ujson.Obj): ProblemDetails =
    ProblemDetails(
      id = obj("id").str,
      slug = obj("slug").str,
      name = obj("name").str,
      problemSetName = obj("problemSetName").str,
      status = obj("status").str,
      description = obj.value.getOrElse("description", ujson.Null),
      io = obj.value.getOrElse("io", ujson.Null),
      scoring = obj.value.getOrElse("scoring", ujson.Null),
      publicTestData = obj.value.getOrElse("publicTestData", ujson.Null)
    )
}