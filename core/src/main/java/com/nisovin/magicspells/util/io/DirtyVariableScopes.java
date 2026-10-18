package com.nisovin.magicspells.util.io;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Owner-thread dirty scopes. Every add is a mutation, even when the scope is already dirty. */
public final class DirtyVariableScopes extends AbstractSet<String> {
	private final Map<String, Long> generations = new HashMap<>();
	private long nextGeneration;

	@Override
	public boolean add(String scope) {
		return generations.put(scope, ++nextGeneration) == null;
	}

	public long generation(String scope) {
		return generations.getOrDefault(scope, 0L);
	}

	public void acknowledge(String scope, long generation) {
		generations.remove(scope, generation);
	}

	@Override
	public Iterator<String> iterator() {
		return generations.keySet().iterator();
	}

	@Override
	public int size() {
		return generations.size();
	}

	@Override
	public boolean contains(Object scope) {
		return generations.containsKey(scope);
	}

	@Override
	public boolean remove(Object scope) {
		return generations.remove(scope) != null;
	}
}
