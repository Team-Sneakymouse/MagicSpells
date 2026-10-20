package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterVariableValuesTest {
	@Test
	void lookupsCopiesAndOrderedPlayerCursorAgreeAfterUpdatesAndRemovals() {
		var values = new CharacterVariableValues<Integer>();
		assertThrows(IllegalArgumentException.class, () -> values.put("malformed", 1));
		assertTrue(values.copy().isEmpty());
		values.put("Alice:b", 2);
		values.put("Alice:a", 1);
		values.put("AliceOther:a", 99);
		values.put("Alice:b", 3);
		assertEquals(3, values.get("Alice:b"));
		assertEquals(7, values.getOrDefault("missing", 7));
		var copy = values.copy();
		assertEquals("Alice:a", values.next("Alice", null).getKey());
		assertEquals(1, values.remove("Alice:a"));
		assertEquals("Alice:b", values.next("Alice", "Alice:a").getKey());
		assertEquals(3, values.remove("Alice:b"));
		assertNull(values.next("Alice", null));
		assertNull(values.remove("Alice:b"));
		values.put("Alice:c", 4);
		assertEquals("Alice:c", values.next("Alice", "Alice:b").getKey());
		assertNull(values.next("Alice", "Alice:c"));
		assertEquals(1, copy.get("Alice:a"));
		assertEquals(99, values.get("AliceOther:a"));
	}
}
