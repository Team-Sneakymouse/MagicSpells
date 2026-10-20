package com.nisovin.magicspells.util.io;

import java.util.Objects;

/** A persistence identity; player names are owner-thread keys, never diagnostic labels. */
public record VariableScope(Kind kind, String player) {
	public enum Kind { GLOBAL, LOCATION, PLAYER }
	private static final VariableScope GLOBAL = new VariableScope(Kind.GLOBAL, null);
	private static final VariableScope LOCATIONS = new VariableScope(Kind.LOCATION, null);

	public VariableScope {
		Objects.requireNonNull(kind);
		if (kind == Kind.PLAYER) Objects.requireNonNull(player);
		else if (player != null) throw new IllegalArgumentException("Only player scopes have a player");
	}

	public static VariableScope global() { return GLOBAL; }
	public static VariableScope locations() { return LOCATIONS; }
	public static VariableScope player(String player) { return new VariableScope(Kind.PLAYER, player); }
}
