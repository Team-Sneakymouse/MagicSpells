package com.nisovin.magicspells.util.compat.placeholderapi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Async-filled cache of offline player variable values for PlaceholderAPI
 * {@code offlinevar}. First miss returns empty and the caller fills asynchronously;
 * join drops the entry; quit refreshes it.
 */
public final class OfflineVariableCache {

	private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
	private final Map<UUID, Boolean> inflight = new ConcurrentHashMap<>();

	public boolean has(UUID uuid) {
		return cache.containsKey(uuid);
	}

	public boolean isInflight(UUID uuid) {
		return inflight.containsKey(uuid);
	}

	/** Marks a fill in progress; returns false if one is already running. */
	public boolean beginFill(UUID uuid) {
		return inflight.putIfAbsent(uuid, Boolean.TRUE) == null;
	}

	public void endFill(UUID uuid) {
		inflight.remove(uuid);
	}

	public String get(UUID uuid, String variableName) {
		Map<String, String> values = cache.get(uuid);
		if (values == null) return null;
		return values.getOrDefault(variableName, "");
	}

	public void putAll(UUID uuid, Map<String, String> values) {
		cache.put(uuid, new HashMap<>(values));
		inflight.remove(uuid);
	}

	public void drop(UUID uuid) {
		cache.remove(uuid);
		inflight.remove(uuid);
	}

	public void clear() {
		cache.clear();
		inflight.clear();
	}

	public static Map<String, String> stringify(Map<String, Object> values) {
		Map<String, String> out = new HashMap<>();
		if (values == null) return out;
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			Object value = entry.getValue();
			out.put(entry.getKey(), value == null ? "" : String.valueOf(value));
		}
		return out;
	}

}
