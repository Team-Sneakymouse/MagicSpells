package com.nisovin.magicspells.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bukkit-free alias expansion for attribute config tokens.
 * Modern Paper keys are bare ({@code attack_damage}); older configs still use
 * {@code generic.attack_damage} / {@code generic_attack_damage} / {@code GENERIC_ATTACK_DAMAGE}.
 */
public final class AttributeNameAliases {

	private AttributeNameAliases() {
	}

	/**
	 * Returns lookup candidates for a config token (lowercased, first is the raw form).
	 */
	public static List<String> candidates(String attribute) {
		if (attribute == null || attribute.isBlank()) return List.of();

		String raw = attribute.trim().toLowerCase(Locale.ROOT);
		Set<String> out = new LinkedHashSet<>();
		out.add(raw);
		out.add(raw.replace('-', '_'));
		out.add(raw.replace('_', '.'));
		out.add(raw.replace('.', '_'));
		out.add(raw.replace("_", ""));
		out.add(raw.replace(".", "").replace("_", ""));

		if (raw.startsWith("generic.")) {
			String stripped = raw.substring("generic.".length());
			out.add(stripped);
			out.add(stripped.replace('.', '_'));
			out.add(stripped.replace("_", ""));
		}
		if (raw.startsWith("generic_")) {
			String stripped = raw.substring("generic_".length());
			out.add(stripped);
			out.add("generic." + stripped);
			out.add(stripped.replace('_', '.'));
			out.add(stripped.replace("_", ""));
		}

		// Bare modern key → legacy generic forms (for reverse registration).
		if (!raw.contains(".") && !raw.startsWith("generic")) {
			out.add("generic." + raw);
			out.add("generic_" + raw);
		}

		return new ArrayList<>(out);
	}

}
