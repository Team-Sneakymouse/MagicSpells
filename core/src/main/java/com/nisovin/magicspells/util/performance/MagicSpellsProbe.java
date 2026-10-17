package com.nisovin.magicspells.util.performance;

import com.teamsneakymouse.sneakydiagnostics.api.Diagnostics;
import java.util.Map;
import java.util.Set;

/** Adapts aggregate-first, bounded measurements into the diagnostics report protocol. */
final class MagicSpellsProbe implements Diagnostics.Provider {
    private final PerformanceRecorder recorder;
    private final Set<String> configuredSpells;
    private Diagnostics.Capture capture;
    private long startedAfterMillis;

    MagicSpellsProbe(PerformanceRecorder recorder, Set<String> configuredSpells) {
        this.recorder = recorder;
        this.configuredSpells = Set.copyOf(configuredSpells);
    }

    @Override public void start(Diagnostics.Capture capture) {
        recorder.start(capture.startedNanos(), capture.seconds(), configuredSpells);
        this.capture = capture;
        startedAfterMillis = capture.offsetMillis();
    }

    @Override public Diagnostics.Result stop(String reason) {
        var report = recorder.stop(reason);
        if (report == null) return new Diagnostics.Result(Map.of("available", false), false);
        var encoded = PerformanceReport.encode(report, capture.id(), startedAfterMillis, capture.offsetMillis());
        capture = null;
        return new Diagnostics.Result(encoded.measurements(), encoded.complete());
    }
}
