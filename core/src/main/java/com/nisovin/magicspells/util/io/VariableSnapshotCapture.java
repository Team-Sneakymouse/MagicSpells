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
	private final String kind;
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

	public VariableSnapshotCapture(String kind, String player, String uid, File folder, Map<String, Variable> variables) {
		this.kind = kind;
		this.player = player;
		this.folder = folder;
		variableIterator = variables.entrySet().iterator();
		if (kind.equals("global")) builder = new VariableFileSnapshot.Builder(
				new File(folder, "GLOBAL.txt").toPath(), null, false, true);
		else if (kind.equals("player")) builder = new VariableFileSnapshot.Builder(
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
						if (kind.equals("location")) {
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
					if (kind.equals("location")) {
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
				if (kind.equals("global")) {
					if (variable instanceof GlobalVariable || variable instanceof GlobalStringVariable) {
						String value = variable.getStringValue("");
						if (!value.equals(variable.getDefaultStringValue())) builder.put(variableName, value);
					}
				} else if (kind.equals("player")) {
					if (variable instanceof CharacterStringVariable cv) entries = cv.getPersistenceStringValues().entrySet().iterator();
					else if (variable instanceof CharacterVariable cv) entries = cv.getPersistenceValues().entrySet().iterator();
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
