package com.nisovin.magicspells.util.managers;

import java.util.*;
import java.io.File;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Location;

import com.nisovin.magicspells.util.BlockLocation;
import com.nisovin.magicspells.util.Region;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.io.OrderedVariableWriter;
import com.nisovin.magicspells.util.io.DirtyVariableScopes;
import com.nisovin.magicspells.util.io.VariableSnapshotCapture;
import com.nisovin.magicspells.variables.*;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.VariableMod;
import com.nisovin.magicspells.variables.meta.*;
import com.nisovin.magicspells.util.PlayerNameUtils;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.variables.variabletypes.*;

public class VariableManager {

	private static final Map<String, Class<? extends Variable>> variableTypes = new HashMap<>();
	private static final Map<String, Variable> metaVariables = new HashMap<>();
	private static final Map<String, Variable> variables = new LinkedHashMap<>();
	private static final DirtyVariableScopes dirtyPlayerVars = new DirtyVariableScopes();

	private static final DirtyVariableScopes dirtyScopes = new DirtyVariableScopes();
	private static File folder;

	private final Thread owner = Thread.currentThread();
	private final OrderedVariableWriter writer = new OrderedVariableWriter();
	private int saveTask = -1;
	private int ticksUntilSave = TimeUtil.TICKS_PER_MINUTE;
	private Deque<String> routinePlayers;
	private boolean routineGlobals;
	private boolean routineLocations;
	private SnapshotCapture capture;
	private boolean closed;
	private long lastSnapshotNanos;
	private long failures;

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

	public Set<String> getDirtyPlayerVariables() {
		return dirtyPlayerVars;
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
		if (section == null) {
			variables.putAll(getMetaVariables());

			// Load vars
			folder = new File(MagicSpells.getInstance().getDataFolder(), "vars");
			if (!folder.exists())
				folder.mkdir();
			loadGlobalVariables();
			loadLocationVariables();
			for (Player player : Bukkit.getOnlinePlayers()) {
				loadPlayerVariables(player.getName(), Util.getUniqueId(player));
				loadBossBars(player);
				loadExpBar(player);
			}

			startSaveTask();
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

			// Check if character manager is required but not available
			if ((variable instanceof CharacterVariable || variable instanceof CharacterStringVariable)
					&& !Bukkit.getPluginManager().isPluginEnabled("SneakyCharacterManager")) {
				MagicSpells.error("Variable '" + var
						+ "' is a character variable but SneakyCharacterManager plugin is not enabled. This variable will not function properly.");
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

			variable.init(var, def, min, max, perm, logInCoreprotect, objective, expBar, bossBarTitle, bossBarStyle,
					bossBarColor, bossBarNamespaceKey);
			variable.loadExtraData(varSection);
			variables.put(var, variable);
			MagicSpells.debug(2, "Loaded variable " + var);
		}

		MagicSpells.debug(1, variables.size() + " variables loaded!");

		variables.putAll(getMetaVariables());

		// Load vars
		folder = new File(MagicSpells.getInstance().getDataFolder(), "vars");
		if (!folder.exists())
			folder.mkdir();

		loadGlobalVariables();
		loadLocationVariables();
		for (Player player : Bukkit.getOnlinePlayers()) {
			loadPlayerVariables(player.getName(), Util.getUniqueId(player));
			loadBossBars(player);
			loadExpBar(player);
		}

		startSaveTask();
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
		variable.set(player, amount);
		updateBossBar(variable, player);
		updateExpBar(variable, player);
		if (!variable.isPermanent())
			return;
		if (variable instanceof CharacterVariable || variable instanceof CharacterStringVariable)
			dirtyPlayerVars.add(player);
		else if (variable instanceof PlayerVariable)
			dirtyPlayerVars.add(player);
		else if (variable instanceof GlobalVariable)
			dirtyScopes.add("global");
		else if (variable instanceof GlobalStringVariable)
			dirtyScopes.add("global");
		else if (variable instanceof LocationVariable)
			dirtyScopes.add("location");
		else if (variable instanceof LocationStringVariable)
			dirtyScopes.add("location");
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
		variable.parseAndSet(player, amount);
		updateBossBar(variable, player);
		updateExpBar(variable, player);
		if (!variable.isPermanent())
			return;
		if (variable instanceof CharacterVariable || variable instanceof CharacterStringVariable)
			dirtyPlayerVars.add(player);
		else if (variable instanceof PlayerVariable)
			dirtyPlayerVars.add(player);
		else if (variable instanceof GlobalVariable)
			dirtyScopes.add("global");
		else if (variable instanceof GlobalStringVariable)
			dirtyScopes.add("global");
		else if (variable instanceof LocationVariable || variable instanceof LocationStringVariable)
			dirtyScopes.add("location");
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
		if (var instanceof LocationVariable locVar)
			return locVar.getValue(location);
		if (var != null)
			return var.getValue("");
		return 0D;
	}

	public String getStringValueAtLocation(String variable, Location location) {
		Variable var = variables.get(variable);
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
		variable.reset(player);
		updateBossBar(variable, player != null ? player.getName() : "");
		updateExpBar(variable, player != null ? player.getName() : "");
		if (!variable.isPermanent())
			return;
		if (variable instanceof CharacterVariable || variable instanceof CharacterStringVariable)
			dirtyPlayerVars.add(player != null ? player.getName() : "");
		else if (variable instanceof PlayerVariable)
			dirtyPlayerVars.add(player != null ? player.getName() : "");
		else if (variable instanceof GlobalVariable)
			dirtyScopes.add("global");
		else if (variable instanceof GlobalStringVariable)
			dirtyScopes.add("global");
		else if (variable instanceof LocationVariable)
			dirtyScopes.add("location");
		else if (variable instanceof LocationStringVariable)
			dirtyScopes.add("location");
	}

	public void updateBossBar(Variable var, String player) {
		if (var == null)
			return;
		if (var.getBossBarTitle() == null)
			return;
		if (player == null || player.isEmpty())
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
		if (player == null || player.isEmpty())
			return;
		if (var instanceof GlobalVariable) {
			double pct = var.getValue("") / var.getMaxValue(null);
			Util.forEachPlayerOnline(p -> p.sendExperienceChange((float) pct, (int) var.getValue("")));
			return;
		}
		if (var instanceof CharacterVariable || var instanceof CharacterStringVariable
				|| var instanceof PlayerVariable) {
			Player p = PlayerNameUtils.getPlayerExact(player);
			if (p == null)
				return;
			p.sendExperienceChange((float) (var.getValue(p) / var.getMaxValue(p)), (int) var.getValue(p));
		}
	}

	public void loadGlobalVariables() {
		flushPending();
		if (dirtyScopes.contains("global")) {
			saveGlobalVariables();
			if (dirtyScopes.contains("global")) throw new IllegalStateException("Cannot load over unsaved variable state");
		}
		File file = new File(folder, "GLOBAL.txt");
		if (!file.exists()) {
			dirtyScopes.remove("global");
			return;
		}

		try {
			Scanner scanner = new Scanner(file, StandardCharsets.UTF_8);
			while (scanner.hasNext()) {
				String line = scanner.nextLine().trim();
				if (!line.isEmpty()) {
					String[] s = line.split("=", 2);
					Variable variable = variables.get(s[0]);
					if (variable instanceof GlobalVariable && variable.isPermanent())
						variable.parseAndSet("", s[1]);
					else if (variable instanceof GlobalStringVariable && variable.isPermanent())
						variable.parseAndSet("", s[1]);
				}
			}
			scanner.close();
		} catch (Exception e) {
			MagicSpells.error("ERROR LOADING GLOBAL VARIABLES");
			MagicSpells.handleException(e);
		}

		dirtyScopes.remove("global");
	}

	public void saveGlobalVariables() {
		flushPending();
		submitGlobal();
		flushPending();
	}

	private void submitGlobal() {
		captureNow("global", null, null);
	}

	public void loadPlayerVariables(String player, String uniqueId) {
		flushPending();
		if (dirtyPlayerVars.contains(player)) {
			savePlayerVariables(player, uniqueId);
			if (dirtyPlayerVars.contains(player)) throw new IllegalStateException("Cannot load over unsaved variable state");
		}
		File file = new File(folder, "PLAYER_" + uniqueId + ".txt");
		if (!file.exists()) {
			File file2 = new File(folder, "PLAYER_" + player + ".txt");
			if (file2.exists())
				file2.renameTo(file);
		}
		if (!file.exists()) {
			dirtyPlayerVars.remove(player);
			return;
		}

		try {
			Scanner scanner = new Scanner(file, StandardCharsets.UTF_8);
			while (scanner.hasNext()) {
				String line = scanner.nextLine().trim();
				if (!line.isEmpty()) {
					String[] s = line.split("=", 2);
					if (s.length < 2)
						continue;

					// Check if this is a character variable (contains :characterUUID in the key)
					String key = s[0];
					if (key.contains(":")) {
						// Character variable format: variableName:characterUUID=value
						String[] keyParts = key.split(":", 2);
						String varName = keyParts[0];
						String compositeKey = player + ":" + keyParts[1]; // player:characterUUID

						Variable variable = variables.get(varName);
						// Check CharacterStringVariable first since it extends CharacterVariable
						if (variable instanceof CharacterStringVariable && variable.isPermanent()) {
							((CharacterStringVariable) variable).setStringWithCompositeKey(compositeKey, s[1]);
						} else if (variable instanceof CharacterVariable && variable.isPermanent()) {
							((CharacterVariable) variable).setWithCompositeKey(compositeKey, Double.parseDouble(s[1]));
						}
					} else {
						// Regular player variable
						Variable variable = variables.get(key);
						if (variable instanceof PlayerVariable && variable.isPermanent())
							variable.parseAndSet(player, s[1]);
					}
				}
			}
			scanner.close();
		} catch (Exception e) {
			MagicSpells.error("ERROR LOADING PLAYER VARIABLES FOR " + player);
			MagicSpells.handleException(e);
		}

		dirtyPlayerVars.remove(player);
	}

	public void savePlayerVariables(String player, String uniqueId) {
		flushPending();
		submitPlayer(player, uniqueId);
		flushPending();
	}

	private void submitPlayer(String player, String uniqueId) {
		captureNow("player", player, uniqueId);
	}

	public void loadLocationVariables() {
		flushPending();
		if (dirtyScopes.contains("location")) {
			saveLocationVariables();
			if (dirtyScopes.contains("location")) throw new IllegalStateException("Cannot load over unsaved variable state");
		}
		for (String varName : variables.keySet()) {
			Variable variable = variables.get(varName);
			if (!(variable instanceof LocationVariable || variable instanceof LocationStringVariable))
				continue;
			if (!variable.isPermanent())
				continue;

			File file = new File(folder, "LOC_" + varName + ".txt");
			if (!file.exists())
				continue;

			try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8)) {
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine().trim();
					if (line.isEmpty())
						continue;
					String[] s = line.split("=", 2);
					if (s.length < 2)
						continue;

					BlockLocation loc = BlockLocation.fromString(s[0]);
					if (loc == null)
						continue;

					if (variable instanceof LocationVariable lv) {
						lv.defineRegion(Collections.singleton(loc), Double.parseDouble(s[1]));
					} else if (variable instanceof LocationStringVariable lsv) {
						lsv.defineRegion(Collections.singleton(loc), s[1]);
					}
				}
			} catch (Exception e) {
				MagicSpells.error("ERROR LOADING LOCATION VARIABLE " + varName);
				MagicSpells.handleException(e);
			}
		}
		dirtyScopes.remove("location");
	}

	public void saveLocationVariables() {
		flushPending();
		submitLocations();
		flushPending();
	}

	private void submitLocations() {
		captureNow("location", null, null);
	}

	private void captureNow(String kind, String player, String uid) {
		SnapshotCapture snapshot = new SnapshotCapture(kind, player, uid);
		while (!snapshot.advance()) { /* Explicit lifecycle saves may block. */ }
		submit(snapshot);
	}

	private final class SnapshotCapture {
		private final VariableSnapshotCapture data;
		private final DirtyVariableScopes dirty;
		private final String key;
		private final long generation;

		private SnapshotCapture(String kind, String player, String uid) {
			dirty = player == null ? dirtyScopes : dirtyPlayerVars;
			key = player == null ? kind : player;
			generation = dirty.generation(key);
			data = new VariableSnapshotCapture(kind, player, uid, folder, variables);
		}

		private boolean advance() { return data.advance(); }
	}

	private void startSaveTask() {
		if (saveTask != -1) MagicSpells.cancelTask(saveTask);
		saveTask = MagicSpells.scheduleRepeatingTask(this::tickPersistence, 1, 1);
	}

	private void tickPersistence() {
		checkOwner();
		acknowledge(writer.poll());
		if (--ticksUntilSave <= 0) {
			ticksUntilSave = TimeUtil.TICKS_PER_MINUTE;
			// Coalesce repeated triggers: do not construct snapshots while storage is busy.
			if (routinePlayers == null) {
				routinePlayers = new ArrayDeque<>(dirtyPlayerVars);
				routineGlobals = dirtyScopes.contains("global");
				routineLocations = dirtyScopes.contains("location");
			}
		}
		if (writer.isBusy() || routinePlayers == null) return;
		if (capture != null) {
			try {
				if (capture.advance()) {
					submit(capture);
					capture = null;
				}
			} catch (ConcurrentModificationException e) {
				// A map changed between ticks. Nothing was acknowledged; retry next save cycle.
				capture = null;
				MagicSpells.debug(2, "Variable snapshot invalidated; dirty generation retained.");
			}
			return;
		}
		if (routineGlobals) {
			routineGlobals = false;
			if (dirtyScopes.contains("global")) capture = new SnapshotCapture("global", null, null);
		} else if (routineLocations) {
			routineLocations = false;
			if (dirtyScopes.contains("location")) capture = new SnapshotCapture("location", null, null);
		} else if (!routinePlayers.isEmpty()) {
			String player = routinePlayers.removeFirst();
			String uid = Util.getUniqueId(player); // cached identity or online player; never offline lookup
			if (uid != null && dirtyPlayerVars.contains(player)) capture = new SnapshotCapture("player", player, uid);
		} else routinePlayers = null;
	}

	private void submit(SnapshotCapture snapshot) {
		checkOwner();
		lastSnapshotNanos = snapshot.data.snapshotNanos();
		if (!writer.submit(snapshot.data.write(), snapshot.dirty, snapshot.key, snapshot.generation))
			throw new IllegalStateException("Variable write already outstanding");
		MagicSpells.debug(2, "Variable persistence submitted: snapshot-ns=" + lastSnapshotNanos
				+ ", max-snapshot-slice-ns=" + snapshot.data.maxSliceNanos() + ", in-flight=1, queued-snapshots=0");
	}

	private void flushPending() {
		checkOwner();
		capture = null; // Explicit saves/loads supersede any unfinished routine capture.
		acknowledge(writer.flush());
	}

	private void acknowledge(OrderedVariableWriter.Completion completion) {
		if (completion == null) return;
		if (completion.failure() != null) {
			failures++;
			// Do not log payloads, file paths or player identities from exception messages.
			MagicSpells.error("Variable persistence failed (" + completion.failure().getClass().getSimpleName()
					+ "); changes retained for retry.");
		}
		MagicSpells.debug(2, "Variable persistence: snapshot-ns=" + lastSnapshotNanos
				+ ", write-ns=" + completion.writeNanos() + ", failures=" + failures
				+ ", pending-player-scopes=" + dirtyPlayerVars.size() + ", pending-global=" + dirtyScopes.contains("global")
				+ ", pending-location=" + dirtyScopes.contains("location") + ", in-flight=0, queued-snapshots=0");
	}

	private void checkOwner() {
		if (Thread.currentThread() != owner || closed)
			throw new IllegalStateException("Variable persistence requires the active manager's owning thread");
	}

	public void saveAllPlayerVariables() {
		for (String playerName : new HashSet<>(dirtyPlayerVars)) {
			String uid = Util.getUniqueId(playerName);
			if (uid != null)
				savePlayerVariables(playerName, uid);
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

	/** Blocking durability barrier, also used before reload starts tearing down other managers. */
	public void flushForUnload() {
		checkOwner();
		flushPending();
		if (dirtyScopes.contains("global")) saveGlobalVariables();
		if (dirtyScopes.contains("location")) saveLocationVariables();
		if (!dirtyPlayerVars.isEmpty()) saveAllPlayerVariables();
		flushPending();
		if (!dirtyScopes.isEmpty() || !dirtyPlayerVars.isEmpty()) {
			MagicSpells.error("Final variable flush incomplete; refusing to discard unsaved state.");
			throw new IllegalStateException("Final variable flush incomplete");
		}
	}

	public void disable() {
		flushForUnload();
		if (saveTask != -1) MagicSpells.cancelTask(saveTask);
		routinePlayers = null;
		writer.close();
		closed = true;
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
		if (variable instanceof LocationStringVariable lsv) {
			switch (op) {
				case SET -> {
					String value = mod.getStringValue(caster, target, locationToMod, args);
					if (value.equals(variable.getDefaultStringValue()))
						lsv.reset(locationToMod);
					else
						lsv.set(locationToMod, value);
					dirtyScopes.add("location");
					return value;
				}
				case ADD -> {
					String value = lsv.getStringValue(locationToMod) + mod.getStringValue(caster, target, locationToMod, args);
					if (value.equals(variable.getDefaultStringValue()))
						lsv.reset(locationToMod);
					else
						lsv.set(locationToMod, value);
					dirtyScopes.add("location");
					return value;
				}
				case MULTIPLY -> {
					int count = (int) mod.getValue(caster, target, locationToMod, power, args);
					String value = lsv.getStringValue(locationToMod).repeat(count);
					if (value.equals(variable.getDefaultStringValue()))
						lsv.reset(locationToMod);
					else
						lsv.set(locationToMod, value);
					dirtyScopes.add("location");
					return value;
				}
			}
		} else if (variable instanceof LocationVariable lv) {
			double value = op.applyTo(lv.getValue(locationToMod), mod.getValue(caster, target, locationToMod, power, args));
			if (value == variable.getDefaultValue())
				lv.reset(locationToMod);
			else
				lv.set(locationToMod, value);
			dirtyScopes.add("location");
			return Double.toString(value);
		}
		return 0 + "";
	}

}
