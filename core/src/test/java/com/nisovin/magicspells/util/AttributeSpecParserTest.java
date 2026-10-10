package com.nisovin.magicspells.util;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeSpecParserTest {

	@Test
	void parsesNameAmountWithDefaultOperation() {
		AttributeSpec spec = AttributeSpecParser.parse("generic_attack_damage 2.5");
		assertEquals("generic_attack_damage", spec.attributeName());
		assertEquals(2.5, spec.amount());
		assertEquals(AttributeOperationNames.ADD_NUMBER, spec.operationName());
		assertNull(spec.slotName());
	}

	@Test
	void parsesOperationAliasesAndSlot() {
		AttributeSpec spec = AttributeSpecParser.parse("scale 0.1 multiplyscalar hand");
		assertEquals(AttributeOperationNames.MULTIPLY_SCALAR_1, spec.operationName());
		assertEquals("hand", spec.slotName());
	}

	@Test
	void requireOperationNeedsThreeTokens() {
		assertThrows(IllegalArgumentException.class,
			() -> AttributeSpecParser.parse("scale 1", true));
		AttributeSpec spec = AttributeSpecParser.parse("scale 1 add_number", true);
		assertEquals(AttributeOperationNames.ADD_NUMBER, spec.operationName());
	}

	@Test
	void rejectsUnknownOperationAndBadAmount() {
		assertThrows(IllegalArgumentException.class,
			() -> AttributeSpecParser.parse("scale 1 not_an_op"));
		assertThrows(IllegalArgumentException.class,
			() -> AttributeSpecParser.parse("scale nope add_number"));
	}

	@Test
	void stableUuidIsDeterministicPerSourceAndIndex() {
		UUID a = AttributeSpecParser.stableUuid("spell.foo", 0);
		UUID b = AttributeSpecParser.stableUuid("spell.foo", 0);
		UUID c = AttributeSpecParser.stableUuid("spell.foo", 1);
		assertEquals(a, b);
		assertTrue(!a.equals(c));
	}

	@Test
	void stableKeyPathIsNamespacedKeySafe() {
		String path = AttributeSpecParser.stableKeyPath("Spell.Name", 2);
		assertTrue(path.matches("[a-z0-9._-]+"));
		assertTrue(path.startsWith("attr-"));
	}

}
