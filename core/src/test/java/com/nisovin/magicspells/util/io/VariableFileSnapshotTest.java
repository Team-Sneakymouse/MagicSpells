package com.nisovin.magicspells.util.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VariableFileSnapshotTest {
	@TempDir Path directory;

	@Test
	void newlineEncodingMatchesPreAsyncPersistence() throws Exception {
		Path target = directory.resolve("GLOBAL.txt");
		String value = "first\nsecond\\nthird";
		new VariableFileSnapshot(target, null, false, Map.of("text", value), Set.of()).write();
		assertEquals("text=" + value.replaceAll("\n", "\\n") + System.lineSeparator(), Files.readString(target));
	}

	@Test
	void playerPatchPrefersLegacyAndPreservesUnloadedValues() throws Exception {
		Path legacy = directory.resolve("PLAYER_name.txt");
		Path uuid = directory.resolve("PLAYER_uuid.txt");
		Files.writeString(legacy, "unknown=keep\ncharacter:other=17\nreset=9\ncoins=1\n");
		Files.writeString(uuid, "coins=100\n");
		var snapshot = new VariableFileSnapshot(uuid, legacy, true,
				Map.of("coins", "2", "character:current", "hello=world\nnext"), Set.of("reset"));
		snapshot.write();
		assertEquals("character:current=hello=worldnnext\ncharacter:other=17\ncoins=2\nunknown=keep\n",
				Files.readString(legacy).replace(System.lineSeparator(), "\n"));
		assertEquals("coins=100\n", Files.readString(uuid));
	}
	@Test
	void builderTransfersImmutableSnapshotAndPreservesLocationFormat() throws Exception {
		Path target = directory.resolve("LOC_zone.txt");
		var builder = new VariableFileSnapshot.Builder(target, null, false, false);
		builder.put("world,1,2,3", "4.5");
		var snapshot = builder.build();
		assertThrows(IllegalStateException.class, () -> builder.put("world,0,0,0", "9"));
		snapshot.write();
		assertEquals("world,1,2,3=4.5" + System.lineSeparator(), Files.readString(target));
	}

	@Test
	void unreadableMergeSourceIsNotReplacedWithPartialSnapshot() throws Exception {
		Path target = Files.createDirectory(directory.resolve("PLAYER_uuid.txt"));
		var snapshot = new VariableFileSnapshot(target, null, true, Map.of("coins", "2"), Set.of());
		assertThrows(java.io.IOException.class, snapshot::write);
		assertTrue(Files.isDirectory(target));
	}

	@Test
	void publicConstructorDetachesMutableInput() throws Exception {
		Path target = directory.resolve("GLOBAL.txt");
		var values = new java.util.HashMap<String, String>();
		values.put("value", "1");
		var snapshot = new VariableFileSnapshot(target, null, false, values, Set.of());
		values.put("value", "2");
		snapshot.write();
		assertEquals("value=1" + System.lineSeparator(), Files.readString(target));
	}

}
