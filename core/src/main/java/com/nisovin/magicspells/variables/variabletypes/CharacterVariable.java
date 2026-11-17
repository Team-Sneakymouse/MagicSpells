package com.nisovin.magicspells.variables.variabletypes;

import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class CharacterVariable extends Variable {

	@Override
	public void set(String player, double amount) {
		VariableOwner owner = VariableOwners.character(player);
		if (owner == null) return;
		owner.setNumber(name, amount);
	}

	@Override
	public double getValue(String player) {
		VariableOwner owner = VariableOwners.character(player);
		if (owner == null) return defaultValue;
		return owner.getNumber(name);
	}

	@Override
	public void reset(String player) {
		VariableOwner owner = VariableOwners.character(player);
		if (owner == null) return;
		owner.reset(name);
	}

	/** False when set/reset would no-op (no online player or no loaded character). */
	public boolean hasCharacterContext(String playerName) {
		return VariableOwners.character(playerName) != null;
	}

}
