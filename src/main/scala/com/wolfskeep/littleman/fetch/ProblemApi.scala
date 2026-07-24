package com.wolfskeep.littleman.fetch

/** A fatal error from the contest API, parsed from the JSON error body. */
final case class ApiError(http: Int, code: String, message: String)

/** The contest REST API client. `listProblems` and `fetchProblem` are
  * unauthenticated (per `docs/api-help.md`); the submission portion will
  * add authenticated methods later. */
trait ProblemApi {
  def listProblems(): Either[ApiError, Vector[ContestProblem]]
  def fetchProblem(slug: String): Either[ApiError, ProblemDetails]
}