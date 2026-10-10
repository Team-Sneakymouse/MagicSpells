package com.nisovin.magicspells.util;

import java.util.Locale;
import java.util.Objects;

/**
 * Bukkit-free sound name → {@code namespace:path} shaping for {@link SoundUtil}.
 */
public final class SoundKeyNames {

	private SoundKeyNames() {
	}

	/**
	 * @return namespaced key string ({@code namespace:path}), or {@code null} if blank/invalid
	 */
	public static String toNamespacedKeyString(String name) {
		if (name == null || name.isBlank()) return null;
		String trimmed = name.trim();
		if (trimmed.contains(":")) {
			return trimmed.toLowerCase(Locale.ROOT);
		}
		if (trimmed.contains(".")) {
			return "minecraft:" + trimmed.toLowerCase(Locale.ROOT);
		}
		return "minecraft:" + trimmed.toLowerCase(Locale.ROOT).replace('_', '.');
	}

	public static String requireNamespacedKeyString(String name) {
		String key = toNamespacedKeyString(name);
		Objects.requireNonNull(key, "sound name");
		return key;
	}

}
