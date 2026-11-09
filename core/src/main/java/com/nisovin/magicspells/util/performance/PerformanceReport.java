package com.nisovin.magicspells.util.performance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.nisovin.magicspells.util.performance.PerformanceRecorder.Counter;
import com.nisovin.magicspells.util.performance.PerformanceRecorder.Summary;

/** Allocates the protocol-1 report allowance to aggregates first, then optional detail. */
final class PerformanceReport {
    // Matches SneakyDiagnostics protocol 1.0's documented per-provider value limit.
    static final int MAX_VALUES = 250_000;
    private static final Summary EMPTY = new Summary("", 0, 0, 0, 0, Map.of());
    private static final int SUMMARY_VALUES = valueCount(named(EMPTY, "spell", new long[1]));
    private static final int DETAIL_VALUES = valueCount(detail(new PerformanceRecorder.Row(
            new PerformanceRecorder.Key("", "", ""), 0, 0, 0, 0, Map.of()), new long[1]));
    private static final int BASE_VALUES = valueCount(base());
    /** MATCH_CALLS stays in the counters for compatibility; nothing in MagicSpells counts it any more. */
    static final Map<String, Object> COUNTER_COVERAGE = Map.of("INVENTORY_SLOTS", "observed",
            "MATCH_CALLS", "unobserved: magic-item matching moved to SneakyItems; see the SneakyItems provider's match.calls");
    private static final int SLOW_VALUES = valueCount(slowOperations(java.util.Collections.nCopies(PerformanceRecorder.MAX_SLOW,
            new PerformanceRecorder.Slow("", "", "", 0, 0, 0, 0, 0, false, false)), 0, new long[1]));
    record Encoded(Map<String, Object> measurements, boolean complete) {}

    /** Reserve room for every loaded spell and every operation, even if all counters become nonzero. */
    static int detailCapacity(int spellNames) {
        if (spellNames < 0) throw new IllegalArgumentException("Spell count must be nonnegative");
        return (int) Math.max(0, (MAX_VALUES - BASE_VALUES - (long) spellNames * SUMMARY_VALUES) / DETAIL_VALUES);
    }

    /** As {@link #detailCapacity(int)}, also reserving the per-second series of a capture this long and the slow operations. */
    static int detailCapacity(int spellNames, int seconds) {
        if (spellNames < 0) throw new IllegalArgumentException("Spell count must be nonnegative");
        long reserved = BASE_VALUES + (long) spellNames * SUMMARY_VALUES + seriesValues(seconds) + SLOW_VALUES;
        return (int) Math.max(0, (MAX_VALUES - reserved) / DETAIL_VALUES);
    }

    /** Values of a full series: every operation present in every window. */
    static int seriesValues(int seconds) {
        int windows = Math.max(1, seconds);
        var operations = new java.util.LinkedHashMap<String, long[]>();
        long[] full = new long[windows + 1];
        java.util.Arrays.fill(full, 1);                      // every operation in every window and in the late slot
        for (String operation : PerformanceRecorder.OPERATIONS) operations.put(operation, full);
        operations.put(PerformanceRecorder.OTHER_OPERATION, full);
        return valueCount(series(new PerformanceRecorder.Series(windows, full, full, operations, operations, 0),
                (long) windows * 1_000_000_000L));
    }

    static Encoded encode(PerformanceRecorder.Report source, String id, long startedAfterMillis, long finishedAfterMillis) {
        return encode(source, id, startedAfterMillis, finishedAfterMillis, Map.of());
    }

    static Encoded encode(PerformanceRecorder.Report source, String id, long startedAfterMillis, long finishedAfterMillis,
                          Map<String, Object> lifecycle) {
        var report = base();
        if (!lifecycle.isEmpty()) report.put("taskLifecycle", lifecycle);
        long[] truncated = {0};
        report.put("captureId", id);
        report.put("startedAfterMillis", startedAfterMillis);
        report.put("finishedAfterMillis", finishedAfterMillis);
        report.put("elapsedNanos", source.elapsedNanos());
        report.put("unattributedMatchCalls", source.unattributedMatchCalls());
        report.put("unknownOwnerScopes", source.unknownOwnerScopes());
        report.put("unknownOperationScopes", source.unknownOperationScopes());
        report.put("unfinishedScopesAtStop", source.unfinishedScopesAtStop());
        report.put("totals", measurement(source.totals()));
        report.put("unattributedSpellTotals", measurement(source.unattributedSpellTotals()));
        report.put("operationTotals", source.operationTotals().stream().map(row -> named(row, "operation", truncated)).toList());
        report.put("counterCoverage", COUNTER_COVERAGE);
        if (source.series() != null) report.put("series", series(source.series(), source.elapsedNanos()));
        long[] truncatedSlowLabels = {0};
        if (source.slowOperations() != null)
            report.put("slowOperations", slowOperations(source.slowOperations(), source.slowObserved(), truncatedSlowLabels));
        var limits = limits(source.maxDetailKeys(), source.configuredSpellNames(), source.rows().size(), 0);
        report.put("limits", limits);
        var spells = new ArrayList<Map<String, Object>>();
        var details = new ArrayList<Map<String, Object>>();
        report.put("spellTotals", spells);
        report.put("rows", details);
        int remaining = MAX_VALUES - valueCount(report);
        Summary omitted = EMPTY;
        int omittedSpells = 0, omittedDetails = 0;
        long omittedCalls = 0;
        for (Summary row : source.spellTotals()) {
            var encoded = named(row, "spell", truncated);
            int cost = valueCount(encoded);
            if (cost <= remaining) { spells.add(encoded); remaining -= cost; }
            else { omittedSpells++; omitted = merge(omitted, row); }
        }
        long truncatedSpellLabels = truncated[0];
        long[] truncatedDetailLabels = {0};
        for (var row : source.rows()) {
            var encoded = detail(row, truncatedDetailLabels);
            int cost = valueCount(encoded);
            if (cost <= remaining) { details.add(encoded); remaining -= cost; }
            else { omittedDetails++; omittedCalls += row.calls(); }
        }
        report.put("omittedSpellTotals", measurement(omitted));
        report.put("omittedSpellRows", omittedSpells);
        report.put("omittedDetailRows", omittedDetails);
        report.put("omittedDetailCalls", omittedCalls);
        report.put("detailOverflowScopes", source.droppedScopes());
        report.put("droppedScopes", source.droppedScopes() + omittedCalls);
        report.put("truncatedLabels", truncatedSpellLabels + truncatedDetailLabels[0] + truncatedSlowLabels[0]);
        report.put("truncatedSpellLabels", truncatedSpellLabels);
        report.put("truncatedDetailLabels", truncatedDetailLabels[0]);
        boolean totalsComplete = source.unfinishedScopesAtStop() == 0;
        boolean operationsComplete = totalsComplete && source.unknownOperationScopes() == 0;
        boolean spellsComplete = totalsComplete && source.unknownOwnerScopes() == 0 && omittedSpells == 0 && truncatedSpellLabels == 0;
        boolean detailComplete = totalsComplete && source.droppedScopes() == 0 && omittedDetails == 0 && truncatedDetailLabels[0] == 0;
        report.put("totalsComplete", totalsComplete);
        report.put("operationTotalsComplete", operationsComplete);
        report.put("spellTotalsComplete", spellsComplete);
        report.put("detailComplete", detailComplete);
        report.put("limits", limits(source.maxDetailKeys(), source.configuredSpellNames(), source.rows().size(), details.size()));
        boolean tasksComplete = lifecycle.isEmpty() || Boolean.TRUE.equals(lifecycle.get("lifecycleComplete"));
        return new Encoded(report, totalsComplete && operationsComplete && spellsComplete && detailComplete && tasksComplete);
    }

    private static LinkedHashMap<String, Object> base() {
        var report = new LinkedHashMap<String, Object>();
        report.put("captureId", "");
        report.put("startedAfterMillis", 0L);
        report.put("finishedAfterMillis", 0L);
        report.put("elapsedNanos", 0L);
        report.put("unattributedMatchCalls", 0L);
        report.put("unknownOwnerScopes", 0L);
        report.put("unknownOperationScopes", 0L);
        report.put("unfinishedScopesAtStop", 0L);
        report.put("totals", measurement(EMPTY));
        report.put("unattributedSpellTotals", measurement(EMPTY));
        report.put("omittedSpellTotals", measurement(EMPTY));
        var operations = new ArrayList<Map<String, Object>>();
        for (String ignored : PerformanceRecorder.OPERATIONS) operations.add(named(EMPTY, "operation", new long[1]));
        operations.add(named(EMPTY, "operation", new long[1]));
        report.put("operationTotals", operations);
        report.put("spellTotals", new ArrayList<>());
        report.put("rows", new ArrayList<>());
        report.put("omittedSpellRows", 0);
        report.put("omittedDetailRows", 0);
        report.put("omittedDetailCalls", 0L);
        report.put("detailOverflowScopes", 0L);
        report.put("droppedScopes", 0L);
        report.put("truncatedLabels", 0L);
        report.put("truncatedSpellLabels", 0L);
        report.put("truncatedDetailLabels", 0L);
        report.put("totalsComplete", true);
        report.put("operationTotalsComplete", true);
        report.put("spellTotalsComplete", true);
        report.put("detailComplete", true);
        report.put("limits", limits(0, 0, 0, 0));
        report.put("notes", "Server-thread inclusive wall time, not CPU time; async work is excluded. "
                + "Totals, operationTotals and spellTotals are independent views of the same scopes: do not add them together or sum inclusive rows. "
                + "Use selfNanos for cost ranking; it subtracts all measured children, including children omitted from detail. "
                + "Detail overflow does not discard aggregate calls, time or counters. droppedScopes concerns detail only. "
                + "Unknown owners remain in unattributedSpellTotals; omitted spell rows remain in omittedSpellTotals and global totals. "
                + "Details retain the first keys encountered, not the most expensive keys. Scope timings can extend beyond the deadline. "
                + "Dedicated spatial-query, altered-block lifecycle, undo and particle probes are not included.");
        return report;
    }

    private static Map<String, Object> series(PerformanceRecorder.Series series, long elapsedNanos) {
        int shown = (int) Math.min(series.windows(), Math.max(0, elapsedNanos) / 1_000_000_000L + 1);
        var result = new LinkedHashMap<String, Object>();
        result.put("windowMillis", 1000L);
        result.put("rootNanos", window(series.rootNanos(), shown));
        result.put("rootCalls", window(series.rootCalls(), shown));
        var operations = new LinkedHashMap<String, Object>();
        series.operationCalls().forEach((operation, calls) -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("calls", window(calls, shown));
            row.put("selfNanos", window(series.operationSelfNanos().get(operation), shown));
            operations.put(operation, row);
        });
        result.put("operations", operations);
        int late = series.windows();
        if (series.rootCalls()[late] != 0) {
            // Scopes that ended at or after the deadline, kept apart: placing them in the last regular window would
            // move them up to a second (or a whole stall) earlier than the tick they ended in.
            var lateTotals = new LinkedHashMap<String, Object>();
            lateTotals.put("fromMillis", (long) late * 1000L);
            lateTotals.put("lastEndOffsetMillis", series.lateLastEndNanos() / 1_000_000);
            lateTotals.put("rootNanos", series.rootNanos()[late]);
            lateTotals.put("rootCalls", series.rootCalls()[late]);
            var lateOperations = new LinkedHashMap<String, Object>();
            series.operationCalls().forEach((operation, calls) -> {
                if (calls[late] == 0) return;
                var row = new LinkedHashMap<String, Object>();
                row.put("calls", calls[late]);
                row.put("selfNanos", series.operationSelfNanos().get(operation)[late]);
                lateOperations.put(operation, row);
            });
            lateTotals.put("operations", lateOperations);
            result.put("late", lateTotals);
        }
        result.put("note", "One-second windows of capture offset by scope end. selfNanos is additive across operations "
                + "(absent operations had no calls); rootNanos is the inclusive time of outermost scopes, without double counting. "
                + "late: scopes that ended at or after the deadline, from fromMillis up to lastEndOffsetMillis.");
        return result;
    }

    private static List<Object> window(long[] values, int shown) {
        var list = new ArrayList<Object>(shown);
        for (int i = 0; i < shown; i++) list.add(values[i]);
        return list;
    }

    private static Map<String, Object> slowOperations(List<PerformanceRecorder.Slow> slow, long observed, long[] truncated) {
        var records = new ArrayList<Object>(slow.size());
        for (var op : slow) {
            var row = new LinkedHashMap<String, Object>();
            row.put("operation", op.operation());
            row.put("spell", label(op.spell(), truncated));
            row.put("spellKey", label(op.spell().toLowerCase(java.util.Locale.ROOT), new long[1]));
            row.put("detail", label(op.detail(), truncated));
            row.put("depth", (long) op.depth());
            row.put("startOffsetMicros", op.startOffsetNanos() / 1_000);
            row.put("endOffsetMicros", (op.startOffsetNanos() + op.nanos()) / 1_000);
            row.put("nanos", op.nanos());
            row.put("selfNanos", op.selfNanos());
            row.put("thread", "server");
            if (op.tick() != PerformanceRecorder.NO_TICK) row.put("tick", (long) op.tick());
            row.put("unfinishedAtStop", op.unfinishedAtStop());
            row.put("endedAfterDeadline", op.endedAfterDeadline());
            records.add(row);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("thresholdNanos", PerformanceRecorder.SLOW_NANOS);
        result.put("capacity", (long) PerformanceRecorder.MAX_SLOW);
        result.put("observed", observed);
        result.put("retained", (long) records.size());
        result.put("evicted", observed - records.size());
        result.put("complete", observed == records.size());
        result.put("note", "The longest scopes of at least thresholdNanos; evicted counts the others. Offsets are microseconds "
                + "from the capture origin, nanos is inclusive, tick is the server tick. Records nest and overlap.");
        result.put("records", records);
        return result;
    }

    private static Map<String, Object> limits(int details, int spells, int recorded, int reported) {
        return Map.of("maxDetailKeys", details, "configuredSpellNames", spells, "recordedDetailKeys", recorded,
                "reportedDetailRows", reported, "reportValueLimit", MAX_VALUES);
    }

    private static Map<String, Object> measurement(Summary row) {
        var counters = new LinkedHashMap<String, Object>();
        for (Counter counter : Counter.values()) counters.put(counter.name(), row.counters().getOrDefault(counter, 0L));
        return new LinkedHashMap<>(Map.of("calls", row.calls(), "totalNanos", row.totalNanos(),
                "selfNanos", row.selfNanos(), "maxNanos", row.maxNanos(), "counters", counters));
    }
    private static Map<String, Object> named(Summary row, String dimension, long[] truncated) {
        var encoded = measurement(row);
        encoded.put(dimension, label(row.name(), truncated));
        if (dimension.equals("spell")) encoded.put("spellKey", label(row.name().toLowerCase(java.util.Locale.ROOT), new long[1]));
        return encoded;
    }
    private static Map<String, Object> detail(PerformanceRecorder.Row row, long[] truncated) {
        var encoded = measurement(new Summary("", row.calls(), row.totalNanos(), row.selfNanos(), row.maxNanos(), row.counters()));
        encoded.put("operation", label(row.key().operation(), truncated));
        encoded.put("spell", label(row.key().spell(), truncated));
        encoded.put("spellKey", label(row.key().spell().toLowerCase(java.util.Locale.ROOT), new long[1]));
        encoded.put("detail", label(row.key().detail(), truncated));
        return encoded;
    }
    private static String label(String label, long[] truncated) {
        if (label.length() <= 1024) return label;
        truncated[0]++;
        return label.substring(0, 1024);
    }
    private static Summary merge(Summary first, Summary next) {
        var counters = new java.util.EnumMap<Counter, Long>(Counter.class);
        for (Counter counter : Counter.values()) counters.put(counter, first.counters().getOrDefault(counter, 0L) + next.counters().getOrDefault(counter, 0L));
        return new Summary("", first.calls() + next.calls(), first.totalNanos() + next.totalNanos(),
                first.selfNanos() + next.selfNanos(), Math.max(first.maxNanos(), next.maxNanos()), counters);
    }
    static int valueCount(Object value) {
        int count = 1;
        if (value instanceof Map<?, ?> map) for (Object child : map.values()) count += valueCount(child);
        else if (value instanceof List<?> list) for (Object child : list) count += valueCount(child);
        return count;
    }
}
