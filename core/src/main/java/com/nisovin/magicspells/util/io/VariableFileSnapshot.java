package com.nisovin.magicspells.util.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;

/** Immutable, Bukkit-free input. File selection, read/merge, formatting and fsync run on the writer. */
public final class VariableFileSnapshot implements OrderedVariableWriter.Write {
	private final Path target;
	private final Path legacy;
	private final boolean merge;
	private final boolean flatten;
	private final Map<String, String> values;
	private final Set<String> removals;

	public VariableFileSnapshot(Path target, Path legacy, boolean merge, Map<String, String> values, Set<String> removals) {
		this(target, legacy, merge, true, Map.copyOf(values), Set.copyOf(removals));
	}

	private VariableFileSnapshot(Path target, Path legacy, boolean merge, boolean flatten,
			Map<String, String> values, Set<String> removals) {
		this.target = target;
		this.legacy = legacy;
		this.merge = merge;
		this.flatten = flatten;
		this.values = values;
		this.removals = removals;
	}

	/** Incrementally capture on the owner thread, then transfer ownership in O(1). */
	public static final class Builder {
		private final Path target;
		private final Path legacy;
		private final boolean merge;
		private final boolean flatten;
		private final Map<String, String> values = new HashMap<>();
		private final Set<String> removals = new HashSet<>();
		private boolean built;

		public Builder(Path target, Path legacy, boolean merge, boolean flatten) {
			this.target = target;
			this.legacy = legacy;
			this.merge = merge;
			this.flatten = flatten;
		}

		public void put(String key, String value) {
			checkOpen();
			values.put(key, value);
		}

		public void remove(String key) {
			checkOpen();
			removals.add(key);
		}

		public VariableFileSnapshot build() {
			checkOpen();
			built = true;
			return new VariableFileSnapshot(target, legacy, merge, flatten,
					Collections.unmodifiableMap(values), Collections.unmodifiableSet(removals));
		}

		private void checkOpen() {
			if (built) throw new IllegalStateException("Snapshot already transferred");
		}
	}

	@Override
	public void run() throws IOException {
		write();
	}

	public void write() throws IOException {
		Path file = legacy != null && Files.exists(legacy) ? legacy : target;
		Map<String, String> result = new TreeMap<>();
		if (merge && Files.exists(file)) {
			try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				String line;
				while ((line = reader.readLine()) != null) {
					int equals = line.indexOf('=');
					if (equals > 0) result.put(line.substring(0, equals), line.substring(equals + 1));
				}
			}
		}
		removals.forEach(result::remove);
		// Match historic Util.flattenLineBreaks regex replacement: newline -> literal n, not backslash+n.
		values.forEach((key, value) -> result.put(key, flatten ? value.replace("\n", "n") : value));
		StringBuilder content = new StringBuilder();
		result.forEach((key, value) -> content.append(key).append('=').append(value).append(System.lineSeparator()));
		AtomicFiles.writeUtf8(file, content.toString());
	}
}
