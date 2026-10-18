# Variable persistence contract and validation

## Scope and ownership

`VariableManager` is constructed and used on the server thread. Its managed mutation, save and load entry points reject a different thread. Existing public variable objects/maps remain live owner-thread interfaces, not async-safe collections. External integrations must not mutate them from async tasks or bypass dirty tracking.

Call-site audit: routine saves originate in `loadVariables`; explicit saves are public manager methods; `VariableListener` loads on join and saves dirty players on quit; `MagicSpells.unload` handles reload/disable. Spell, variable-mod and prompt paths mutate manager state. `Util.getUniqueId(String)` checks its cache / an online player only; no offline identity or storage lookup is added. Unknown identities remain dirty, with final-flush failure surfaced rather than silently skipped.

## Routine path

Every 1,200 ticks, capture a bounded cycle of dirty scope identifiers. A one-tick driver polls completion without waiting and visits at most 256 live variable/map entries per active capture step. Character persistence views avoid copying entire histories before filtering a player. Captures seal private builder storage by ownership transfer, avoiding an O(n) final map copy. String escaping, sorting, player-file reads/merging, UTF-8 encoding, replacement and fsync happen on the writer.

At most **one scope capture OR one outstanding scope write** exists. A scope is global, all permanent location files, or one player file. The bound is in work items, not bytes: one snapshot may be as large as that scope. The dirty identifier list is proportional to existing dirty players, not the number of repeated triggers. Individual map operations are amortized, not hard real-time deadlines. Snapshot total and maximum slice timings are available at debug level 2.

Structural changes to a traversed live map invalidate its iterator; the incomplete capture is discarded without acknowledging dirtiness and retried in a later cycle. Continuous structural churn can defer routine persistence until a stable capture or the final owner-thread flush. Non-structural changes during capture are safe because the captured generation cannot acknowledge newer mutations. Existing public APIs that bypass manager dirty tracking retain their previous caller responsibilities.

`DirtyVariableScopes` assigns a new generation on every add, even for an already dirty scope. Only successful completion acknowledges the captured generation. A failed explicit save of previously clean state marks it dirty. A failed batch of location files retains the whole location scope for retry, including files already written successfully. There are no independent writes that can finish out of order.

## File compatibility

- Global/location files replace their captured contents; defaults remain omitted from global files.
- Player saves choose an existing legacy-name file before the UUID path, read it on the writer, apply captured updates/default removals, and preserve unknown and uncaptured character keys.
- Existing load-time legacy migration remains synchronous and is serialized after outstanding writes.
- Player/global newline flattening is preserved; location serialization keeps its existing behavior.
- The existing `AtomicFiles` implementation is unchanged, including fsync, atomic-move fallback and temporary-file cleanup.
- The configured-variable load path now also loads/saves location variables, matching the no-config path. String-based location mutations now mark location state dirty, matching numeric mutations/resets.

## Explicit save, player lifecycle, reload and disable

Explicit saves cancel unfinished routine capture, wait for any outstanding write, capture current state and wait for its write. Failures remain dirty and are logged without payloads, paths or player identities. Public save methods retain their void/logging contract; return means the attempt completed, not that a storage failure was suppressed into success.

Player loads wait for pending writes and first persist any dirty in-memory state for that player. They refuse to load stale disk contents over unsaved state after a failed write. This also orders rapid quit/rejoin against routine writes.

`MagicSpells.unload` runs `flushForUnload` before destructive teardown. It waits for the outstanding write and saves all remaining dirty generations. Failure (including unresolved identities) throws before teardown and retains the manager for a possible retry. `disable` performs another final barrier after spell teardown, cancels the driver, closes the drained executor and only then clears variables. A failed late flush also surfaces rather than clearing data. No old writer can modify files after a successful unload. A blocked filesystem can block a lifecycle barrier indefinitely; no timeout or cancellation silently weakens durability. Forced process exit and persistently failing storage remain outside any durability guarantee.

## Local verification

Run from the workspace root:

```powershell
./gradlew.bat :core:test
./gradlew.bat build
```

Tests at the approved persistence seams:

- `OrderedVariableWriterTest`: latch-controlled owner responsiveness with blocked storage; 1,000 overlapping submissions rejected without queue growth; mutation during a write; exact-generation acknowledgement; real atomic replacement failure preserving the previous file, cleanup and retry; blocking final flush; drained writer shutdown/replacement; off-owner rejection; failed explicit clean save becoming dirty.
- `DirtyVariableScopesTest`: mutation during capture cannot be cleared by acknowledgement of an earlier generation.
- `VariableSnapshotCaptureTest`: actual numeric/string global and character values, default removal and preservation of unknown/unloaded player data, location formats and reconstruction, 600 location entries requiring multiple capture steps, detached mutable regions, and incomplete/sealed capture guards.
- `VariableFileSnapshotTest`: legacy path precedence with both files present, newline/equals compatibility, ownership-transferred builders, immutable input capture, and unreadable merge-source protection.
- Existing `AtomicFilesTest` verifies the actual replacement/fallback/cleanup seams. The full test suite also runs existing diagnostics/provider aggregation tests.

Verified locally: `:core:test` passed **158 tests**, with zero failures/errors; the full `build` passed, including publication/JAR checks. Existing compiler/Javadoc/Gradle deprecation warnings remain. `git diff --check` passed in both repositories. Wiki inventories were not regenerated because no spell/effect/modifier inventory or configuration key changed.

These tests need no running Bukkit server. They do not prove join/quit/reload event wiring or performance at production scale. No production commands, deployment, storage or configuration changes are part of this task.

## Remaining live validation

On an authorized local/dev Paper server, exercise routine save, mutation while a write is delayed, quit/rejoin, reload and graceful stop/restart for player/global/location values. Check legacy and UUID files and unsaved-state error reporting. A storage-delay test must control the write boundary, not merely compare fragile elapsed-time thresholds.

For production comparison, capture at least one complete **1,200-tick save cycle** under comparable players/activity/storage conditions. A 60-second wall-clock profile can miss the save at low TPS. Enable the existing debug-level-2 seam if needed to correlate capture slices, writer duration/failures and dirty counts with tick timestamps; do not log identities or values. Compare save-related main-thread stacks and worst tick, not only mean TPS. The expected result is absence of save-path read/merge/encoding/fsync on routine main-thread stacks, not a claim that this alone resolves sustained low TPS.
