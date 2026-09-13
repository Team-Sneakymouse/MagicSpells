package com.nisovin.magicspells.util.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded, thread-confined measurements. Disabled entry does not allocate or read the clock. */
public final class PerformanceRecorder {

    public enum Counter { REGISTERED_ENTITIES, ACTIVATIONS, INVENTORY_SLOTS, ITEMS_EXAMINED,
        MAPPING_COUNT, ITEMS_UPDATED, MATCH_CALLS }

    public record Key(String operation, String spell, String detail) {}
    public record Row(Key key, long calls, long totalNanos, long selfNanos, long maxNanos,
                      Map<Counter, Long> counters) {}
    public record Report(long elapsedNanos, int requestedSeconds, String reason, long droppedScopes,
                         long unattributedMatchCalls, List<Row> rows) {}

    private final LongSupplier clock;
    private final int maxKeys;
    private volatile Session active;

    public PerformanceRecorder() { this(System::nanoTime, 4096); }

    public PerformanceRecorder(LongSupplier clock, int maxKeys) {
        if (maxKeys < 1) throw new IllegalArgumentException("maxKeys must be positive");
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    public void start(int seconds) {
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("Duration must be 1..300 seconds");
        if (active != null) throw new IllegalStateException("A performance capture is already running");
        active = new Session(clock.getAsLong(), seconds);
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
            if (session.stats.size() >= maxKeys) {
                session.droppedScopes++;
                return Scope.NONE;
            }
            stats = new Stats();
            session.stats.put(key, stats);
        }
        Scope scope = new Scope(session, parent, key, stats, now);
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
        while (session.top != null) session.top.finish(now);
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
                session.unattributedMatchCalls, List.copyOf(rows));
    }

    private static final class Stats {
        long calls, total, self, max;
        final long[] counters = new long[Counter.values().length];
    }

    private static final class Session {
        final Thread owner = Thread.currentThread();
        final Map<Key, Stats> stats = new HashMap<>();
        final long start;
        final int seconds;
        long droppedScopes, unattributedMatchCalls;
        Scope top;
        Session(long start, int seconds) { this.start = start; this.seconds = seconds; }
    }

    public final class Scope implements AutoCloseable {
        // One immutable sentinel shared by disabled calls.
        private static final Scope NONE = new PerformanceRecorder(() -> 0, 1).new Scope();
        private final Session session;
        private final Scope parent;
        private final Key key;
        private final Stats stats;
        private final long started;
        private long children;
        private boolean closed;

        private Scope() { session = null; parent = null; key = null; stats = null; started = 0; }
        private Scope(Session session, Scope parent, Key key, Stats stats, long started) {
            this.session = session; this.parent = parent; this.key = key;
            this.stats = stats; this.started = started;
        }

        public void add(Counter counter, long amount) {
            if (session != null && !closed) stats.counters[counter.ordinal()] += amount;
        }

        @Override public void close() {
            if (session == null || closed) return;
            finish(clock.getAsLong());
        }

        private void finish(long now) {
            if (closed) return;
            long elapsed = Math.max(0, now - started);
            stats.calls++;
            stats.total += elapsed;
            stats.self += Math.max(0, elapsed - children);
            stats.max = Math.max(stats.max, elapsed);
            if (parent != null) parent.children += elapsed;
            session.top = parent;
            closed = true;
        }
    }
}
