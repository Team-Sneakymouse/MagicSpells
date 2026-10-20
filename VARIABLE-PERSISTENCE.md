# Variable persistence contract and validation

## Ownership and module interface

`VariableManager` and live variable objects belong to the server thread. `VariablePersistence` owns dirty generations, save-cycle selection, capture progress, single-flight writer completion, diagnostics and lifecycle barriers. The manager supplies captures and connects the one-tick driver; the writer only executes detached writes and reports completion. It does not own mutation bookkeeping.

Scopes are typed identities (`global`, all permanent locations, or one player). Named capture factories exclude invalid string/null combinations. Every `markDirty` advances a generation, even when already dirty. Only successful completion of that exact generation clears it. A failed explicit save of clean state becomes dirty; unresolved identities remain dirty and prevent a successful final flush.

Integrations must mutate on the owner thread through the manager, or call `VariableManager.markDirty(variable, playerName)` after direct mutation. `getDirtyPlayerVariables()` returns a read-only snapshot, not an editable persistence data structure. Existing variable objects/maps are not async-safe.

## Routine work and progress

Every 1,200 ticks, dirty scope identifiers enter a coalescing ordered set. Quit requests can expedite a dirty player without waiting or creating snapshots. The routine driver keeps at most **one active capture OR one outstanding write**; completed work occupies the writer slot until collected on the owner thread. Repeated triggers cannot accumulate snapshots. The identifier backlog is bounded by dirty/requested scopes, not trigger count; the snapshot bound is in work items, not bytes.

Capture visits at most 256 variable/map entries per step. Private builder storage transfers ownership without a final O(n) map copy. Player-file reads/merging, string escaping, sorting, encoding, atomic replacement and fsync happen on the worker. Scope identifier selection and individual strings/map operations are not hard real-time guarantees.

Unrelated loads/saves do not discard an active capture. If a structural change invalidates a remaining live iterator (e.g. region definitions), its dirty scope is promptly requeued at the tail rather than waiting for the next minute. Other scopes can progress first. Continuous changes to those structures can still delay their capture; final flush captures synchronously.

Character values retain hash-based gameplay lookups. An ordered per-player key index is maintained only when keys are inserted/removed. Persistence seeks through that player's history; it neither scans all other players nor holds a fail-fast character-map iterator across ticks. This adds index memory proportional to character entries and logarithmic index work on structural changes, not on ordinary value updates. Insertions behind an active cursor and other mutations remain pending through generation checks; an incremental snapshot is not a transactionally consistent view across all values.

## Player lifecycle and explicit saves

- **Quit:** request saving the dirty player; no global wait. Data stays in memory and retries remain dirty. Disconnect is no longer a synchronous durability barrier; a crash before completion may lose those recent changes, as with other pending routine saves.
- **Join/load:** wait only for an outstanding write to the same scope before reading its file. Dirty memory is authoritative: do not overwrite it with disk state, or force a save merely to read it straight back. This also handles quit/rejoin before the queued save runs. A same-player in-flight write may still block join; unrelated global/location/player writes do not. UUID aliases (renamed/case-changed players) are treated as the same file: outstanding or dirty old-name state is persisted before reading it under the new name.
- **Explicit public saves:** retain the blocking attempt-completed contract. The single writer must drain before an explicit write; errors are logged and retained as dirty state. An unrelated unfinished capture is retained (so an explicit blocking save can temporarily hold that capture plus its own snapshot).
- **Reload/disable:** `flushForUnload` runs before destructive teardown. Drain the outstanding write and save all remaining dirty scopes. Failure throws without discarding dirty state. `disable` repeats the barrier after spell teardown, closes the drained writer, then clears memory. No successfully closed old writer can overwrite a replacement manager's files.

There is no storage timeout. A hung same-scope join or deliberate explicit/final barrier can wait indefinitely. Persistent storage failure or forced process termination cannot guarantee durability. Ordinary join-time file reads and legacy migration remain synchronous; this work does not claim to eliminate all server-thread I/O.

## Compatibility and diagnostics

Existing player/global/location paths, defaults, legacy-file precedence and player partial-file merge behavior are retained. Unknown/unloaded character keys survive player patches. `AtomicFiles` is unchanged, including synchronization, replacement fallback and temporary-file cleanup.

Player/global newline flattening deliberately matches the pre-async implementation: the historic regex replacement turns a newline into literal `n`, **not** backslash+n. Existing literal backslash+n text is left alone. Correcting this lossy historical format requires a separate compatibility decision. Location formatting is unchanged.

Debug level 2 reports total/max capture-step duration, write duration, dirty scope count, in-flight work and failure count. Write errors expose only the exception class, not variable values, paths or player identities.

## Local validation

Run from `MagicSpells/`:

```powershell
./gradlew.bat :core:test
./gradlew.bat build
```

`VariablePersistenceTest` drives the production coordinator with real captures/atomic writes and a controllable executor. It covers joins between every capture slice, unrelated joins with storage deliberately delayed, coalesced quit requests, dirty-memory rejoin, mutation during a write, prompt/fair invalidation retry, periodic trigger backpressure, failed writes/retry, explicit clean-state failure, unresolved identity and final flush/replacement, including same-UUID name changes. Assertions inspect queued work and resulting files, not performance timing thresholds.

Writer tests retain latch-controlled nonblocking submission, deliberate flush waiting, owner-thread rejection and shutdown ordering. Capture/file tests cover formats, legacy/partial files, historical newline encoding, immutable ownership transfer, and player-scoped capture finishing in one slice despite 10,000 unrelated histories. Character key-index tests cover updates, removal/reinsertion, cursor ordering and detached copies. Existing `AtomicFilesTest` exercises replacement/fallback/cleanup failures.

Validated locally: `:core:test` and the full `build` pass; **181 tests**, zero failures/errors. Publication and deployable-JAR checks pass. Existing compiler/Javadoc, Shadow metadata and Gradle deprecation messages remain (including the tolerated empty factions Javadoc task). `git diff --check` passes in both repositories. No inventory regeneration is needed: configuration keys and spell/effect/modifier inventories are unchanged.

These checks do not prove Paper join/quit/reload event wiring or production-scale performance. No deployment, production commands, storage or configuration changes are included.

## Remaining live validation

On an authorized dev Paper server, exercise routine saves, delayed storage, quit/rejoin, reload and graceful stop/restart for player/global/location values, including legacy files and failure reporting. Verify unrelated joins do not wait on a delayed write and dirty rejoin values remain current.

For production comparison, capture at least one complete **1,200-tick save cycle** under comparable workload. At low TPS, 60 wall-clock seconds can miss the save. Compare save-related main-thread stacks and worst tick, not only average TPS. This change targets persistence spikes; it does not claim to resolve sustained overload.
