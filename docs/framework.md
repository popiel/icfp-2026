## Contest structure
* The contest consists of several problem sets.
* The user will create multiple submission candidates designed to solve problem sets.
  * Each submission candidate MUST be built from a specific commit.
* For each problem set, one or more candidates will be submitted to fulfill the terms of the problem set.

## Coordination framework
* The coordination framework will handle:
  * Fetching of problem sets from the contest server.
  * Registration of submission candidates.
  * Evaluation of candidate solution programs against the problem sets.
  * Submission of the best candidate for each problem set.
* Fetching
  * Periodically (e.g. every five minutes), the coordination framework will check to see if there are more problem sets available for download.
* For each problem set, the coordination framework will:
  * Create a new subdirectory under `problems/` based on the problem id.
  * Download any problem description or input materials into the new directory.
  * Maintain a metadata file in the directory named `meta.json` that includes:
    * The best score possible (if known).
    * A sorted list of candidate scores, ordered from best to worst, with each entry containing the score, the candidate sha that achieved that score, and a timestamp of when that run happened.
    * The last submission to the contest server for the problem set, including:
      * The candidate submitted.
      * The timestamp of submission.
      * The expected score.
      * The server response.
    * Whether the problem is frozen (no more evaluations to be done).
* For each submission candidate, the coordination framework will:
  * Create a new subdirectory under `candidates/` based on the commit sha.
  * Make an image of the submission repo tree at the specified commit in the new directory.
  * Run the candidate against all the non-frozen problem sets.
  * Evaluate the result output against the problem set scoring criteria.
* Problem / candidate evaluation runs
  * Each time the coordination framework evaluates a candidate against a problem set, it will:
    * Make a new subdirectory `{candidate_directory}/run/{problem_id}/{timestamp}`
    * Invoke the candidate with that new directory as the working directory, supplying the candidate with the problem id and input data.
    * Evaluate the result output according to whatever scoring criteria are appropriate.
    * Update the problem set metadata with information about the run.
* Submissions
  * Periodically (e.g. every five minutes), the coordination framework will scan the problem set metadata for problem sets with candidate scores better than the last submitted score.
    * if no candidate has been submitted for a problem, and a candidate has successfully run for the problem, then submit the best scoring candidate.
    * if a new cadidate run is better than the last submitted candidate (and the last candidate submitted is different from the last submission), submit the improved candidate.
  * When submitting a candidate, update the metadata with the submission data.
