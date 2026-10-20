package com.nisovin.magicspells.util.io;

import java.io.File;
import java.util.*;
import com.nisovin.magicspells.util.Region;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.*;

/** Owner-thread capture of existing variable formats, amortized to 256 live entries per advance. */
public final class VariableSnapshotCapture {
	private final Thread owner = Thread.currentThread();
	private boolean complete;
	private final VariableScope.Kind kind;
	private final String player;
	private final File folder;
	private final Iterator<Map.Entry<String, Variable>> variableIterator;
	private final List<VariableFileSnapshot> snapshots = new ArrayList<>();
	private Iterator<? extends Map.Entry<?, ?>> entries;
	private VariableFileSnapshot.Builder builder;
	private Variable variable;
	private String variableName;
	private long snapshotNanos;
	private long maxSliceNanos;

	public static VariableSnapshotCapture global(File folder, Map<String, Variable> variables) {
		return new VariableSnapshotCapture(VariableScope.global(), null, folder, variables);
	}

	public static VariableSnapshotCapture locations(File folder, Map<String, Variable> variables) {
		return new VariableSnapshotCapture(VariableScope.locations(), null, folder, variables);
	}

	public static VariableSnapshotCapture player(String player, String uid, File folder, Map<String, Variable> variables) {
		return new VariableSnapshotCapture(VariableScope.player(player), Objects.requireNonNull(uid), folder, variables);
	}

	private VariableSnapshotCapture(VariableScope scope, String uid, File folder, Map<String, Variable> variables) {
		kind = scope.kind();
		player = scope.player();
		this.folder = folder;
		variableIterator = variables.entrySet().iterator();
		if (kind == VariableScope.Kind.GLOBAL) builder = new VariableFileSnapshot.Builder(
				new File(folder, "GLOBAL.txt").toPath(), null, false, true);
		else if (kind == VariableScope.Kind.PLAYER) builder = new VariableFileSnapshot.Builder(
				new File(folder, "PLAYER_" + uid + ".txt").toPath(),
				new File(folder, "PLAYER_" + player + ".txt").toPath(), true, true);
	}

	public boolean advance() {
		checkOwner();
		if (complete) throw new IllegalStateException("Snapshot already sealed");
		long start = System.nanoTime();
		try {
			for (int budget = 0; budget < 256; budget++) {
				if (entries != null) {
					if (entries.hasNext()) {
						Map.Entry<?, ?> entry = entries.next();
						if (kind == VariableScope.Kind.LOCATION) {
							builder.put(entry.getKey().toString(), ((Region<?>) entry.getValue()).getValue().toString());
						} else {
							String composite = (String) entry.getKey();
							if (composite.startsWith(player + ":")) {
								putOrRemove(variableName + ":" + composite.substring(player.length() + 1), entry.getValue().toString());
							}
						}
						continue;
					}
					entries = null;
					if (kind == VariableScope.Kind.LOCATION) {
						snapshots.add(builder.build());
						builder = null;
					}
				}
				if (!variableIterator.hasNext()) {
					if (builder != null) snapshots.add(builder.build());
					complete = true;
					return true;
				}
				Map.Entry<String, Variable> entry = variableIterator.next();
				variableName = entry.getKey();
				variable = entry.getValue();
				if (!variable.isPermanent()) continue;
				if (kind == VariableScope.Kind.GLOBAL) {
					if (variable instanceof GlobalVariable || variable instanceof GlobalStringVariable) {
						String value = variable.getStringValue("");
						if (!value.equals(variable.getDefaultStringValue())) builder.put(variableName, value);
					}
				} else if (kind == VariableScope.Kind.PLAYER) {
					if (variable instanceof CharacterStringVariable cv) entries = characterEntries(previous -> cv.getNextPersistenceStringValue(player, previous));
					else if (variable instanceof CharacterVariable cv) entries = characterEntries(previous -> cv.getNextPersistenceValue(player, previous));
					else if (variable instanceof PlayerVariable) putOrRemove(variableName, variable.getStringValue(player));
				} else {
					if (variable instanceof LocationVariable lv) entries = lv.getBlockMap().entrySet().iterator();
					else if (variable instanceof LocationStringVariable lv) entries = lv.getBlockMap().entrySet().iterator();
					else continue;
					builder = new VariableFileSnapshot.Builder(new File(folder, "LOC_" + variableName + ".txt").toPath(),
							null, false, false);
				}
			}
			return false;
		} finally {
			long duration = System.nanoTime() - start;
			snapshotNanos += duration;
			maxSliceNanos = Math.max(maxSliceNanos, duration);
		}
	}

	/** Ordered cursor survives structural mutations. Newer mutations stay dirty via captured generations. */
	private static Iterator<Map.Entry<String, ?>> characterEntries(java.util.function.Function<String, ? extends Map.Entry<String, ?>> next) {
		return new Iterator<>() {
			private String previous;
			private Map.Entry<String, ?> nextEntry() {
				return next.apply(previous);
			}
			@Override public boolean hasNext() { return nextEntry() != null; }
			@Override public Map.Entry<String, ?> next() {
				Map.Entry<String, ?> entry = nextEntry();
				if (entry == null) throw new NoSuchElementException();
				previous = entry.getKey();
				return entry;
			}
		};
	}

	private void putOrRemove(String key, String value) {
		if (value.equals(variable.getDefaultStringValue())) builder.remove(key);
		else builder.put(key, value);
	}

	public long snapshotNanos() { return snapshotNanos; }
	public long maxSliceNanos() { return maxSliceNanos; }

	/** Only detached sealed snapshots cross threads. Do not advance again after completion. */
	public OrderedVariableWriter.Write write() {
		checkOwner();
		if (!complete) throw new IllegalStateException("Snapshot not complete");
		List<VariableFileSnapshot> files = Collections.unmodifiableList(snapshots);
		return () -> {
			for (VariableFileSnapshot file : files) file.write();
		};
	}

	private void checkOwner() {
		if (Thread.currentThread() != owner) throw new IllegalStateException("Capture requires its owning thread");
	}

}
