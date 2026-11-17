package com.nisovin.magicspells.variables;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import com.nisovin.magicspells.util.PlayerNameUtils;

import com.sneakyvariables.api.SneakyVariables;
import com.sneakyvariables.api.VariableOwner;

/** Resolves SneakyVariables owners from MagicSpells player-name APIs. */
public final class VariableOwners {

	private VariableOwners() {
	}

	@Nullable
	public static Player resolvePlayer(@Nullable String playerName) {
		if (playerName == null || playerName.isEmpty()) return null;
		return PlayerNameUtils.getPlayerExact(playerName);
	}

	@Nullable
	public static VariableOwner player(@Nullable String playerName) {
		Player player = resolvePlayer(playerName);
		if (player == null) return null;
		return SneakyVariables.api().player(player);
	}

	@Nullable
	public static VariableOwner character(@Nullable String playerName) {
		Player player = resolvePlayer(playerName);
		if (player == null) return null;
		return SneakyVariables.api().character(player);
	}

	public static VariableOwner global() {
		return SneakyVariables.api().global();
	}

}
