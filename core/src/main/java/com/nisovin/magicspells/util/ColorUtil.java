package com.nisovin.magicspells.util;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Color;

import com.nisovin.magicspells.handlers.DebugHandler;

/**
 * Color helpers: Bukkit {@code Color} parsing plus legacy section-sign formatting
 * without {@code org.bukkit.ChatColor}.
 */
public class ColorUtil {

	/** Legacy formatting character ({@code §}). */
	public static final char COLOR_CHAR = '\u00A7';

	public static final Pattern HEX_PATTERN = Pattern.compile("&(#\\w{6})");

	private static final String ALL_CODES = "0123456789abcdefklmnorx";
	private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-ORX]");

	public static final String BLACK = sectionCode('0');
	public static final String DARK_BLUE = sectionCode('1');
	public static final String DARK_GREEN = sectionCode('2');
	public static final String DARK_AQUA = sectionCode('3');
	public static final String DARK_RED = sectionCode('4');
	public static final String DARK_PURPLE = sectionCode('5');
	public static final String GOLD = sectionCode('6');
	public static final String GRAY = sectionCode('7');
	public static final String DARK_GRAY = sectionCode('8');
	public static final String BLUE = sectionCode('9');
	public static final String GREEN = sectionCode('a');
	public static final String AQUA = sectionCode('b');
	public static final String RED = sectionCode('c');
	public static final String LIGHT_PURPLE = sectionCode('d');
	public static final String YELLOW = sectionCode('e');
	public static final String WHITE = sectionCode('f');
	public static final String RESET = sectionCode('r');

	private ColorUtil() {
	}

	public static String sectionCode(char code) {
		return COLOR_CHAR + String.valueOf(Character.toLowerCase(code));
	}

	/**
	 * Parse a single-character config color ({@code "3"}, {@code "a"}) into a section code
	 * ({@code §3}). Falls back to {@code defaultCode} when missing or invalid.
	 */
	public static String sectionCodeFromConfig(String value, char defaultCode) {
		Character parsed = parseColorChar(value);
		return sectionCode(parsed != null ? parsed : defaultCode);
	}

	public static Character parseColorChar(String value) {
		if (value == null || value.isEmpty()) return null;
		char c = Character.toLowerCase(value.charAt(0));
		if (ALL_CODES.indexOf(c) < 0) return null;
		return c;
	}

	/** MiniMessage tag for a legacy color/format code char, or {@code null} if unknown. */
	public static String miniMessageTagFor(char code) {
		return switch (Character.toLowerCase(code)) {
			case '0' -> "black";
			case '1' -> "dark_blue";
			case '2' -> "dark_green";
			case '3' -> "dark_aqua";
			case '4' -> "dark_red";
			case '5' -> "dark_purple";
			case '6' -> "gold";
			case '7' -> "gray";
			case '8' -> "dark_gray";
			case '9' -> "blue";
			case 'a' -> "green";
			case 'b' -> "aqua";
			case 'c' -> "red";
			case 'd' -> "light_purple";
			case 'e' -> "yellow";
			case 'f' -> "white";
			case 'k' -> "obfuscated";
			case 'l' -> "bold";
			case 'm' -> "strikethrough";
			case 'n' -> "underlined";
			case 'o' -> "italic";
			case 'r' -> "reset";
			default -> null;
		};
	}

	/**
	 * Same rules as Bukkit {@code ChatColor.translateAlternateColorCodes}: replace
	 * {@code altColorChar} + valid code with section-sign form. Does not touch {@code &#rrggbb}.
	 */
	public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
		if (textToTranslate == null || textToTranslate.isEmpty()) return textToTranslate;
		char[] b = textToTranslate.toCharArray();
		for (int i = 0; i < b.length - 1; i++) {
			if (b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
				b[i] = COLOR_CHAR;
				b[i + 1] = Character.toLowerCase(b[i + 1]);
			}
		}
		return new String(b);
	}

	public static String stripColor(String input) {
		if (input == null) return null;
		return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
	}

	/** Translate {@code &} codes and expand {@code &#rrggbb} into weird-hex section form. */
	public static String colorize(String string) {
		if (string == null || string.isEmpty()) return string == null ? null : "";

		Matcher matcher = HEX_PATTERN.matcher(translateAlternateColorCodes('&', string));
		StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			String code = matcher.group(1);

			StringBuilder magic = new StringBuilder();
			magic.append(COLOR_CHAR).append('x');
			for (int i = 1; i < code.length(); i++) magic.append(COLOR_CHAR).append(code.charAt(i));

			matcher.appendReplacement(builder, Matcher.quoteReplacement(magic.toString()));
		}

		return matcher.appendTail(builder).toString();
	}

	public static String decolorize(String string) {
		if (string == null || string.isEmpty()) return string == null ? null : "";
		return stripColor(colorize(string));
	}

	public static Color getColorFromHexString(String hex, boolean debug) {
		if (hex == null) return null;

		String working = hex.replace("#", "");
		try {
			int value = Integer.parseInt(working, 16);
			return Color.fromRGB(value);
		} catch (IllegalArgumentException e) {
			if (debug) DebugHandler.debugIllegalArgumentException(e);
			return null;
		}
	}

	public static Color getColorFromHexString(String hex) {
		return getColorFromHexString(hex, true);
	}

	public static Color getColorFromARGHexString(String hex, boolean debug) {
		if (hex == null) return null;

		if (hex.startsWith("#")) hex = hex.substring(1);

		try {
			int value = new BigInteger(hex, 16).intValue();
			return Color.fromARGB(value);
		} catch (IllegalArgumentException e) {
			if (debug) DebugHandler.debugIllegalArgumentException(e);
			return null;
		}
	}

	public static Color getColorFromRGBString(String value) {
		if (value == null) return null;
		String[] splits = value.split(",");
		if (splits.length < 3) return null;

		int red;
		int green;
		int blue;
		try {
			red = Integer.parseInt(splits[0]);
			green = Integer.parseInt(splits[1]);
			blue = Integer.parseInt(splits[2]);
			return Color.fromRGB(red, green, blue);
		} catch (IllegalArgumentException e) {
			DebugHandler.debugIllegalArgumentException(e);
			return null;
		}

	}

	/** Resolve a GlowSpell-style enum name ({@code RED}, {@code dark_aqua}) to a section code. */
	public static String sectionCodeFromName(String name, String fallback) {
		if (name == null || name.isEmpty()) return fallback;
		String key = name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		return switch (key) {
			case "black" -> BLACK;
			case "dark_blue" -> DARK_BLUE;
			case "dark_green" -> DARK_GREEN;
			case "dark_aqua" -> DARK_AQUA;
			case "dark_red" -> DARK_RED;
			case "dark_purple" -> DARK_PURPLE;
			case "gold" -> GOLD;
			case "gray", "grey" -> GRAY;
			case "dark_gray", "dark_grey" -> DARK_GRAY;
			case "blue" -> BLUE;
			case "green" -> GREEN;
			case "aqua" -> AQUA;
			case "red" -> RED;
			case "light_purple" -> LIGHT_PURPLE;
			case "yellow" -> YELLOW;
			case "white" -> WHITE;
			default -> fallback;
		};
	}

}
