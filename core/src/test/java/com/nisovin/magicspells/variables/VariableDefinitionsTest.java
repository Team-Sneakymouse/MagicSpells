package com.nisovin.magicspells.variables;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import com.sneakyvariables.api.Kind;
import com.sneakyvariables.api.Scope;
import com.sneakyvariables.api.VariableDefinition;

import static org.junit.jupiter.api.Assertions.*;

class VariableDefinitionsTest {

	@Test
	void mapsStoredTypes() {
		assertEquals(Scope.PLAYER, VariableDefinitions.from("a", "player", section()).scope());
		assertEquals(Kind.NUMBER, VariableDefinitions.from("a", "player", section()).kind());

		assertEquals(Scope.PLAYER, VariableDefinitions.from("a", "playerstring", section()).scope());
		assertEquals(Kind.STRING, VariableDefinitions.from("a", "playerstring", section()).kind());

		assertEquals(Scope.CHARACTER, VariableDefinitions.from("a", "character", section()).scope());
		assertEquals(Kind.STRING, VariableDefinitions.from("a", "characterstring", section()).kind());

		assertEquals(Scope.GLOBAL, VariableDefinitions.from("a", "global", section()).scope());
		assertEquals(Kind.STRING, VariableDefinitions.from("a", "globalstring", section()).kind());

		assertEquals(Kind.BLOCK_NUMBER, VariableDefinitions.from("a", "location", section()).kind());
		assertEquals(Scope.GLOBAL, VariableDefinitions.from("a", "location", section()).scope());
		assertEquals(Kind.BLOCK_STRING, VariableDefinitions.from("a", "locationstring", section()).kind());

		assertEquals(Scope.PLAYER, VariableDefinitions.from("a", "playerlocation", section()).scope());
		assertEquals(Kind.BLOCK_NUMBER, VariableDefinitions.from("a", "playerlocation", section()).kind());
		assertEquals(Kind.BLOCK_STRING, VariableDefinitions.from("a", "playerlocationstring", section()).kind());

		assertEquals(Scope.CHARACTER, VariableDefinitions.from("a", "characterlocation", section()).scope());
		assertEquals(Kind.BLOCK_STRING, VariableDefinitions.from("a", "characterlocationstring", section()).kind());
	}

	@Test
	void returnsNullForComputedAndUnknown() {
		assertNull(VariableDefinitions.from("a", "distancetolocation", section()));
		assertNull(VariableDefinitions.from("a", "squareddistancetolocation", section()));
		assertNull(VariableDefinitions.from("a", "meta_location_x", section()));
		assertNull(VariableDefinitions.from("a", "nope", section()));
		assertNull(VariableDefinitions.from("a", null, section()));
	}

	@Test
	void defaultsAndPermanent() {
		ConfigurationSection section = section();
		section.set("default", 3.5);
		section.set("default-value", "hello");
		section.set("permanent", false);

		VariableDefinition number = VariableDefinitions.from("n", "player", section);
		assertEquals(3.5, number.defaultNumber());
		assertFalse(number.persistent());

		VariableDefinition string = VariableDefinitions.from("s", "playerstring", section);
		assertEquals("hello", string.defaultString());
		assertFalse(string.persistent());

		VariableDefinition permanentDefault = VariableDefinitions.from("p", "global", section());
		assertTrue(permanentDefault.persistent());
		assertEquals(0, permanentDefault.defaultNumber());
	}

	@Test
	void constantBoundsUseStaticValues() {
		ConfigurationSection section = section();
		section.set("min", "1.5");
		section.set("max", "9");

		VariableDefinition def = VariableDefinitions.from("n", "global", section);
		assertEquals(1.5, def.min());
		assertEquals(9.0, def.max());
		assertNull(def.minFunction());
		assertNull(def.maxFunction());
	}

	@Test
	void expressionBoundsKeepMagicSpellsDefaultsAndFunctions() {
		ConfigurationSection section = section();
		// "power" is a built-in FunctionData variable, so this builds without a live server.
		section.set("min", "power");
		section.set("max", "power*2");

		VariableDefinition def = VariableDefinitions.from("n", "player", section);
		assertEquals(VariableDefinitions.DEFAULT_MIN, def.min());
		assertEquals(VariableDefinitions.DEFAULT_MAX, def.max());
		assertNotNull(def.minFunction());
		assertNotNull(def.maxFunction());
	}

	@Test
	void missingBoundsUseMagicSpellsDefaults() {
		VariableDefinition def = VariableDefinitions.from("n", "global", section());
		assertEquals(0.0, def.min());
		assertEquals(Double.MAX_VALUE, def.max());
	}

	private static ConfigurationSection section() {
		return new MemoryConfiguration();
	}

}
