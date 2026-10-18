package com.nisovin.magicspells.variables.variabletypes;

import java.util.Map;
import java.util.HashMap;

import com.nisovin.magicspells.util.PlayerNameUtils;
import net.coreprotect.CoreProtect;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class CharacterStringVariable extends CharacterVariable {

	private final Map<String, String> data;

	public CharacterStringVariable() {
		data = new HashMap<>();
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
		return new HashMap<>(data);
	}

	/**
	 * Sets a string value directly with composite key (used for loading from file).
	 */
	public void setStringWithCompositeKey(String compositeKey, String value) {
		data.put(compositeKey, value);
	}

	/** Live read-only persistence view. Traverse only on the server thread; never pass to a writer. */
	public Map<String, String> getPersistenceStringValues() {
		return java.util.Collections.unmodifiableMap(data);
	}

}

