package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PotionIdentityTest {

	@Test
	void legacyExtendedMapsToLong() {
		assertEquals("LONG_SWIFTNESS", PotionIdentity.parse("swiftness extended"));
		assertEquals("LONG_SWIFTNESS", PotionIdentity.parse("SWIFTNESS extended"));
	}

	@Test
	void legacyUpgradedMapsToStrong() {
		assertEquals("STRONG_SWIFTNESS", PotionIdentity.parse("swiftness upgraded"));
		assertEquals("STRONG_HEALING", PotionIdentity.parse("HEALING upgraded"));
	}

	@Test
	void modernNamesPassThrough() {
		assertEquals("LONG_SWIFTNESS", PotionIdentity.parse("long_swiftness"));
		assertEquals("STRONG_HEALING", PotionIdentity.parse("STRONG_HEALING"));
		assertEquals("WATER", PotionIdentity.parse("water"));
	}

	@Test
	void legacyAndModernResolveToSameCanonical() {
		assertEquals(PotionIdentity.parse("swiftness extended"), PotionIdentity.parse("long_swiftness"));
		assertEquals(PotionIdentity.parse("poison upgraded"), PotionIdentity.parse("strong_poison"));
	}

	@Test
	void serializeEmitsLowercaseModernName() {
		assertEquals("long_swiftness", PotionIdentity.serialize("LONG_SWIFTNESS"));
		assertEquals("water", PotionIdentity.serialize(PotionIdentity.parse("water")));
	}

	@Test
	void roundTripModernSerializeThenParse() {
		String canonical = PotionIdentity.parse("swiftness extended");
		assertEquals(canonical, PotionIdentity.parse(PotionIdentity.serialize(canonical)));
	}

	@Test
	void rejectsBothFlags() {
		assertThrows(IllegalArgumentException.class, () -> PotionIdentity.parse("swiftness extended upgraded"));
	}

	@Test
	void rejectsModernWithLegacyFlag() {
		assertThrows(IllegalArgumentException.class, () -> PotionIdentity.parse("long_swiftness extended"));
		assertThrows(IllegalArgumentException.class, () -> PotionIdentity.parse("strong_poison upgraded"));
	}

	@Test
	void rejectsUnknownFlagAndEmpty() {
		assertThrows(IllegalArgumentException.class, () -> PotionIdentity.parse("swiftness mega"));
		assertThrows(IllegalArgumentException.class, () -> PotionIdentity.parse(""));
		assertThrows(IllegalArgumentException.class, () -> PotionIdentity.parse("   "));
	}

}
