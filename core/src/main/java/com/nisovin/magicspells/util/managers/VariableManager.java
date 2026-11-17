package com.nisovin.magicspells.util.managers;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.PlayerNameUtils;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.VariableMod;
import com.nisovin.magicspells.variables.GlobalVariableAdd;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.VariableDefinitions;
import com.nisovin.magicspells.variables.meta.*;
import com.nisovin.magicspells.variables.variabletypes.*;

import com.sneakyvariables.api.SneakyVariables;
import com.sneakyvariables.api.VariableDefinition;

public class VariableManager {

	private static final Map<String, Class<? extends Variable>> variableTypes = new HashMap<>();
	private static final Map<String, Variable> metaVariables = new HashMap<>();
	private static final Map<String, Variable> variables = new LinkedHashMap<>();

	public VariableManager() {
		initialize();
	}

	public void addVariableType(String name, Class<? extends Variable> variable) {
		variableTypes.put(name.toLowerCase(), variable);
	}

	public void addVariableType(Class<? extends Variable> variable, String name) {
		variableTypes.put(name.toLowerCase(), variable);
	}

	public void addMetaVariableType(String name, Variable variable) {
		metaVariables.put("meta_" + name.toLowerCase(), variable);
	}

	public void addMetaVariableType(Variable variable, String name) {
		metaVariables.put("meta_" + name.toLowerCase(), variable);
	}

	public Variable getVariableType(String name) {
		Class<? extends Variable> clazz = variableTypes.get(name.toLowerCase());
		if (clazz == null)
			return null;

		try {
			return clazz.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			DebugHandler.debugGeneral(e);
			return null;
		}
	}

	public Map<String, Class<? extends Variable>> getVariableTypes() {
		return variableTypes;
	}

	public Map<String, Variable> getMetaVariables() {
		return metaVariables;
	}

	private void initialize() {
		// variable types
		addVariableType("player", PlayerVariable.class);
		addVariableType("global", GlobalVariable.class);
		addVariableType("globalstring", GlobalStringVariable.class);
		addVariableType("distancetolocation", DistanceToLocationVariable.class);
		addVariableType("squareddistancetolocation", SquaredDistanceToLocationVariable.class);
		addVariableType("playerstring", PlayerStringVariable.class);
		addVariableType("character", CharacterVariable.class);
		addVariableType("characterstring", CharacterStringVariable.class);
		addVariableType("location", LocationVariable.class);
		addVariableType("locationstring", LocationStringVariable.class);
		addVariableType("playerlocation", PlayerLocationVariable.class);
		addVariableType("playerlocationstring", PlayerLocationStringVariable.class);
		addVariableType("characterlocation", CharacterLocationVariable.class);
		addVariableType("characterlocationstring", CharacterLocationStringVariable.class);

		// meta variable types
		addMetaVariableType("location_x", new CoordXVariable());
		addMetaVariableType("location_y", new CoordYVariable());
		addMetaVariableType("location_z", new CoordZVariable());
		addMetaVariableType("location_pitch", new CoordPitchVariable());
		addMetaVariableType("location_yaw", new CoordYawVariable());
		addMetaVariableType("saturation", new SaturationVariable());
		addMetaVariableType("experience_level", new ExperienceLevelVariable());
		addMetaVariableType("experience_points", new ExperienceVariable());
		addMetaVariableType("remaining_air", new RemainingAirVariable());
		addMetaVariableType("max_air", new MaximumAirVariable());
		addMetaVariableType("fly_speed", new FlySpeedVariable());
		addMetaVariableType("walk_speed", new WalkSpeedVariable());
		addMetaVariableType("food_level", new FoodLevelVariable());
		addMetaVariableType("entity_id", new EntityIDVariable());
		addMetaVariableType("fire_ticks", new FireTicksVariable());
		addMetaVariableType("fall_distance", new FallDistanceVariable());
		addMetaVariableType("players_online", new PlayersOnlineVariable());
		addMetaVariableType("max_health", new MaxHealthVariable());
		addMetaVariableType("current_health", new CurrentHealthVariable());
		addMetaVariableType("health_scale", new HealthScaleVariable());
		addMetaVariableType("compass_target_x", new CompassTargetXVariable());
		addMetaVariableType("compass_target_y", new CompassTargetYVariable());
		addMetaVariableType("compass_target_z", new CompassTargetZVariable());
		addMetaVariableType("velocity_x", new VelocityXVariable());
		addMetaVariableType("velocity_y", new VelocityYVariable());
		addMetaVariableType("velocity_z", new VelocityZVariable());
		addMetaVariableType("no_damage_ticks", new NoDamageTicksVariable());
		addMetaVariableType("max_no_damage_ticks", new MaximumNoDamageTicksVariable());
		addMetaVariableType("last_damage", new LastDamageVariable());
		addMetaVariableType("sleep_ticks", new SleepTicksVariable());
		addMetaVariableType("bed_location_x", new BedCoordXVariable());
		addMetaVariableType("bed_location_y", new BedCoordYVariable());
		addMetaVariableType("bed_location_z", new BedCoordZVariable());
		addMetaVariableType("altitude", new AltitudeVariable());
		addMetaVariableType("absorption", new AbsorptionVariable());
		addMetaVariableType("timestamp_days", new TimestampDaysVariable());
		addMetaVariableType("timestamp_hours", new TimestampHoursVariable());
		addMetaVariableType("timestamp_minutes", new TimestampMinutesVariable());
		addMetaVariableType("timestamp_seconds", new TimestampSecondsVariable());
		addMetaVariableType("timestamp_milliseconds", new TimestampMillisecondsVariable());
		addMetaVariableType("mana", new ManaVariable());
		addMetaVariableType("max_mana", new MaxManaVariable());
		addMetaVariableType("mana_regen", new ManaRegenVariable());
		addMetaVariableType("world", new WorldVariable());
		addMetaVariableType("living_entities", new LivingEntitiesVariable());
		addMetaVariableType("spawned_entities", new SpawnedEntitiesVariable());
		addMetaVariableType("freeze_ticks", new FreezeTicksVariable());
		addMetaVariableType("held_block", new HeldBlockVariable());

		// meta variable attribute types
		for (Attribute attribute : Registry.ATTRIBUTE) {
			String name = attribute.getKey().getKey().replace('.', '_').toLowerCase();
			addMetaVariableType("attribute_" + name, new AttributeVariable(attribute));
			addMetaVariableType("attribute_" + name + "_base", new AttributeBaseValueVariable(attribute));
		}
	}

	// DEBUG INFO: level 2, loaded variable (name)
	// DEBUG INFO: level 1, # variables loaded
	public void loadVariables(ConfigurationSection section) {
		checkOwner();
		SneakyVariables.api().undefineAll(MagicSpells.getInstance());
		variables.clear();

		if (section == null) {
			variables.putAll(getMetaVariables());
			for (Player player : Bukkit.getOnlinePlayers()) {
				loadBossBars(player);
				loadExpBar(player);
			}
			return;
		}

		for (String var : section.getKeys(false)) {
			ConfigurationSection varSection = section.getConfigurationSection(var);
			String path = var + ".";
			String type = section.getString(path + "type", "global");
			double def = section.getDouble(path + "default", 0);
			String min = section.getString(path + "min", 0 + "");
			String max = section.getString(path + "max", Double.MAX_VALUE + "");
			boolean perm = section.getBoolean(path + "permanent", true);
			boolean logInCoreprotect = section.getBoolean(path + "log-in-coreprotect", true);

			Variable variable = getVariableType(type);
			if (variable == null) {
				MagicSpells.error("Variable '" + var + "' has an invalid variable type defined: " + type);
				continue;
			}

			String scoreName = section.getString(path + "scoreboard-title", null);
			String scorePos = section.getString(path + "scoreboard-position", null);
			Objective objective = null;
			if (scoreName != null && scorePos != null) {
				String objName = "MSV_" + var;
				if (objName.length() > 16)
					objName = objName.substring(0, 16);
				Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
				objective = scoreboard.getObjective(objName);
				if (objective != null)
					objective.unregister();
				objective = scoreboard.registerNewObjective(objName, Criteria.DUMMY, Util.getMiniMessage(scoreName));
				if (scorePos.equalsIgnoreCase("nameplate"))
					objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
				else if (scorePos.equalsIgnoreCase("playerlist"))
					objective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
				else
					objective.setDisplaySlot(DisplaySlot.SIDEBAR);
			}

			boolean expBar = section.getBoolean(path + "exp-bar", false);

			String bossBarTitle = null;
			BarStyle bossBarStyle = null;
			BarColor bossBarColor = null;
			String bossBarNamespaceKey = null;
			// Reserve preceded handling.
			if (section.isString(path + "boss-bar"))
				bossBarTitle = section.getString(path + "boss-bar");
			else {
				ConfigurationSection bossBar = section.getConfigurationSection(path + "boss-bar");
				if (bossBar != null) {
					bossBarTitle = bossBar.getString("title");
					String style = bossBar.getString("style");
					String color = bossBar.getString("color");
					if (style != null) {
						try {
							bossBarStyle = BarStyle.valueOf(style.toUpperCase());
						} catch (IllegalArgumentException ignored) {
							MagicSpells.error(
									"Variable '" + var + "' has an invalid bossBar style defined: '" + style + "'");
						}
					}
					if (color != null) {
						try {
							bossBarColor = BarColor.valueOf(color.toUpperCase());
						} catch (IllegalArgumentException ignored) {
							MagicSpells.error(
									"Variable '" + var + "' has an invalid bossBar color defined: '" + color + "'");
						}
					}
					bossBarNamespaceKey = bossBar.getString("namespace-key");
					if (!MagicSpells.getBossBarManager().isNamespaceKey(bossBarNamespaceKey)) {
						MagicSpells.error("Variable '" + var + "' has an invalid bossBar namespace-key defined: '"
								+ bossBarNamespaceKey + "'");
					}
				}
			}
			if (bossBarStyle == null)
				bossBarStyle = BarStyle.SOLID;
			if (bossBarColor == null)
				bossBarColor = BarColor.PURPLE;
			if (bossBarNamespaceKey == null || bossBarNamespaceKey.isEmpty())
				bossBarNamespaceKey = MagicSpells.getBossBarManager().getNamespaceKeyVariable();

			VariableDefinition definition = VariableDefinitions.from(var, type, varSection);
			if (definition != null
					&& !SneakyVariables.api().define(MagicSpells.getInstance(), definition)) {
				MagicSpells.error("Variable '" + var
						+ "' could not be defined in SneakyVariables (scope/kind conflict with another plugin).");
				continue;
			}

			variable.init(var, def, min, max, perm, logInCoreprotect, objective, expBar, bossBarTitle, bossBarStyle,
					bossBarColor, bossBarNamespaceKey);
			variable.loadExtraData(varSection);
			variables.put(var, variable);
			MagicSpells.debug(2, "Loaded variable " + var);
		}

		MagicSpells.debug(1, variables.size() + " variables loaded!");

		variables.putAll(getMetaVariables());

		for (Player player : Bukkit.getOnlinePlayers()) {
			loadBossBars(player);
			loadExpBar(player);
		}
	}

	/**
	 * Adds a variable with the provided name to the list of variables.
	 * This will replace existing variables if the same name is used.
	 *
	 * @param name     the name of the variable
	 * @param variable the variable to add
	 * @return Returns true if an existing variable was overwritten
	 */
	public boolean addVariable(String name, Variable variable) {
		checkOwner();
		return variables.put(name, variable) != null;
	}

	public Map<String, Variable> getVariables() {
		return variables;
	}

	public void set(String variable, Player player, double amount) {
		set(variable, player.getName(), amount);
	}

	public void set(String variable, String player, double amount) {
		Variable var = variables.get(variable);
		set(var, player, amount);
	}

	public void set(Variable variable, String player, double amount) {
		checkOwner();
		if (variable == null)
			return;
		if (!hasOwnerContext(variable, player))
			return;
		variable.set(player, amount);
	}

	public void set(String variable, Player player, String amount) {
		set(variable, player.getName(), amount);
	}

	public void set(String variable, String player, String amount) {
		Variable var = variables.get(variable);
		set(var, player, amount);
	}

	public void set(Variable variable, String player, String amount) {
		checkOwner();
		if (variable == null)
			return;
		if (!hasOwnerContext(variable, player))
			return;
		variable.parseAndSet(player, amount);
	}

	public double getValue(String variable, Player player) {
		Variable var = variables.get(variable);
		if (var != null)
			return var.getValue(player);
		return 0D;
	}

	public String getStringValue(String variable, Player player) {
		Variable var = variables.get(variable);
		if (var != null)
			return var.getStringValue(player);
		return 0D + "";
	}

	public double getValueAtLocation(String variable, Location location) {
		Variable var = variables.get(variable);
		if (isOwnerLocationType(var))
			return var != null ? var.getDefaultValue() : 0D;
		if (var instanceof LocationVariable locVar)
			return locVar.getValue(location);
		if (var != null)
			return var.getValue("");
		return 0D;
	}

	public String getStringValueAtLocation(String variable, Location location) {
		Variable var = variables.get(variable);
		if (isOwnerLocationType(var))
			return var != null ? var.getDefaultStringValue() : "";
		if (var instanceof LocationStringVariable locVar)
			return locVar.getStringValue(location);
		if (var != null)
			return var.getStringValue("");
		return "";
	}

	public double getValue(String variable, String player) {
		Variable var = variables.get(variable);
		if (var != null)
			return var.getValue(player);
		return 0;
	}

	public String getStringValue(String variable, String player) {
		Variable var = variables.get(variable);
		if (var != null)
			return var.getStringValue(player);
		return 0D + "";
	}

	public Variable getVariable(String name) {
		return variables.get(name);
	}

	public void reset(String variable, Player player) {
		Variable var = variables.get(variable);
		reset(var, player);
	}

	public void reset(Variable variable, Player player) {
		checkOwner();
		if (variable == null)
			return;
		String playerName = player != null ? player.getName() : "";
		if (!hasOwnerContext(variable, playerName))
			return;
		variable.reset(player);
	}

	public void updateBossBar(Variable var, String player) {
		if (var == null)
			return;
		if (var.getBossBarTitle() == null)
			return;
		if (var instanceof GlobalVariable || var instanceof GlobalStringVariable) {
			double pct = var.getValue("") / var.getMaxValue(null);
			for (Player pl : Bukkit.getOnlinePlayers()) {
				if (pl == null || !pl.isValid())
					continue;
				BossBarManager.Bar bar = MagicSpells.getBossBarManager().getBar(pl, var.getBossBarNamespacedKey());
				if (bar == null)
					continue;
				bar.set(var.getBossBarTitle(), pct, var.getBossBarStyle(), var.getBossBarColor());
			}
			return;
		}
		if (player == null || player.isEmpty())
			return;
		if (var instanceof CharacterVariable || var instanceof CharacterStringVariable
				|| var instanceof PlayerVariable) {
			Player pl = PlayerNameUtils.getPlayerExact(player);
			if (pl == null)
				return;
			BossBarManager.Bar bar = MagicSpells.getBossBarManager().getBar(pl, var.getBossBarNamespacedKey());
			if (bar == null)
				return;
			bar.set(var.getBossBarTitle(), var.getValue(pl) / var.getMaxValue(pl), var.getBossBarStyle(),
					var.getBossBarColor());
		}
	}

	public void updateExpBar(Variable var, String player) {
		if (var == null)
			return;
		if (!var.isDisplayedOnExpBar())
			return;
		if (var instanceof GlobalVariable) {
			double pct = var.getValue("") / var.getMaxValue(null);
			Util.forEachPlayerOnline(p -> p.sendExperienceChange((float) pct, (int) var.getValue("")));
			return;
		}
		if (player == null || player.isEmpty())
			return;
		if (var instanceof CharacterVariable || var instanceof CharacterStringVariable
				|| var instanceof PlayerVariable) {
			Player p = PlayerNameUtils.getPlayerExact(player);
			if (p == null)
				return;
			p.sendExperienceChange((float) (var.getValue(p) / var.getMaxValue(p)), (int) var.getValue(p));
		}
	}

	public void loadBossBars(Player player) {
		for (Variable var : variables.values()) {
			if (var.getBossBarTitle() == null)
				continue;
			MagicSpells.getBossBarManager().getBar(player, var.getBossBarNamespacedKey()).set(var.getBossBarTitle(),
					var.getValue(player) / var.getMaxValue(player), var.getBossBarStyle(), var.getBossBarColor());
		}
	}

	public void loadExpBar(Player player) {
		for (Variable var : variables.values()) {
			if (!var.isDisplayedOnExpBar())
				continue;
			player.sendExperienceChange((float) (var.getValue(player) / var.getMaxValue(player)),
					(int) var.getValue(player));
			break;
		}
	}

	public void disable() {
		checkOwner();
		SneakyVariables.api().undefineAll(MagicSpells.getInstance());
		variables.clear();
	}

	public String processVariableMods(String var, VariableMod mod, Player playerToMod, Player caster, Player target,
			float power, String[] args) {
		if (mod == null)
			return 0 + "";
		Variable variable = getVariable(var);
		if (variable == null)
			return 0 + "";
		return processVariableMods(variable, mod, playerToMod, caster, target, power, args);
	}

	public String processVariableModsAtLocation(String var, VariableMod mod, Location locationToMod, Player caster,
			Player target,
			float power, String[] args) {
		if (mod == null)
			return 0 + "";
		Variable variable = getVariable(var);
		if (variable == null)
			return 0 + "";
		return processVariableModsAtLocation(variable, mod, locationToMod, caster, target, power, args);
	}

	public String processVariableMods(Variable variable, VariableMod mod, Player playerToMod, Player caster,
			Player target, float power, String[] args) {
		checkOwner();
		VariableMod.Operation op = mod.getOperation();

		if (variable instanceof PlayerStringVariable || variable instanceof CharacterStringVariable
				|| variable instanceof GlobalStringVariable) {
			switch (op) {
				case SET -> {
					String value = mod.getStringValue(caster, target, null, args);
					if (value.equals(variable.getDefaultStringValue()))
						reset(variable, playerToMod);
					else
						set(variable, playerToMod != null ? playerToMod.getName() : "", value);
					return value;
				}
				case ADD -> {
					String value = variable.getStringValue(playerToMod != null ? playerToMod.getName() : "")
							+ mod.getStringValue(caster, target, null, args);
					if (value.equals(variable.getDefaultStringValue()))
						reset(variable, playerToMod);
					else
						set(variable, playerToMod != null ? playerToMod.getName() : "", value);
					return value;
				}
				case MULTIPLY -> {
					int count = (int) mod.getValue(caster, target, power, args);
					String value = variable.getStringValue(playerToMod != null ? playerToMod.getName() : "")
							.repeat(count);
					if (value.equals(variable.getDefaultStringValue()))
						reset(variable, playerToMod);
					else
						set(variable, playerToMod != null ? playerToMod.getName() : "", value);
					return value;
				}
			}
		}

		if (variable instanceof GlobalVariable global && GlobalVariableAdd.shouldUseAdd(op.name())) {
			double delta = mod.getValue(caster, target, power, args);
			double value = global.add(delta);
			if (value == variable.getDefaultValue())
				reset(variable, playerToMod);
			return Double.toString(value);
		}

		double value = op.applyTo(variable.getValue(playerToMod), mod.getValue(caster, target, power, args));

		if (value == variable.getDefaultValue() && !(variable instanceof MetaVariable)) {
			reset(variable, playerToMod);
		} else {
			set(variable, playerToMod != null ? playerToMod.getName() : "", value);
		}

		return Double.toString(value);
	}

	public String processVariableModsAtLocation(Variable variable, VariableMod mod, Location locationToMod,
			Player caster,
			Player target, float power, String[] args) {
		checkOwner();
		VariableMod.Operation op = mod.getOperation();

		if (variable instanceof PlayerLocationStringVariable plsv) {
			return processOwnerLocationString(plsv, mod, op, locationToMod, caster, target, power, args);
		}
		if (variable instanceof CharacterLocationStringVariable clsv) {
			return processOwnerLocationString(clsv, mod, op, locationToMod, caster, target, power, args);
		}
		if (variable instanceof LocationStringVariable lsv) {
			switch (op) {
				case SET -> {
					String value = mod.getStringValue(caster, target, locationToMod, args);
					if (value.equals(variable.getDefaultStringValue()))
						lsv.reset(locationToMod);
					else
						lsv.set(locationToMod, value);
					return value;
				}
				case ADD -> {
					String value = lsv.getStringValue(locationToMod)
							+ mod.getStringValue(caster, target, locationToMod, args);
					if (value.equals(variable.getDefaultStringValue()))
						lsv.reset(locationToMod);
					else
						lsv.set(locationToMod, value);
					return value;
				}
				case MULTIPLY -> {
					int count = (int) mod.getValue(caster, target, locationToMod, power, args);
					String value = lsv.getStringValue(locationToMod).repeat(count);
					if (value.equals(variable.getDefaultStringValue()))
						lsv.reset(locationToMod);
					else
						lsv.set(locationToMod, value);
					return value;
				}
			}
		}

		if (variable instanceof PlayerLocationVariable plv) {
			if (caster == null) return 0 + "";
			double value = op.applyTo(plv.getValue(caster, locationToMod),
					mod.getValue(caster, target, locationToMod, power, args));
			if (value == variable.getDefaultValue())
				plv.reset(caster, locationToMod);
			else
				plv.set(caster, locationToMod, value);
			return Double.toString(value);
		}
		if (variable instanceof CharacterLocationVariable clv) {
			if (caster == null) return 0 + "";
			double value = op.applyTo(clv.getValue(caster, locationToMod),
					mod.getValue(caster, target, locationToMod, power, args));
			if (value == variable.getDefaultValue())
				clv.reset(caster, locationToMod);
			else
				clv.set(caster, locationToMod, value);
			return Double.toString(value);
		}
		if (variable instanceof LocationVariable lv) {
			double value = op.applyTo(lv.getValue(locationToMod),
					mod.getValue(caster, target, locationToMod, power, args));
			if (value == variable.getDefaultValue())
				lv.reset(locationToMod);
			else
				lv.set(locationToMod, value);
			return Double.toString(value);
		}
		return 0 + "";
	}

	private String processOwnerLocationString(PlayerLocationStringVariable variable, VariableMod mod,
			VariableMod.Operation op, Location locationToMod, Player caster, Player target, float power, String[] args) {
		if (caster == null) return 0 + "";
		switch (op) {
			case SET -> {
				String value = mod.getStringValue(caster, target, locationToMod, args);
				if (value.equals(variable.getDefaultStringValue()))
					variable.reset(caster, locationToMod);
				else
					variable.set(caster, locationToMod, value);
				return value;
			}
			case ADD -> {
				String value = variable.getStringValue(caster, locationToMod)
						+ mod.getStringValue(caster, target, locationToMod, args);
				if (value.equals(variable.getDefaultStringValue()))
					variable.reset(caster, locationToMod);
				else
					variable.set(caster, locationToMod, value);
				return value;
			}
			case MULTIPLY -> {
				int count = (int) mod.getValue(caster, target, locationToMod, power, args);
				String value = variable.getStringValue(caster, locationToMod).repeat(count);
				if (value.equals(variable.getDefaultStringValue()))
					variable.reset(caster, locationToMod);
				else
					variable.set(caster, locationToMod, value);
				return value;
			}
			default -> {
				return 0 + "";
			}
		}
	}

	private String processOwnerLocationString(CharacterLocationStringVariable variable, VariableMod mod,
			VariableMod.Operation op, Location locationToMod, Player caster, Player target, float power, String[] args) {
		if (caster == null) return 0 + "";
		switch (op) {
			case SET -> {
				String value = mod.getStringValue(caster, target, locationToMod, args);
				if (value.equals(variable.getDefaultStringValue()))
					variable.reset(caster, locationToMod);
				else
					variable.set(caster, locationToMod, value);
				return value;
			}
			case ADD -> {
				String value = variable.getStringValue(caster, locationToMod)
						+ mod.getStringValue(caster, target, locationToMod, args);
				if (value.equals(variable.getDefaultStringValue()))
					variable.reset(caster, locationToMod);
				else
					variable.set(caster, locationToMod, value);
				return value;
			}
			case MULTIPLY -> {
				int count = (int) mod.getValue(caster, target, locationToMod, power, args);
				String value = variable.getStringValue(caster, locationToMod).repeat(count);
				if (value.equals(variable.getDefaultStringValue()))
					variable.reset(caster, locationToMod);
				else
					variable.set(caster, locationToMod, value);
				return value;
			}
			default -> {
				return 0 + "";
			}
		}
	}

	private static boolean hasOwnerContext(Variable variable, String player) {
		if (variable instanceof CharacterVariable character && !character.hasCharacterContext(player))
			return false;
		if (variable instanceof CharacterLocationVariable character && !character.hasCharacterContext(player))
			return false;
		if (variable instanceof CharacterLocationStringVariable character && !character.hasCharacterContext(player))
			return false;
		return true;
	}

	private static boolean isOwnerLocationType(Variable var) {
		return var instanceof PlayerLocationVariable
				|| var instanceof PlayerLocationStringVariable
				|| var instanceof CharacterLocationVariable
				|| var instanceof CharacterLocationStringVariable;
	}

	private void checkOwner() {
		if (!Bukkit.isPrimaryThread())
			throw new IllegalStateException("Variable access requires the main thread");
	}

}
