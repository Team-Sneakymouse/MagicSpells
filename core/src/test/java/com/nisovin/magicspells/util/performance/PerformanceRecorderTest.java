package com.nisovin.magicspells.util.performance;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceRecorderTest {
    @Test void droppedChildDoesNotChargeItsMatchesToParent() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 1);
        recorder.start(60);
        try (var parent = recorder.enter("spell", "parent", "")) {
            clock.set(10);
            try (var dropped = recorder.enter("spell", "child", "")) {
                recorder.matchCall();
                clock.set(30);
            }
            recorder.matchCall();
            clock.set(50);
        }
        var report = recorder.stop("manual");
        assertEquals(1, report.droppedScopes());
        assertEquals(1L, report.rows().getFirst().counters().get(PerformanceRecorder.Counter.MATCH_CALLS));
        assertEquals(30, report.rows().getFirst().selfNanos());
    }

    @Test void disabledDoesNotReadClock() {
        var recorder = new PerformanceRecorder(() -> { throw new AssertionError("disabled clock read"); }, 4);
        try (var scope = recorder.enter("passive", "test", "")) {
            scope.add(PerformanceRecorder.Counter.INVENTORY_SLOTS, 1);
            recorder.matchCall();
        }
        assertNull(recorder.stop("manual"));
    }

    @Test void nestedScopesKeepSelfTimeAndContext() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 10);
        recorder.start(60);
        try (var parent = recorder.enter("passive", "spell-a", "")) {
            clock.set(10);
            try (var child = recorder.enter("inventory_scan", null, "")) {
                recorder.matchCall();
                clock.set(30);
            }
            clock.set(50);
        }
        var report = recorder.stop("manual");
        var parent = report.rows().get(0);
        var child = report.rows().get(1);
        assertEquals(50, parent.totalNanos());
        assertEquals(30, parent.selfNanos());
        assertEquals("spell-a", child.key().spell());
        assertEquals(1L, child.counters().get(PerformanceRecorder.Counter.MATCH_CALLS));
        assertEquals(50, report.rows().stream().mapToLong(PerformanceRecorder.Row::selfNanos).sum());
    }

    @Test void recursiveCallsAccumulateInclusiveTimeWithoutDoublingSelfTime() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 4);
        recorder.start(60);
        try (var outer = recorder.enter("passive", "recursive", "")) {
            clock.set(10);
            try (var inner = recorder.enter("passive", "recursive", "")) {
                clock.set(30);
            }
            clock.set(50);
        }
        var row = recorder.stop("manual").rows().getFirst();
        assertEquals(2, row.calls());
        assertEquals(70, row.totalNanos());
        assertEquals(50, row.selfNanos());
        assertEquals(50, row.maxNanos());
    }

    @Test void exceptionClosesScopeAndRestoresAttribution() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 4);
        recorder.start(1);
        assertThrows(IllegalArgumentException.class, () -> {
            try (var ignored = recorder.enter("passive", "fails", "")) {
                clock.set(20);
                throw new IllegalArgumentException();
            }
        });
        recorder.matchCall();
        var report = recorder.stop("manual");
        assertEquals(1, report.unattributedMatchCalls());
        assertEquals(20, report.rows().getFirst().totalNanos());
    }

    @Test void deadlineBoundsNewScopesAndRestartDoesNotReuseOldScope() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 4);
        recorder.start(1);
        var scope = recorder.enter("passive", "old", "");
        clock.set(1_000_000_000L);
        assertTrue(recorder.isExpired());
        recorder.enter("passive", "expired", "").close();
        assertEquals(1, recorder.stop("timeout").rows().size());
        recorder.start(1);
        scope.close();
        assertTrue(recorder.stop("manual").rows().isEmpty());
    }

    @Test void boundsKeysAndRejectsOverlappingSessions() {
        var recorder = new PerformanceRecorder(() -> 0, 1);
        recorder.start(1);
        assertThrows(IllegalStateException.class, () -> recorder.start(1));
        recorder.enter("passive", "one", "").close();
        recorder.enter("passive", "two", "").close();
        var report = recorder.stop("manual");
        assertEquals(1, report.rows().size());
        assertEquals(1, report.droppedScopes());
    }

    @Test void ignoresOtherThreads() throws Exception {
        var recorder = new PerformanceRecorder(() -> 0, 4);
        recorder.start(1);
        var thread = new Thread(() -> {
            recorder.enter("passive", "async", "").close();
            recorder.matchCall();
        });
        thread.start(); thread.join();
        assertTrue(recorder.stop("manual").rows().isEmpty());
    }
}
