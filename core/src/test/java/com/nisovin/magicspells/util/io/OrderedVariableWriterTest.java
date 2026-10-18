package com.nisovin.magicspells.util.io;

import java.util.concurrent.CountDownLatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderedVariableWriterTest {
	@Test
	void blockedWriteDoesNotBlockOwnerOrAcceptMoreWork() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (OrderedVariableWriter writer = new OrderedVariableWriter()) {
			assertTrue(writer.submit(() -> {
				entered.countDown();
				if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("writer not released");
			}));
			try {
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				assertFalse(writer.submit(() -> fail("must not queue")));
				assertNull(writer.poll());
			} finally {
				release.countDown();
			}
			assertNull(writer.flush().failure());
		}
	}
	@TempDir Path directory;

	@Test
	void mutationDuringWriteSurvivesAndNextSaveWins() throws Exception {
		Path file = directory.resolve("GLOBAL.txt");
		var dirty = new DirtyVariableScopes();
		dirty.add("global");
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (var writer = new OrderedVariableWriter()) {
			var first = new VariableFileSnapshot(file, null, false, Map.of("value", "1"), Set.of());
			assertTrue(writer.submit(() -> {
				entered.countDown();
				if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("not released");
				first.write();
			}, dirty, "global", dirty.generation("global")));
			try {
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				dirty.add("global");
				for (int i = 0; i < 1000; i++) {
					assertFalse(writer.submit(() -> fail("unbounded queue")));
				}
				assertTrue(dirty.contains("global"));
			} finally {
				release.countDown();
			}
			assertNull(writer.flush().failure());
			assertTrue(dirty.contains("global"));
			var second = new VariableFileSnapshot(file, null, false, Map.of("value", "2"), Set.of());
			assertTrue(writer.submit(second, dirty, "global", dirty.generation("global")));
			assertNull(writer.flush().failure());
			assertFalse(dirty.contains("global"));
			assertEquals("value=2" + System.lineSeparator(), Files.readString(file));
		}
	}

	@Test
	void realReplacementFailureKeepsOldFileAndDirtyGenerationForRetry() throws Exception {
		Path file = directory.resolve("PLAYER_uuid.txt");
		Files.writeString(file, "coins=1\n");
		var dirty = new DirtyVariableScopes();
		dirty.add("player");
		try (var writer = new OrderedVariableWriter()) {
			assertTrue(writer.submit(() -> AtomicFiles.writeUtf8(file, "coins=2\n",
					(temporary, destination) -> { throw new IOException("replacement failed"); },
					Files::deleteIfExists), dirty, "player", dirty.generation("player")));
			assertInstanceOf(IOException.class, writer.flush().failure());
			assertEquals("coins=1\n", Files.readString(file));
			assertTrue(dirty.contains("player"));
			try (var paths = Files.list(directory)) {
				assertEquals(1, paths.count());
			}
			assertTrue(writer.submit(new VariableFileSnapshot(file, null, true, Map.of("coins", "2"), Set.of()),
					dirty, "player", dirty.generation("player")));
			assertNull(writer.flush().failure());
			assertTrue(dirty.isEmpty());
			assertEquals("coins=2" + System.lineSeparator(), Files.readString(file));
		}
	}

	@Test
	void flushBeforeReloadPreventsOldWriterOverwritingNewManager() throws Exception {
		Path file = directory.resolve("GLOBAL.txt");
		var old = new OrderedVariableWriter();
		assertTrue(old.submit(new VariableFileSnapshot(file, null, false, Map.of("v", "old"), Set.of())));
		assertThrows(IllegalStateException.class, old::close);
		assertNull(old.flush().failure());
		old.close();
		assertThrows(IllegalStateException.class, () -> old.submit(() -> fail("closed writer")));
		try (var replacement = new OrderedVariableWriter()) {
			assertTrue(replacement.submit(new VariableFileSnapshot(file, null, false, Map.of("v", "new"), Set.of())));
			assertNull(replacement.flush().failure());
		}
		assertEquals("v=new" + System.lineSeparator(), Files.readString(file));
	}

	@Test
	void offOwnerAccessIsRejected() throws Exception {
		try (var writer = new OrderedVariableWriter()) {
			var failure = java.util.concurrent.CompletableFuture.supplyAsync(() ->
					assertThrows(IllegalStateException.class, writer::flush));
			assertNotNull(failure.get(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void finalFlushWaitsForStorageBeforeClosing() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch flushing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		var owner = java.util.concurrent.CompletableFuture.runAsync(() -> {
			try (var writer = new OrderedVariableWriter()) {
				writer.submit(() -> {
					entered.countDown();
					if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("not released");
				});
				flushing.countDown();
				assertNull(writer.flush().failure());
			}
		});
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertTrue(flushing.await(5, TimeUnit.SECONDS));
			assertFalse(owner.isDone()); // Cannot finish the flush while storage is latched.
		} finally {
			release.countDown();
		}
		owner.get(5, TimeUnit.SECONDS);
	}

	@Test
	void failedExplicitSaveOfCleanScopeBecomesDirty() {
		var dirty = new DirtyVariableScopes();
		try (var writer = new OrderedVariableWriter()) {
			assertTrue(writer.submit(() -> { throw new IOException("write failed"); }, dirty, "global", 0));
			assertInstanceOf(IOException.class, writer.flush().failure());
			assertTrue(dirty.contains("global"));
		}
	}

}
