package com.nisovin.magicspells.util.performance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import com.nisovin.magicspells.MagicSpells;

/** Command lifecycle and disk export; the recorder itself has no Bukkit dependency. */
public final class PerformanceDiagnostics {
    public static final PerformanceRecorder RECORDER = new PerformanceRecorder();
    private static BukkitTask stopTask;
    private static Instant started;
    private static int startTick;
    private static double startMspt;

    private PerformanceDiagnostics() {}

    public static void start(int seconds) {
        RECORDER.start(seconds);
        started = Instant.now();
        startTick = Bukkit.getCurrentTick();
        startMspt = Bukkit.getAverageTickTime();
        try {
            stopTask = Bukkit.getScheduler().runTaskTimer(MagicSpells.getInstance(), () -> {
                if (RECORDER.isExpired()) stop("timeout", false);
            }, 1, 1);
        } catch (RuntimeException e) {
            RECORDER.stop("start_failed");
            throw e;
        }
    }

    public static Path stop(String reason, boolean synchronous) {
        PerformanceRecorder.Report report = RECORDER.stop(reason);
        if (report == null) return null;
        if (stopTask != null) stopTask.cancel();
        stopTask = null;
        MagicSpells plugin = MagicSpells.getInstance();
        Path output = plugin.getDataFolder().toPath().resolve("performance")
                .resolve("perf-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".json");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 1);
        document.put("instrumentationVersion", "2026-09-13.1");
        document.put("startedAt", started.toString());
        document.put("finishedAt", Instant.now().toString());
        document.put("serverVersion", Bukkit.getVersion());
        document.put("pluginVersion", plugin.getPluginMeta().getVersion());
        document.put("serverTicks", Bukkit.getCurrentTick() - startTick);
        document.put("startMspt", startMspt);
        document.put("endMspt", Bukkit.getAverageTickTime());
        document.put("report", report);
        Runnable write = () -> {
            try {
                Files.createDirectories(output.getParent());
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(document),
                        StandardOpenOption.CREATE_NEW);
                plugin.getLogger().info("[MS-PERF] Saved " + output.toAbsolutePath());
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[MS-PERF] Cannot save " + output, e);
            }
        };
        if (synchronous) write.run();
        else Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        return output;
    }
}
