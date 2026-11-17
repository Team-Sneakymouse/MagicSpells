package com.nisovin.magicspells.variables.variabletypes;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class CharacterLocationVariable extends LocationVariable {

	private Player ownerPlayer;

	public void bindOwner(Player player) {
		this.ownerPlayer = player;
	}

	public void clearOwner() {
		this.ownerPlayer = null;
	}

	public boolean hasCharacterContext(String playerName) {
		return VariableOwners.character(playerName) != null;
	}

	@Override
	protected VariableOwner owner() {
		if (ownerPlayer != null) return VariableOwners.character(ownerPlayer.getName());
		return null;
	}

	@Override
	public void set(String player, double amount) {
		Player pl = VariableOwners.resolvePlayer(player);
		if (pl == null || !hasCharacterContext(player)) return;
		bindOwner(pl);
		try {
			set(pl.getLocation(), amount);
		} finally {
			clearOwner();
		}
	}

	@Override
	public double getValue(String player) {
		Player pl = VariableOwners.resolvePlayer(player);
		if (pl == null || !hasCharacterContext(player)) return defaultValue;
		bindOwner(pl);
		try {
			return getValue(pl.getLocation());
		} finally {
			clearOwner();
		}
	}

	@Override
	public void reset(String player) {
		Player pl = VariableOwners.resolvePlayer(player);
		if (pl == null || !hasCharacterContext(player)) return;
		bindOwner(pl);
		try {
			reset(pl.getLocation());
		} finally {
			clearOwner();
		}
	}

	public double getValue(Player player, Location location) {
		if (player == null || !hasCharacterContext(player.getName())) return defaultValue;
		bindOwner(player);
		try {
			return getValue(location);
		} finally {
			clearOwner();
		}
	}

	public void set(Player player, Location location, double amount) {
		if (player == null || !hasCharacterContext(player.getName())) return;
		bindOwner(player);
		try {
			set(location, amount);
		} finally {
			clearOwner();
		}
	}

	public void reset(Player player, Location location) {
		if (player == null || !hasCharacterContext(player.getName())) return;
		bindOwner(player);
		try {
			reset(location);
		} finally {
			clearOwner();
		}
	}

}
