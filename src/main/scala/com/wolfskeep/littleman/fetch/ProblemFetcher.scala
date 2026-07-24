package com.wolfskeep.littleman.fetch

/** The result of one polling pass: counts and any per-problem errors. */
final case class FetchReport(
  fetched: Vector[String] = Vector.empty,
  skipped: Vector[String] = Vector.empty,
  errors: Vector[(String, ApiError)] = Vector.empty,
  fatalError: Option[ApiError] = None
)

/** One idempotent polling pass: list all problems, fetch those with a
  * status change (or never fetched), and update `meta.json`.
  *
  * @param api   the contest API client
  * @param store the filesystem store
  * @param clock supplies timestamp strings for `fetchedAt`/`bodyFetchedAt`
  */
final class ProblemFetcher(api: ProblemApi, store: ProblemStore, clock: () => String) {

  def fetchOnce(): FetchReport = {
    api.listProblems() match {
      case Left(err) =>
        FetchReport(fatalError = Some(err))
      case Right(problems) =>
        var fetched = Vector.empty[String]
        var skipped = Vector.empty[String]
        var errors  = Vector.empty[(String, ApiError)]
        for (prob <- problems) {
          val slug = prob.slug
          store.ensureProblemDir(slug)
          val prevMeta = store.readMeta(slug)
          val prevStatus = prevMeta.map(_.status).filter(_.nonEmpty)

          val needsRefetch = prevStatus.forall(_ != prob.status) || !store.hasBody(slug)
          val bodyFetchedAt: Option[String] =
            if (needsRefetch) {
              api.fetchProblem(slug) match {
                case Right(details) =>
                  store.writeProblemBody(slug, details)
                  fetched = fetched :+ slug
                  Some(clock())
                case Left(err) =>
                  errors = errors :+ ((slug, err))
                  // use prevMeta's bodyFetchedAt or None
                  prevMeta.flatMap(_.bodyFetchedAt)
              }
            } else {
              skipped = skipped :+ slug
              prevMeta.flatMap(_.bodyFetchedAt)
            }

          store.writeMeta(slug, FetcherFields(
            id = prob.id,
            slug = slug,
            name = prob.name,
            problemSetName = prob.problemSetName,
            status = prob.status,
            fetchedAt = clock(),
            bodyFetchedAt = bodyFetchedAt
          ))
        }
        FetchReport(fetched, skipped, errors)
    }
  }
}