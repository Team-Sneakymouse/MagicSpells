package com.nisovin.magicspells.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.nisovin.magicspells.util.ItemPdcKeys.Slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-arg parsing for slotted PDC modifier ConditionVars (no Bukkit).
 */
class ItemPdcConditionArgsTest {

	private static final Pattern EXPRESSION_MATCHER =
			Pattern.compile("((?:%..*%|[^%<>:=]*)*)([<>:=])((?:%..*%|[^%<>:=]*)*)");

	@Test
	void hasItemPdcShape() {
		assertTrue(parseHas("cast;charges"));
		assertTrue(parseHas("mainhand;permanent:id"));
		assertFalse(parseHas("cast"));
		assertFalse(parseHas("bag;charges"));
	}

	@Test
	void equalsContainsShape() {
		assertTrue(parseThree("cast;charges;\"3\""));
		assertTrue(parseThree("offhand;name;%var:x%"));
		assertFalse(parseThree("cast;charges"));
	}

	@Test
	void expressionShape() {
		assertNotNull(parseExpression("cast;charges>3"));
		assertEquals("charges", parseExpression("cast;charges>3")[0]);
		assertEquals(">", parseExpression("cast;charges>3")[1]);
		assertEquals("3", parseExpression("cast;charges>3")[2]);
		assertNull(parseExpression("cast;charges"));
		assertNull(parseExpression("nope;charges>3"));
	}

	private static boolean parseHas(String var) {
		String[] parts = var.split(";", 2);
		if (parts.length != 2) return false;
		return Slot.parse(parts[0]) != null && !parts[1].trim().isEmpty();
	}

	private static boolean parseThree(String var) {
		String[] parts = var.split(";", 3);
		if (parts.length != 3) return false;
		return Slot.parse(parts[0]) != null && !parts[1].trim().isEmpty() && !parts[2].trim().isEmpty();
	}

	private static String[] parseExpression(String var) {
		String[] parts = var.split(";", 2);
		if (parts.length != 2 || Slot.parse(parts[0]) == null) return null;
		Matcher matcher = EXPRESSION_MATCHER.matcher(parts[1].trim());
		if (!matcher.matches()) return null;
		String key = matcher.group(1).trim();
		if (key.isEmpty()) return null;
		return new String[]{key, matcher.group(2), matcher.group(3).trim()};
	}

}
