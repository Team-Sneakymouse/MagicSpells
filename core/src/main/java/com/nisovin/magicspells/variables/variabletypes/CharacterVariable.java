package com.nisovin.magicspells.variables.variabletypes;

import java.util.Map;

import net.coreprotect.CoreProtect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.util.CharacterVariableValues;
import com.nisovin.magicspells.util.PlayerNameUtils;
import com.nisovin.magicspells.variables.Variable;

public class CharacterVariable extends Variable {

	private final CharacterVariableValues<Double> map = new CharacterVariableValues<>();
	private static final double EPSILON = 1e-10;

	@Override
	public void set(String player, double amount) {
		String compositeKey = getCompositeKey(player);
		if (compositeKey == null) return;

		Double current = map.get(compositeKey);

		Player p = PlayerNameUtils.getPlayerExact(player);

		double min = getMinValue(p);
		double max = getMaxValue(p);

		if (amount > max) amount = max;
		else if (amount < min) amount = min;
		map.put(compositeKey, amount);

		if (current != null) {
			double change = amount - current;

			if (Math.abs(change) > EPSILON && logInCoreprotect && Bukkit.getPluginManager().isPluginEnabled("CoreProtect"))
				CoreProtect.getInstance().getAPI().logCommand(p, "/ms_var " + name + ((change < 0D) ? " " : " +") + change);
		}

		if (objective == null) return;
		objective.getScore(player).setScore((int) amount);
	}

	@Override
	public double getValue(String player) {
		String compositeKey = getCompositeKey(player);
		if (compositeKey == null) return defaultValue;
		return map.getOrDefault(compositeKey, defaultValue);
	}

	@Override
	public void reset(String player) {
		String compositeKey = getCompositeKey(player);
		if (compositeKey == null) return;

		double current = map.getOrDefault(compositeKey, defaultValue);

		Player p = PlayerNameUtils.getPlayerExact(player);

		map.remove(compositeKey);

		double change = defaultValue - current;
		if (Math.abs(change) > EPSILON && logInCoreprotect && Bukkit.getPluginManager().isPluginEnabled("CoreProtect"))
			CoreProtect.getInstance().getAPI().logCommand(p, "/ms_var " + name + ((change < 0D) ? " " : " +") + change);

		if (objective == null) return;
		objective.getScore(player).setScore((int) defaultValue);
	}

	/** False when set/reset would no-op (no online player or no loaded character). */
	public boolean hasCharacterContext(String playerName) {
		return getCompositeKey(playerName) != null;
	}

	/**
	 * Gets the composite key for a player combining player name and character UUID.
	 * Returns null if character manager is not available or no character is loaded.
	 */
	protected String getCompositeKey(String playerName) {
		Player player = PlayerNameUtils.getPlayerExact(playerName);
		if (player == null) return null;

		String characterUUID = getCharacterUUID(player);
		if (characterUUID == null) return null;

		return playerName + ":" + characterUUID;
	}

	/**
	 * Gets the character UUID for a player using the SneakyCharacterManager API.
	 * Returns null if the plugin is not available.
	 */
	protected String getCharacterUUID(Player player) {
		if (!Bukkit.getPluginManager().isPluginEnabled("SneakyCharacterManager")) return null;

		try {
			// Use reflection to avoid hard dependency
			Class<?> characterClass = Class.forName("net.sneakycharactermanager.paper.handlers.character.Character");
			Object character = characterClass.getMethod("get", Player.class).invoke(null, player);
			if (character == null) return null;
			return (String) characterClass.getMethod("getCharacterUUID").invoke(character);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Gets all stored values for this variable (used for saving).
	 * Returns map with composite keys.
	 */
	public Map<String, Double> getAllValues() {
		return map.copy();
	}

	/**
	 * Sets a value directly with composite key (used for loading from file).
	 */
	public void setWithCompositeKey(String compositeKey, double value) {
		map.put(compositeKey, value);
	}

	/** Owner-thread cursor over just this player's history; never pass live data to a writer. */
	public Map.Entry<String, Double> getNextPersistenceValue(String player, String previous) {
		return map.next(player, previous);
	}

}

