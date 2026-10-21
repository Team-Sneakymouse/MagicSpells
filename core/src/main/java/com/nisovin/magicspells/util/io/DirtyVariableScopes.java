package com.nisovin.magicspells.util.io;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Owner-thread mutation generations. Repeated marks represent new mutations, not set insertion. */
public final class DirtyVariableScopes<S> {
	private final Map<S, Long> generations = new LinkedHashMap<>();
	private long nextGeneration;

	public void markDirty(S scope) { generations.put(scope, ++nextGeneration); }
	public long generation(S scope) { return generations.getOrDefault(scope, 0L); }
	public void acknowledge(S scope, long generation) { generations.remove(scope, generation); }
	/** Drop dirty tracking without a successful write (e.g. refuse to snapshot unloaded player memory). */
	public void clear(S scope) { generations.remove(scope); }
	public boolean contains(S scope) { return generations.containsKey(scope); }
	public boolean isEmpty() { return generations.isEmpty(); }
	public Set<S> scopes() { return Collections.unmodifiableSet(generations.keySet()); }
}
