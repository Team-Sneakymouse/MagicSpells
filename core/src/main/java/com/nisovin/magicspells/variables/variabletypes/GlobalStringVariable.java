package com.nisovin.magicspells.variables.variabletypes;

import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class GlobalStringVariable extends Variable {

	@Override
	public void loadExtraData(ConfigurationSection section) {
		super.loadExtraData(section);
		defaultStringValue = section.getString("default-value", "");
	}

	@Override
	protected void init() {
		// Defaults come from definition / defaultStringValue.
	}

	@Override
	public void set(String player, double amount) {
		// String variables ignore numeric set.
	}

	@Override
	public double getValue(String player) {
		return 0;
	}

	@Override
	public String getStringValue(String player) {
		return VariableOwners.global().getString(name);
	}

	@Override
	public void parseAndSet(String player, String textValue) {
		VariableOwners.global().setString(name, textValue);
	}

	@Override
	public void reset(String player) {
		VariableOwner owner = VariableOwners.global();
		owner.reset(name);
	}

}
