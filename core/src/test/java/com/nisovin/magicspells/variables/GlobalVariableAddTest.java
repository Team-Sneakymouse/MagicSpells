package com.nisovin.magicspells.variables;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalVariableAddTest {

	@Test
	void onlyAddUsesAtomicAdd() {
		assertTrue(GlobalVariableAdd.shouldUseAdd("ADD"));
		assertFalse(GlobalVariableAdd.shouldUseAdd("SET"));
		assertFalse(GlobalVariableAdd.shouldUseAdd("MULTIPLY"));
		assertFalse(GlobalVariableAdd.shouldUseAdd("DIVIDE"));
		assertFalse(GlobalVariableAdd.shouldUseAdd("MODULO"));
		assertFalse(GlobalVariableAdd.shouldUseAdd("POWER"));
		assertFalse(GlobalVariableAdd.shouldUseAdd("RANDOM"));
		assertFalse(GlobalVariableAdd.shouldUseAdd(null));
	}

}
