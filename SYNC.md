# Upstream Synchronization

genhttp-kt follows [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) and
[Glyph11](https://github.com/dotnet-web-stack/Glyph11). Changes upstream are ported by an agent (or a human) following
this procedure. The rules of [PORTING.md](PORTING.md) apply to every synced change.

## State

[`upstream.properties`](upstream.properties) records the last upstream commits that are fully reflected in this
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
   1. Inspect the diff (`git show <sha>`) and map every changed file to its counterpart (see the layout table in
      PORTING.md). Changes to Kestrel, ioxide, the ASP.NET Core adapter, `.csproj`/NuGet plumbing and other items
      listed as deviations are not ported, but their *behavioral* intent is checked (e.g. a bug fix in shared engine
      code also applies to the Kotlin engine).
   2. Port production code and tests together. Ported tests must mirror the upstream tests 1:1.
   3. Run `./gradlew check` (includes test parity, API dumps and coverage verification).
   4. Commit with a message that references the upstream commit(s) via trailers:
      ```
      Port: <upstream subject>

      Upstream-Commit: Kaliumhexacyanoferrat/GenHTTP@<sha>
      ```
      Use one `Upstream-Commit:` trailer per ported commit, `dotnet-web-stack/Glyph11@<sha>` for Glyph11.
4. Update `upstream.properties` (`genhttp.commit`, `glyph11.commit`) in the last commit of the sync.
5. If `<Version>` in GenHTTP's `Directory.Build.props` changed, set `genhttp.version` to the new value and reset
   `port.revision=0`. Otherwise, if the sync is released, increment `port.revision`.
6. Update the deviation log (docs/DEVIATIONS.md) if a new deviation was introduced.
7. Release by running the *Release* workflow (it tags `v<version>` and publishes to Maven Central).

## Rules for sync agents

- Never port a change partially without saying so: if something cannot be ported, document it in the commit message
  and in PORTING.md.
- Never weaken or delete tests to make a sync pass. If upstream changed a test, change the Kotlin test the same way.
- Keep the public API identical to upstream in name, parameters, defaults and nullability (after the PORTING.md
  translation). Run `tools/api-parity` after API changes.
- Do not refactor unrelated code during a sync; keep diffs reviewable and traceable to upstream commits.
- Dependency updates of the port are separate commits without `Upstream-Commit` trailers.
