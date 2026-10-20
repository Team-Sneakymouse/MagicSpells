package com.nisovin.magicspells.variables.variabletypes;

import java.util.Map;

import com.nisovin.magicspells.util.CharacterVariableValues;
import com.nisovin.magicspells.util.PlayerNameUtils;
import net.coreprotect.CoreProtect;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class CharacterStringVariable extends CharacterVariable {

	private final CharacterVariableValues<String> data;

	public CharacterStringVariable() {
		data = new CharacterVariableValues<>();
	}

	@Override
	public void loadExtraData(ConfigurationSection section) {
		super.loadExtraData(section);
		defaultStringValue = section.getString("default-value", "");
	}

	@Override
	public String getStringValue(String player) {
		String compositeKey = getCompositeKey(player);
		if (compositeKey == null) return defaultStringValue;
		return data.getOrDefault(compositeKey, defaultStringValue);
	}

	@Override
	public void parseAndSet(String player, String textValue) {
		String compositeKey = getCompositeKey(player);
		if (compositeKey == null) return;

		String current = data.get(compositeKey);

		data.put(compositeKey, textValue);
		Player p = PlayerNameUtils.getPlayerExact(player);
		if (current != null && !current.equals(textValue) && logInCoreprotect && Bukkit.getPluginManager().isPluginEnabled("CoreProtect"))
			CoreProtect.getInstance().getAPI().logCommand(p, "/ms_var " + name + " =" + textValue);
	}

	@Override
	public void reset(String player) {
		String compositeKey = getCompositeKey(player);
		if (compositeKey == null) return;

		String current = data.remove(compositeKey);
		Player p = PlayerNameUtils.getPlayerExact(player);
		if (current != null && !current.equals(defaultStringValue) && logInCoreprotect && Bukkit.getPluginManager().isPluginEnabled("CoreProtect"))
			CoreProtect.getInstance().getAPI().logCommand(p, "/ms_var " + name + " =" + defaultStringValue);
	}

	/**
	 * Gets all stored string values for this variable (used for saving).
	 * Returns map with composite keys.
	 */
	public Map<String, String> getAllStringValues() {
		return data.copy();
	}

	/**
	 * Sets a string value directly with composite key (used for loading from file).
	 */
	public void setStringWithCompositeKey(String compositeKey, String value) {
		data.put(compositeKey, value);
	}

	/** Owner-thread cursor over just this player's history; never pass live data to a writer. */
	public Map.Entry<String, String> getNextPersistenceStringValue(String player, String previous) {
		return data.next(player, previous);
	}

}

