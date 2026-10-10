package com.nisovin.magicspells.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Parses attribute config lines without Bukkit.
 * Format: {@code <attribute> <amount> [operation] [slot]} — operation defaults to {@code add_number}.
 */
public final class AttributeSpecParser {

	private AttributeSpecParser() {
	}

	/**
	 * @param requireOperation when true, at least three tokens are required (AttributeManager legacy)
	 */
	public static AttributeSpec parse(String line, boolean requireOperation) {
		Objects.requireNonNull(line, "line");
		String trimmed = line.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("attribute line is empty");
		}

		String[] args = trimmed.split("\\s+");
		if (requireOperation) {
			if (args.length < 3) {
				throw new IllegalArgumentException("attribute line needs name, amount, and operation");
			}
		} else if (args.length < 2) {
			throw new IllegalArgumentException("attribute line needs name and amount");
		}

		double amount;
		try {
			amount = Double.parseDouble(args[1]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("invalid attribute amount '" + args[1] + "'", e);
		}

		String operationRaw = args.length >= 3 ? args[2] : AttributeOperationNames.ADD_NUMBER;
		String operation = AttributeOperationNames.canonicalize(operationRaw);
		if (operation == null) {
			throw new IllegalArgumentException("unknown attribute operation '" + operationRaw + "'");
		}

		String slot = args.length >= 4 ? args[3] : null;
		return new AttributeSpec(args[0], amount, operation, slot);
	}

	public static AttributeSpec parse(String line) {
		return parse(line, false);
	}

	public static UUID stableUuid(String sourceKey, int index) {
		String key = (sourceKey == null ? "attributes" : sourceKey) + ":" + index;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Namespaced-key path segment for modern {@code AttributeModifier} keys ({@code [a-z0-9._-]}).
	 */
	public static String stableKeyPath(String sourceKey, int index) {
		UUID uuid = stableUuid(sourceKey, index);
		return ("attr-" + uuid).toLowerCase(Locale.ROOT);
	}

}
