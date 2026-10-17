package com.nisovin.magicspells.util.performance;

import com.teamsneakymouse.sneakydiagnostics.api.Diagnostics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagicSpellsProbeTest {
    @Test void reportsExistingMeasurementsThroughTheSharedCaptureInterface() {
        var clock = new AtomicLong(100);
        var recorder = new PerformanceRecorder(clock::get, 4096);
        var probe = new MagicSpellsProbe(recorder, java.util.Set.of("fire", "first", "dropped", "after-deadline"));
        probe.start(new Diagnostics.Capture("shared", "now", 50, 4, 1800));
        try (var scope = recorder.enter("spell", "fire", "execute")) {
            recorder.matchCall();
            clock.set(120);
        }
        var result = probe.stop("completed");
        assertTrue(result.complete());
        assertFalse(recorder.isActive());
        assertEquals("shared", result.measurements().get("captureId"));
        assertEquals(70L, result.measurements().get("elapsedNanos"));
        var rows = (List<?>) result.measurements().get("rows");
        var row = (Map<?, ?>) rows.getFirst();
        assertEquals("fire", row.get("spell"));
        assertEquals(20L, row.get("selfNanos"));
        assertEquals(Map.of("MATCH_CALLS", 1L, "INVENTORY_SLOTS", 0L), row.get("counters"));
    }

    @Test void droppedScopesMarkProviderCoverageIncomplete() {
        var recorder = new PerformanceRecorder(() -> 0, 1);
        var probe = new MagicSpellsProbe(recorder, java.util.Set.of("fire", "first", "dropped", "after-deadline"));
        probe.start(new Diagnostics.Capture("id", "now", 0, 4, 1));
        recorder.enter("spell", "first", "").close();
        recorder.enter("spell", "dropped", "").close();
        var result = probe.stop("manual_stop");
        assertFalse(result.complete());
        assertEquals(1L, result.measurements().get("droppedScopes"));
    }

    @Test void deadlineUsesTheSharedOriginAndStopsAdmissionEvenWithoutAnotherTick() {
        var clock = new AtomicLong(500_000_000L);
        var recorder = new PerformanceRecorder(clock::get, 4);
        var probe = new MagicSpellsProbe(recorder, java.util.Set.of("fire", "first", "dropped", "after-deadline"));
        probe.start(new Diagnostics.Capture("id", "now", 0, 0, 1));
        clock.set(1_000_000_000L);
        recorder.enter("spell", "after-deadline", "").close();
        recorder.matchCall();
        assertTrue(recorder.isExpired());
        assertEquals(List.of(), probe.stop("completed").measurements().get("rows"));
    }
}
