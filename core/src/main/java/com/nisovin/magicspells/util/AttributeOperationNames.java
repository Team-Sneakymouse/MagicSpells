package com.nisovin.magicspells.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bukkit-free aliases for {@code AttributeModifier.Operation} config tokens.
 */
public final class AttributeOperationNames {

	public static final String ADD_NUMBER = "add_number";
	public static final String ADD_SCALAR = "add_scalar";
	public static final String MULTIPLY_SCALAR_1 = "multiply_scalar_1";

	private static final Map<String, String> ALIASES;

	static {
		Map<String, String> map = new HashMap<>();
		put(map, ADD_NUMBER, "add_number", "addnumber");
		put(map, ADD_SCALAR, "add_scalar", "addscalar");
		put(map, MULTIPLY_SCALAR_1, "multiply_scalar_1", "multiplyscalar1", "multiply_scalar", "multiplyscalar");
		ALIASES = Collections.unmodifiableMap(map);
	}

	private AttributeOperationNames() {
	}

	private static void put(Map<String, String> map, String canonical, String... aliases) {
		map.put(canonical, canonical);
		for (String alias : aliases) {
			map.put(alias, canonical);
		}
	}

	/**
	 * @return canonical operation name, or {@code null} if unknown
	 */
	public static String canonicalize(String operation) {
		if (operation == null || operation.isBlank()) return null;
		return ALIASES.get(operation.trim().toLowerCase(Locale.ROOT));
	}

}
