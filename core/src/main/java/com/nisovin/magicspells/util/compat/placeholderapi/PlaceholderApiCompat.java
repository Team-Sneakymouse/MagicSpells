package com.nisovin.magicspells.util.compat.placeholderapi;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.events.ExpansionRegisterEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.compat.CompatBasics;

/**
 * Registers the built-in {@code %ms_...%} PlaceholderAPI expansion when PlaceholderAPI is present.
 */
public final class PlaceholderApiCompat {

	private static MagicSpellsPlaceholderExpansion expansion;
	private static Listener lifecycleListener;

	private PlaceholderApiCompat() {
	}

	public static void setup(MagicSpells plugin) {
		ensureLifecycleListener(plugin);
		if (CompatBasics.pluginEnabled("PlaceholderAPI")) register();
	}

	public static void teardown() {
		if (lifecycleListener != null) {
			HandlerList.unregisterAll(lifecycleListener);
			lifecycleListener = null;
		}
		unregister();
	}

	private static void ensureLifecycleListener(MagicSpells plugin) {
		if (lifecycleListener != null) return;
		lifecycleListener = new Listener() {
			@EventHandler
			public void onPluginEnable(PluginEnableEvent event) {
				if (!event.getPlugin().getName().equals("PlaceholderAPI")) return;
				register();
			}

			@EventHandler(priority = EventPriority.LOWEST)
			public void onExpansionRegister(ExpansionRegisterEvent event) {
				PlaceholderExpansion registering = event.getExpansion();
				if (!registering.getIdentifier().equalsIgnoreCase("ms")) return;
				if (registering instanceof MagicSpellsPlaceholderExpansion) return;
				event.setCancelled(true);
				MagicSpells.log("Blocked external PlaceholderAPI expansion 'ms'; MagicSpells provides this built-in. "
						+ "Remove the old jar from PlaceholderAPI/expansions/.");
			}
		};
		Bukkit.getPluginManager().registerEvents(lifecycleListener, plugin);
	}

	private static void register() {
		if (!CompatBasics.pluginEnabled("PlaceholderAPI")) return;

		unregisterExternalMsExpansion();

		if (expansion != null && expansion.isRegistered()) return;

		expansion = new MagicSpellsPlaceholderExpansion();
		if (expansion.register()) {
			MagicSpells.log("Registered built-in PlaceholderAPI expansion (ms).");
		} else {
			MagicSpells.error("Failed to register built-in PlaceholderAPI expansion (ms). "
					+ "Remove any old MagicSpells expansion from PlaceholderAPI/expansions/ and run /papi reload.");
			expansion = null;
		}
	}

	private static void unregisterExternalMsExpansion() {
		PlaceholderExpansion registered = PlaceholderAPIPlugin.getInstance()
				.getLocalExpansionManager()
				.getExpansion("ms");
		if (registered == null || registered instanceof MagicSpellsPlaceholderExpansion) return;
		registered.unregister();
		MagicSpells.log("Unregistered external PlaceholderAPI expansion 'ms'; using the built-in MagicSpells expansion.");
	}

	private static void unregister() {
		if (expansion == null) return;
		expansion.clear();
		if (expansion.isRegistered()) expansion.unregister();
		expansion = null;
	}

}
