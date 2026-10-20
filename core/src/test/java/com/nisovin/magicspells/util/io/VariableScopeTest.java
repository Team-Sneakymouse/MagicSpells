package com.nisovin.magicspells.util.io;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VariableScopeTest {
	@Test
	void namedScopesHaveDistinctIdentitiesAndRejectInvalidCombinations() {
		assertEquals(VariableScope.player("Alice"), VariableScope.player("Alice"));
		assertNotEquals(VariableScope.player("Alice"), VariableScope.player("Bob"));
		assertNotEquals(VariableScope.global(), VariableScope.locations());
		assertThrows(NullPointerException.class, () -> VariableScope.player(null));
		assertThrows(NullPointerException.class, () -> new VariableScope(null, null));
		assertThrows(IllegalArgumentException.class, () -> new VariableScope(VariableScope.Kind.GLOBAL, "Alice"));
	}
}
