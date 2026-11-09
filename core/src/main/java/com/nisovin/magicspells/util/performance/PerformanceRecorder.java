package com.nisovin.magicspells.util.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Bounded, thread-confined measurements. Disabled entry does not allocate or read the clock.
 * <p>
 * Besides the aggregates, a capture keeps a per-second series (by the capture second in which a scope ends: calls and
 * self time per operation, and the inclusive time of outermost scopes) and the {@value #MAX_SLOW} longest scopes of at
 * least {@value #SLOW_NANOS} ns with their capture offsets and server tick. Both are fixed size for the capture.
 */
public final class PerformanceRecorder {

    public static final long SLOW_NANOS = 1_000_000L;
    public static final int MAX_SLOW = 256;
    /** Tick value meaning the tick source is unavailable (tests). */
    public static final int NO_TICK = Integer.MIN_VALUE;

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
                         long unknownOperationScopes, long unfinishedScopesAtStop, Series series,
                         List<Slow> slowOperations, long slowObserved) {}
    /**
     * One-second windows of capture offset, by scope end. Arrays are per window; operations per label. Each array has one
     * more slot than there are windows: index {@code windows} holds scopes that ended at or after the deadline (they
     * may run past it), so a late scope is never folded into the last regular window. {@code lateLastEndNanos} is the
     * latest such end, as a capture offset.
     */
    public record Series(int windows, long[] rootNanos, long[] rootCalls, Map<String, long[]> operationCalls,
                         Map<String, long[]> operationSelfNanos, long lateLastEndNanos) {}
    /** A scope of at least {@link #SLOW_NANOS}: offsets from the capture origin, nesting depth (0 = outermost). */
    public record Slow(String operation, String spell, String detail, int depth, long startOffsetNanos, long nanos,
                       long selfNanos, int tick, boolean unfinishedAtStop, boolean endedAfterDeadline) {}

    static final List<String> OPERATIONS = java.util.Arrays.stream(PerformanceExecution.Operation.values())
            .map(PerformanceExecution.Operation::label).toList();
    static final String OTHER_OPERATION = "[other]";

    private final LongSupplier clock;
    private final IntSupplier tick;
    private final int maxKeys;
    private volatile Session active;

    public PerformanceRecorder() { this(System::nanoTime, Integer.MAX_VALUE); }

    public PerformanceRecorder(LongSupplier clock, int maxKeys) { this(clock, maxKeys, () -> NO_TICK); }

    /** {@code tick} is read only for slow scopes, on the capture thread (the server thread). */
    public PerformanceRecorder(LongSupplier clock, int maxKeys, IntSupplier tick) {
        if (maxKeys < 0) throw new IllegalArgumentException("maxKeys must be nonnegative");
        this.clock = clock;
        this.maxKeys = maxKeys;
        this.tick = tick;
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
        active = new Session(startedNanos, seconds, names, Math.min(maxKeys, PerformanceReport.detailCapacity(names.size(), seconds)));
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
        owner = session.ownerNames.getOrDefault(owner.toLowerCase(Locale.ROOT), owner);
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
        Scope scope = new Scope(session, parent, key, stats, spellStats, operationStats, now, parent == null ? 0 : parent.depth + 1);
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
        while (session.top != null) { unfinished++; session.top.finish(now, true); }
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
        var operationCalls = new java.util.LinkedHashMap<String, long[]>();
        var operationSelf = new java.util.LinkedHashMap<String, long[]>();
        session.operationStats.forEach((label, stats) -> {
            if (stats.calls == 0) return;
            operationCalls.put(label, session.windowCalls[stats.index]);
            operationSelf.put(label, session.windowSelf[stats.index]);
        });
        var series = new Series(session.windows, session.rootNanos, session.rootCalls, operationCalls, operationSelf,
                session.lateLastEnd);
        var slow = new ArrayList<>(session.slow);
        slow.sort(Comparator.comparingLong(Slow::startOffsetNanos));
        return new Report(now - session.start, session.seconds, reason, session.droppedScopes,
                session.unattributedMatchCalls, List.copyOf(rows), session.totals.summary("all"),
                summaries(session.spellStats), summaries(session.operationStats),
                session.unattributedSpellStats.summary(PerformanceExecution.UNATTRIBUTED),
                session.maxDetailKeys, session.configuredSpells.size(), session.unknownOwnerScopes,
                session.unknownOperationScopes, unfinished, series, List.copyOf(slow), session.slowObserved);
    }

    private static List<Summary> summaries(Map<String, Stats> source) {
        return source.entrySet().stream().map(entry -> entry.getValue().summary(entry.getKey()))
                .sorted(Comparator.comparingLong(Summary::selfNanos).reversed().thenComparing(Summary::name)).toList();
    }

    private static final class Stats {
        final int index;            // series row of an operation's stats; -1 otherwise
        long calls, total, self, max;
        Stats() { this(-1); }
        Stats(int index) { this.index = index; }
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
        final Map<String, String> ownerNames = new HashMap<>();
        final int maxDetailKeys;
        final Stats totals = new Stats(), unattributedSpellStats = new Stats();
        final Map<String, Stats> spellStats = new HashMap<>(), operationStats = new HashMap<>();
        final long start;
        final int seconds;
        long droppedScopes, unattributedMatchCalls, unknownOwnerScopes, unknownOperationScopes, slowObserved;
        final int windows;
        long lateLastEnd;
        final long[] rootNanos, rootCalls;
        final long[][] windowCalls, windowSelf;
        final PriorityQueue<Slow> slow = new PriorityQueue<>(Comparator.comparingLong(Slow::nanos));
        Scope top;
        Session(long start, int seconds, Set<String> configuredSpells, int maxDetailKeys) {
            this.start = start; this.seconds = seconds; this.configuredSpells = configuredSpells; this.maxDetailKeys = maxDetailKeys;
            configuredSpells.stream().sorted().forEach(name -> ownerNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
            int index = 0;
            for (String operation : OPERATIONS) operationStats.put(operation, new Stats(index++));
            operationStats.put(OTHER_OPERATION, new Stats(index++));
            windows = seconds;
            rootNanos = new long[windows + 1];
            rootCalls = new long[windows + 1];
            windowCalls = new long[index][windows + 1];
            windowSelf = new long[index][windows + 1];
        }
        /** The capture second a scope ending now belongs to; {@code windows} (the late slot) at or after the deadline. */
        int window(long now) {
            long offset = now - start;
            if (offset >= seconds * 1_000_000_000L) {
                lateLastEnd = Math.max(lateLastEnd, offset);
                return windows;
            }
            return (int) Math.min(Math.max(offset / 1_000_000_000L, 0), windows - 1);
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
        private final int depth;
        private long children;
        private boolean closed;

        private Scope() { session = null; parent = null; key = null; stats = null; spellStats = null; operationStats = null; started = 0; depth = 0; }
        private Scope(Session session, Scope parent, Key key, Stats stats, Stats spellStats, Stats operationStats, long started, int depth) {
            this.session = session; this.parent = parent; this.key = key;
            this.stats = stats; this.spellStats = spellStats; this.operationStats = operationStats; this.started = started;
            this.depth = depth;
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
            finish(clock.getAsLong(), false);
        }

        private void finish(long now, boolean unfinished) {
            if (closed) return;
            long elapsed = Math.max(0, now - started);
            long self = Math.max(0, elapsed - children);
            session.totals.record(elapsed, self);
            spellStats.record(elapsed, self);
            operationStats.record(elapsed, self);
            if (stats != null) stats.record(elapsed, self);
            if (parent != null) parent.children += elapsed;
            int window = session.window(now);
            session.windowCalls[operationStats.index][window]++;
            session.windowSelf[operationStats.index][window] += self;
            if (parent == null) {
                session.rootNanos[window] += elapsed;
                session.rootCalls[window]++;
            }
            if (elapsed >= SLOW_NANOS) slow(elapsed, self, now, unfinished);
            session.top = parent;
            closed = true;
        }

        /** Keeps the longest slow scopes; every slow scope is counted, kept or not. */
        private void slow(long elapsed, long self, long now, boolean unfinished) {
            session.slowObserved++;
            if (session.slow.size() >= MAX_SLOW) {
                if (elapsed <= session.slow.peek().nanos()) return;
                session.slow.poll();
            }
            session.slow.add(new Slow(key.operation(), key.spell(), key.detail(), depth, started - session.start, elapsed, self,
                    tick.getAsInt(), unfinished, now - session.start > session.seconds * 1_000_000_000L));
        }
    }
}
