package com.nisovin.magicspells.util.performance;

import org.bukkit.plugin.java.JavaPlugin;

/** Ownership is always available; SneakyDiagnostics owns collection and report writing. */
public final class PerformanceDiagnostics {
    private static final PerformanceRecorder RECORDER = new PerformanceRecorder();
    public static final PerformanceExecution EXECUTION = new PerformanceExecution(RECORDER);
    public static final TaskLifecycle TASKS = new TaskLifecycle();
    private static DiagnosticsAttachment attachment;

    private PerformanceDiagnostics() {}

    public static void register(JavaPlugin plugin) {
        unregister();
        // The adapter that needs the API is only reached once SneakyDiagnostics' service exists, and again after
        // a Diagnostics-only reload or a late enable.
        attachment = new DiagnosticsAttachment(plugin, () -> SneakyDiagnosticsHook.register(plugin, RECORDER));
        attachment.hook();
    }

    public static void unregister() {
        try { if (attachment != null) attachment.unhook(); }
        finally {
            attachment = null;
            RECORDER.stop("plugin_unload");
        }
    }
}
