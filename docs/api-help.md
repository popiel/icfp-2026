# ICFP Contest 2026 — API Specification

A concise specification for the contest submission REST API.

Source: the [API help page](https://icfpcontest2026.com/api-help).

## 0. Conventions

- **Base URL:** `https://icfpcontest2026.com/api/v1`
- All responses are **JSON**.
- **Authentication:** send your team's API key as a bearer token on every request that requires one:
  ```
  Authorization: Bearer <API-KEY>
  ```
- The API key is a secret. Ours lives in `/api-key` (gitignored — **never commit it**).

## 1. List problems

- **`GET /public/problems`** — no auth required.
- Returns every released problem. Each entry has:
  - `id` — the value used when **submitting**.
  - `slug` — the value used by **every other endpoint**.
  - `name`
  - `problemSetName`
  - `status` — `practice` problems are ungraded and reject submissions.

```
curl https://icfpcontest2026.com/api/v1/public/problems
```

## 2. Fetch one problem

- **`GET /public/problems/<slug>`** — no auth required.
- Adds (relative to the list entry):
  - `description`
  - `io`
  - `scoring`
  - `publicTestData` — the same public cases the editor runs. **Private cases are not served.**

```
curl https://icfpcontest2026.com/api/v1/public/problems/<slug>
```

## 3. Submit a program

- **`POST /submissions`** — requires the bearer token.
- Request body (JSON):
  - `problemId` — the problem's `id` (not `slug`).
  - `program` — the grid source itself, **newlines and all**.
- On success returns **`202`** with `{"id":"…","status":"pending"}`.

```
curl -X POST https://icfpcontest2026.com/api/v1/submissions \
  -H "Authorization: Bearer <API-KEY>" \
  -H "Content-Type: application/json" \
  -d '{"problemId":"<problem-id>","program":"<source>"}'
```

## 4. Poll a result

- **`GET /submissions/<submission-id>`** — requires the bearer token. You may only read your own team's submissions.
- `status` transitions: **`pending` → `running` → `done`** (or **`failed`**).
- On `done`:
  - `casesPassed` / `casesTotal` — the pass counts.
  - `output` — the runner's summary.
  - On a full pass, `score` is your program's score (see §5); until every case passes, `score`, `area2`, and `avgTicks` are all `null`.
  - If the program failed to load, `loadError` carries the load failure instead — no test case was run.

```
curl https://icfpcontest2026.com/api/v1/submissions/<submission-id> \
  -H "Authorization: Bearer <API-KEY>"
```

## 5. Scoring

A passing program's `score` is **lower-is-better**, computed from two quantities:

| Quantity | Meaning |
| --- | --- |
| `area2` | `max(width, height)`² of the program grid. |
| `avgTicks` | Average ticks across test cases; **`null` on `footprint` problems**. |

- Normal problems: `score = area2 × avgTicks`.
- `footprint` problems: `score = area2` (since `avgTicks` is `null`).

`score`, `area2`, and `avgTicks` are all `null` until every case passes.

## 6. Limits and errors

- Every error response is `{"error":{"code":"…","message":"…"}}` with a matching HTTP status.

| HTTP | `code` | Cause |
| --- | --- | --- |
| 401 | `unauthorized` | Missing or invalid API key. |
| 403 | `forbidden` | The problem is practice-only. |
| 404 | `not_found` | No such problem, or it isn't released. |
| 413 | `payload_too_large` | Programs cap at **10 MB**. |
| 429 | `too_many_requests` | **5** of your submissions may be waiting to run at once — wait for one to finish. |