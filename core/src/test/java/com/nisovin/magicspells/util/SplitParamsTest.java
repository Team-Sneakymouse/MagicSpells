package com.nisovin.magicspells.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SplitParamsTest {

	@Test
	void splitsOnWhitespace() {
		assertArrayEquals(new String[]{"a", "b", "c"}, ParamUtil.splitParams("a b c"));
	}

	@Test
	void doubleQuotesKeepInternalSpaces() {
		assertArrayEquals(
			new String[]{"spell", "hello world", "baz"},
			ParamUtil.splitParams(new String[]{"spell", "\"hello", "world\"", "baz"})
		);
	}

	@Test
	void singleQuotesKeepInternalSpaces() {
		assertArrayEquals(
			new String[]{"spell", "hello world"},
			ParamUtil.splitParams("spell 'hello world'")
		);
	}

	@Test
	void stripsQuotesFromSingleTokenArgument() {
		assertArrayEquals(new String[]{"spell", "hello"}, ParamUtil.splitParams("spell \"hello\""));
	}

	@Test
	void emptyQuotedArgument() {
		assertArrayEquals(new String[]{"spell", ""}, ParamUtil.splitParams("spell \"\""));
	}

	@Test
	void powerFlagWithQuotedArg() {
		assertArrayEquals(
			new String[]{"spell", "-p:2", "foo bar"},
			ParamUtil.splitParams("spell -p:2 \"foo bar\"")
		);
	}

	@Test
	void maxConsumesRemainderUnparsed() {
		assertArrayEquals(
			new String[]{"anvil", "\"diamond sword\" any"},
			ParamUtil.splitParams("anvil \"diamond sword\" any", 2)
		);
	}

	@Test
	void unclosedQuoteTakesRemainder() {
		assertArrayEquals(
			new String[]{"spell", "hello world"},
			ParamUtil.splitParams("spell \"hello world")
		);
	}

	@Test
	void apostropheInsideUnquotedWordIsLiteral() {
		assertArrayEquals(new String[]{"don't", "touch"}, ParamUtil.splitParams("don't touch"));
	}

	@Test
	void emptyAndBlankInput() {
		assertArrayEquals(new String[]{""}, ParamUtil.splitParams(""));
		assertArrayEquals(new String[]{""}, ParamUtil.splitParams("   "));
	}

}
