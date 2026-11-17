package com.nisovin.magicspells.variables.variabletypes;

import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class PlayerVariable extends Variable {

	@Override
	public void set(String player, double amount) {
		VariableOwner owner = VariableOwners.player(player);
		if (owner == null) return;
		owner.setNumber(name, amount);
	}

	@Override
	public double getValue(String player) {
		VariableOwner owner = VariableOwners.player(player);
		if (owner == null) return defaultValue;
		return owner.getNumber(name);
	}

	@Override
	public void reset(String player) {
		VariableOwner owner = VariableOwners.player(player);
		if (owner == null) return;
		owner.reset(name);
	}

}
