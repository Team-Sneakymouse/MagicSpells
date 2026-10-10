package com.nisovin.magicspells.util;

import java.util.Locale;

/**
 * Bukkit-free potion-effect name → {@code namespace:path} shaping for registry lookup.
 */
public final class PotionEffectKeyNames {

	private PotionEffectKeyNames() {
	}

	/**
	 * @return namespaced key string, or {@code null} if blank
	 */
	public static String toNamespacedKeyString(String name) {
		if (name == null || name.isBlank()) return null;
		String trimmed = name.trim().toLowerCase(Locale.ROOT);
		if (trimmed.contains(":")) return trimmed;
		return "minecraft:" + trimmed.replace(' ', '_');
	}

}
