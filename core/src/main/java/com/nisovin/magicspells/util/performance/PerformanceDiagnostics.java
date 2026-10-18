package com.nisovin.magicspells.util.performance;

import org.bukkit.plugin.java.JavaPlugin;

/** Ownership is always available; SneakyDiagnostics owns collection and report writing. */
public final class PerformanceDiagnostics {
    private static final PerformanceRecorder RECORDER = new PerformanceRecorder();
    public static final PerformanceExecution EXECUTION = new PerformanceExecution(RECORDER);
    public static final TaskLifecycle TASKS = new TaskLifecycle();
    private static Runnable unregister = () -> {};

    private PerformanceDiagnostics() {}

    public static void register(JavaPlugin plugin) {
        unregister();
        // Keep the API-dependent adapter unloaded when diagnostics is absent.
        if (plugin.getServer().getPluginManager().isPluginEnabled("SneakyDiagnostics"))
            unregister = SneakyDiagnosticsHook.register(plugin, RECORDER);
    }

    public static void unregister() {
        try { unregister.run(); }
        finally {
            unregister = () -> {};
            RECORDER.stop("plugin_unload");
        }
    }
}
