package com.nisovin.magicspells.util.performance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskLifecycleTest {
    @Test void inventoriesPreCaptureWorkAndSeparatesRepeatingExecutionsFromCreationAndCompletion() {
        var clock = new AtomicLong();
        var tasks = new TaskLifecycle(clock::get, 10, 10);
        var delayed = new AtomicReference<Runnable>();
        var repeating = new AtomicReference<Runnable>();
        tasks.schedule("Mixed-Spell", "Example#delay:12", 200, -1, () -> {}, body -> { delayed.set(body); return 1; });
        tasks.schedule("Mixed-Spell", "Example#loop:34", 0, 5, () -> {}, body -> { repeating.set(body); return 2; });
        clock.set(1_000_000_000L);
        tasks.start(clock.get(), 60, () -> List.of(new TaskLifecycle.Pending(1, true), new TaskLifecycle.Pending(2, true)));
        delayed.get().run();
        repeating.get().run();
        repeating.get().run();
        var report = tasks.stop(() -> List.of(new TaskLifecycle.Pending(2, true)));
        var counts = map(report.get("totals"));
        assertEquals(0L, counts.get("registrations"));
        assertEquals(3L, counts.get("executions"));
        assertEquals(2L, counts.get("repeatingExecutions"));
        assertEquals(1L, counts.get("oneShotCompletions"));
        assertEquals(2L, map(report.get("start")).get("pendingTasks"));
        assertEquals(1L, map(report.get("end")).get("pendingTasks"));
        var rows = (List<?>) map(report.get("start")).get("groups");
        var row = map(rows.getFirst());
        assertEquals("mixed-spell", row.get("spellKey"));
        assertEquals("Mixed-Spell", row.get("spell"));
        assertEquals(1000L, row.get("oldestAgeMillis"));
        assertNull(row.get("nextRunTick"));
    }

    @Test void cancellationAndReconciliationDoNotInventCompleteLifecycleCounts() {
        var tasks = new TaskLifecycle(() -> 0, 10, 10);
        tasks.schedule("spell", "site", 100, -1, () -> {}, body -> 1);
        tasks.schedule("spell", "site", 100, -1, () -> {}, body -> 2);
        tasks.start(0, 60, () -> List.of(new TaskLifecycle.Pending(1, true), new TaskLifecycle.Pending(2, true)));
        tasks.cancel(1, () -> {});
        tasks.cancel(1, () -> {});
        var report = tasks.stop(List::of);
        var counts = map(report.get("totals"));
        assertEquals(2L, counts.get("cancellationRequests"));
        assertEquals(1L, counts.get("trackedCancellations"));
        assertEquals(1L, counts.get("unobservedRemovals"));
        assertEquals(0, tasks.retainedTasks());
        assertEquals(false, report.get("lifecycleComplete"));
    }

    private static Map<?, ?> map(Object value) { return (Map<?, ?>) value; }

    @Test void overflowBoundsMetadataAndReportsUnknownPendingWorkWithoutLosingRegistrations() {
        var tasks = new TaskLifecycle(() -> 0, 1, 1);
        tasks.start(0, 60, List::of);
        tasks.schedule("first", "site", 100, -1, () -> {}, body -> 1);
        tasks.schedule("second", "site", 100, -1, () -> {}, body -> 2);
        assertEquals(1, tasks.retainedTasks());
        var report = tasks.stop(() -> List.of(new TaskLifecycle.Pending(1, true), new TaskLifecycle.Pending(2, true),
                new TaskLifecycle.Pending(3, false)));
        assertEquals(2L, map(report.get("totals")).get("registrations"));
        assertEquals(1L, report.get("registryOverflowRegistrations"));
        assertEquals(2L, map(report.get("end")).get("unknownMetadataTasks"));
        assertEquals(false, report.get("inventoryComplete"));
        assertEquals(false, report.get("lifecycleComplete"));
    }

    @Test void callbackFailuresAndReloadCleanupPreserveBehaviorAndReleaseMetadata() {
        var tasks = new TaskLifecycle(() -> 0, 10, 10);
        var oneShot = new AtomicReference<Runnable>();
        var repeating = new AtomicReference<Runnable>();
        var failure = new IllegalArgumentException("original");
        tasks.start(0, 60, List::of);
        tasks.schedule("owner", "site", 0, -1, () -> { throw failure; }, body -> { oneShot.set(body); return 1; });
        tasks.schedule("owner", "site", 0, 2, () -> { throw failure; }, body -> { repeating.set(body); return 2; });
        assertSame(failure, assertThrows(IllegalArgumentException.class, oneShot.get()::run));
        assertEquals(1, tasks.retainedTasks());
        assertSame(failure, assertThrows(IllegalArgumentException.class, repeating.get()::run));
        assertEquals(1, tasks.retainedTasks());
        tasks.clear();
        assertEquals(0, tasks.retainedTasks());
        var report = tasks.stop(List::of);
        assertEquals(2L, map(report.get("totals")).get("executionFailures"));
        assertEquals(1L, map(report.get("totals")).get("cleanupTerminations"));
        var rows = (List<?>) report.get("groups");
        assertEquals(2, rows.size());
        assertEquals("owner", map(rows.getFirst()).get("spellKey"));
    }

    @Test void deadlineStopsLifecycleAdmissionButStillCleansUpFinishedWork() {
        var clock = new AtomicLong();
        var tasks = new TaskLifecycle(clock::get, 10, 10);
        var body = new AtomicReference<Runnable>();
        tasks.start(0, 1, List::of);
        tasks.schedule("spell", "site", 1, -1, () -> {}, callback -> { body.set(callback); return 1; });
        clock.set(1_000_000_000L);
        body.get().run();
        clock.set(1_100_000_000L);
        var report = tasks.stop(List::of);
        assertEquals(0L, map(report.get("totals")).get("executions"));
        assertEquals(0, tasks.retainedTasks());
        assertEquals(true, report.get("endObservedAfterDeadline"));
        assertEquals(false, report.get("lifecycleComplete"));
    }

    @Test void cancellationsBeforeCaptureArePrunedWithoutBeingCountedInsideTheCapture() {
        var tasks = new TaskLifecycle(() -> 0, 10, 10);
        tasks.schedule("spell", "site", 1, -1, () -> {}, callback -> 1);
        tasks.start(0, 60, List::of);
        var report = tasks.stop(List::of);
        assertEquals(0L, map(report.get("totals")).get("unobservedRemovals"));
        assertEquals(1L, report.get("preCaptureUnobservedRemovals"));
        assertEquals(0, tasks.retainedTasks());
    }

    @Test void nestedDelayedCallbacksKeepTheirSpellOwnershipAcrossCaptures() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 10);
        var execution = new PerformanceExecution(recorder);
        var tasks = new TaskLifecycle(clock::get, 10, 10);
        var scheduled = new java.util.HashMap<Integer, Runnable>();
        try (var parent = execution.enter(PerformanceExecution.Operation.SPELL, "parent", "cast")) {
            try (var child = execution.enter(PerformanceExecution.Operation.SUBSPELL, "Mixed-Child", "cast")) {
                tasks.schedule(execution.owner(), "child-site", 5, -1, execution.task(null, "delay", () -> {
                    tasks.schedule(execution.owner(), "nested-site", 10, -1,
                            execution.task(null, "delay", () -> assertEquals("Mixed-Child", execution.owner())),
                            body -> { scheduled.put(2, body); return 2; });
                }), body -> { scheduled.put(1, body); return 1; });
            }
        }
        tasks.start(0, 60, () -> List.of(new TaskLifecycle.Pending(1, true)));
        scheduled.remove(1).run();
        var first = tasks.stop(() -> List.of(new TaskLifecycle.Pending(2, true)));
        assertEquals("mixed-child", map(((List<?>) map(first.get("end")).get("groups")).getFirst()).get("spellKey"));
        tasks.start(0, 60, () -> List.of(new TaskLifecycle.Pending(2, true)));
        scheduled.remove(2).run();
        var second = tasks.stop(List::of);
        assertEquals(1L, map(second.get("totals")).get("oneShotCompletions"));
        assertEquals(0L, map(second.get("totals")).get("registrations"));
        assertEquals(0, tasks.retainedTasks());
    }

    @Test void rejectedSchedulingAndCancellationPreserveExceptionsAndDoNotInventTerminations() {
        var tasks = new TaskLifecycle(() -> 0, 10, 10);
        var failure = new IllegalStateException("scheduler failure");
        tasks.start(0, 60, List::of);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tasks.schedule("spell", "site", 1, -1,
                () -> {}, body -> { throw failure; })));
        assertEquals(0, tasks.retainedTasks());
        assertEquals(123, tasks.schedule("spell", "site", 1, -1, () -> {}, body -> 123));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tasks.cancel(123, () -> { throw failure; })));
        assertEquals(1, tasks.retainedTasks());
        var report = tasks.stop(() -> List.of(new TaskLifecycle.Pending(123, true)));
        assertEquals(1L, map(report.get("totals")).get("registrations"));
        assertEquals(0L, map(report.get("totals")).get("trackedCancellations"));
    }

    @Test void inventoryKeepsALateDominantOriginWhenTheExportOverflows() {
        var tasks = new TaskLifecycle(() -> 0, 20, 1);
        var pending = new java.util.ArrayList<TaskLifecycle.Pending>();
        for (int i = 0; i < 5; i++) {
            int id = i;
            tasks.schedule("spell", i < 2 ? "early" : "dominant", -10, 0, () -> {}, body -> id);
            pending.add(new TaskLifecycle.Pending(id, true));
        }
        tasks.start(0, 60, () -> pending);
        var report = tasks.stop(() -> pending);
        var end = map(report.get("end"));
        var row = map(((List<?>) end.get("groups")).getFirst());
        assertEquals("dominant", row.get("origin"));
        assertEquals(3L, row.get("pendingTasks"));
        assertEquals(0L, row.get("delayTicks"));
        assertEquals(1L, row.get("repeatIntervalTicks"));
        assertEquals(2L, end.get("omittedGroupTasks"));
    }

    @Test void unavailableSnapshotsAreExplicitAndDoNotPreventLaterCaptures() {
        var tasks = new TaskLifecycle(() -> 0, 10, 10);
        tasks.start(0, 60, () -> { throw new IllegalStateException("private server data"); });
        var report = tasks.stop(List::of);
        assertEquals(false, report.get("inventoryComplete"));
        assertEquals("public_scheduler_snapshot_failed", map(report.get("start")).get("error"));
        assertFalse(report.toString().contains("private server data"));
        tasks.start(0, 60, List::of);
        assertEquals(true, tasks.stop(List::of).get("inventoryComplete"));
    }

    @Test void cancellationInsideACallbackCountsOneTerminationAndStillCountsCallbackCompletion() {
        var tasks = new TaskLifecycle(() -> 0, 10, 10);
        var callback = new AtomicReference<Runnable>();
        tasks.start(0, 60, List::of);
        tasks.schedule("spell", "site", 1, -1, () -> tasks.cancel(1, () -> {}), body -> { callback.set(body); return 1; });
        callback.get().run();
        var counts = map(tasks.stop(List::of).get("totals"));
        assertEquals(1L, counts.get("executions"));
        assertEquals(1L, counts.get("callbackCompletions"));
        assertEquals(1L, counts.get("trackedCancellations"));
        assertEquals(0L, counts.get("oneShotCompletions"));
        assertEquals(0, tasks.retainedTasks());
    }
}
