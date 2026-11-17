package com.nisovin.magicspells.variables.variabletypes;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class PlayerLocationStringVariable extends LocationStringVariable {

	private Player ownerPlayer;

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
	public void set(String player, String value) {
		Player pl = VariableOwners.resolvePlayer(player);
		if (pl == null) return;
		bindOwner(pl);
		try {
			set(pl.getLocation(), value);
		} finally {
			clearOwner();
		}
	}

	@Override
	public String getStringValue(String player) {
		Player pl = VariableOwners.resolvePlayer(player);
		if (pl == null) return defaultStringValue;
		bindOwner(pl);
		try {
			return getStringValue(pl.getLocation());
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

	public String getStringValue(Player player, Location location) {
		if (player == null) return defaultStringValue;
		bindOwner(player);
		try {
			return getStringValue(location);
		} finally {
			clearOwner();
		}
	}

	public void set(Player player, Location location, String value) {
		if (player == null) return;
		bindOwner(player);
		try {
			set(location, value);
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
