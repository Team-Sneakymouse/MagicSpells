package com.nisovin.magicspells.variables.variabletypes;

import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.variables.VariableOwners;

import com.sneakyvariables.api.VariableOwner;

public class PlayerStringVariable extends PlayerVariable {

	@Override
	public void loadExtraData(ConfigurationSection section) {
		super.loadExtraData(section);
		defaultStringValue = section.getString("default-value", "");
	}

	@Override
	public String getStringValue(String player) {
		VariableOwner owner = VariableOwners.player(player);
		if (owner == null) return defaultStringValue;
		return owner.getString(name);
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
	public void set(String player, double amount) {
		parseAndSet(player, Double.toString(amount));
	}

	@Override
	public void parseAndSet(String player, String textValue) {
		VariableOwner owner = VariableOwners.player(player);
		if (owner == null) return;
		owner.setString(name, textValue);
	}

	@Override
	public void reset(String player) {
		VariableOwner owner = VariableOwners.player(player);
		if (owner == null) return;
		owner.reset(name);
	}

}
