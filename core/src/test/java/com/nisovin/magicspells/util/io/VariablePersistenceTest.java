package com.nisovin.magicspells.util.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import com.nisovin.magicspells.util.BlockLocation;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.GlobalVariable;
import com.nisovin.magicspells.variables.variabletypes.LocationVariable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Tests drive the same coordinator used by VariableManager, with real capture and atomic file writes. */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class VariablePersistenceTest {
	@TempDir Path directory;
	private final ManualExecutor storage = new ManualExecutor();
	private final List<String> failures = new ArrayList<>();
	private final GlobalVariable number = number();
	private final LocationVariable locations = locations();
	private final Map<String, Variable> variables = Map.of("number", number, "zone", locations);

	@Test
	void unrelatedJoinNeitherWaitsForStorageNorCancelsLocationCapture() throws Exception {
		try (var persistence = coordinator()) {
			persistence.markDirty(VariableScope.locations());
			persistence.requestSave(VariableScope.locations());
			persistence.tick(); // Select the capture.
			for (int i = 0; i < 3; i++) {
				assertTrue(persistence.prepareLoad(VariableScope.player("joining")));
				persistence.tick(); // 600 entries still finish despite a join between every slice.
			}
			assertEquals(1, storage.work.size());
			assertTrue(persistence.prepareLoad(VariableScope.player("another")));
			assertFalse(Files.exists(directory.resolve("LOC_zone.txt"))); // Storage is still deliberately delayed.
			assertEquals(1, storage.work.size());
			storage.runNext();
			persistence.tick();
			assertFalse(persistence.isDirty(VariableScope.locations()));
			assertEquals(600, Files.readAllLines(directory.resolve("LOC_zone.txt")).size());
		}
	}

	@Test
	void quitRequestsCoalesceWhileUnrelatedStorageIsDelayed() throws Exception {
		try (var persistence = coordinator()) {
			submitGlobal(persistence);
			var alice = VariableScope.player("Alice");
			persistence.markHydrated(alice);
			persistence.markDirty(alice);
			for (int i = 0; i < 1000; i++) {
				persistence.requestSave(alice);
				persistence.tick();
			}
			assertEquals(1, storage.work.size());
			assertFalse(persistence.prepareLoad(alice)); // Keep dirty memory on rejoin.
			storage.runNext();
			persistence.tick(); // Select Alice.
			persistence.tick(); // Submit Alice.
			assertEquals(1, storage.work.size());
			storage.runNext();
			for (int i = 0; i < 20; i++) persistence.tick();
			assertTrue(storage.work.isEmpty());
			assertTrue(persistence.dirtyScopes().isEmpty());
		}
	}

	@Test
	void mutationDuringWriteSurvivesAndNextSaveWins() throws Exception {
		try (var persistence = coordinator()) {
			submitGlobal(persistence);
			number.set("", 2);
			persistence.markDirty(VariableScope.global());
			persistence.requestSave(VariableScope.global());
			storage.runNext();
			persistence.tick();
			assertTrue(persistence.isDirty(VariableScope.global()));
			persistence.tick();
			storage.runNext();
			persistence.tick();
			assertFalse(persistence.isDirty(VariableScope.global()));
			assertEquals("number=2.0" + System.lineSeparator(), Files.readString(directory.resolve("GLOBAL.txt")));
		}
	}

	@Test
	void structuralInvalidationIsRetriedPromptlyAndDoesNotLoseOtherScopes() throws Exception {
		try (var persistence = coordinator()) {
			persistence.markDirty(VariableScope.locations());
			persistence.requestSave(VariableScope.locations());
			persistence.tick();
			persistence.tick(); // Live iterator spans ticks.
			locations.defineRegion(Set.of(new BlockLocation("world", 9999, 2, 3)), 8);
			persistence.markDirty(VariableScope.locations());
			persistence.markDirty(VariableScope.global());
			persistence.requestSave(VariableScope.global());
			persistence.tick(); // Invalidated location moves to tail, not next 1200-tick cycle.
			persistence.tick();
			persistence.tick();
			storage.runNext(); // Other scope makes progress first.
			for (int i = 0; i < 5; i++) persistence.tick();
			assertEquals(1, storage.work.size());
			storage.runNext();
			persistence.tick();
			assertTrue(persistence.dirtyScopes().isEmpty());
			assertEquals(601, Files.readAllLines(directory.resolve("LOC_zone.txt")).size());
		}
	}

	@Test
	void writeFailureKeepsDirtyAndRetryPreservesExistingFile() throws Exception {
		Path target = Files.createDirectory(directory.resolve("GLOBAL.txt"));
		try (var persistence = coordinator()) {
			submitGlobal(persistence);
			storage.runNext();
			persistence.tick();
			assertTrue(persistence.isDirty(VariableScope.global()));
			assertEquals(1, failures.size());
			assertTrue(Files.isDirectory(target));
			Files.delete(target);
			persistence.requestSave(VariableScope.global());
			persistence.tick();
			persistence.tick();
			storage.runNext();
			persistence.tick();
			assertTrue(persistence.dirtyScopes().isEmpty());
			try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
		}
	}

	@Test
	void failedExplicitCleanSaveBecomesDirtyAndFinalFlushRefusesToDiscardIt() throws Exception {
		Files.createDirectory(directory.resolve("GLOBAL.txt"));
		// Inline executor permits deliberate blocking lifecycle saves, without another thread.
		try (var persistence = new VariablePersistence(this::capture,
				new OrderedVariableWriter(new InlineExecutor()), message -> { }, failures::add)) {
			persistence.saveNow(VariableScope.global());
			assertTrue(persistence.isDirty(VariableScope.global()));
			assertThrows(IllegalStateException.class, persistence::flush);
			Files.delete(directory.resolve("GLOBAL.txt"));
			persistence.flush();
			assertTrue(persistence.dirtyScopes().isEmpty());
		}
	}

	@Test
	void samePlayerRejoinCollectsItsWriteButKeepsNewerMemory() {
		try (var persistence = coordinator()) {
			var alice = VariableScope.player("Alice");
			persistence.markHydrated(alice);
			persistence.markDirty(alice);
			persistence.requestSave(alice);
			persistence.tick();
			persistence.tick();
			persistence.markDirty(alice);
			storage.runNext();
			assertFalse(persistence.prepareLoad(alice));
			// Flush using inline execution once delayed work has been collected.
			storage.inline = true;
		}
	}

	@Test
	void dirtyBeforeHydrationStillLoadsFromDisk() {
		try (var persistence = coordinator()) {
			var alice = VariableScope.player("Alice");
			persistence.markDirty(alice);
			assertTrue(persistence.prepareLoad(alice)); // Must not skip: memory is not authoritative yet.
			persistence.markHydrated(alice);
			assertFalse(persistence.prepareLoad(alice)); // After load, dirty memory wins on rejoin.
			storage.inline = true;
		}
	}

	@Test
	void unhydratedPlayerSaveDoesNotEraseExistingFile() throws Exception {
		Path file = directory.resolve("PLAYER_uuid.txt");
		Files.writeString(file, "number=42.0" + System.lineSeparator());
		try (var persistence = coordinator()) {
			var alice = VariableScope.player("Alice");
			persistence.markDirty(alice);
			persistence.saveNow(alice); // Would write defaults over the file if capture were allowed.
			assertFalse(persistence.isDirty(alice));
			assertEquals(1, failures.size());
			assertEquals("number=42.0" + System.lineSeparator(), Files.readString(file));
		}
	}

	@Test
	void finalFlushAndReplacementWriterCannotOverwriteNewState() throws Exception {
		storage.inline = true;
		var old = coordinator();
		submitGlobal(old);
		number.set("", 9);
		old.markDirty(VariableScope.global());
		old.close(); // Collect old completion, save newer memory, then close.
		assertThrows(IllegalStateException.class, old::tick);
		try (var replacement = new VariablePersistence(this::capture, message -> { }, failures::add)) {
			number.set("", 10);
			replacement.markDirty(VariableScope.global());
		}
		assertEquals("number=10.0" + System.lineSeparator(), Files.readString(directory.resolve("GLOBAL.txt")));
	}

	@Test
	void unresolvedIdentityRetainsDirtyStateUntilFinalFlushCanResolveIt() {
		storage.inline = true;
		boolean[] resolved = { false };
		try (var persistence = new VariablePersistence(scope -> resolved[0] ? capture(scope) : null,
				new OrderedVariableWriter(storage), message -> { }, failures::add)) {
			var alice = VariableScope.player("Alice");
			persistence.markHydrated(alice);
			persistence.markDirty(alice);
			persistence.requestSave(alice);
			persistence.tick();
			assertThrows(IllegalStateException.class, persistence::flush);
			assertTrue(persistence.isDirty(alice));
			resolved[0] = true;
			persistence.flush();
		}
	}

	@Test
	void explicitSaveWithUnknownIdentityIsNotSilentlyLost() {
		storage.inline = true;
		boolean[] resolved = { false };
		try (var persistence = new VariablePersistence(scope -> resolved[0] ? capture(scope) : null,
				new OrderedVariableWriter(storage), message -> { }, failures::add)) {
			var alice = VariableScope.player("Alice");
			persistence.markHydrated(alice);
			persistence.saveNow(alice);
			assertTrue(persistence.isDirty(alice));
			assertEquals(1, failures.size());
			resolved[0] = true;
		}
	}

	@Test
	void routineIntervalAndRepeatedTriggersDoNotQueueSnapshotsBehindDelayedStorage() {
		try (var persistence = coordinator()) {
			persistence.markDirty(VariableScope.global());
			for (int i = 0; i < 1199; i++) persistence.tick();
			assertTrue(storage.work.isEmpty());
			persistence.tick(); // Interval selects the scope.
			persistence.tick(); // Capture is submitted.
			assertEquals(1, storage.work.size());
			for (int i = 0; i < 3600; i++) persistence.tick();
			assertEquals(1, storage.work.size());
			storage.runNext();
			for (int i = 0; i < 10; i++) persistence.tick();
			assertTrue(storage.work.isEmpty());
			assertTrue(persistence.dirtyScopes().isEmpty());
		}
	}

	@Test
	void renamedPlayerLoadsOnlyAfterDirtyOldIdentityIsPersisted() throws Exception {
		storage.inline = true;
		var character = new com.nisovin.magicspells.variables.variabletypes.CharacterVariable();
		character.setPermanent(true);
		character.setWithCompositeKey("OldName:character", 8);
		var oldName = VariableScope.player("OldName");
		var newName = VariableScope.player("NewName");
		try (var persistence = new VariablePersistence(scope -> VariableSnapshotCapture.player(
				scope.player(), "same-uuid", directory.toFile(), Map.of("stat", character)),
				new OrderedVariableWriter(storage), message -> { }, failures::add)) {
			persistence.markHydrated(oldName);
			persistence.markDirty(oldName);
			persistence.requestSave(oldName); // Still queued when the same UUID joins under another name.
			assertTrue(persistence.prepareLoad(newName, scope -> scope.equals(oldName) || scope.equals(newName)));
			assertFalse(persistence.isDirty(oldName));
			assertEquals("stat:character=8.0" + System.lineSeparator(),
					Files.readString(directory.resolve("PLAYER_same-uuid.txt")));
		}
	}

	@Test
	void newerMutationDuringCaptureCannotBeAcknowledgedByItsWrite() {
		try (var persistence = coordinator()) {
			persistence.markDirty(VariableScope.locations());
			persistence.requestSave(VariableScope.locations());
			persistence.tick();
			persistence.tick(); // First slice has captured old values.
			locations.getBlockMap().values().forEach(region -> region.setValue(9.0));
			persistence.markDirty(VariableScope.locations());
			persistence.tick();
			persistence.tick();
			storage.runNext();
			persistence.tick();
			assertTrue(persistence.isDirty(VariableScope.locations()));
			storage.inline = true; // Final flush persists the newer generation, not just the old snapshot.
			persistence.flush();
			assertTrue(persistence.dirtyScopes().isEmpty());
		}
	}

	@Test
	void offOwnerMutationIsRejectedByTheCoordinator() throws Exception {
		try (var persistence = coordinator()) {
			var attempted = java.util.concurrent.CompletableFuture.runAsync(() ->
					assertThrows(IllegalStateException.class, () -> persistence.markDirty(VariableScope.global())));
			attempted.get(5, TimeUnit.SECONDS);
			assertTrue(persistence.dirtyScopes().isEmpty());
		}
	}

	private void submitGlobal(VariablePersistence persistence) {
		persistence.markDirty(VariableScope.global());
		persistence.requestSave(VariableScope.global());
		persistence.tick();
		persistence.tick();
	}

	private VariablePersistence coordinator() {
		return new VariablePersistence(this::capture, new OrderedVariableWriter(storage), message -> { }, failures::add);
	}

	private VariableSnapshotCapture capture(VariableScope scope) {
		return switch (scope.kind()) {
			case GLOBAL -> VariableSnapshotCapture.global(directory.toFile(), variables);
			case LOCATION -> VariableSnapshotCapture.locations(directory.toFile(), variables);
			case PLAYER -> VariableSnapshotCapture.player(scope.player(), "uuid", directory.toFile(), variables);
		};
	}

	private static GlobalVariable number() {
		var value = new GlobalVariable();
		value.setPermanent(true);
		value.set("", 1);
		return value;
	}

	private static LocationVariable locations() {
		var value = new LocationVariable();
		value.setPermanent(true);
		for (int i = 0; i < 600; i++) value.defineRegion(Set.of(new BlockLocation("world", i, 2, 3)), 4.5);
		return value;
	}

	private static class ManualExecutor extends AbstractExecutorService {
		final ArrayDeque<Runnable> work = new ArrayDeque<>();
		boolean inline;
		boolean shutdown;
		void runNext() { work.removeFirst().run(); }
		@Override public void execute(Runnable command) {
			if (shutdown) throw new java.util.concurrent.RejectedExecutionException();
			if (inline) command.run(); else work.add(command);
		}
		@Override public void shutdown() { shutdown = true; }
		@Override public List<Runnable> shutdownNow() { shutdown(); return List.copyOf(work); }
		@Override public boolean isShutdown() { return shutdown; }
		@Override public boolean isTerminated() { return shutdown && work.isEmpty(); }
		@Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
	}

	private static final class InlineExecutor extends ManualExecutor {
		InlineExecutor() { inline = true; }
	}
}
