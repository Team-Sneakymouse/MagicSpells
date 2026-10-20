package com.nisovin.magicspells.util.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.nisovin.magicspells.util.BlockLocation;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VariableSnapshotCaptureTest {
	@TempDir Path directory;

	@Test
	void incompleteCaptureCannotEscapeToWriter() {
		var capture = VariableSnapshotCapture.global(directory.toFile(), Map.of());
		assertThrows(IllegalStateException.class, capture::write);
		assertTrue(capture.advance());
		assertNotNull(capture.write());
		assertThrows(IllegalStateException.class, capture::advance);
	}
	@Test
	void globalRoundTripDropsDefaultsAndRestoresNumericAndStringValues() throws Exception {
		var number = new GlobalVariable();
		number.setPermanent(true);
		number.set("", 4.5);
		var text = new GlobalStringVariable();
		text.setPermanent(true);
		text.setDefaultStringValue("");
		text.parseAndSet("", "hello=world\nnext");
		var defaulted = new GlobalVariable();
		defaulted.setPermanent(true);
		var transientValue = new GlobalVariable();
		transientValue.set("", 99);
		writeCapture("global", null, Map.of("number", number, "text", text,
				"defaulted", defaulted, "temporary", transientValue));
		String saved = Files.readString(directory.resolve("GLOBAL.txt"));
		assertEquals("number=4.5\ntext=hello=worldnnext\n", saved.replace(System.lineSeparator(), "\n"));
		var restored = new GlobalVariable();
		restored.parseAndSet("", saved.lines().findFirst().orElseThrow().split("=", 2)[1]);
		assertEquals(4.5, restored.getValue(""));
	}

	@Test
	void playerAndCharacterPatchKeepsUnknownAndUnloadedCharacters() throws Exception {
		var character = new CharacterVariable();
		character.setPermanent(true);
		character.setWithCompositeKey("Alice:current", 8);
		character.setWithCompositeKey("Bob:other", 999);
		var text = new CharacterStringVariable();
		text.setPermanent(true);
		text.setStringWithCompositeKey("Alice:current", "hello=world");
		var reset = new PlayerVariable();
		reset.setPermanent(true);
		Path file = directory.resolve("PLAYER_uuid.txt");
		Files.writeString(file, "coins=4\nunknown=keep\nstat:unloaded=17\n");
		writeCapture("player", "Alice", Map.of("stat", character, "story", text, "coins", reset));
		assertEquals("stat:current=8.0\nstat:unloaded=17\nstory:current=hello=world\nunknown=keep\n",
				Files.readString(file).replace(System.lineSeparator(), "\n"));
		var restored = new CharacterVariable();
		restored.setWithCompositeKey("Alice:current", Double.parseDouble(Files.readAllLines(file).getFirst().split("=", 2)[1]));
		assertEquals(8.0, restored.getAllValues().get("Alice:current"));
	}

	@Test
	void locationCaptureIsAmortizedAndDetachedFromMutableRegions() throws Exception {
		var locations = new LocationVariable();
		locations.setPermanent(true);
		for (int i = 0; i < 600; i++) locations.defineRegion(Set.of(new BlockLocation("world", i, 2, 3)), 4.5);
		var text = new LocationStringVariable();
		text.setPermanent(true);
		text.defineRegion(Set.of(new BlockLocation("world", 1, 2, 3)), "label=value");
		var capture = VariableSnapshotCapture.locations(directory.toFile(),
				Map.of("zone", locations, "labels", text));
		assertFalse(capture.advance());
		assertFalse(capture.advance());
		assertTrue(capture.advance());
		var snapshot = capture.write();
		locations.getBlockMap().values().forEach(region -> region.setValue(99.0));
		snapshot.run();
		List<String> lines = Files.readAllLines(directory.resolve("LOC_zone.txt"));
		assertEquals(600, lines.size());
		assertTrue(lines.contains("world,0,2,3=4.5"));
		assertTrue(lines.contains("world,599,2,3=4.5"));
		assertEquals("world,1,2,3=label=value" + System.lineSeparator(), Files.readString(directory.resolve("LOC_labels.txt")));
		var restored = new LocationVariable();
		for (String line : lines) {
			String[] pair = line.split("=", 2);
			restored.defineRegion(Set.of(BlockLocation.fromString(pair[0])), Double.parseDouble(pair[1]));
		}
		assertEquals(4.5, restored.getBlockMap().get(new BlockLocation("world", 599, 2, 3)).getValue());
	}

	@Test
	void mutationDuringAmortizedCaptureRemainsDirtyAfterWrite() throws Exception {
		var locations = new LocationVariable();
		locations.setPermanent(true);
		for (int i = 0; i < 600; i++) locations.defineRegion(Set.of(new BlockLocation("world", i, 2, 3)), 1);
		var dirty = new DirtyVariableScopes<String>();
		dirty.markDirty("location");
		long generation = dirty.generation("location");
		var capture = VariableSnapshotCapture.locations(directory.toFile(), Map.of("zone", locations));
		assertFalse(capture.advance());
		locations.getBlockMap().values().forEach(region -> region.setValue(2.0));
		dirty.markDirty("location");
		while (!capture.advance()) { }
		try (var writer = new OrderedVariableWriter()) {
			assertTrue(writer.submit(capture.write()));
			assertNull(writer.flush().failure());
			dirty.acknowledge("location", generation);
			assertTrue(dirty.contains("location"));
		}
	}

	@Test
	void playerCaptureVisitsOnlyThatPlayersHistory() throws Exception {
		var number = new CharacterVariable();
		number.setPermanent(true);
		var text = new CharacterStringVariable();
		text.setPermanent(true);
		number.setWithCompositeKey("Alice:current", 7);
		text.setStringWithCompositeKey("Alice:current", "story");
		for (int i = 0; i < 10000; i++) {
			number.setWithCompositeKey("Bob:" + i, 99);
			text.setStringWithCompositeKey("AliceOther:" + i, "unrelated");
		}
		var capture = VariableSnapshotCapture.player("Alice", "uuid", directory.toFile(), Map.of("n", number, "s", text));
		assertTrue(capture.advance()); // Old cross-player scan cannot finish within one 256-entry slice.
		capture.write().run();
		assertEquals("n:current=7.0\ns:current=story\n",
				Files.readString(directory.resolve("PLAYER_uuid.txt")).replace(System.lineSeparator(), "\n"));
	}

	@Test
	void characterCursorSurvivesStructuralChangesBetweenSlices() throws Exception {
		var character = new CharacterVariable();
		character.setPermanent(true);
		for (int i = 0; i < 600; i++) character.setWithCompositeKey("Alice:" + String.format("%04d", i), 1);
		var capture = VariableSnapshotCapture.player("Alice", "uuid", directory.toFile(), Map.of("stat", character));
		assertFalse(capture.advance());
		character.setWithCompositeKey("Bob:new", 99);
		character.setWithCompositeKey("Alice:zzzz", 2);
		assertFalse(capture.advance());
		assertTrue(capture.advance());
		capture.write().run();
		List<String> saved = Files.readAllLines(directory.resolve("PLAYER_uuid.txt"));
		assertEquals(601, saved.size());
		assertTrue(saved.contains("stat:zzzz=2.0"));
	}

	private void writeCapture(String kind, String player, Map<String, Variable> variables) throws Exception {
		var capture = switch (kind) {
			case "global" -> VariableSnapshotCapture.global(directory.toFile(), variables);
			case "player" -> VariableSnapshotCapture.player(player, "uuid", directory.toFile(), variables);
			default -> VariableSnapshotCapture.locations(directory.toFile(), variables);
		};
		while (!capture.advance()) { }
		try (var writer = new OrderedVariableWriter()) {
			assertTrue(writer.submit(capture.write()));
			assertNull(writer.flush().failure());
		}
	}

}
