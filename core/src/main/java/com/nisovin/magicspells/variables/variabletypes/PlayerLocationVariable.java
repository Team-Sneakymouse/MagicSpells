package com.nisovin.magicspells.variables.variabletypes;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class PlayerLocationVariable extends LocationVariable {

	private Player ownerPlayer;

	/** Bind the owning player for the next location read/write (used by VariableManager). */
	public void bindOwner(Player player) {
		this.ownerPlayer = player;
	}

	public void clearOwner() {
		this.ownerPlayer = null;
	}

	@Override
	protected VariableOwner owner() {
		if (ownerPlayer != null) return VariableOwners.player(ownerPlayer.getName());
		return null;
	}

	@Override
	public void set(String player, double amount) {
		Player pl = VariableOwners.resolvePlayer(player);
		if (pl == null) return;
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
		if (pl == null) return defaultValue;
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
		if (pl == null) return;
		bindOwner(pl);
		try {
			reset(pl.getLocation());
		} finally {
			clearOwner();
		}
	}

	public double getValue(Player player, Location location) {
		if (player == null) return defaultValue;
		bindOwner(player);
		try {
			return getValue(location);
		} finally {
			clearOwner();
		}
	}

	public void set(Player player, Location location, double amount) {
		if (player == null) return;
		bindOwner(player);
		try {
			set(location, amount);
		} finally {
			clearOwner();
		}
	}

	public void reset(Player player, Location location) {
		if (player == null) return;
		bindOwner(player);
		try {
			reset(location);
		} finally {
			clearOwner();
		}
	}

}
