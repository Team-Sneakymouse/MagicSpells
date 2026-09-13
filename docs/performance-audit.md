# Performance audit integration

## Feature-author contract

An ordinary spell needs no audit imports, annotations, counters, or method wrappers.
Implement the normal spell methods. Shared cast and subspell dispatch measures them.
Use the spell's existing `registerEvents`, `scheduleDelayedTask`, and
`scheduleRepeatingTask` helpers to supply ownership automatically, even outside captures.

For a helper object that knows its spell, use an owner-aware overload:

```java
MagicSpells.scheduleRepeatingTask(passiveSpell, this, interval, interval);
MagicSpells.registerEvents(spell, listener, priority);
```

Existing ownerless static overloads remain compatible. They capture the current
execution owner when available. Initialization, casting, passive activation, events,
and scheduled callbacks establish that context. Unowned manager tasks stay unowned.
Do not guess ownership by inspecting callback fields.

Code bypassing these helpers still works but may not get attribution. Do not change
task cancellation, event priority, or spell inheritance just for auditing.

## Module design

`PerformanceExecution` owns thread-local spell context and operation labels. Its
interface accepts names, not Bukkit objects. Task wrappers capture names when
registered, independent of recording state. Each callback enters a fresh scope when
invoked; waiting time is excluded. Lexical frames are reused by nesting depth and
clear ownership/recording references on exit. Never retain them outside their lexical scope.

`PerformanceRecorder` handles bounded aggregation on the capture thread.
`PerformanceDiagnostics` handles commands, metadata, timeout, and export.
`PerformanceExports` retains queued writes and drains them exactly once on unload.
Write failures are logged.

The capture initiator receives completion or failure messages. Export workers queue
notifications for delivery on the server thread. The lifecycle task stays alive until
exports and notifications finish; unload flushes both without requiring another tick.
Each export captures its own recipient, so a later capture cannot redirect its message.

Shared seams are normal cast preparation and execution, four subspell execution
methods, passive activation, listener dispatch, scheduling helpers, and shared
reagent/inventory operations. `ItemTagSpell` has no audit code; its event handler is
measured automatically. The ticker only supplies its owner to the scheduling helper.
The old measured-method twins and investigation-specific counters were removed.

## Limits and verification

This is shared execution attribution, not universal method interception. Direct
targeted administrative casts, direct Bukkit tasks/listeners, external libraries,
and direct calls between implementations can bypass it. Shared managers may have
no single spell owner. Async execution is excluded. These limits are documented in
the wiki. The legacy profiler stays intact; consolidation is a separate compatibility decision.

Tests cover idle ownership, callbacks across captures, nested self time, exceptions,
thread isolation, capture replacement, key overflow, and pending/running/failed exports.
They are not an end-to-end Paper test. Before production deployment, verify cast
results, event priority/cancellation, task cancellation, timeout and reload on a test
server, then compare recording on/off under representative player activity.

## Synthetic overhead check

After `:core:classes`, run with Java 25 from the plugin repository:

```text
java --class-path core/build/classes/java/main scripts/PerformanceAuditBenchmark.java
```

Each operation uses a task, passive and inventory scope, 36 matching counters, and
an arithmetic payload. Registration is outside the timed loop. The last three warmed
rounds and thread allocations are printed. This is a smoke benchmark, not JMH or
Minecraft gameplay; timings must not become CI thresholds.

One local Java 25 run measured baseline 5.6–5.8 ns, idle 42.2–50.1 ns, and recording
250.2–258.7 ns per operation. Idle allocated 0 bytes after warmup; recording allocated
240 bytes per operation. Production overhead and GC effects still need measurement.
