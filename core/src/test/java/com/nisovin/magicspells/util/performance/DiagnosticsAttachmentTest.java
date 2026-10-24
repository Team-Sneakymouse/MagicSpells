package com.nisovin.magicspells.util.performance;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsAttachmentTest {
    private static final String SERVICE = "com.teamsneakymouse.sneakydiagnostics.api.Diagnostics";

    private static final class World {
        boolean diagnosticsEnabled;
        int listenersRegistered, registered, unregistered;
        RuntimeException failure;
        final PluginManager manager = proxy(PluginManager.class, (p, m, a) -> switch (m.getName()) {
            case "isPluginEnabled" -> diagnosticsEnabled;
            case "registerEvents" -> { listenersRegistered++; yield null; }
            default -> fallback(m.getReturnType());
        });
        final Server server = proxy(Server.class, (p, m, a) -> m.getName().equals("getPluginManager") ? manager : fallback(m.getReturnType()));
        final Plugin plugin = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getServer" -> server;
            case "getName" -> "Test";
            case "isEnabled" -> true;
            case "getLogger" -> Logger.getAnonymousLogger();
            default -> fallback(m.getReturnType());
        });

        DiagnosticsAttachment attachment() {
            return new DiagnosticsAttachment(plugin, () -> {
                if (failure != null) throw failure;
                registered++;
                return () -> unregistered++;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object fallback(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    @Test void registersAtOnceWhenDiagnosticsIsAlreadyEnabled() {
        var world = new World();
        world.diagnosticsEnabled = true;
        world.attachment().hook();
        assertEquals(1, world.listenersRegistered);
        assertEquals(1, world.registered);
    }

    @Test void waitsForTheServiceWhenDiagnosticsIsNotEnabled() {
        var world = new World();
        var attachment = world.attachment();
        attachment.hook();
        assertEquals(0, world.registered);
        attachment.service(SERVICE, true);
        assertEquals(1, world.registered);
    }

    @Test void reRegistersAfterADiagnosticsOnlyReloadAndReleasesTheOldRegistrationFirst() {
        var world = new World();
        world.diagnosticsEnabled = true;
        var attachment = world.attachment();
        attachment.hook();
        attachment.service(SERVICE, false);
        assertEquals(1, world.unregistered);
        attachment.service(SERVICE, true);
        assertEquals(2, world.registered);
        attachment.service(SERVICE, true);
        assertEquals(3, world.registered);
        assertEquals(2, world.unregistered, "a repeated announcement releases the previous registration before registering again");
    }

    @Test void ignoresOtherServices() {
        var world = new World();
        var attachment = world.attachment();
        attachment.hook();
        attachment.service("net.milkbowl.vault.economy.Economy", true);
        attachment.service("net.milkbowl.vault.economy.Economy", false);
        assertEquals(0, world.registered);
        assertEquals(0, world.unregistered);
    }

    @Test void survivesAFailingRegistrarAndAttachesOnTheNextAnnouncement() {
        var world = new World();
        world.diagnosticsEnabled = true;
        world.failure = new IllegalStateException("Provider already registered");
        var attachment = world.attachment();
        assertDoesNotThrow(attachment::hook);
        world.failure = null;
        attachment.service(SERVICE, true);
        assertEquals(1, world.registered);
    }

    @Test void unhookReleasesAndIgnoresLaterAnnouncements() {
        var world = new World();
        world.diagnosticsEnabled = true;
        var attachment = world.attachment();
        attachment.hook();
        attachment.unhook();
        assertEquals(1, world.unregistered);
        attachment.service(SERVICE, true);
        assertEquals(1, world.registered, "a stale announcement after unhook must not register");
        attachment.unhook();
        assertEquals(1, world.unregistered, "unhook is idempotent");
    }

    @Test void hookTwiceRegistersTheListenerOnce() {
        var world = new World();
        world.diagnosticsEnabled = true;
        var attachment = world.attachment();
        attachment.hook();
        attachment.hook();
        assertEquals(1, world.listenersRegistered);
        assertEquals(1, world.registered);
    }
}
