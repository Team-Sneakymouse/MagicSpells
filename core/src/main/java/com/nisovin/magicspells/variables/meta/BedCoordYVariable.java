package com.nisovin.magicspells.variables.meta;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.util.PlayerNameUtils;
import com.nisovin.magicspells.variables.variabletypes.MetaVariable;

public class BedCoordYVariable extends MetaVariable {

	@Override
	public double getValue(String player) {
		Player p = PlayerNameUtils.getPlayerExact(player);
		if (p == null) return 0D;

		Location bedSpawnLocation = p.getRespawnLocation();
		if (bedSpawnLocation != null) return bedSpawnLocation.getY();
		return p.getWorld().getSpawnLocation().getY();
	}

	@Override
	public void set(String player, double amount) {
		Player p = PlayerNameUtils.getPlayerExact(player);
		if (p == null) return;

		Location to = p.getRespawnLocation();
		if (to == null) return;
		to.setY(amount);
		p.setRespawnLocation(to, true);
	}

}
