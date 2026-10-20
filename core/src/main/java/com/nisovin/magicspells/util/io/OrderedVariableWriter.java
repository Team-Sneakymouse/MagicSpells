package com.nisovin.magicspells.util.io;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owner-thread interface to a single ordered writer. There is no pending queue: callers retain
 * dirty state until successful completion is collected. A completed write occupies the slot until collected.
 * Neither poll nor submit waits for storage. flush deliberately does, without Bukkit callbacks. close requires a collected flush.
 */
public final class OrderedVariableWriter implements AutoCloseable {
	private final Thread owner = Thread.currentThread();
	private final ExecutorService executor;
	private CompletableFuture<Completion> outstanding;
	private boolean closed;

	public OrderedVariableWriter() {
		this(Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "MagicSpells-variable-writer");
			thread.setDaemon(true);
			return thread;
		}));
	}

	OrderedVariableWriter(ExecutorService executor) {
		this.executor = executor;
	}

	@FunctionalInterface
	public interface Write {
		void run() throws Exception;
	}

	public record Completion(Throwable failure, long writeNanos) { }

	public boolean submit(Write write) {
		checkOwner();
		if (closed) throw new IllegalStateException("Variable writer is closed");
		if (outstanding != null) return false;
		outstanding = CompletableFuture.supplyAsync(() -> {
			long start = System.nanoTime();
			Throwable failure = null;
			try {
				write.run();
			} catch (Throwable e) {
				failure = e;
			}
			return new Completion(failure, System.nanoTime() - start);
		}, executor);
		return true;
	}

	public Completion poll() {
		checkOwner();
		return outstanding != null && outstanding.isDone() ? flush() : null;
	}

	public Completion flush() {
		checkOwner();
		if (outstanding == null) return null;
		Completion result = outstanding.join();
		outstanding = null;
		return result;
	}

	public boolean isBusy() {
		checkOwner();
		return outstanding != null;
	}

	@Override
	public void close() {
		checkOwner();
		if (outstanding != null) throw new IllegalStateException("Flush and collect the write before closing");
		closed = true;
		executor.shutdown();
	}

	private void checkOwner() {
		if (Thread.currentThread() != owner)
			throw new IllegalStateException("Variable persistence must be called on its owning thread");
	}
}
