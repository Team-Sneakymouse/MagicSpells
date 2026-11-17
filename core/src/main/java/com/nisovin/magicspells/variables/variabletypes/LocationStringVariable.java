package com.nisovin.magicspells.variables.variabletypes;

import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.util.BlockLocation;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.BlockVariable;
import com.sneakyvariables.api.VariableOwner;

public class LocationStringVariable extends Variable {

	@Override
	public void loadExtraData(ConfigurationSection section) {
		super.loadExtraData(section);
		defaultStringValue = section.getString("default-value", "");
	}

	@Override
	public void set(String player, double amount) {
		set(player, amount + "");
	}

	public void set(String player, String value) {
		Player pl = Bukkit.getPlayerExact(player);
		if (pl != null) set(pl.getLocation(), value);
	}

	@Override
	public void parseAndSet(String player, String textValue) {
		Player pl = Bukkit.getPlayerExact(player);
		if (pl != null) set(pl.getLocation(), textValue);
	}

	@Override
	public double getValue(String player) {
		try {
			return Double.parseDouble(getStringValue(player));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	public String getStringValue(String player) {
		Player pl = Bukkit.getPlayerExact(player);
		if (pl != null) return getStringValue(pl.getLocation());
		return defaultStringValue;
	}

	@Override
	public void reset(String player) {
		Player pl = Bukkit.getPlayerExact(player);
		if (pl != null) reset(pl.getLocation());
	}

	public String getStringValue(Location location) {
		BlockVariable block = blockAt(location);
		if (block == null) return defaultStringValue;
		return block.getString();
	}

	public void set(Location location, String value) {
		BlockVariable block = blockAt(location);
		if (block == null) return;
		block.setString(value);
	}

	public void reset(Location location) {
		BlockVariable block = blockAt(location);
		if (block == null) return;
		block.reset();
	}

	/** Sets each block independently (no shared region value). */
	public void defineRegion(Set<BlockLocation> blocks, String value) {
		if (blocks == null) return;
		for (BlockLocation block : blocks) {
			Location location = toLocation(block);
			if (location != null) set(location, value);
		}
	}

	protected BlockVariable blockAt(Location location) {
		if (location == null || location.getWorld() == null) return null;
		VariableOwner owner = owner();
		if (owner == null) return null;
		return owner.block(name, location);
	}

	protected VariableOwner owner() {
		return VariableOwners.global();
	}

	protected static Location toLocation(BlockLocation block) {
		if (block == null) return null;
		World world = Bukkit.getWorld(block.getWorldName());
		if (world == null) return null;
		return new Location(world, block.getX(), block.getY(), block.getZ());
	}

}
