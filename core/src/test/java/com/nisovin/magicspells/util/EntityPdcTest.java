package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bukkit-free helpers that mirror EntityPdc / LightningSpell packing.
 * Live cover: {@code MANUAL-TEST-PDC.md}.
 */
class EntityPdcTest {

	@Test
	void lightningChargeBitsPackAsExpected() {
		// bit0 = charge creepers, bit1 = zap pigs
		byte both = pack(true, true);
		byte neither = pack(false, false);
		byte creepersOnly = pack(true, false);
		byte pigsOnly = pack(false, true);

		assertEquals(3, both);
		assertEquals(0, neither);
		assertEquals(1, creepersOnly);
		assertEquals(2, pigsOnly);
		assertEquals(1, both & 1);
		assertEquals(0, neither & 1);
		assertEquals(2, both & 2);
		assertEquals(0, neither & 2);
	}

	@Test
	void sanitizeKeyLowercasesAndReplacesIllegalChars() {
		assertEquals("msfirework", sanitize("MSFirework"));
		assertEquals("lightning_pdc_lightning_safe", sanitize("lightning_pdc_lightning_safe"));
		assertEquals("bow-draw-strength", sanitize("bow-draw-strength"));
		assertEquals("spell_with_spaces", sanitize("Spell With Spaces"));
	}

	/** Mirrors {@link EntityPdc#key(String)} sanitization without loading Bukkit. */
	private static String sanitize(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.\\-/]", "_");
	}

	private static byte pack(boolean chargeCreepers, boolean zapPigs) {
		return (byte) ((chargeCreepers ? 1 : 0) | (zapPigs ? 2 : 0));
	}

}
