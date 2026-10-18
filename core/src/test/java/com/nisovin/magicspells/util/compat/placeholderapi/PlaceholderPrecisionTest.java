package com.nisovin.magicspells.util.compat.placeholderapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceholderPrecisionTest {

	@Test
	void nullPrecisionLeavesValueUnchanged() {
		assertEquals("1.2345", PlaceholderPrecision.apply("1.2345", null));
	}

	@Test
	void nullValueReturnsNull() {
		assertNull(PlaceholderPrecision.apply(null, "2"));
	}

	@Test
	void scalesFloatingPoint() {
		assertEquals("1.23", PlaceholderPrecision.apply("1.2345", "2"));
	}

	@Test
	void roundsHalfUp() {
		assertEquals("1.24", PlaceholderPrecision.apply("1.235", "2"));
	}

	@Test
	void nonNumericValueUnchanged() {
		assertEquals("abc", PlaceholderPrecision.apply("abc", "2"));
	}

	@Test
	void nonNumericPrecisionUnchanged() {
		assertEquals("1.5", PlaceholderPrecision.apply("1.5", "x"));
	}

}
