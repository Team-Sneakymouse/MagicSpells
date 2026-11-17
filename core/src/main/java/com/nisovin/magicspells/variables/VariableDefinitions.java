package com.nisovin.magicspells.variables;

import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.FunctionData;

import com.sneakyvariables.api.Kind;
import com.sneakyvariables.api.Scope;
import com.sneakyvariables.api.VariableDefinition;

/**
 * Builds SneakyVariables definitions from MagicSpells YAML variable sections.
 * Returns null for computed / unknown types that are never stored.
 */
public final class VariableDefinitions {

	public static final double DEFAULT_MIN = 0;
	public static final double DEFAULT_MAX = Double.MAX_VALUE;

	private VariableDefinitions() {
	}

	/**
	 * @return a definition to register, or null when the type is computed/unknown
	 */
	public static VariableDefinition from(String name, String type, ConfigurationSection section) {
		if (name == null || name.isBlank() || type == null) return null;

		VariableDefinition.Builder builder = switch (type.toLowerCase()) {
			case "player" -> VariableDefinition.number(name, Scope.PLAYER);
			case "playerstring" -> VariableDefinition.string(name, Scope.PLAYER);
			case "character" -> VariableDefinition.number(name, Scope.CHARACTER);
			case "characterstring" -> VariableDefinition.string(name, Scope.CHARACTER);
			case "global" -> VariableDefinition.number(name, Scope.GLOBAL);
			case "globalstring" -> VariableDefinition.string(name, Scope.GLOBAL);
			case "location" -> VariableDefinition.blockNumber(name, Scope.GLOBAL);
			case "locationstring" -> VariableDefinition.blockString(name, Scope.GLOBAL);
			case "playerlocation" -> VariableDefinition.blockNumber(name, Scope.PLAYER);
			case "playerlocationstring" -> VariableDefinition.blockString(name, Scope.PLAYER);
			case "characterlocation" -> VariableDefinition.blockNumber(name, Scope.CHARACTER);
			case "characterlocationstring" -> VariableDefinition.blockString(name, Scope.CHARACTER);
			default -> null;
		};
		if (builder == null) return null;

		boolean permanent = section == null || section.getBoolean("permanent", true);
		builder.persistent(permanent);

		Kind kind = kindFor(type.toLowerCase());
		if (kind.isNumber()) {
			double def = section != null ? section.getDouble("default", 0) : 0;
			builder.defaultValue(def);
			applyBounds(builder, section);
		} else {
			String def = section != null ? section.getString("default-value", "") : "";
			builder.defaultValue(def != null ? def : "");
		}

		return builder.build();
	}

	private static Kind kindFor(String type) {
		return switch (type) {
			case "playerstring", "characterstring", "globalstring" -> Kind.STRING;
			case "locationstring", "playerlocationstring", "characterlocationstring" -> Kind.BLOCK_STRING;
			case "location", "playerlocation", "characterlocation" -> Kind.BLOCK_NUMBER;
			default -> Kind.NUMBER;
		};
	}

	private static void applyBounds(VariableDefinition.Builder builder, ConfigurationSection section) {
		String minStr = section != null ? section.getString("min", DEFAULT_MIN + "") : DEFAULT_MIN + "";
		String maxStr = section != null ? section.getString("max", DEFAULT_MAX + "") : DEFAULT_MAX + "";
		if (minStr == null) minStr = DEFAULT_MIN + "";
		if (maxStr == null) maxStr = DEFAULT_MAX + "";

		applyBound(minStr, DEFAULT_MIN, builder::min, builder::minFunction);
		applyBound(maxStr, DEFAULT_MAX, builder::max, builder::maxFunction);
	}

	private static void applyBound(String raw, double fallback, DoubleConsumer staticBound,
			java.util.function.Consumer<ToDoubleFunction<Player>> functionBound) {
		try {
			staticBound.accept(Double.parseDouble(raw));
			return;
		} catch (NumberFormatException ignored) {
			// Expression bound.
		}

		staticBound.accept(fallback);
		FunctionData<Double> data = FunctionData.build(raw, Function.identity(), true);
		if (data == null) return;
		functionBound.accept(playerBound(data, fallback));
	}

	private static ToDoubleFunction<Player> playerBound(ConfigData<Double> data, double fallback) {
		return player -> {
			Double value = data.get(player, null, 1f, null);
			return value != null ? value : fallback;
		};
	}

}
