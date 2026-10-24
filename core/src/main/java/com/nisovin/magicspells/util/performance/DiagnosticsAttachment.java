package com.nisovin.magicspells.util.performance;

import java.util.function.Supplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;

/**
 * Keeps this plugin's SneakyDiagnostics registration across a Diagnostics-only reload or a late enable: registers
 * again when its service appears and lets go when it disappears. No API type appears in this class (Bukkit reflects
 * over listener signatures); the API-dependent adapter is only reached through the registrar.
 */
public final class DiagnosticsAttachment implements Listener {
    private static final String SERVICE = "com.teamsneakymouse.sneakydiagnostics.api.Diagnostics";

    private final Plugin plugin;
    private final Supplier<Runnable> registrar;
    private Runnable unregister = () -> {};
    private boolean hooked;

    /** The registrar registers with the service and returns the action that unregisters; it only runs once the service exists. */
    public DiagnosticsAttachment(Plugin plugin, Supplier<Runnable> registrar) {
        this.plugin = plugin;
        this.registrar = registrar;
    }

    /** Server thread. Registers now if SneakyDiagnostics is enabled, and again whenever its service reappears. */
    public void hook() {
        if (hooked) return;
        hooked = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getServer().getPluginManager().isPluginEnabled("SneakyDiagnostics")) attach();
    }

    /** Server thread, before the measured state goes. */
    public void unhook() {
        if (!hooked) return;
        hooked = false;
        HandlerList.unregisterAll(this);
        detach();
    }

    @EventHandler public void onServiceUp(ServiceRegisterEvent event) {
        service(event.getProvider().getService().getName(), true);
    }

    @EventHandler public void onServiceDown(ServiceUnregisterEvent event) {
        service(event.getProvider().getService().getName(), false);
    }

    /** Matched by name: naming the API class here would load it, and it is absent without SneakyDiagnostics. */
    void service(String name, boolean up) {
        if (!hooked || !SERVICE.equals(name)) return;
        if (up) attach();
        else detach();
    }

    private void attach() {
        detach();
        try {
            unregister = registrar.get();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Couldn't register with SneakyDiagnostics (" + e + "); its captures won't include " + plugin.getName() + ".");
        }
    }

    private void detach() {
        Runnable release = unregister;
        unregister = () -> {};
        try {
            release.run();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Couldn't leave SneakyDiagnostics: " + e);
        }
    }
}
