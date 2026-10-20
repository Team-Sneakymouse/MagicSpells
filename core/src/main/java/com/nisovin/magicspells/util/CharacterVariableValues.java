package com.nisovin.magicspells.util;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/** Hash lookups for gameplay, plus a player-scoped ordered key index for resumable persistence. */
public final class CharacterVariableValues<T> {
	private final Map<String, T> values = new HashMap<>();
	private final Map<String, NavigableSet<String>> playerKeys = new HashMap<>();

	public T get(String key) { return values.get(key); }
	public T getOrDefault(String key, T defaultValue) { return values.getOrDefault(key, defaultValue); }

	public void put(String key, T value) {
		if (!values.containsKey(key)) playerKeys.computeIfAbsent(player(key), ignored -> new TreeSet<>()).add(key);
		values.put(key, value);
	}

	public T remove(String key) {
		if (!values.containsKey(key)) return null;
		String player = player(key);
		NavigableSet<String> keys = playerKeys.get(player);
		keys.remove(key);
		if (keys.isEmpty()) playerKeys.remove(player);
		return values.remove(key);
	}

	public Map<String, T> copy() { return new HashMap<>(values); }

	/** A seek, not a fail-fast iterator. Unrelated players and structural mutations do not invalidate it. */
	public Map.Entry<String, T> next(String player, String previous) {
		NavigableSet<String> keys = playerKeys.get(player);
		if (keys == null) return null;
		String key = previous == null ? keys.first() : keys.higher(previous);
		return key == null ? null : Map.entry(key, values.get(key));
	}

	private static String player(String key) {
		int colon = key.indexOf(':');
		if (colon < 0) throw new IllegalArgumentException("Character key requires player and character");
		return key.substring(0, colon);
	}
}
