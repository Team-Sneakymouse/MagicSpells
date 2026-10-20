package com.nisovin.magicspells.util.io;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DirtyVariableScopesTest {
	@Test
	void acknowledgingSnapshotDoesNotClearMutationDuringCapture() {
		var dirty = new DirtyVariableScopes<String>();
		dirty.markDirty("global");
		long captured = dirty.generation("global");
		dirty.markDirty("global");
		dirty.acknowledge("global", captured);
		assertTrue(dirty.contains("global"));
		dirty.acknowledge("global", dirty.generation("global"));
		assertFalse(dirty.contains("global"));
	}
}
