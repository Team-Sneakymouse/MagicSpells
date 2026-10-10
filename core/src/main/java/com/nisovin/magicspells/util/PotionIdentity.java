package com.nisovin.magicspells.util;

import java.util.Locale;
import java.util.Objects;

/**
 * Bukkit-free potion-data grammar for magic items / cast items.
 * Accepts legacy {@code TYPE [extended|upgraded]} and modern {@code LONG_*} / {@code STRONG_*}
 * names; always canonicalizes to an uppercase {@code PotionType}-style name.
 */
public final class PotionIdentity {

	private PotionIdentity() {
	}

	/**
	 * Parses a potion-data config string into a canonical uppercase type name.
	 *
	 * @throws IllegalArgumentException if the input is blank, has invalid flags, or mixes
	 *                                  modern prefixes with legacy flags
	 */
	public static String parse(String input) {
		Objects.requireNonNull(input, "input");
		String trimmed = input.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("potion-data is empty");
		}

		String[] args = trimmed.split("\\s+");
		String type = args[0].toUpperCase(Locale.ROOT);
		boolean modern = type.startsWith("LONG_") || type.startsWith("STRONG_");

		boolean extended = false;
		boolean upgraded = false;
		if (args.length > 1) {
			String flag = args[1];
			if (flag.equalsIgnoreCase("extended")) {
				extended = true;
			} else if (flag.equalsIgnoreCase("upgraded")) {
				upgraded = true;
			} else {
				throw new IllegalArgumentException("unknown potion-data flag '" + flag + "'");
			}
		}
		if (args.length > 2) {
			throw new IllegalArgumentException("potion-data has too many tokens");
		}
		if (extended && upgraded) {
			throw new IllegalArgumentException("potion-data cannot be both extended and upgraded");
		}
		if (modern && (extended || upgraded)) {
			throw new IllegalArgumentException("modern potion type cannot use extended/upgraded flags");
		}
		if (extended) {
			return "LONG_" + type;
		}
		if (upgraded) {
			return "STRONG_" + type;
		}
		return type;
	}

	/**
	 * Serializes a canonical type name for config / magic-item strings (lowercase, no flags).
	 */
	public static String serialize(String canonicalTypeName) {
		Objects.requireNonNull(canonicalTypeName, "canonicalTypeName");
		String trimmed = canonicalTypeName.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("canonical type name is empty");
		}
		return trimmed.toLowerCase(Locale.ROOT);
	}

}
