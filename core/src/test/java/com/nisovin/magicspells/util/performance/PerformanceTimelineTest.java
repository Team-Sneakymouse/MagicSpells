package com.nisovin.magicspells.util.performance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Per-second series and slow operations: what lets aggregate rankings be placed on the capture timeline. */
class PerformanceTimelineTest {
    private static final long MS = 1_000_000L, SECOND = 1_000_000_000L;

    @Test void seriesCountsScopesInTheSecondTheyEndWithAdditiveSelfAndOutermostTime() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 100);
        recorder.start(0, 3, Set.of("craft"));
        clock.set(SECOND - 10 * MS);                      // a task starting in second 0 ...
        try (var task = recorder.enter("task", "craft", "delayed")) {
            clock.addAndGet(5 * MS);
            try (var event = recorder.enter("event", null, "Listener.on")) { clock.addAndGet(20 * MS); }
        }                                                  // ... ends in second 1
        var report = recorder.stop("completed");
        var series = report.series();
        assertArrayEquals(new long[]{0, 25 * MS, 0, 0}, series.rootNanos(), "outermost scope, inclusive, by end; last slot: late");
        assertArrayEquals(new long[]{0, 1, 0, 0}, series.rootCalls());
        assertArrayEquals(new long[]{0, 5 * MS, 0, 0}, series.operationSelfNanos().get("task"));
        assertArrayEquals(new long[]{0, 20 * MS, 0, 0}, series.operationSelfNanos().get("event"));
        assertFalse(series.operationCalls().containsKey("passive"), "operations with no calls are left out");
    }

    @Test void slowOperationsKeepTheLongestWithOffsetsTickDepthAndEvictionCount() {
        var clock = new AtomicLong(SECOND);
        var tick = new AtomicInteger(500);
        var recorder = new PerformanceRecorder(clock::get, 100, tick::get);
        recorder.start(SECOND, 60, Set.of("craft"));
        try (var fast = recorder.enter("event", "craft", "fast")) { clock.addAndGet(PerformanceRecorder.SLOW_NANOS - 1); }
        clock.set(SECOND + 2 * SECOND);
        try (var outer = recorder.enter("task", "craft", "delayed")) {
            clock.addAndGet(MS);
            tick.set(501);
            try (var inner = recorder.enter("subspell", null, "inner")) { clock.addAndGet(4 * MS); }
            clock.addAndGet(MS);
        }
        for (int i = 0; i < PerformanceRecorder.MAX_SLOW; i++) {
            try (var filler = recorder.enter("event", "craft", "filler")) { clock.addAndGet(PerformanceRecorder.SLOW_NANOS); }
        }
        var report = recorder.stop("completed");
        assertEquals(2 + PerformanceRecorder.MAX_SLOW, report.slowObserved());
        assertEquals(PerformanceRecorder.MAX_SLOW, report.slowOperations().size(), "capped; the shortest are evicted");
        var outer = report.slowOperations().stream().filter(op -> op.detail().equals("delayed")).findFirst().orElseThrow();
        assertEquals(2 * SECOND, outer.startOffsetNanos());
        assertEquals(6 * MS, outer.nanos());
        assertEquals(2 * MS, outer.selfNanos());
        assertEquals(0, outer.depth());
        assertEquals(501, outer.tick());
        var inner = report.slowOperations().stream().filter(op -> op.detail().equals("inner")).findFirst().orElseThrow();
        assertEquals(1, inner.depth());
        assertEquals("craft", inner.spell(), "nested scopes inherit their owner");
    }

    /** A one-second capture with a scope from 900 to 1200 ms: it ended in second 1, not in window 0. */
    @Test void scopesEndingAfterTheDeadlineAreKeptApartNotFoldedIntoTheLastWindow() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 100);
        recorder.start(0, 1, Set.of("craft"));
        clock.set(900 * MS);
        var late = recorder.enter("task", "craft", "delayed");
        clock.set(1_200 * MS);
        late.close();
        var report = recorder.stop("completed");
        var series = report.series();
        assertEquals(0, series.rootNanos()[0], "nothing ended inside the one regular window");
        assertEquals(300 * MS, series.rootNanos()[series.windows()]);
        assertEquals(1_200 * MS, series.lateLastEndNanos());
        var encoded = (Map<?, ?>) PerformanceReport.encode(report, "id", 0, 0).measurements().get("series");
        assertEquals(List.of(0L), encoded.get("rootNanos"));
        var lateTotals = (Map<?, ?>) encoded.get("late");
        assertEquals(1_000L, lateTotals.get("fromMillis"));
        assertEquals(1_200L, lateTotals.get("lastEndOffsetMillis"));
        assertEquals(300 * MS, lateTotals.get("rootNanos"));
        assertEquals(300 * MS, ((Map<?, ?>) ((Map<?, ?>) lateTotals.get("operations")).get("task")).get("selfNanos"));

        recorder.start(0, 1, Set.of());                       // nothing late: no late totals at all
        clock.set(100 * MS);
        try (var quick = recorder.enter("event", null, "quick")) { clock.set(200 * MS); }
        var onTime = (Map<?, ?>) PerformanceReport.encode(recorder.stop("completed"), "id", 0, 0).measurements().get("series");
        assertFalse(onTime.containsKey("late"));
    }

    @Test void unfinishedAndLateScopesAreFlaggedNotDropped() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 100);
        recorder.start(0, 1, Set.of());
        var late = recorder.enter("task", null, "late");
        clock.set(SECOND + 300 * MS);
        late.close();
        var open = recorder.enter("task", null, "too-late");      // after the deadline: not recorded at all
        assertNotNull(open);
        var stuck = recorder.enter("passive", null, "stuck");
        var report = recorder.stop("completed");
        var lateOp = report.slowOperations().getFirst();
        assertTrue(lateOp.endedAfterDeadline());
        assertFalse(lateOp.unfinishedAtStop());
        assertEquals(1, report.slowOperations().size());
        stuck.close();
        open.close();
    }

    @Test void runningScopeAtStopIsReportedUnfinished() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 100);
        recorder.start(0, 60, Set.of());
        var stuck = recorder.enter("task", null, "stuck");
        clock.set(50 * MS);
        var report = recorder.stop("manual");
        assertEquals(1, report.unfinishedScopesAtStop());
        assertTrue(report.slowOperations().getFirst().unfinishedAtStop());
        stuck.close();
    }

    @Test void encodedReportReservesSeriesAndSlowOperationsWithinTheProviderBudget() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, Integer.MAX_VALUE, () -> 7);
        int seconds = 1800;
        recorder.start(0, seconds, Set.of("fire"));
        for (int i = 0; i < 300_000; i++) {
            clock.set((long) (i % seconds) * SECOND);
            try (var scope = recorder.enter(i % 2 == 0 ? "spell" : "event", "fire", "detail-" + i)) { clock.addAndGet(2 * MS); }
        }
        clock.set(seconds * SECOND);                       // stopped at the deadline: every window is exported
        var report = recorder.stop("completed");
        var encoded = PerformanceReport.encode(report, "id", 0, 0);
        assertTrue(PerformanceReport.valueCount(encoded.measurements()) <= PerformanceReport.MAX_VALUES);
        assertStringsFit(encoded.measurements());              // the consumer rejects strings over 1024 characters
        var series = (Map<?, ?>) encoded.measurements().get("series");
        assertEquals(seconds, ((List<?>) series.get("rootNanos")).size());
        var slow = (Map<?, ?>) encoded.measurements().get("slowOperations");
        assertEquals(300_000L, slow.get("observed"));
        assertEquals(false, slow.get("complete"));
        assertEquals(7L, ((Map<?, ?>) ((List<?>) slow.get("records")).getFirst()).get("tick"));
        assertEquals(true, encoded.measurements().get("totalsComplete"));
        assertTrue(PerformanceReport.detailCapacity(1, seconds) < PerformanceReport.detailCapacity(1, 60));
        assertEquals("observed", ((Map<?, ?>) encoded.measurements().get("counterCoverage")).get("INVENTORY_SLOTS"));
        assertTrue(((String) ((Map<?, ?>) encoded.measurements().get("counterCoverage")).get("MATCH_CALLS")).startsWith("unobserved"));
    }

    private static void assertStringsFit(Object value) {
        if (value instanceof String text) assertTrue(text.length() <= 1024, () -> "string of " + text.length() + " characters");
        else if (value instanceof Map<?, ?> map) map.forEach((key, child) -> { assertStringsFit(key); assertStringsFit(child); });
        else if (value instanceof List<?> list) list.forEach(PerformanceTimelineTest::assertStringsFit);
    }
}
