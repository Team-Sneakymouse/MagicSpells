package com.nisovin.magicspells.util;

import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;

/**
 * Resolve {@link Sound} from config tokens without {@code Sound.valueOf}.
 * Accepts {@code minecraft:entity.item.pickup}, {@code entity.item.pickup},
 * and legacy enum-style {@code ENTITY_ITEM_PICKUP}.
 */
public final class SoundUtil {

	private SoundUtil() {
	}

	public static Sound resolve(String name) {
		String keyString = SoundKeyNames.toNamespacedKeyString(name);
		if (keyString == null) return null;
		NamespacedKey key = NamespacedKey.fromString(keyString);
		if (key == null) return null;
		return Registry.SOUNDS.get(key);
	}

}
