package com.nisovin.magicspells.util.performance;

import com.teamsneakymouse.sneakydiagnostics.api.Diagnostics;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adapts the existing bounded recorder into detached diagnostics measurements. */
final class MagicSpellsProbe implements Diagnostics.Provider {
    private final PerformanceRecorder recorder;
    private Diagnostics.Capture capture;
    private long startedAfterMillis;

    MagicSpellsProbe(PerformanceRecorder recorder) { this.recorder = recorder; }

    @Override public void start(Diagnostics.Capture capture) {
        recorder.start(capture.startedNanos(), capture.seconds());
        this.capture = capture;
        startedAfterMillis = capture.offsetMillis();
    }

    @Override public Diagnostics.Result stop(String reason) {
        var report = recorder.stop(reason);
        if (report == null) return new Diagnostics.Result(Map.of("available", false), false);
        var measurements = new LinkedHashMap<String, Object>();
        measurements.put("captureId", capture.id());
        measurements.put("startedAfterMillis", startedAfterMillis);
        measurements.put("finishedAfterMillis", capture.offsetMillis());
        measurements.put("elapsedNanos", report.elapsedNanos());
        measurements.put("droppedScopes", report.droppedScopes());
        measurements.put("unattributedMatchCalls", report.unattributedMatchCalls());
        measurements.put("rows", report.rows().stream().map(row -> {
            var counters = new LinkedHashMap<String, Object>();
            row.counters().forEach((counter, count) -> counters.put(counter.name(), count));
            return Map.<String, Object>of("operation", row.key().operation(), "spell", row.key().spell(),
                    "detail", row.key().detail(), "calls", row.calls(), "totalNanos", row.totalNanos(),
                    "selfNanos", row.selfNanos(), "maxNanos", row.maxNanos(), "counters", counters);
        }).toList());
        measurements.put("limits", Map.of("maxKeys", 4096));
        measurements.put("notes", "Server-thread inclusive wall time; async execution is excluded. Do not sum inclusive rows. "
                + "Self time subtracts measured children, but includes uninstrumented work and some probe overhead. "
                + "In-flight scopes may finish after the shared deadline. Calls are operation attempts, not successful casts. "
                + "Ownership follows execution/registration and scheduled work retains spell names across captures. "
                + "These measurements do not include dedicated spatial-query, altered-block lifecycle, undo or particle probes.");
        capture = null;
        return new Diagnostics.Result(measurements, report.droppedScopes() == 0);
    }
}
