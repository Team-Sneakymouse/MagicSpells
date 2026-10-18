package com.nisovin.magicspells.util.performance;

import com.teamsneakymouse.sneakydiagnostics.api.Diagnostics;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Adapts aggregate-first, bounded measurements into the diagnostics report protocol. */
final class MagicSpellsProbe implements Diagnostics.Provider {
    private final PerformanceRecorder recorder;
    private final Set<String> configuredSpells;
    private final TaskLifecycle tasks;
    private final Supplier<? extends Iterable<TaskLifecycle.Pending>> pending;
    private Diagnostics.Capture capture;
    private long startedAfterMillis;

    MagicSpellsProbe(PerformanceRecorder recorder, Set<String> configuredSpells) {
        this(recorder, configuredSpells, null, java.util.List::of);
    }

    MagicSpellsProbe(PerformanceRecorder recorder, Set<String> configuredSpells, TaskLifecycle tasks,
                     Supplier<? extends Iterable<TaskLifecycle.Pending>> pending) {
        this.recorder = recorder;
        this.configuredSpells = Set.copyOf(configuredSpells);
        this.tasks = tasks;
        this.pending = pending;
    }

    @Override public void start(Diagnostics.Capture capture) {
        recorder.start(capture.startedNanos(), capture.seconds(), configuredSpells);
        this.capture = capture;
        startedAfterMillis = capture.offsetMillis();
        if (tasks != null) tasks.start(capture.startedNanos(), capture.seconds(), pending);
    }

    @Override public Diagnostics.Result stop(String reason) {
        var report = recorder.stop(reason);
        if (report == null) return new Diagnostics.Result(Map.of("available", false), false);
        var lifecycle = tasks == null ? Map.<String, Object>of() : tasks.stop(pending);
        var encoded = PerformanceReport.encode(report, capture.id(), startedAfterMillis, capture.offsetMillis(), lifecycle);
        capture = null;
        return new Diagnostics.Result(encoded.measurements(), encoded.complete());
    }
}
