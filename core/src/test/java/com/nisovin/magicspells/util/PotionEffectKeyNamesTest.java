package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PotionEffectKeyNamesTest {

	@Test
	void blankIsNull() {
		assertNull(PotionEffectKeyNames.toNamespacedKeyString(null));
		assertNull(PotionEffectKeyNames.toNamespacedKeyString(""));
		assertNull(PotionEffectKeyNames.toNamespacedKeyString("   "));
	}

	@Test
	void bareNameGetsMinecraftNamespace() {
		assertEquals("minecraft:speed", PotionEffectKeyNames.toNamespacedKeyString("SPEED"));
		assertEquals("minecraft:slowness", PotionEffectKeyNames.toNamespacedKeyString("slowness"));
	}

	@Test
	void alreadyNamespacedPassesThroughLowercased() {
		assertEquals("minecraft:speed", PotionEffectKeyNames.toNamespacedKeyString("Minecraft:Speed"));
	}

}
