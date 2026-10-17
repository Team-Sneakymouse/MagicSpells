package com.nisovin.magicspells.util.performance;

import com.teamsneakymouse.sneakydiagnostics.api.Diagnostics;
import org.bukkit.plugin.java.JavaPlugin;

/** Loaded only when the optional SneakyDiagnostics plugin is enabled. Never shade its API. */
final class SneakyDiagnosticsHook {
    private SneakyDiagnosticsHook() {}

    static Runnable register(JavaPlugin plugin, PerformanceRecorder recorder) {
        Diagnostics diagnostics = plugin.getServer().getServicesManager().load(Diagnostics.class);
        if (diagnostics == null) {
            plugin.getLogger().warning("SneakyDiagnostics does not provide the diagnostics service; spell measurements are unavailable.");
            return () -> {};
        }
        var registration = diagnostics.register(plugin.getName(), plugin.getPluginMeta().getVersion(),
                "magicspells-execution-1", new MagicSpellsProbe(recorder));
        return registration::close;
    }
}
