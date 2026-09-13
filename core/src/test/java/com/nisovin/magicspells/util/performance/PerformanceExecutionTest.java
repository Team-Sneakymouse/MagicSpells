package com.nisovin.magicspells.util.performance;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.nisovin.magicspells.util.performance.PerformanceExecution.Operation.*;

class PerformanceExecutionTest {
    private final AtomicLong clock = new AtomicLong();
    private final PerformanceRecorder recorder = new PerformanceRecorder(clock::get, 100);
    private final PerformanceExecution execution = new PerformanceExecution(recorder);

    @Test void taskRegisteredBeforeCaptureRetainsOwnerAndExcludesWaiting() {
        Runnable task;
        try (var ignored = execution.enter(INITIALIZE, "projectile", "")) {
            task = execution.task(null, "interval:1", () -> clock.addAndGet(10));
        }
        assertEquals(PerformanceExecution.UNATTRIBUTED, execution.owner());
        clock.set(1_000_000);
        recorder.start(60);
        clock.addAndGet(1000);
        task.run();
        var row = recorder.stop("manual").rows().getFirst();
        assertEquals("projectile", row.key().spell());
        assertEquals("task", row.key().operation());
        assertEquals(10, row.totalNanos());
        assertEquals(PerformanceExecution.UNATTRIBUTED, execution.owner());
    }

    @Test void recurringTaskParticipatesInLaterCapturesWithoutRetainingSessions() {
        Runnable task = execution.task("loop", "interval:2", () -> clock.addAndGet(7));
        task.run();
        for (int capture = 0; capture < 2; capture++) {
            recorder.start(60);
            task.run();
            task.run();
            var row = recorder.stop("manual").rows().getFirst();
            assertEquals(2, row.calls());
            assertEquals(14, row.totalNanos());
        }
    }

    @Test void nestedDispatchAndSharedWorkRestoreParentOwnership() {
        recorder.start(60);
        try (var parent = execution.enter(SPELL, "parent", "")) {
            clock.set(10);
            try (var child = execution.enter(SUBSPELL, "child", "")) {
                try (var inventory = execution.enter(INVENTORY_SCAN, null, "")) {
                    execution.matchCall();
                    clock.set(30);
                }
            }
            assertEquals("parent", execution.owner());
            clock.set(50);
        }
        var report = recorder.stop("manual");
        assertEquals(50, report.rows().stream().mapToLong(PerformanceRecorder.Row::selfNanos).sum());
        var scan = report.rows().stream().filter(r -> r.key().operation().equals("inventory_scan")).findFirst().orElseThrow();
        assertEquals("child", scan.key().spell());
        assertEquals(1L, scan.counters().get(PerformanceRecorder.Counter.MATCH_CALLS));
    }

    @Test void callbackExceptionIsUnchangedAndContextIsRestored() {
        var failure = new IllegalArgumentException("gameplay failure");
        Runnable task = execution.task("child", "delayed", () -> { throw failure; });
        try (var parent = execution.enter(EVENT, "parent", "listener")) {
            assertSame(failure, assertThrows(IllegalArgumentException.class, task::run));
            assertEquals("parent", execution.owner());
        }
        assertEquals(PerformanceExecution.UNATTRIBUTED, execution.owner());
    }

    @Test void unownedTaskDoesNotBorrowItsInvokersOwner() {
        Runnable task = execution.task(null, "delayed", () -> assertEquals(
                PerformanceExecution.UNATTRIBUTED, execution.owner()));
        try (var ignored = execution.enter(SPELL, "unrelated", "")) { task.run(); }
    }

    @Test void newTaskInCallbackInheritsRegisteredOwner() {
        AtomicReference<Runnable> next = new AtomicReference<>();
        execution.task("loop", "delayed", () -> next.set(execution.task(null, "delayed",
                () -> assertEquals("loop", execution.owner())))).run();
        next.get().run();
    }

    @Test void ownershipIsThreadLocalAndAsyncTimingIsExcluded() throws Exception {
        AtomicReference<String> otherOwner = new AtomicReference<>();
        recorder.start(60);
        try (var ignored = execution.enter(SPELL, "main", "")) {
            Thread worker = new Thread(() -> {
                try (var async = execution.enter(EVENT, "async", "")) {
                    otherOwner.set(execution.owner());
                    execution.matchCall();
                }
            });
            worker.start();
            worker.join();
            assertEquals("main", execution.owner());
        }
        assertEquals("async", otherOwner.get());
        var report = recorder.stop("manual");
        assertEquals(1, report.rows().size());
        assertEquals(0, report.unattributedMatchCalls());
    }

    @Test void idleFramesAreReusedWithoutReadingClock() {
        var recorder = new PerformanceRecorder(() -> { throw new AssertionError("idle clock read"); }, 1);
        var execution = new PerformanceExecution(recorder);
        var first = execution.enter(SPELL, "first", "");
        first.close();
        try (var second = execution.enter(SPELL, "second", "")) {
            assertSame(first, second);
            assertEquals("second", execution.owner());
        }
        assertEquals(PerformanceExecution.UNATTRIBUTED, execution.owner());
    }

    @Test void stoppingInsideCallbackDoesNotCorruptFollowingCapture() {
        recorder.start(60);
        try (var parent = execution.enter(SPELL, "old", "")) {
            recorder.stop("reload");
            recorder.start(60);
            try (var child = execution.enter(SPELL, "new", "")) { clock.addAndGet(5); }
        }
        var rows = recorder.stop("manual").rows();
        assertEquals(1, rows.size());
        assertEquals("new", rows.getFirst().key().spell());
        assertEquals(5, rows.getFirst().totalNanos());
    }
}
