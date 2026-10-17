package com.nisovin.magicspells.util.compat.placeholderapi;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Precision formatting for PlaceholderAPI values.
 * Ported from JasperLorelai/Expansion-MagicSpells (GPL-3.0).
 */
public final class PlaceholderPrecision {

	private PlaceholderPrecision() {
	}

	public static String apply(String value, String precision) {
		if (precision == null || value == null) return value;
		try {
			float floatValue = Float.parseFloat(value);
			int scale = Integer.parseInt(precision);
			return BigDecimal.valueOf(floatValue).setScale(scale, RoundingMode.HALF_UP).toString();
		} catch (NumberFormatException ignored) {
			return value;
		}
	}

}
