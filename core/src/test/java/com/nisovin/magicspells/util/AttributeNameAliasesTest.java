package com.nisovin.magicspells.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeNameAliasesTest {

	@Test
	void legacyGenericDotResolvesToBareKey() {
		List<String> c = AttributeNameAliases.candidates("generic.attack_damage");
		assertTrue(c.contains("generic.attack_damage"));
		assertTrue(c.contains("attack_damage"));
	}

	@Test
	void legacyGenericUnderscoreResolvesToBareKey() {
		List<String> c = AttributeNameAliases.candidates("generic_attack_damage");
		assertTrue(c.contains("attack_damage"));
		assertTrue(c.contains("generic.attack_damage"));
	}

	@Test
	void bareModernKeyAlsoOffersLegacyForms() {
		List<String> c = AttributeNameAliases.candidates("attack_damage");
		assertTrue(c.contains("attack_damage"));
		assertTrue(c.contains("generic.attack_damage"));
		assertTrue(c.contains("generic_attack_damage"));
	}

	@Test
	void movementSpeedAliases() {
		List<String> c = AttributeNameAliases.candidates("generic.movement_speed");
		assertTrue(c.contains("movement_speed"));
	}

}
