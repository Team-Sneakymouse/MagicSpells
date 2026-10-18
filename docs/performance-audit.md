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

`PerformanceRecorder` handles aggregate-first collection on the capture thread. Every
admitted scope updates independent global, fixed-operation and loaded-spell aggregates.
The operation/spell/detail map is optional attribution: saturating it never discards
aggregate calls, time or counters. Spell aggregate keys are admitted from the loaded
spell-name snapshot; unknown names share one explicit unattributed bucket.

`PerformanceReport` owns the JSON shape and derives detail capacity from the documented
SneakyDiagnostics protocol-1 value allowance. It reserves aggregate/metadata capacity
before detail, prioritizes spell self-time ranking on export, and preserves the combined
cost of spell rows whose names cannot fit. No provider report should fail simply because
it recorded more keys than can be exported. Coverage is explicit for each view.

`PerformanceDiagnostics` owns registration and idle execution ownership. The optional
`SneakyDiagnosticsHook` registers `MagicSpellsProbe` after load and closes registration
before unload. SneakyDiagnostics owns capture commands, deadlines and report writing.
The instrumentation schema is `magicspells-execution-3`; there is no `/ms perf` writer.

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
the wiki. The legacy profiler stays intact.

Tests cover idle ownership, callbacks across captures, nested self time, exceptions,
thread isolation, capture replacement, detail overflow with intact aggregates, large loaded-spell sets, export limits and label truncation. The consumer classloader check saturates detail in the actual rebuilt JAR and verifies aggregate counters through the real report guard.
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

Earlier measurements predate aggregate-first recording and are not comparable overhead
claims for this implementation. Measure the current build with a representative loaded
spell set and compare recording on/off on an isolated server before deployment.

## Pending task inventory and lifecycle (execution-3)

Spell names are matched using `Locale.ROOT` case folding. The provider supplies actual
configured internal names rather than the lowercase lookup-map keys. Timing rows and
spell totals expose canonical `spellKey` alongside configured `spell` spelling.

`taskLifecycle.start` and `.end` inventory MagicSpells tasks visible through the public
Bukkit scheduler at their actual `observedAfterMillis` offsets. The largest 512 groups
are exported at each boundary, ranked by task count before truncation. Each group has
`spellKey`, readable `spell`, source `origin` (`class#method:line`, stable within a build),
`kind`, effective initial `delayTicks`, `repeatIntervalTicks`, `pendingTasks`, oldest and
youngest ages, and oldest/newest creation offsets relative to capture origin. Negative
creation offsets identify tasks registered before capture. Ages are monotonic wall time,
while delays and intervals are scheduler ticks: do not convert them using an assumed TPS.
Paper 26.2's scheduler normalizes negative delays to zero, zero intervals to one tick,
and intervals below -1 to one-shot work; original scheduling arguments remain unchanged.

`nextRunTick` is null and `nextRunAvailable` is false. The public `BukkitTask` interface
does not expose next-run state, so the provider does not estimate it. Work predating
instrumentation, direct Bukkit tasks, external-library tasks and overflowed registrations
contribute to `unknownMetadataTasks`; their owner, origin, kind, period and creation/age
are unknown. They are included in the boundary total and sync/async split without
inventing historical metadata. A missing snapshot has an explicit fixed error label.

Lifecycle `totals` and bounded `groups` distinguish successful registrations, callback
starts (`executions`), `repeatingExecutions`, `callbackCompletions`, `executionFailures`,
`oneShotCompletions`, successful wrapper `cancellationRequests`, `trackedCancellations`,
and reload/disable `cleanupTerminations`. A repeating execution never increments
registrations or one-shot completions. Repeated cancellation requests count separately;
only the first request removing tracked metadata increments tracked cancellations.
Cancellation during execution still permits the current callback to finish; it does not
count a second terminal removal. Counters admit events only until the shared deadline.
They may include completion of work already running at capture start; snapshot
`inFlightTasks` identifies that boundary condition.

These counters cover the MagicSpells sync helpers only (`coverage`); direct Bukkit
registration/cancellation is not intercepted. `allPluginLifecycleComplete` is always
false. Boundary reconciliation removes stale tracked metadata, reports removals of
unknown cause as `unobservedRemovals`, and never labels these as cancellations. Stale
metadata pruned at capture start is separately `preCaptureUnobservedRemovals`, outside
the capture's event totals. A direct task created and terminated between boundaries can
be entirely invisible. Neither public snapshot churn nor this provider is an exact
count of all plugin task lifecycles. Ignite remains a separate, overlapping view.

`inventoryComplete` concerns attribution of the observed boundary populations;
`lifecycleComplete` additionally requires no registry overflow, unknown owner events,
omitted lifecycle groups, unresolved removals, truncated labels, unfinished callbacks or
end/deadline gap. A normal tick-end stop can occur after the deadline:
`endObservedAfterDeadline` makes this mismatch explicit, and counters cannot explain
changes in that uncovered interval. Check these flags separately from timing coverage.
Declared delay/repeat settings and observed age help distinguish timer populations;
they do not prove the feature author's intended lifetime or establish a leak.

Outside capture, `TaskLifecycle` retains at most 65,536 entries (above the observed
36,266-task population): task ID, bounded labels, delay/period, monotonic creation time
and lifecycle flags. It retains no task body, Bukkit object, spell object, player data,
captured stack or capture session per task. One source frame is obtained at registration.
Idle callback tracking uses a monitor and updates lifecycle flags without reading the
capture clock or allocating counter events. Supported one-shot completion (including
exceptions) and helper cancellation remove metadata immediately. Unload closes the
provider before clearing spell state; scheduler bulk cancellation then clears the
registry on both reload and disable. External cancellation is reconciled at the next
capture boundary or unload; until then only bounded metadata remains. If the registry
fills, scheduling proceeds normally without additional lifecycle metadata. Overflow
counts are conservative lifetime counts since load/cleanup; later captures remain
marked incomplete even if the dropped tasks have disappeared.

During capture, lifecycle aggregates retain the first 512 keys and report
`omittedLifecycleGroupEvents`. Snapshot aggregation is bounded by the registry capacity
and exports the largest groups; `omittedGroupTasks` counts excluded tasks. The public
scheduler itself allocates its complete pending-task list twice per capture; the adapter
streams it without allocating another full list. Lifecycle export reserves space before
timing details under the shared 250,000-value allowance. Timing aggregates retain their
existing independent coverage and omitted-total accounting. No new Bukkit internals,
Ignite dependency, provider protocol change or optional-plugin hard dependency is added.

## Scheduler capture validation

Local regression coverage includes pre-capture tasks, nested delayed ownership,
mixed-case names, recurring callbacks across captures, successful and failed callbacks,
cancellation/repeated requests, failed scheduler operations, cleanup, deadline gaps,
missing public snapshots, unknown work, registry/group overflow and dominant-origin
ranking. The rebuilt-JAR consumer check inventories 36,000 tasks and saturates timing
detail, then checks the actual detached report through the service's value/string/depth
guard, including unregistration and metadata cleanup.

Remaining server proof: on an isolated Paper instance, exercise a delayed spell and a
repeating spell before and during a capture, cancel through the helper and directly
through Bukkit, reload and disable, and compare callback behavior with diagnostics
absent and present. Compare representative recording-on/off overhead, including busy
registration and the two public boundary snapshots. Pure JVM tests do not establish
production overhead or prove a gameplay timer's intended lifetime. Production deployment
and commands require separate authorization. After that validation, the smallest
production evidence request is one authorized 60-second plugins+scheduler capture with
execution-3 and recorded plugin builds during the pending-growth workload; inspect
largest origins, age ranges, churn and all coverage flags before changing gameplay.

Validated locally on 2026-10-18 with Java 25: MagicSpells `gradlew.bat build` (including
core tests, publications and deployable-JAR checks), then SneakyDiagnostics
`gradlew.bat build :plugin:verifyMagicSpells -PmagicSpellsJar=<rebuilt shaded jar>`.
The latter passes all three consumer/provider checks, including the execution-3 inventory.
The local uncommitted JAR retains HEAD's version `2026.10.17-98101c570935`; instrumentation
version identifies the new schema, but commit changes before publishing a distinct build.

Registry-only smoke measurement is reproducible with:

```text
java --class-path core/build/classes/java/main scripts/TaskLifecycleBenchmark.java
```

Three warmed rounds with 36,000 retained registrations measured approximately 29–31 ns
and zero allocated bytes per idle callback, 99–115 ns per capturing callback, 1.60–1.69
microseconds per registration including origin lookup, and 3.4–4.4 ms for the registry's
start snapshot. Capturing allocations varied with JVM warmup. These figures exclude
Bukkit's pending-list acquisition, gameplay, execution timing scopes and report writing;
they are a synthetic smoke result, not production overhead estimates or CI thresholds.
