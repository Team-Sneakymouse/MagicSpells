package com.nisovin.magicspells.util.performance;

import com.teamsneakymouse.sneakydiagnostics.api.Diagnostics;
import com.nisovin.magicspells.MagicSpells;
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
                "magicspells-execution-3", new MagicSpellsProbe(recorder, MagicSpells.getSpells().values().stream()
                        .map(spell -> spell.getInternalName()).collect(java.util.stream.Collectors.toSet()),
                        PerformanceDiagnostics.TASKS, () -> pending(plugin)));
        return registration::close;
    }

    private static Iterable<TaskLifecycle.Pending> pending(JavaPlugin plugin) {
        // The public API materializes all scheduler tasks. Do not allocate another full list.
        var tasks = plugin.getServer().getScheduler().getPendingTasks();
        return () -> tasks.stream().filter(task -> task.getOwner() == plugin && !task.isCancelled())
                .map(task -> new TaskLifecycle.Pending(task.getTaskId(), task.isSync())).iterator();
    }
}
