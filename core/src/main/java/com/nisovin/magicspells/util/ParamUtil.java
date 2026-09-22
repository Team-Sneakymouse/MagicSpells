package com.nisovin.magicspells.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Whitespace splitting with optional quote-delimited arguments.
 * Kept free of Bukkit so it can be unit-tested in isolation.
 */
public final class ParamUtil {

	private ParamUtil() {
	}

	/**
	 * Splits a parameter string on whitespace, treating {@code "} or {@code '} as text
	 * qualifiers: spaces inside a matched quote pair belong to that argument, and the
	 * quote characters themselves are not included in the result.
	 *
	 * @param string input to split
	 * @param max    maximum number of arguments; if {@code > 0}, the last argument
	 *               consumes the remainder of the input (quotes in that remainder are
	 *               not specially parsed)
	 */
	public static String[] splitParams(String string, int max) {
		if (string == null) return new String[]{""};

		String trimmed = string.trim();
		// Match String.split on empty input: a single empty element (keeps cast-command checks safe).
		if (trimmed.isEmpty()) return new String[]{""};

		List<String> list = new ArrayList<>();
		StringBuilder building = new StringBuilder();
		char quote = 0;
		boolean inArg = false;

		int length = trimmed.length();
		for (int i = 0; i < length; i++) {
			char c = trimmed.charAt(i);

			if (max > 0 && list.size() == max - 1) {
				if (!inArg) {
					// Skip leading whitespace before the final remainder arg.
					if (c == ' ' || c == '\t') continue;
					inArg = true;
				}
				building.append(trimmed.substring(i));
				break;
			}

			if (quote != 0) {
				if (c == quote) {
					quote = 0;
					list.add(building.toString());
					building.setLength(0);
					inArg = false;
				} else {
					building.append(c);
				}
				continue;
			}

			if (c == ' ' || c == '\t') {
				if (inArg) {
					list.add(building.toString());
					building.setLength(0);
					inArg = false;
				}
				continue;
			}

			if (!inArg && (c == '"' || c == '\'')) {
				quote = c;
				inArg = true;
				continue;
			}

			building.append(c);
			inArg = true;
		}

		if (inArg || quote != 0) list.add(building.toString());

		return list.toArray(new String[0]);
	}

	public static String[] splitParams(String string) {
		return splitParams(string, 0);
	}

	public static String[] splitParams(String[] split, int max) {
		return splitParams(join(split), max);
	}

	public static String[] splitParams(String[] split) {
		return splitParams(join(split), 0);
	}

	private static String join(String[] array) {
		if (array == null || array.length == 0) return "";
		StringBuilder sb = new StringBuilder(array[0]);
		for (int i = 1; i < array.length; i++) {
			sb.append(' ');
			sb.append(array[i]);
		}
		return sb.toString();
	}

}
