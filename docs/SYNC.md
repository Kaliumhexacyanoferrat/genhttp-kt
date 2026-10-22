# Upstream Synchronization

genhttp-kt follows [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) and
[Glyph11](https://github.com/dotnet-web-stack/Glyph11). Changes upstream are ported by an agent (or a human) following
this procedure. The rules of [PORTING.md](PORTING.md) apply to every synced change.

## State

[`upstream.properties`](../upstream.properties) records the last upstream commits that are fully reflected in this
repository and the version the port is based on:

```properties
genhttp.commit=<sha>      # last GenHTTP commit that has been ported
glyph11.commit=<sha>      # last Glyph11 commit that has been ported
genhttp.version=11.1.0    # <Version> from GenHTTP's Directory.Build.props at genhttp.commit
port.revision=0           # port-only release counter for this upstream version
```

## Versioning

genhttp-kt versions are `<upstream version>.<port revision>`, e.g. `11.1.0.0`, `11.1.0.1`, `11.2.0.0`.

- The first three components are always the GenHTTP version the port is based on.
- The fourth component counts releases of genhttp-kt for that upstream version. It starts at `0` and is incremented
  for port-only releases (bug fixes in the port, dependency updates, port-specific features).
- When the synced upstream version changes, `port.revision` resets to `0`.
- Git tags are `v<version>` (e.g. `v11.1.0.0`). Maven and Gradle order four-component versions correctly.

## Procedure

1. Read `upstream.properties`. Fetch the upstream repositories (`../GenHTTP`, `../Glyph11` or fresh clones).
2. List the new upstream commits: `git log --reverse <genhttp.commit>..origin/main` (same for Glyph11).
3. For each upstream commit (oldest first) or a coherent group of commits:
   1. Inspect the diff (`git show <sha>`) and map every changed file to its counterpart (see the
      [layout table](PORTING.md#project-layout)). Changes to Kestrel, ioxide, the ASP.NET Core adapter, `.csproj`/NuGet
      plumbing and other items listed as deviations are not ported, but their *behavioral* intent is checked (e.g. a
      bug fix in shared engine code also applies to the Kotlin engine).
   2. Port production code and tests together. Ported tests must mirror the upstream tests 1:1. A new upstream
      module becomes a Gradle project (`module(...)` in `settings.gradle.kts`, dependency of `Packages/Full` and of
      `Testing/Acceptance`).
   3. Refresh the ABI dumps of changed projects (`./gradlew :<project>:updateKotlinAbi`) and review the diff: it must
      reflect exactly the upstream API change.
   4. Verify:
      ```sh
      ./gradlew build :koverVerify                  # compile, tests, ABI check, coverage >= 90 %
      python tools/test_parity.py                   # every upstream test has its Kotlin counterpart
      python tools/api_parity.py --verbose          # every upstream public declaration has its counterpart
      python tools/api_parity.py --signatures       # parameter counts and default values match
      ```
      Intentional differences go to `tools/test_parity_exclusions.txt` / `tools/api_parity_exclusions.txt` with a
      reason and to the deviation log.
   5. Commit with a message that references the upstream commit(s) via trailers:
      ```
      Port: <upstream subject>

      Upstream-Commit: Kaliumhexacyanoferrat/GenHTTP@<sha>
      ```
      Use one `Upstream-Commit:` trailer per ported commit, `dotnet-web-stack/Glyph11@<sha>` for Glyph11.
4. Update `upstream.properties` (`genhttp.commit`, `glyph11.commit`) in the last commit of the sync.
5. If `<Version>` in GenHTTP's `Directory.Build.props` changed, set `genhttp.version` to the new value and reset
   `port.revision=0`. Otherwise, if the sync is released, increment `port.revision`.
6. Update the [deviation log](DEVIATIONS.md) if a new deviation was introduced.
7. Release by running the *Release* workflow (it tags `v<version>` and publishes to Maven Central).

## The website

The website [genhttp-kt.genhttp.run](https://genhttp-kt.genhttp.run/) is the documentation of GenHTTP
(genhttp.org) ported to Kotlin. It is a lambda on [GenHTTP Lambda](https://genhttp.dev/) and follows two upstreams
the same way this repository follows GenHTTP: the GenHTTP website, and genhttp-kt itself. It records the commits it
reflects (`site/data/upstream.yaml` in the lambda, published as
[`upstream.json`](https://genhttp-kt.genhttp.run/upstream.json)), every page names the GenHTTP page and the modules
of genhttp-kt it documents, and `node tools/sync.mjs` in the lambda lists what is pending and the pages it affects.
Its `docs/sync.md` describes the procedure.

The *Website Watch* workflow (`.github/workflows/website.yml`) checks daily whether the published website reflects
the latest documentation of GenHTTP, the latest release of genhttp-kt on Maven Central and the changes to the public
API (`api/*.api`), modules and docs since, and keeps an issue labeled `website-sync` up to date (closing it when
nothing is pending). After a release of genhttp-kt, sync the website: its samples are compiled against the release
it describes, so the new version shows every sample an API change breaks.

## Rules for sync agents

- Never port a change partially without saying so: if something cannot be ported, document it in the commit message
  and in the [deviation log](DEVIATIONS.md).
- Never weaken or delete tests to make a sync pass. If upstream changed a test, change the Kotlin test the same way.
- Keep the public API identical to upstream in name, parameters, defaults and nullability (after the PORTING.md
  translation). Run `python tools/api_parity.py` (with and without `--signatures`) after API changes.
- Do not refactor unrelated code during a sync; keep diffs reviewable and traceable to upstream commits.
- Dependency updates of the port are separate commits without `Upstream-Commit` trailers.
- Changes to upstream benchmarks (`Benchmarks/`) and the playground are ported as well.
- When delegating to sub-agents, follow the delegation rules in [CLAUDE.md](../CLAUDE.md) and the briefs in [specs/](specs/).
