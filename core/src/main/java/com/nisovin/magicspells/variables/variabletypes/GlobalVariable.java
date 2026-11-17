package com.nisovin.magicspells.variables.variabletypes;

import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class GlobalVariable extends Variable {

	@Override
	public void set(String player, double amount) {
		VariableOwners.global().setNumber(name, amount);
	}

	@Override
	public double getValue(String player) {
		return VariableOwners.global().getNumber(name);
	}

	@Override
	public void reset(String player) {
		VariableOwners.global().reset(name);
	}

	/** Atomic add used by VariableManager for ADD mods so concurrent servers both count. */
	public double add(double delta) {
		VariableOwner owner = VariableOwners.global();
		return owner.add(name, delta);
	}

}
