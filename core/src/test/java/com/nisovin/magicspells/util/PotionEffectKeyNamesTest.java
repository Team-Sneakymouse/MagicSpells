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
		assertNull(PotionEffectKeyNames.canonicalizePath(null));
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

	@Test
	void legacyBukkitNamesMapToModernKeys() {
		assertEquals("slowness", PotionEffectKeyNames.canonicalizePath("slow"));
		assertEquals("instant_health", PotionEffectKeyNames.canonicalizePath("heal"));
		assertEquals("instant_damage", PotionEffectKeyNames.canonicalizePath("harm"));
		assertEquals("haste", PotionEffectKeyNames.canonicalizePath("fast_digging"));
		assertEquals("mining_fatigue", PotionEffectKeyNames.canonicalizePath("slow_digging"));
		assertEquals("strength", PotionEffectKeyNames.canonicalizePath("increase_damage"));
		assertEquals("jump_boost", PotionEffectKeyNames.canonicalizePath("jump"));
		assertEquals("nausea", PotionEffectKeyNames.canonicalizePath("confusion"));
		assertEquals("resistance", PotionEffectKeyNames.canonicalizePath("damage_resistance"));
	}

	@Test
	void legacyNamespacedKeysAreCanonicalized() {
		assertEquals("minecraft:slowness", PotionEffectKeyNames.toNamespacedKeyString("minecraft:slow"));
		assertEquals("minecraft:instant_health", PotionEffectKeyNames.toNamespacedKeyString("HEAL"));
	}

	@Test
	void modernNamesUnchanged() {
		assertEquals("slowness", PotionEffectKeyNames.canonicalizePath("slowness"));
		assertEquals("instant_health", PotionEffectKeyNames.canonicalizePath("instant_health"));
	}

}
