package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SoundKeyNamesTest {

	@Test
	void blankIsNull() {
		assertNull(SoundKeyNames.toNamespacedKeyString(null));
		assertNull(SoundKeyNames.toNamespacedKeyString(""));
		assertNull(SoundKeyNames.toNamespacedKeyString("   "));
	}

	@Test
	void legacyEnumStyleBecomesMinecraftDotted() {
		assertEquals("minecraft:entity.item.pickup",
			SoundKeyNames.toNamespacedKeyString("ENTITY_ITEM_PICKUP"));
	}

	@Test
	void dottedPathGetsMinecraftNamespace() {
		assertEquals("minecraft:entity.item.pickup",
			SoundKeyNames.toNamespacedKeyString("entity.item.pickup"));
	}

	@Test
	void alreadyNamespacedPassesThroughLowercased() {
		assertEquals("minecraft:block.note_block.harp",
			SoundKeyNames.toNamespacedKeyString("Minecraft:Block.Note_Block.Harp"));
	}

}
