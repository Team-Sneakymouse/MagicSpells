package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColorUtilTest {

	@Test
	void translateAmpersandCodes() {
		assertEquals(ColorUtil.COLOR_CHAR + "aHello",
			ColorUtil.translateAlternateColorCodes('&', "&aHello"));
	}

	@Test
	void translateLeavesHexAmpersandForColorize() {
		assertEquals("&#aabbcc", ColorUtil.translateAlternateColorCodes('&', "&#aabbcc"));
	}

	@Test
	void colorizeExpandsHex() {
		String out = ColorUtil.colorize("&#aabbcc");
		assertEquals(
			"" + ColorUtil.COLOR_CHAR + 'x'
				+ ColorUtil.COLOR_CHAR + 'a' + ColorUtil.COLOR_CHAR + 'a'
				+ ColorUtil.COLOR_CHAR + 'b' + ColorUtil.COLOR_CHAR + 'b'
				+ ColorUtil.COLOR_CHAR + 'c' + ColorUtil.COLOR_CHAR + 'c',
			out);
	}

	@Test
	void colorizeAndDecolorizeRoundTripPlain() {
		assertEquals("Hello", ColorUtil.decolorize("&aHello&r"));
	}

	@Test
	void sectionCodeFromConfigFallsBack() {
		assertEquals(ColorUtil.DARK_AQUA, ColorUtil.sectionCodeFromConfig("3", '3'));
		assertEquals(ColorUtil.GREEN, ColorUtil.sectionCodeFromConfig("", 'a'));
		assertEquals(ColorUtil.GREEN, ColorUtil.sectionCodeFromConfig("zz", 'a'));
	}

	@Test
	void miniMessageTags() {
		assertEquals("underlined", ColorUtil.miniMessageTagFor('n'));
		assertEquals("green", ColorUtil.miniMessageTagFor('a'));
		assertNull(ColorUtil.miniMessageTagFor('z'));
	}

	@Test
	void stripColorRemovesCodes() {
		assertEquals("Hi", ColorUtil.stripColor(ColorUtil.RED + "Hi" + ColorUtil.RESET));
	}

}
