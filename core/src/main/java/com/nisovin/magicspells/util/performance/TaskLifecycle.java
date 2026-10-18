package com.nisovin.magicspells.util.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Bounded registration metadata, independent of capture sessions and runnable payloads. */
public final class TaskLifecycle {
    private static final StackWalker CALLER = StackWalker.getInstance();

    /** One source frame per registration; no stack or argument values are retained. */
    public static String schedulingOrigin() {
        return CALLER.walk(frames -> frames.filter(frame -> !frame.getClassName().equals(TaskLifecycle.class.getName()))
                .filter(frame -> !(frame.getClassName().equals("com.nisovin.magicspells.MagicSpells")
                        && (frame.getMethodName().equals("scheduleDelayedTask") || frame.getMethodName().equals("scheduleRepeatingTask"))))
                .findFirst().map(frame -> frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber())
                .orElse("[unknown]"));
    }

    public record Pending(int id, boolean sync) {}
    private record Key(String spell, String origin, long delay, long interval) {}
    private static final class Entry {
        final Key key;
        final long created;
        int id, running;
        boolean finished;
        boolean cleared;
        Entry(Key key, long created) { this.key = key; this.created = created; }
    }
    private static final class Counts {
        long registrations, executions, repeatingExecutions, oneShotCompletions;
        long cancellationRequests, trackedCancellations, unobservedRemovals;
        long callbackCompletions, executionFailures, cleanupTerminations;
        Map<String, Object> encode() {
            var result = new LinkedHashMap<String, Object>();
            result.put("registrations", registrations);
            result.put("executions", executions);
            result.put("repeatingExecutions", repeatingExecutions);
            result.put("oneShotCompletions", oneShotCompletions);
            result.put("cancellationRequests", cancellationRequests);
            result.put("trackedCancellations", trackedCancellations);
            result.put("unobservedRemovals", unobservedRemovals);
            result.put("callbackCompletions", callbackCompletions);
            result.put("executionFailures", executionFailures);
            result.put("cleanupTerminations", cleanupTerminations);
            return result;
        }
    }
    private static final class Session {
        final long start, deadline;
        final Counts totals = new Counts();
        final Map<Key, Counts> groups = new LinkedHashMap<>();
        long omittedGroupEvents;
        long preCaptureUnobservedRemovals;
        long unknownOwnerEvents;
        Map<String, Object> initial;
        Session(long start, int seconds) { this.start = start; deadline = start + seconds * 1_000_000_000L; }
    }
    private final LongSupplier clock;
    private final int capacity, groupCapacity;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private Session session;
    private long overflowRegistrations;
    private long truncatedMetadataLabels;

    public TaskLifecycle() { this(System::nanoTime, 65_536, 512); }
    public TaskLifecycle(LongSupplier clock, int capacity, int groupCapacity) {
        if (capacity < 0 || groupCapacity < 0) throw new IllegalArgumentException("Limits must be nonnegative");
        this.clock = clock; this.capacity = capacity; this.groupCapacity = groupCapacity;
    }

    /** Scheduler is invoked exactly once, with its original result and exceptions preserved. */
    public synchronized int schedule(String owner, String origin, long delay, long interval,
                                     Runnable body, ToIntFunction<Runnable> scheduler) {
        if (entries.size() >= capacity) {
            int id = scheduler.applyAsInt(body);
            overflowRegistrations++;
            record(null, counts -> counts.registrations++);
            return id;
        }
        var entry = new Entry(new Key(label(owner == null ? PerformanceExecution.UNATTRIBUTED : owner), label(origin),
                Math.max(0, delay), interval == 0 ? 1 : Math.max(-1, interval)), clock.getAsLong());
        int id = scheduler.applyAsInt(() -> {
            synchronized (this) {
                entry.running++;
                if (!entry.finished && observing()) record(entry.key, counts -> {
                    counts.executions++;
                    if (entry.key.interval > 0) counts.repeatingExecutions++;
                });
            }
            boolean succeeded = false;
            try { body.run(); succeeded = true; }
            finally {
                synchronized (this) {
                    entry.running--;
                    if (!entry.cleared && observing()) {
                        boolean failed = !succeeded;
                        record(entry.key, counts -> {
                            counts.callbackCompletions++;
                            if (failed) counts.executionFailures++;
                            if (entry.key.interval < 0 && !entry.finished) counts.oneShotCompletions++;
                        });
                    }
                    if (entry.key.interval < 0 && !entry.finished) {
                        entry.finished = true;
                        entries.remove(entry.id, entry);
                    }
                }
            }
        });
        entry.id = id;
        if (!entry.finished && id >= 0 && entries.size() < capacity) entries.put(id, entry);
        record(entry.key, counts -> counts.registrations++);
        return id;
    }

    public synchronized void start(long startedNanos, int seconds, Supplier<? extends Iterable<Pending>> pending) {
        if (seconds < 1 || seconds > 1800) throw new IllegalArgumentException("Duration must be 1..1800 seconds");
        if (session != null) throw new IllegalStateException("A task capture is already running");
        session = new Session(startedNanos, seconds);
        session.initial = snapshotSafely(pending);
    }

    public synchronized Map<String, Object> stop(Supplier<? extends Iterable<Pending>> pending) {
        if (session == null) return Map.of("available", false);
        var end = snapshotSafely(pending);
        var result = new LinkedHashMap<String, Object>();
        result.put("start", session.initial);
        result.put("end", end);
        result.put("totals", session.totals.encode());
        result.put("groups", session.groups.entrySet().stream().map(group -> {
            var row = labels(group.getKey());
            row.putAll(group.getValue().encode());
            return row;
        }).toList());
        result.put("omittedLifecycleGroupEvents", session.omittedGroupEvents);
        result.put("preCaptureUnobservedRemovals", session.preCaptureUnobservedRemovals);
        result.put("unknownOwnerEvents", session.unknownOwnerEvents);
        result.put("registryOverflowRegistrations", overflowRegistrations);
        result.put("truncatedMetadataLabels", truncatedMetadataLabels);
        result.put("available", true);
        result.put("coverage", "magicspells_sync_wrappers_only");
        result.put("allPluginLifecycleComplete", false);
        result.put("nextRunAvailable", false);
        result.put("registryCapacity", capacity);
        result.put("groupCapacity", groupCapacity);
        result.put("retainedTasksAtStop", entries.size());
        result.put("deadlineAfterMillis", (session.deadline - session.start) / 1_000_000);
        boolean afterDeadline = clock.getAsLong() > session.deadline;
        result.put("endObservedAfterDeadline", afterDeadline);
        boolean inventoryComplete = (boolean) session.initial.get("complete") && (boolean) end.get("complete");
        result.put("inventoryComplete", inventoryComplete);
        result.put("lifecycleComplete", inventoryComplete && overflowRegistrations == 0 && session.totals.unobservedRemovals == 0
                && session.omittedGroupEvents == 0 && session.unknownOwnerEvents == 0 && truncatedMetadataLabels == 0 && !afterDeadline
                && ((Number) end.getOrDefault("inFlightTasks", 0)).longValue() == 0);
        session = null;
        return result;
    }

    private boolean observing() { return session != null && clock.getAsLong() < session.deadline; }

    private void record(Key key, Consumer<Counts> event) {
        if (!observing()) return;
        event.accept(session.totals);
        if (key == null) return;
        if (key.spell.equals(PerformanceExecution.UNATTRIBUTED)) session.unknownOwnerEvents++;
        Counts counts = session.groups.get(key);
        if (counts == null && session.groups.size() < groupCapacity) {
            counts = new Counts();
            session.groups.put(key, counts);
        }
        if (counts != null) event.accept(counts);
        else session.omittedGroupEvents++;
    }

    public synchronized int retainedTasks() { return entries.size(); }

    public synchronized void cancel(int id, Runnable cancel) {
        cancel.run();
        Entry removed = entries.remove(id);
        record(removed == null ? null : removed.key, counts -> counts.cancellationRequests++);
        if (removed != null) {
            removed.finished = true;
            record(removed.key, counts -> counts.trackedCancellations++);
        }
    }

    /** Called after the scheduler cancels plugin work on reload/disable. */
    public synchronized void clear() {
        for (Entry entry : entries.values()) {
            entry.finished = true;
            entry.cleared = true;
            record(entry.key, counts -> counts.cleanupTerminations++);
        }
        entries.clear();
        overflowRegistrations = 0;
        truncatedMetadataLabels = 0;
    }

    private String label(String value) {
        if (value.length() <= 1024) return value;
        truncatedMetadataLabels++;
        return value.substring(0, 1024);
    }

    private Map<String, Object> snapshotSafely(Supplier<? extends Iterable<Pending>> pending) {
        try { return snapshot(pending.get()); }
        catch (RuntimeException failure) {
            return Map.of("available", false, "complete", false, "error", "public_scheduler_snapshot_failed",
                    "observedAfterMillis", (clock.getAsLong() - session.start) / 1_000_000);
        }
    }

    private Map<String, Object> snapshot(Iterable<Pending> pending) {
        long now = clock.getAsLong();
        var ids = new HashSet<Integer>();
        var groups = new LinkedHashMap<Key, PendingGroup>();
        long count = 0, unknown = 0, omitted = 0, async = 0, unattributed = 0;
        for (Pending task : pending) {
            count++;
            if (!task.sync) async++;
            Entry entry = entries.get(task.id);
            if (entry == null) { unknown++; continue; }
            ids.add(task.id);
            if (entry.key.spell.equals(PerformanceExecution.UNATTRIBUTED)) unattributed++;
            // At most registryCapacity distinct keys. Rank all retained metadata before truncating export.
            var group = groups.computeIfAbsent(entry.key, ignored -> new PendingGroup());
            group.count++;
            group.oldest = Math.min(group.oldest, entry.created);
            group.newest = Math.max(group.newest, entry.created);
        }
        long inFlight = entries.values().stream().filter(entry -> entry.running > 0).count();
        entries.values().removeIf(entry -> {
            if (entry.running > 0 || ids.contains(entry.id)) return false;
            if (session.initial == null) session.preCaptureUnobservedRemovals++;
            else session.totals.unobservedRemovals++;
            entry.finished = true;
            return true;
        });
        var rows = new ArrayList<Map<String, Object>>();
        var ranked = groups.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Key, PendingGroup>>comparingLong(group -> group.getValue().count).reversed()
                        .thenComparing(group -> group.getKey().origin).thenComparing(group -> group.getKey().spell)
                        .thenComparingLong(group -> group.getKey().interval).thenComparingLong(group -> group.getKey().delay)).toList();
        for (var item : ranked) {
            Key key = item.getKey();
            PendingGroup group = item.getValue();
            if (rows.size() >= groupCapacity) { omitted += group.count; continue; }
            var row = labels(key);
            row.put("pendingTasks", group.count);
            row.put("oldestAgeMillis", Math.max(0, now - group.oldest) / 1_000_000);
            row.put("youngestAgeMillis", Math.max(0, now - group.newest) / 1_000_000);
            row.put("oldestCreatedAfterMillis", (group.oldest - session.start) / 1_000_000);
            row.put("newestCreatedAfterMillis", (group.newest - session.start) / 1_000_000);
            row.put("nextRunTick", null);
            rows.add(row);
        }
        return Map.of("pendingTasks", count, "groups", rows,
                "observedAfterMillis", (now - session.start) / 1_000_000,
                "unknownMetadataTasks", unknown, "omittedGroupTasks", omitted,
                "asyncTasks", async, "unattributedTasks", unattributed, "inFlightTasks", inFlight,
                "complete", unknown == 0 && omitted == 0 && unattributed == 0 && truncatedMetadataLabels == 0);
    }

    private static final class PendingGroup {
        long count, oldest = Long.MAX_VALUE, newest = Long.MIN_VALUE;
    }

    private LinkedHashMap<String, Object> labels(Key key) {
        var row = new LinkedHashMap<String, Object>();
        row.put("spell", key.spell);
        row.put("spellKey", label(key.spell.toLowerCase(Locale.ROOT)));
        row.put("origin", key.origin);
        row.put("kind", key.interval > 0 ? "repeating" : "one_shot");
        row.put("delayTicks", key.delay);
        row.put("repeatIntervalTicks", key.interval > 0 ? key.interval : null);
        return row;
    }
}
