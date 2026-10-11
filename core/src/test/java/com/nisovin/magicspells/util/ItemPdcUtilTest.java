package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;

import com.nisovin.magicspells.util.ItemPdcKeys.Slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemPdcUtilTest {

	@Test
	void parseSlotAliases() {
		assertEquals(Slot.CAST, Slot.parse("cast"));
		assertEquals(Slot.MAINHAND, Slot.parse("mainhand"));
		assertEquals(Slot.MAINHAND, Slot.parse("main-hand"));
		assertEquals(Slot.MAINHAND, Slot.parse("hand"));
		assertEquals(Slot.OFFHAND, Slot.parse("offhand"));
		assertEquals(Slot.OFFHAND, Slot.parse("off-hand"));
		assertNull(Slot.parse("inventory"));
		assertNull(Slot.parse(null));
	}

	@Test
	void toPdcKeyNameRegularAndPermanent() {
		assertEquals(ItemPdcKeys.REGULAR_PREFIX + "charges", ItemPdcKeys.toPdcKeyName("charges"));
		assertEquals(ItemPdcKeys.PERMANENT_PREFIX + "id", ItemPdcKeys.toPdcKeyName("permanent:id"));
		assertEquals(ItemPdcKeys.PERMANENT_PREFIX + "id", ItemPdcKeys.toPdcKeyName("permanent-data:id"));
		assertEquals(ItemPdcKeys.PERMANENT_PREFIX + "id", ItemPdcKeys.toPdcKeyName("pdc-permanent:id"));
		assertNull(ItemPdcKeys.toPdcKeyName(null));
		assertNull(ItemPdcKeys.toPdcKeyName(""));
	}

	@Test
	void stripQuotes() {
		assertEquals("lit", ItemPdcKeys.stripQuotes("\"lit\""));
		assertEquals("plain", ItemPdcKeys.stripQuotes("plain"));
		assertNull(ItemPdcKeys.stripQuotes(null));
	}

}
