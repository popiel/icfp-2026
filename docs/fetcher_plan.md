# Problem-Fetching Portion — Design Plan

A design for the problem-fetching portion of the contest coordination
framework. The fetcher periodically polls the contest server for problem
sets, downloads each problem's materials to `problems/<slug>/`, and
maintains the fetcher-owned fields of each problem's `meta.json`. Candidate
evaluation, scoring, and server submission are **separate** framework
portions (out of scope here).

Sources: `docs/framework.md` (framework overview) and `docs/api-help.md`
(contest REST API).

## 0. Scope & key facts

**In scope (this portion):** periodically poll the contest server for
problem sets, fetch each new/changed problem, write its materials to
`problems/<slug>/`, and maintain the fetcher-owned fields of each problem's
`meta.json`.

**Explicitly out of scope** (separate framework portions): candidate
evaluation runs, score computation, and server submission/polling. The
fetcher **shares** `meta.json` with those portions and must preserve their
fields (see §6).

**Confirmed decisions:**
1. Directory naming: **by `slug`** (matches the existing
   `problems/1-1-triangle`). The numeric `id` is stored inside `meta.json`
   for use at submission time.
2. HTTP + JSON: **JDK `java.net.http.HttpClient`** (no transport dep) +
   **upickle** (`com.lihaoyi:upickle`, one new sbt dep) for JSON AST and
   case-class codecs.
3. Stored files: **raw `problem.json`** (full API response) + **split
   derived files** (`description.md`, `io.json`, `scoring.json`,
   `publicTestData.json`) + `meta.json`.
4. Poll behavior: **re-fetch the full problem body only when `status`
   changed** (or on first fetch); always cheaply refresh list-level fields
   (id/name/problemSetName/status) from the list response.

**Key simplification:** per `docs/api-help.md`, the List and Fetch-one
endpoints are **unauthenticated** — no API key required. So the
**problem-fetcher never reads `/api-key`** (it stays gitignored and
untouched by this portion; only the submission portion will need it).

## 1. Package & layout

Flat package `com.wolfskeep.littleman.fetch` (matches the existing flat
`com.wolfskeep.littleman` convention; a `fetch` subpackage keeps the new
code visually separate from the interpreter/parser/runtime).

```
src/main/scala/com/wolfskeep/littleman/fetch/
  ContestProblem.scala        // immutable: id, slug, name, problemSetName, status
  ProblemDetails.scala        // ContestProblem + description, io, scoring, publicTestData (as ujson.Value)
  ProblemMeta.scala           // the meta.json schema (see §6), with upickle codecs
  ProblemApi.scala            // trait: listProblems(), fetchProblem(slug); + JdkContestApi impl + FakeContestApi for tests
  ProblemStore.scala          // trait: read/write meta.json + derived files; merge-preserving eval fields
  FsProblemStore.scala        // filesystem impl of ProblemStore
  ProblemFetcher.scala        // orchestrator: one polling pass; idempotent
  PollLoop.scala              // scheduled re-invocation of ProblemFetcher (daemon mode)
  FetchMain.scala             // CLI: --once | --watch --interval 5m ...
src/test/scala/com/wolfskeep/littleman/fetch/
  ProblemApiSpec.scala        // fake API + assertions on fetcher behaviour
  ProblemStoreSpec.scala      // temp-dir merge-preserve + derived files
  JdkContestApiSpec.scala     // real HttpClient vs in-process HttpServer stub
  FetchMainSpec.scala         // CLI args, exit codes
```

DI throughout: `ProblemFetcher(api, store)` and `FetchMain` wires
`JdkContestApi` + `FsProblemStore`. `JdkContestApi` is instantiable (takes
`baseUrl`), not a singleton — testable.

## 2. Add to `build.sbt`

```scala
libraryDependencies += "com.lihaoyi" %% "upickle" % "3.1.0"   // latest 3.x for Scala 2.13
```

No HTTP dependency (JDK `HttpClient`). No new test deps.

## 3. Types (immutable, `upickle.default` codecs)

```scala
final case class ContestProblem(id: String, slug: String, name: String,
                                 problemSetName: String, status: String)
// status values seen so far: "practice", "released" (we don't hard-code an
// enum; "practice" simply marks the problem as ungraded; the fetcher stores
// verbatim).

final case class ProblemDetails(
  id: String, slug: String, name: String, problemSetName: String, status: String,
  description: ujson.Value,       // may be Str / Null / Obj
  io: ujson.Value,
  scoring: ujson.Value,
  publicTestData: ujson.Value
)
```

Using `ujson.Value` for the four shape-unknown fields keeps us
**forward-compatible** with whatever the server returns, and lets us
round-trip the raw `problem.json` exactly. `description` is written as
`description.md` only if it's a `Str`; otherwise it's a `.json` too.

## 4. `ProblemApi` trait + JDK impl

```scala
trait ProblemApi {
  def listProblems(): Either[ApiError, Vector[ContestProblem]]
  def fetchProblem(slug: String): Either[ApiError, ProblemDetails]
}
final case class ApiError(http: Int, code: String, message: String)
```

`JdkContestApi(baseUrl: String)` uses `java.net.http.HttpClient` with:
- `GET /public/problems` → parse JSON array → `Vector[ContestProblem]`.
- `GET /public/problems/<slug>` → parse JSON object → `ProblemDetails`.
- Non-2xx → `Left(ApiError(...))` parsed from `{"error":{"code","message"}}`.
- Short, fixed read timeout (e.g. 10 s); no retries inside the client (the
  fetcher decides retry/backoff).

**`FakeContestApi`** (in test sources, mutable map of slug → details) drives
all fetcher-logic tests without any network.

## 5. `ProblemStore` trait + `FsProblemStore`

```scala
trait ProblemStore {
  def ensureProblemDir(slug: String): Path
  def readMeta(slug: String): Option[ProblemMeta]
  def writeMeta(slug: String, meta: ProblemMeta): Unit       // MERGES, preserving eval fields (§6)
  def writeProblemBody(slug: String, rawJson: String, details: ProblemDetails): Unit
  def hasBody(slug: String): Boolean
}
```

`FsProblemStore(problemsDir: Path)`:
- `ensureProblemDir`: `Files.createDirectories(problems/<slug>/)`.
  **Must not clobber** a hand-made dir whose contents predate the framework
  (e.g. the existing `1-1-triangle/solution.man`); it only creates the dir
  if absent.
- `writeProblemBody`: writes `problem.json` (the raw server JSON,
  pretty-printed via `ujson.read`/`write(..., indent=2)`) and the split
  derived files (`description.md`/`.json`, `io.json`, `scoring.json`,
  `publicTestData.json`).
- `writeMeta`: **reads** the existing meta.json (if any), overlays
  fetcher-owned fields, **preserves** the eval-owned fields and any unknown
  keys (forward-compat). Atomic write (temp file + move).

## 6. `meta.json` schema and field ownership

```
{
  // — fetcher-owned (refreshed every poll or on status change) —
  "id": "<api id, used for submissions>",
  "slug": "1-1-triangle",
  "name": "...",
  "problemSetName": "...",
  "status": "released" | "practice" | ...,
  "fetchedAt":   "2026-07-24T12:00:00Z",   // last list-poll pass
  "bodyFetchedAt": "2026-07-24T12:00:00Z",  // last full fetch

  // — eval portion-owned: fetcher NEVER overwrites; only initializes on create —
  "bestScorePossible": null,   // filled in later by the scorer
  "candidateScores":   [],     // {score, candidateSha, timestamp}, best->worst
  "lastSubmission":    null,   // {candidate, timestamp, expectedScore, serverResponse}
  "frozen":            false
}
```

**Critical contract:** on `writeMeta`, the fetcher merges — it overwrites
only the eight fetcher-owned keys above; it reads and re-writes every other
key (the four eval/submission-owned fields, plus any unknown keys added by
future portions) **verbatim**.

## 7. `ProblemFetcher` orchestrator (one pass; idempotent)

```
def fetchOnce(): FetchReport
```

Each pass:
1. `listProblems()` → on `Left` log and end (non-fatal — return report; exit
   0 for `--once`).
2. For each `ContestProblem`:
   a. `ensureProblemDir(slug)`.
   b. Read existing `meta.json` → `prevStatus`.
   c. Refresh fetcher-owned list fields (id, slug, name, problemSetName,
      status, `fetchedAt`) into meta, preserving the rest.
   d. **Re-fetch rule:** if `prevStatus != status` **or**
      `!hasBody(slug)` → `fetchProblem(slug)` → on `Right`,
      `writeProblemBody(...)` and set `bodyFetchedAt`. On `Left` error,
      log it and skip this problem (do **not** abort the whole pass).
   e. `writeMeta(slug, merged)`.
3. Return a `FetchReport` (counts: fetched, skipped, errors per slug).

Idempotent: running twice with no status changes makes zero full GETs and
only updates `fetchedAt`.

`status` transitions to watch: the contest may flip `practice` →
`released` (or to a "closed" state) over time; the rule re-pulls on any
change.

## 8. `PollLoop` (daemon mode)

`PollLoop(fetcher, interval)` runs `fetchOnce()` then
`Thread.sleep(interval.toMillis)` until interrupted (Ctrl-C via a shutdown
hook). No background executor — single thread; the operation is short and
I/O-bound.

## 9. `FetchMain` CLI

```
FetchMain [--once | --watch] [--interval 5m] [--base-url URL] [--problems-dir problems]
```

- Default: `--once`, base url `https://icfpcontest2026.com/api/v1`,
  problems dir `problems`.
- `--once`: one `fetchOnce()`; exit 0 unless the list call itself fails
  fatally, in which case exit 1. Per-problem fetch errors are logged to
  stderr but don't fail the run.
- `--watch`: `PollLoop`; never exits except on signal.
- No API key is read by this entry point.

## 10. TDD plan (tests-first, per AGENTS.md)

1. **`ProblemApiSpec`** (uses `FakeContestApi` + a temp-dir
   `FsProblemStore` + a real `ProblemFetcher`):
   - First pass: for two canned problems, creates `problems/<slug>/`,
     writes `problem.json` + the four derived files, and `meta.json` with
     correct fetcher-owned fields and the four eval fields initialized
     (`candidateScores=[]`, `lastSubmission=null`, `frozen=false`,
     `bestScorePossible=null`).
   - Second pass, **status unchanged**: `listProblems` called once;
     `fetchProblem` not called; `bodyFetchedAt` unchanged; `fetchedAt`
     advanced.
   - Status change on one problem → `fetchProblem` called only for that
     one; its `problem.json` rewritten and `bodyFetchedAt` advanced; the
     other untouched.
   - **Preserve eval fields**: pre-write `meta.json` with non-empty
     `candidateScores`, a `lastSubmission`, `frozen=true`, and a sentinel
     unknown key; run a fetch pass; assert those four preserved exactly
     while static fields updated.
   - **Practice problem** (`status="practice"`): dir + files created
     identically; no special-casing.
   - **Per-problem error**: one slug's `fetchProblem` returns
     `ApiError(404,...)`; the other problem still fetched; report records
     the error; exit clean.
   - List-call failure: `listProblems` returns `Left` → no dirs touched,
     report records it.
2. **`ProblemStoreSpec`** (temp dir; no network):
   - `ensureProblemDir` idempotent and non-destructive of pre-existing
     `solution.man`.
   - `writeMeta` merge preserves eval fields + unknown keys; initializes
     them on first create.
   - `writeProblemBody` writes `problem.json` byte-identical to the input
     pretty-printed, and the derived files.
   - `hasBody` reflects `problem.json` presence.
3. **`JdkContestApiSpec`** (real HttpClient against an in-process
   `com.sun.net.httpserver.HttpServer` stub):
   - `listProblems` parses a JSON array into `Vector[ContestProblem]`.
   - `fetchProblem("x")` parses the object into `ProblemDetails` with
     `publicTestData` round-tripped.
   - 404 response → `Left(ApiError(404, "not_found", ...))` parsed from
     the error body.
4. **`FetchMainSpec`**:
   - `--once` with a fake API injected via a test seam exits 0 after one
     pass.
   - `--interval` parsing (`5m`, `30s`, `90s`).
   - Missing fatal list call → exit 1.

A small test seam: `FetchMain.run(args, api, store)` so specs inject
`FakeContestApi` + a temp dir without touching the network or real argv.

## 11. Assumptions / open items

1. **`description`/`io`/`scoring`/`publicTestData` shapes are unknown.**
   Storing them as `ujson.Value` and round-tripping the raw JSON avoids
   guessing. `description` is written as `.md` only when it's a string;
   otherwise a `.json`.
2. **"Best score possible"** is not known at fetch time (the API `scoring`
   field's exact meaning is unclear). `meta.json.bestScorePossible` is left
   `null` by the fetcher; the scorer (separate portion) fills it from
   `scoring.json`.
3. **Poll cadence default 5 minutes**, overridable via `--interval`. The
   fetcher itself never reads `/api-key`. Submit/submit-poll is a separate
   portion that will re-use this `JdkContestApi`/`ApiError` infrastructure
   and add a bearer-token header.
4. **`JdkContestApi` will be written to be reusable** by the later
   submission portion (it will gain an `apiKey` param and POST/poll
   methods), but those methods are **not** implemented in this portion —
   only `listProblems`/`fetchProblem`.
5. **No new CI** is added by this portion; the existing `sbt test` covers
   the new specs.
6. **Concurrent fetcher runs** are not guarded (no lockfile). If you'll run
   `--watch` plus a cron `--once`, a simple `FileLock` on a
   `problems/.lock` can be added later.
7. **`problems/1-1-triangle` coexistence**: the existing hand-made dir has
   only `solution.man` (no `meta.json`/`problem.json`). The fetcher will,
   on first poll, add `meta.json` + `problem.json` into it (since
   `prevStatus` is absent → full re-fetch). `ensureProblemDir` leaves
   `solution.man` untouched.