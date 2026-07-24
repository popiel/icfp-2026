package com.wolfskeep.littleman.fetch

/** A problem as returned by `GET /public/problems` (the list endpoint). */
final case class ContestProblem(
  id: String,
  slug: String,
  name: String,
  problemSetName: String,
  status: String
)

/** A problem with its full details, as returned by
  * `GET /public/problems/<slug>`. The four content fields are stored as
  * `ujson.Value` because their exact shape is server-defined and may
  * change; we round-trip them verbatim. */
final case class ProblemDetails(
  id: String,
  slug: String,
  name: String,
  problemSetName: String,
  status: String,
  description: ujson.Value,
  io: ujson.Value,
  scoring: ujson.Value,
  publicTestData: ujson.Value
)

/** The fetcher-owned fields of `meta.json`, passed to `writeMeta` to be
  * merged with the eval-owned fields already on disk. */
final case class FetcherFields(
  id: String,
  slug: String,
  name: String,
  problemSetName: String,
  status: String,
  fetchedAt: String,
  bodyFetchedAt: Option[String]
)

/** The full `meta.json` as parsed; the fetcher reads only the fetcher-owned
  * fields (`status`, `bodyFetchedAt`), while the eval-portion owns the rest. */
final case class ProblemMeta(
  id: String,
  slug: String,
  name: String,
  problemSetName: String,
  status: String,
  fetchedAt: String,
  bodyFetchedAt: Option[String],
  bestScorePossible: ujson.Value,
  candidateScores: ujson.Value,
  lastSubmission: ujson.Value,
  frozen: ujson.Value
)