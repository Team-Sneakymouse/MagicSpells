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
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import com.nisovin.magicspells.MagicSpells;

/** Command lifecycle and disk export; the recorder itself has no Bukkit dependency. */
public final class PerformanceDiagnostics {
    private static final PerformanceRecorder RECORDER = new PerformanceRecorder();
    public static final PerformanceExecution EXECUTION = new PerformanceExecution(RECORDER);
    private static final PerformanceExports EXPORTS = new PerformanceExports();
    private static BukkitTask stopTask;
    private static Instant started;
    private static int startTick;
    private static double startMspt;
    private static Audience recipient;

    private PerformanceDiagnostics() {}

    public static boolean isActive() { return RECORDER.isActive(); }

    public static void start(int seconds) {
        start(seconds, Bukkit.getConsoleSender());
    }

    public static void start(int seconds, Audience audience) {
        java.util.Objects.requireNonNull(audience, "audience");
        RECORDER.start(seconds);
        recipient = audience;
        started = Instant.now();
        startTick = Bukkit.getCurrentTick();
        startMspt = Bukkit.getAverageTickTime();
        try {
            if (stopTask == null) stopTask = Bukkit.getScheduler().runTaskTimer(MagicSpells.getInstance(), () -> {
                if (RECORDER.isExpired()) stop("timeout", false);
                deliverNotifications();
            }, 1, 1);
        } catch (RuntimeException e) {
            RECORDER.stop("start_failed");
            recipient = null;
            throw e;
        }
    }

    public static Path stop(String reason, boolean synchronous) {
        PerformanceRecorder.Report report = RECORDER.stop(reason);
        if (report == null) {
            if (synchronous) {
                EXPORTS.flush();
                deliverNotifications();
            }
            return null;
        }
        Audience captureRecipient = recipient;
        recipient = null;
        MagicSpells plugin = MagicSpells.getInstance();
        Path output = plugin.getDataFolder().toPath().resolve("performance")
                .resolve("perf-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".json");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 2);
        document.put("instrumentationVersion", "2026-09-13.2");
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
                notifyRecipient(plugin, captureRecipient, "[MS-PERF] Saved " + output.toAbsolutePath());
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[MS-PERF] Cannot save " + output, e);
                notifyRecipient(plugin, captureRecipient, "[MS-PERF] Cannot save " + output + "; see console for details.");
            }
        };
        if (synchronous) {
            try { write.run(); } finally {
                EXPORTS.flush();
                deliverNotifications();
            }
        } else EXPORTS.submit(write, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        return output;
    }

    private static void notifyRecipient(MagicSpells plugin, Audience audience, String message) {
        EXPORTS.enqueueNotification(() -> {
            try {
                audience.sendMessage(Component.text(message));
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[MS-PERF] Cannot notify capture initiator", e);
            }
        });
    }

    private static void deliverNotifications() {
        EXPORTS.deliverNotifications();
        if (!RECORDER.isActive() && EXPORTS.isIdle() && stopTask != null) {
            stopTask.cancel();
            stopTask = null;
        }
    }
}
