package com.nisovin.magicspells.util.performance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceAggregatesTest {
    @Test void overflowPreservesNestedSelfTimeAndInnermostCountersInEveryAggregate() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 1);
        recorder.start(0, 60, Set.of("parent", "child"));
        try (var parent = recorder.enter("spell", "parent", "")) {
            clock.set(10);
            try (var child = recorder.enter("subspell", "child", "omitted")) {
                recorder.matchCall();
                child.add(PerformanceRecorder.Counter.INVENTORY_SLOTS, 4);
                clock.set(30);
            }
            recorder.matchCall();
            clock.set(50);
        }
        var report = recorder.stop("completed");
        assertEquals(1, report.droppedScopes());
        assertEquals(2, report.totals().calls());
        assertEquals(70, report.totals().totalNanos());
        assertEquals(50, report.totals().selfNanos());
        assertEquals(2L, report.totals().counters().get(PerformanceRecorder.Counter.MATCH_CALLS));
        assertEquals(4L, report.totals().counters().get(PerformanceRecorder.Counter.INVENTORY_SLOTS));
        assertEquals(30, named(report.spellTotals(), "parent").selfNanos());
        assertEquals(20, named(report.spellTotals(), "child").selfNanos());
        assertEquals(1L, named(report.spellTotals(), "child").counters().get(PerformanceRecorder.Counter.MATCH_CALLS));
        assertEquals(30, named(report.operationTotals(), "spell").selfNanos());
        assertEquals(20, named(report.operationTotals(), "subspell").selfNanos());
        assertEquals(30, report.rows().getFirst().selfNanos());
        var encoded = PerformanceReport.encode(report, "id", 0, 0);
        assertFalse(encoded.complete());
        assertEquals(true, encoded.measurements().get("totalsComplete"));
        assertEquals(true, encoded.measurements().get("spellTotalsComplete"));
        assertEquals(false, encoded.measurements().get("detailComplete"));
    }

    @Test void defaultCapacityExceeds4096AndReflectsTheConfiguredSpellUniverse() {
        var recorder = new PerformanceRecorder(() -> 0, Integer.MAX_VALUE);
        recorder.start(0, 60, Set.of("fire"));
        for (int i = 0; i < 10_000; i++) recorder.enter("spell", "fire", "detail-" + i).close();
        var report = recorder.stop("completed");
        assertEquals(10_000, report.rows().size());
        assertEquals(0, report.droppedScopes());
        assertEquals(PerformanceReport.detailCapacity(1), report.maxDetailKeys());
        assertTrue(PerformanceReport.detailCapacity(1) > PerformanceReport.detailCapacity(1000));
        assertTrue(PerformanceReport.encode(report, "id", 0, 0).complete());
    }

    @Test void aLateExpensiveSpellStillLeadsTheRankingWhenNoDetailCanBeStored() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 0);
        recorder.start(0, 60, Set.of("early", "late"));
        try (var ignored = recorder.enter("event", "early", "first")) { clock.set(1); }
        try (var ignored = recorder.enter("spell", "late", "last")) { clock.set(101); }
        var report = recorder.stop("completed");
        assertTrue(report.rows().isEmpty());
        assertEquals(2, report.droppedScopes());
        assertEquals("late", report.spellTotals().getFirst().name());
        assertEquals(100, report.spellTotals().getFirst().selfNanos());
        assertEquals(101, report.totals().selfNanos());
    }

    @Test void arbitraryUnknownOwnersAndOperationsCannotGrowAggregateMaps() {
        var recorder = new PerformanceRecorder(() -> 0, 0);
        recorder.start(0, 60, Set.of("known"));
        for (int i = 0; i < 10_000; i++) {
            try (var ignored = recorder.enter("operation-" + i, "unknown-" + i, "detail")) { recorder.matchCall(); }
        }
        var report = recorder.stop("completed");
        assertTrue(report.spellTotals().isEmpty());
        assertEquals(10_000, report.unattributedSpellTotals().calls());
        assertEquals(10_000, report.unknownOwnerScopes());
        assertEquals(10_000, report.unknownOperationScopes());
        assertEquals(PerformanceRecorder.OPERATIONS.size() + 1, report.operationTotals().size());
        assertEquals(10_000, named(report.operationTotals(), PerformanceRecorder.OTHER_OPERATION).calls());
        assertEquals(10_000L, report.totals().counters().get(PerformanceRecorder.Counter.MATCH_CALLS));
    }

    @Test void evenHugeSpellSetsFitTheReportWithoutDiscardingGlobalTotals() {
        Set<String> names = IntStream.range(0, 30_000).mapToObj(i -> "spell-" + i).collect(Collectors.toSet());
        var recorder = new PerformanceRecorder(() -> 0, Integer.MAX_VALUE);
        recorder.start(0, 60, names);
        for (String name : names) {
            try (var ignored = recorder.enter("spell", name, "")) { recorder.matchCall(); }
        }
        var report = recorder.stop("completed");
        assertEquals(0, report.maxDetailKeys());
        var encoded = PerformanceReport.encode(report, "id", 0, 0);
        assertTrue(PerformanceReport.valueCount(encoded.measurements()) <= PerformanceReport.MAX_VALUES);
        assertEquals(true, encoded.measurements().get("totalsComplete"));
        assertEquals(false, encoded.measurements().get("spellTotalsComplete"));
        assertTrue((Integer) encoded.measurements().get("omittedSpellRows") > 0);
        assertEquals(30_000L, map(encoded.measurements().get("totals")).get("calls"));
        long exportedCalls = ((List<?>) encoded.measurements().get("spellTotals")).stream()
                .mapToLong(row -> (Long) map(row).get("calls")).sum();
        assertEquals(30_000L, exportedCalls + (Long) map(encoded.measurements().get("omittedSpellTotals")).get("calls"));
    }

    @Test void truncatedLabelsDoNotMakeTheEntireProviderReportUnserializable() {
        String owner = "x".repeat(2000);
        var recorder = new PerformanceRecorder(() -> 0, 1);
        recorder.start(0, 60, Set.of(owner));
        recorder.enter("spell", owner, "detail").close();
        var encoded = PerformanceReport.encode(recorder.stop("completed"), "id", 0, 0);
        assertTrue((Long) encoded.measurements().get("truncatedLabels") > 0);
        assertEquals(true, encoded.measurements().get("totalsComplete"));
        assertEquals(false, encoded.measurements().get("spellTotalsComplete"));
        assertFalse(encoded.complete());
    }

    @Test void stopClosesScopesButDoesNotPretendTheyFinishedNaturally() {
        var clock = new AtomicLong();
        var recorder = new PerformanceRecorder(clock::get, 1);
        recorder.start(0, 60, Set.of("fire"));
        var scope = recorder.enter("spell", "fire", "");
        clock.set(50);
        var report = recorder.stop("completed");
        scope.close();
        assertEquals(1, report.unfinishedScopesAtStop());
        assertEquals(50, report.totals().selfNanos());
        assertEquals(false, PerformanceReport.encode(report, "id", 0, 0).measurements().get("totalsComplete"));
    }

    @Test void aLongDetailLabelDoesNotInvalidateTheSpellAggregates() {
        var recorder = new PerformanceRecorder(() -> 0, 1);
        recorder.start(0, 60, Set.of("fire"));
        recorder.enter("spell", "fire", "x".repeat(2000)).close();
        var encoded = PerformanceReport.encode(recorder.stop("completed"), "id", 0, 0);
        assertEquals(true, encoded.measurements().get("totalsComplete"));
        assertEquals(true, encoded.measurements().get("spellTotalsComplete"));
        assertEquals(false, encoded.measurements().get("detailComplete"));
        assertEquals(0L, encoded.measurements().get("truncatedSpellLabels"));
        assertEquals(1L, encoded.measurements().get("truncatedDetailLabels"));
    }

    private static PerformanceRecorder.Summary named(List<PerformanceRecorder.Summary> rows, String name) {
        return rows.stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow();
    }
    private static Map<?, ?> map(Object value) { return (Map<?, ?>) value; }
}
