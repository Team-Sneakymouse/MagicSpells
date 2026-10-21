package com.nisovin.magicspells.util.io;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Owner-thread persistence coordinator. Dirty scopes are the backlog, not queued snapshots.
 * One capture or detached write is active; ordinary player lifecycle operations never drain unrelated work.
 */
public final class VariablePersistence implements AutoCloseable {
	private static final int SAVE_INTERVAL = 1200;
	private final Thread owner = Thread.currentThread();
	private final OrderedVariableWriter writer;
	private final Function<VariableScope, VariableSnapshotCapture> captures;
	private final Consumer<String> debug;
	private final Consumer<String> error;
	private final DirtyVariableScopes<VariableScope> dirty = new DirtyVariableScopes<>();
	private final Set<VariableScope> pending = new LinkedHashSet<>();
	/** Scopes whose durable state has been loaded into live memory at least once. */
	private final Set<VariableScope> hydrated = new HashSet<>();
	private Active capture;
	private Active outstanding;
	private int ticksUntilSave = SAVE_INTERVAL;
	private long failures;
	private boolean closed;

	private record Active(VariableScope scope, long generation, VariableSnapshotCapture data) { }

	public VariablePersistence(Function<VariableScope, VariableSnapshotCapture> captures,
			Consumer<String> debug, Consumer<String> error) {
		this(captures, new OrderedVariableWriter(), debug, error);
	}

	VariablePersistence(Function<VariableScope, VariableSnapshotCapture> captures, OrderedVariableWriter writer,
			Consumer<String> debug, Consumer<String> error) {
		this.captures = captures;
		this.writer = writer;
		this.debug = debug;
		this.error = error;
	}

	public void markDirty(VariableScope scope) { checkOwner(); dirty.markDirty(scope); }
	public boolean isDirty(VariableScope scope) { checkOwner(); return dirty.contains(scope); }
	public Set<VariableScope> dirtyScopes() { checkOwner(); return dirty.scopes(); }

	/** Record that durable state for this scope is present in live memory. */
	public void markHydrated(VariableScope scope) { checkOwner(); hydrated.add(scope); }
	public boolean isHydrated(VariableScope scope) { checkOwner(); return hydrated.contains(scope); }

	/** Expedite a dirty scope (e.g. quit) without waiting for storage or constructing a snapshot. */
	public void requestSave(VariableScope scope) {
		checkOwner();
		if (dirty.contains(scope)) pending.add(scope);
	}

	/**
	 * Only a write to this scope can conflict with its load. Dirty memory is authoritative
	 * only after the scope was hydrated: skip reloading it instead of saving synchronously
	 * merely to read it straight back. Unhydrated dirty state must still load from disk —
	 * otherwise empty defaults replace the file and wipe progress.
	 */
	public boolean prepareLoad(VariableScope scope) {
		checkOwner();
		if (outstanding != null && outstanding.scope().equals(scope)) complete(writer.flush());
		return !skipLoad(scope);
	}

	/** Identity resolution can recognize renamed/case-changed players sharing the same UUID file. */
	public boolean prepareLoad(VariableScope scope, Predicate<VariableScope> sameFile) {
		checkOwner();
		if (outstanding != null && sameFile.test(outstanding.scope())) complete(writer.flush());
		// Only copy conflicts, not the entire dirty backlog, on ordinary joins.
		var aliases = dirty.scopes().stream().filter(other -> !other.equals(scope) && sameFile.test(other)).toList();
		for (VariableScope other : aliases) {
			// Dirty memory under an old name must reach disk before loading it under a new name.
			saveNow(other);
			if (dirty.contains(other)) throw new IllegalStateException("Cannot load over unsaved variable identity");
		}
		return !skipLoad(scope);
	}

	public void tick() {
		checkOwner();
		complete(writer.poll());
		if (--ticksUntilSave <= 0) {
			ticksUntilSave = SAVE_INTERVAL;
			pending.addAll(dirty.scopes());
		}
		if (writer.isBusy()) return;
		if (capture != null) {
			try {
				if (capture.data().advance()) {
					submit(capture);
					capture = null;
				}
			} catch (ConcurrentModificationException invalidated) {
				// Retry at the tail promptly, without starving other scopes or waiting another minute.
				pending.add(capture.scope());
				capture = null;
				debug.accept("Variable snapshot invalidated; dirty scope rescheduled.");
			}
			return;
		}
		if (pending.isEmpty()) return;
		var iterator = pending.iterator();
		VariableScope scope = iterator.next();
		iterator.remove();
		if (dirty.contains(scope)) {
			Active snapshot = capture(scope);
			// Unhydrated player captures are refused; dirty stays until load hydrates, then the next interval.
			if (snapshot != null) capture = snapshot;
		}
	}

	/** Explicit public saves retain their blocking attempt-completed contract. */
	public void saveNow(VariableScope scope) {
		checkOwner();
		complete(writer.flush());
		if (capture != null && capture.scope().equals(scope)) capture = null;
		Active snapshot = capture(scope);
		if (snapshot == null) {
			if (scope.kind() == VariableScope.Kind.PLAYER && !hydrated.contains(scope)) {
				// Unloaded player memory is defaults: writing it would erase the file.
				dirty.clear(scope);
				pending.remove(scope);
				error.accept("Variable save skipped: player scope not loaded; existing file preserved.");
				return;
			}
			// Even an explicit attempt on previously clean state must not silently disappear.
			if (!dirty.contains(scope)) dirty.markDirty(scope);
			error.accept("Variable save deferred: unresolved identity; changes retained for retry.");
			return;
		}
		while (!snapshot.data().advance()) { /* Deliberate explicit durability barrier. */ }
		submit(snapshot);
		complete(writer.flush());
	}

	/** Flush before destructive teardown; never discard dirty state after an incomplete attempt. */
	public void flush() {
		checkOwner();
		complete(writer.flush());
		for (VariableScope scope : new ArrayList<>(dirty.scopes())) saveNow(scope);
		if (!dirty.isEmpty()) throw new IllegalStateException("Final variable flush incomplete; unsaved state retained");
	}

	@Override
	public void close() {
		flush();
		writer.close();
		capture = null;
		pending.clear();
		hydrated.clear();
		closed = true;
	}

	private boolean skipLoad(VariableScope scope) {
		return dirty.contains(scope) && hydrated.contains(scope);
	}

	private Active capture(VariableScope scope) {
		if (scope.kind() == VariableScope.Kind.PLAYER && !hydrated.contains(scope)) return null;
		long generation = dirty.generation(scope);
		VariableSnapshotCapture data = captures.apply(scope);
		return data == null ? null : new Active(scope, generation, data);
	}

	private void submit(Active snapshot) {
		if (!writer.submit(snapshot.data().write())) throw new IllegalStateException("Variable write already outstanding");
		outstanding = snapshot;
		debug.accept("Variable persistence submitted: snapshot-ns=" + snapshot.data().snapshotNanos()
				+ ", max-snapshot-slice-ns=" + snapshot.data().maxSliceNanos()
				+ ", pending-scopes=" + pending.size() + ", in-flight=1");
	}

	private void complete(OrderedVariableWriter.Completion completion) {
		if (completion == null) return;
		if (completion.failure() == null) dirty.acknowledge(outstanding.scope(), outstanding.generation());
		else {
			if (!dirty.contains(outstanding.scope())) dirty.markDirty(outstanding.scope());
			failures++;
			error.accept("Variable persistence failed (" + completion.failure().getClass().getSimpleName()
					+ "); changes retained for retry.");
		}
		outstanding = null;
		debug.accept("Variable persistence: write-ns=" + completion.writeNanos() + ", failures=" + failures
				+ ", dirty-scopes=" + dirty.scopes().size() + ", in-flight=0");
	}

	private void checkOwner() {
		if (Thread.currentThread() != owner || closed)
			throw new IllegalStateException("Variable persistence requires the active owner's thread");
	}
}
