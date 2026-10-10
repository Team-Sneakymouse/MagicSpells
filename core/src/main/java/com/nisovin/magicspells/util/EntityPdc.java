package com.nisovin.magicspells.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Entity Persistent Data Container helpers, plus ephemeral object attachment
 * keyed by entity UUID (PDC cannot store arbitrary Java objects).
 * <p>
 * Keys use the {@code magicspells} namespace (plugin.yml name, lowercased) so
 * static holders do not need {@code MagicSpells.getInstance()} at class-init.
 */
public final class EntityPdc {

	private static final String NAMESPACE = "magicspells";

	private EntityPdc() {
	}

	/**
	 * Builds a PDC key. Names are lowercased and non-{@code [a-z0-9_-./]} chars
	 * become {@code _} so spell internal names stay valid.
	 */
	public static NamespacedKey key(String name) {
		String sanitized = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.\\-/]", "_");
		return new NamespacedKey(NAMESPACE, sanitized);
	}

	public static boolean hasFlag(Entity entity, NamespacedKey key) {
		return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
	}

	public static void setFlag(Entity entity, NamespacedKey key) {
		entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
	}

	public static void remove(Entity entity, NamespacedKey key) {
		entity.getPersistentDataContainer().remove(key);
	}

	public static void setString(Entity entity, NamespacedKey key, String value) {
		entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
	}

	public static String getString(Entity entity, NamespacedKey key) {
		return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
	}

	public static void setDouble(Entity entity, NamespacedKey key, double value) {
		entity.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
	}

	public static Double getDouble(Entity entity, NamespacedKey key) {
		return entity.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
	}

	public static void setFloat(Entity entity, NamespacedKey key, float value) {
		entity.getPersistentDataContainer().set(key, PersistentDataType.FLOAT, value);
	}

	public static Float getFloat(Entity entity, NamespacedKey key) {
		return entity.getPersistentDataContainer().get(key, PersistentDataType.FLOAT);
	}

	public static void setByte(Entity entity, NamespacedKey key, byte value) {
		entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, value);
	}

	public static Byte getByte(Entity entity, NamespacedKey key) {
		return entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
	}

	/**
	 * In-memory store for values that cannot live in PDC (object graphs, spell refs).
	 * Presence is mirrored with a PDC flag so {@link #has} works after restarts
	 * only for the flag — attached objects are always session-local.
	 */
	public static final class ObjectStore<T> {

		private final NamespacedKey flagKey;
		private final Map<UUID, T> values = new ConcurrentHashMap<>();

		public ObjectStore(String flagKeyName) {
			this.flagKey = key(flagKeyName);
		}

		public boolean has(Entity entity) {
			return hasFlag(entity, flagKey);
		}

		public T get(Entity entity) {
			return values.get(entity.getUniqueId());
		}

		public void set(Entity entity, T value) {
			values.put(entity.getUniqueId(), value);
			setFlag(entity, flagKey);
		}

		public T computeIfAbsent(Entity entity, Function<UUID, T> mappingFunction) {
			T value = values.computeIfAbsent(entity.getUniqueId(), mappingFunction);
			setFlag(entity, flagKey);
			return value;
		}

		public void remove(Entity entity) {
			values.remove(entity.getUniqueId());
			EntityPdc.remove(entity, flagKey);
		}

		public void clear() {
			values.clear();
		}

	}

}
