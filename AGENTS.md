## Development guidelines
* For new features:
  * Implement features incrementally.
  * ALWAYS implement tests before mainline code.
  * NEVER implement mainline code unless a test is shown to be failing.
  * ONLY implement code related to the feature and failing tests.
  * COMMIT when the feature is complete. 
* For code refactoring:
  * NEVER start a refactor with uncommitted files present.
  * NEVER start a refactor with failing tests.
  * NEVER leave tests broken after a refactor.
  * COMMIT when the refactor is complete.

## Architecture guidelines
* Avoid singletons.  Prefer instantiated classes with dependency injection for greater testability.
* Make each class have a single responsibility.  Avoid generic utility dumping grounds.
* Prefer immutable data structures.
* Prefer idempotent methods.
