# jdbscript Examples

Runnable, self-contained example projects showing how a consumer project uses `jdbscript` as a
normal dependency — copy any module as a starting point.

Each example is its own Maven module with its own tests. This directory is **not** part of the
`jdbscript` reactor build on purpose: it depends on `jdbscript` the way a real project would
(resolved from your local/remote Maven repository), not by reaching into the library's internal
source.

## Running the examples

```sh
# from the repo root — builds jdbscript and installs it to your local repo
mvn install -DskipTests -Dgpg.skip=true

# then, from this directory
cd examples
mvn test
```

## Modules

| # | Module | Demonstrates |
|---|--------|---------------|
| 01 | [`01-quickstart`](01-quickstart) | Schema interfaces, `resetDB`/`insertDB` to arrange DB state, then calling a real `src/main` class (the system under test) and asserting on **its** return value — the pattern most jdbscript tests actually follow. Plain JUnit 5. |
| 02 | [`02-class-scripts-and-include`](02-class-scripts-and-include) | A reusable base dataset as a class-based script, run directly and composed with test-specific rows via `db.include(...)`. |
| 03 | [`03-recordtools-defaults`](03-recordtools-defaults) | Auto-generated IDs and templated column values via `defaults(RecordTools)`, and how they interact with columns you set explicitly. |
| 04 | [`04-scripting-power`](04-scripting-power) | A script is just Java: generating 100+ rows with a for loop, and why seeding your randomness (`new Random(42)`) matters for reproducibility. |
| 05 | [`05-custom-converters`](05-custom-converters) | Teaching jdbscript a domain value type via `IJDBTypeConverter`, registered alongside the built-in converters with `.converter(...)`. |
| 06 | [`06-insert-power`](06-insert-power) | `insertDB` used to interleave arrange and act within a single test — simulating a sequence of events over time, not just one static snapshot. |

See the main [README](../README.md) for the full feature list in prose form.
