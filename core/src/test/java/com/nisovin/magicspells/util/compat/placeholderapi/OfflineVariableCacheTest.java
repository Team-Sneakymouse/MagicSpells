package com.nisovin.magicspells.util.compat.placeholderapi;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfflineVariableCacheTest {

	private final OfflineVariableCache cache = new OfflineVariableCache();
	private final UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

	@Test
	void firstRequestIsMissThenFill() {
		assertNull(cache.get(uuid, "mana"));
		assertTrue(cache.beginFill(uuid));
		assertFalse(cache.beginFill(uuid));
		cache.putAll(uuid, Map.of("mana", "12"));
		assertEquals("12", cache.get(uuid, "mana"));
		assertEquals("", cache.get(uuid, "missing"));
		assertFalse(cache.isInflight(uuid));
	}

	@Test
	void dropOnJoinClears() {
		cache.putAll(uuid, Map.of("mana", "12"));
		cache.drop(uuid);
		assertNull(cache.get(uuid, "mana"));
		assertFalse(cache.has(uuid));
	}

	@Test
	void refreshAfterQuitReplacesValues() {
		cache.putAll(uuid, Map.of("mana", "1"));
		cache.drop(uuid);
		assertTrue(cache.beginFill(uuid));
		cache.putAll(uuid, Map.of("mana", "99", "title", "hero"));
		assertEquals("99", cache.get(uuid, "mana"));
		assertEquals("hero", cache.get(uuid, "title"));
	}

	@Test
	void stringifyConvertsNumbers() {
		Map<String, String> values = OfflineVariableCache.stringify(Map.of("a", 1.5, "b", "x"));
		assertEquals("1.5", values.get("a"));
		assertEquals("x", values.get("b"));
	}

}
