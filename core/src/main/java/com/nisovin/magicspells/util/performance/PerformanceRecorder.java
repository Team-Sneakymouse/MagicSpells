package com.nisovin.magicspells.util.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Bounded, thread-confined measurements. Disabled entry does not allocate or read the clock. */
public final class PerformanceRecorder {

    public enum Counter { INVENTORY_SLOTS, MATCH_CALLS }

    public record Key(String operation, String spell, String detail) {}
    public record Row(Key key, long calls, long totalNanos, long selfNanos, long maxNanos,
                      Map<Counter, Long> counters) {}
    public record Summary(String name, long calls, long totalNanos, long selfNanos, long maxNanos,
                          Map<Counter, Long> counters) {}
    public record Report(long elapsedNanos, int requestedSeconds, String reason, long droppedScopes,
                         long unattributedMatchCalls, List<Row> rows, Summary totals,
                         List<Summary> spellTotals, List<Summary> operationTotals, Summary unattributedSpellTotals,
                         int maxDetailKeys, int configuredSpellNames, long unknownOwnerScopes,
                         long unknownOperationScopes, long unfinishedScopesAtStop) {}

    static final List<String> OPERATIONS = java.util.Arrays.stream(PerformanceExecution.Operation.values())
            .map(PerformanceExecution.Operation::label).toList();
    static final String OTHER_OPERATION = "[other]";

    private final LongSupplier clock;
    private final int maxKeys;
    private volatile Session active;

    public PerformanceRecorder() { this(System::nanoTime, Integer.MAX_VALUE); }

    public PerformanceRecorder(LongSupplier clock, int maxKeys) {
        if (maxKeys < 0) throw new IllegalArgumentException("maxKeys must be nonnegative");
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    public void start(int seconds) {
        start(clock.getAsLong(), seconds);
    }

    /** Use the diagnostics clock origin so every provider shares one deadline. */
    public void start(long startedNanos, int seconds) {
        start(startedNanos, seconds, Set.of());
    }

    public void start(long startedNanos, int seconds, Set<String> configuredSpells) {
        if (seconds < 1 || seconds > 1800) throw new IllegalArgumentException("Duration must be 1..1800 seconds");
        if (active != null) throw new IllegalStateException("A performance capture is already running");
        Set<String> names = Set.copyOf(configuredSpells);
        active = new Session(startedNanos, seconds, names, Math.min(maxKeys, PerformanceReport.detailCapacity(names.size())));
    }

    public boolean isActive() { return active != null; }

    public boolean isExpired() {
        Session session = active;
        return session != null && clock.getAsLong() - session.start >= session.seconds * 1_000_000_000L;
    }

    public Scope enter(String operation, String spell, String detail) {
        Session session = active;
        if (session == null || session.owner != Thread.currentThread()) return Scope.NONE;
        long now = clock.getAsLong();
        if (now - session.start >= session.seconds * 1_000_000_000L) return Scope.NONE;
        Scope parent = session.top;
        String owner = spell != null ? spell : parent == null ? "[unattributed]" : parent.key.spell();
        Key key = new Key(operation, owner, detail);
        Stats stats = session.stats.get(key);
        if (stats == null) {
            if (session.stats.size() >= session.maxDetailKeys) {
                session.droppedScopes++;
                // Only detail is omitted. Every admitted scope still updates its aggregates.
            } else {
                stats = new Stats();
                session.stats.put(key, stats);
            }
        }
        Stats spellStats;
        if (session.configuredSpells.contains(owner)) spellStats = session.spellStats.computeIfAbsent(owner, ignored -> new Stats());
        else {
            spellStats = session.unattributedSpellStats;
            if (!owner.equals(PerformanceExecution.UNATTRIBUTED)) session.unknownOwnerScopes++;
        }
        Stats operationStats = session.operationStats.get(operation);
        if (operationStats == null) {
            operationStats = session.operationStats.get(OTHER_OPERATION);
            session.unknownOperationScopes++;
        }
        Scope scope = new Scope(session, parent, key, stats, spellStats, operationStats, now);
        session.top = scope;
        return scope;
    }

    /** Counts matching attempts in the innermost measured operation, without a timer per match. */
    public void matchCall() {
        Session session = active;
        if (session == null || session.owner != Thread.currentThread()) return;
        if (session.top != null) session.top.add(Counter.MATCH_CALLS, 1);
        // Bound collection even if the next tick cannot run the stop task yet.
        else if (clock.getAsLong() - session.start < session.seconds * 1_000_000_000L)
            session.unattributedMatchCalls++;
    }

    public Report stop(String reason) {
        Session session = active;
        if (session == null) return null;
        if (session.owner != Thread.currentThread()) throw new IllegalStateException("Stop on the capture thread");
        long now = clock.getAsLong();
        // Finish any in-flight scopes at the boundary. Later finally blocks become no-ops.
        long unfinished = 0;
        while (session.top != null) { unfinished++; session.top.finish(now); }
        active = null;
        List<Row> rows = new ArrayList<>();
        session.stats.forEach((key, stats) -> {
            Map<Counter, Long> counters = new java.util.EnumMap<>(Counter.class);
            for (Counter counter : Counter.values()) {
                long value = stats.counters[counter.ordinal()];
                if (value != 0) counters.put(counter, value);
            }
            rows.add(new Row(key, stats.calls, stats.total, stats.self, stats.max, Map.copyOf(counters)));
        });
        rows.sort(Comparator.comparingLong(Row::totalNanos).reversed()
                .thenComparing(row -> row.key().toString()));
        return new Report(now - session.start, session.seconds, reason, session.droppedScopes,
                session.unattributedMatchCalls, List.copyOf(rows), session.totals.summary("all"),
                summaries(session.spellStats), summaries(session.operationStats),
                session.unattributedSpellStats.summary(PerformanceExecution.UNATTRIBUTED),
                session.maxDetailKeys, session.configuredSpells.size(), session.unknownOwnerScopes,
                session.unknownOperationScopes, unfinished);
    }

    private static List<Summary> summaries(Map<String, Stats> source) {
        return source.entrySet().stream().map(entry -> entry.getValue().summary(entry.getKey()))
                .sorted(Comparator.comparingLong(Summary::selfNanos).reversed().thenComparing(Summary::name)).toList();
    }

    private static final class Stats {
        long calls, total, self, max;
        final long[] counters = new long[Counter.values().length];
        void record(long elapsed, long selfNanos) {
            calls++; total += elapsed; self += selfNanos; max = Math.max(max, elapsed);
        }
        Summary summary(String name) {
            var values = new java.util.EnumMap<Counter, Long>(Counter.class);
            for (Counter counter : Counter.values()) if (counters[counter.ordinal()] != 0) values.put(counter, counters[counter.ordinal()]);
            return new Summary(name, calls, total, self, max, Map.copyOf(values));
        }
    }

    private static final class Session {
        final Thread owner = Thread.currentThread();
        final Map<Key, Stats> stats = new HashMap<>();
        final Set<String> configuredSpells;
        final int maxDetailKeys;
        final Stats totals = new Stats(), unattributedSpellStats = new Stats();
        final Map<String, Stats> spellStats = new HashMap<>(), operationStats = new HashMap<>();
        final long start;
        final int seconds;
        long droppedScopes, unattributedMatchCalls, unknownOwnerScopes, unknownOperationScopes;
        Scope top;
        Session(long start, int seconds, Set<String> configuredSpells, int maxDetailKeys) {
            this.start = start; this.seconds = seconds; this.configuredSpells = configuredSpells; this.maxDetailKeys = maxDetailKeys;
            for (String operation : OPERATIONS) operationStats.put(operation, new Stats());
            operationStats.put(OTHER_OPERATION, new Stats());
        }
    }

    public final class Scope implements AutoCloseable {
        // One immutable sentinel shared by disabled calls.
        private static final Scope NONE = new PerformanceRecorder(() -> 0, 1).new Scope();
        private final Session session;
        private final Scope parent;
        private final Key key;
        private final Stats stats, spellStats, operationStats;
        private final long started;
        private long children;
        private boolean closed;

        private Scope() { session = null; parent = null; key = null; stats = null; spellStats = null; operationStats = null; started = 0; }
        private Scope(Session session, Scope parent, Key key, Stats stats, Stats spellStats, Stats operationStats, long started) {
            this.session = session; this.parent = parent; this.key = key;
            this.stats = stats; this.spellStats = spellStats; this.operationStats = operationStats; this.started = started;
        }

        public void add(Counter counter, long amount) {
            if (session == null || closed) return;
            int index = counter.ordinal();
            session.totals.counters[index] += amount;
            spellStats.counters[index] += amount;
            operationStats.counters[index] += amount;
            if (stats != null) stats.counters[index] += amount;
        }

        @Override public void close() {
            if (session == null || closed) return;
            finish(clock.getAsLong());
        }

        private void finish(long now) {
            if (closed) return;
            long elapsed = Math.max(0, now - started);
            long self = Math.max(0, elapsed - children);
            session.totals.record(elapsed, self);
            spellStats.record(elapsed, self);
            operationStats.record(elapsed, self);
            if (stats != null) stats.record(elapsed, self);
            if (parent != null) parent.children += elapsed;
            session.top = parent;
            closed = true;
        }
    }
}
