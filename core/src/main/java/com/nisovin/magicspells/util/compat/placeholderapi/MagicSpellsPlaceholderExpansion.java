package com.nisovin.magicspells.util.compat.placeholderapi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.mana.ManaHandler;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.data.DataLivingEntity;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.managers.VariableManager;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable;
import com.nisovin.magicspells.variables.variabletypes.GlobalVariable;
import com.nisovin.magicspells.variables.variabletypes.PlayerVariable;

/**
 * Built-in PlaceholderAPI expansion ({@code %ms_...%}).
 * <p>
 * Ported from <a href="https://github.com/JasperLorelai/Expansion-MagicSpells">JasperLorelai/Expansion-MagicSpells</a>
 * (GPL-3.0) so MagicSpells no longer needs a separate expansion jar.
 */
public class MagicSpellsPlaceholderExpansion extends PlaceholderExpansion implements Listener, Cacheable {

	private static final Pattern VARIABLE_FILE_PATTERN = Pattern.compile(
			"PLAYER_(?<id1>\\w{8})(?<id2>\\w{4})(?<id3>\\w{4})(?<id4>\\w{4})(?<id5>\\w{12})\\.txt");

	private static final String IDENTIFIER = "ms";
	private static final String PLUGIN = "MagicSpells";
	private static final String NAME = "magicspells";

	private MagicSpells plugin;
	private File variableDir;
	private final Map<UUID, Map<String, String>> variableCache = new HashMap<>();
	private boolean eventsRegistered;

	@Override
	public boolean canRegister() {
		return Bukkit.getPluginManager().getPlugin(getRequiredPlugin()) != null;
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public boolean register() {
		if (!canRegister()) return false;

		plugin = (MagicSpells) Bukkit.getPluginManager().getPlugin(getRequiredPlugin());
		if (plugin == null) return false;

		variableDir = new File(plugin.getDataFolder(), "vars");
		registerEvents();
		cacheOfflineVariableFiles();

		return super.register();
	}

	private void registerEvents() {
		if (eventsRegistered || plugin == null) return;
		Bukkit.getPluginManager().registerEvents(this, plugin);
		eventsRegistered = true;
	}

	private void unregisterEvents() {
		if (!eventsRegistered) return;
		HandlerList.unregisterAll(this);
		eventsRegistered = false;
	}

	private void cacheOfflineVariableFiles() {
		Bukkit.getScheduler().runTask(plugin, () -> {
			File[] variableFiles = variableDir.listFiles();
			if (variableFiles == null) return;
			var onlinePlayers = Bukkit.getOnlinePlayers();

			varLoop:
			for (File variableFile : variableFiles) {
				if (variableFile.isDirectory()) continue;

				Matcher matcher = VARIABLE_FILE_PATTERN.matcher(variableFile.getName());
				if (!matcher.matches()) continue;

				try {
					UUID uuid = UUID.fromString(Stream.of("id1", "id2", "id3", "id4", "id5")
							.map(matcher::group)
							.collect(Collectors.joining("-")));
					for (Player player : onlinePlayers) {
						if (player.getUniqueId().equals(uuid)) continue varLoop;
					}
					cacheVariableFile(uuid, variableFile);
				} catch (IllegalArgumentException ignored) {
				}
			}
		});
	}

	@NotNull
	@Override
	public String getAuthor() {
		return "JasperLorelai, Team-Sneakymouse";
	}

	@NotNull
	@Override
	public String getName() {
		return NAME;
	}

	@NotNull
	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	@NotNull
	@Override
	public String getRequiredPlugin() {
		return PLUGIN;
	}

	@NotNull
	@Override
	public String getVersion() {
		return plugin != null ? plugin.getPluginMeta().getVersion() : "unknown";
	}

	@Override
	@Nullable
	public String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
		Player player = offlinePlayer != null && offlinePlayer.isOnline() ? (Player) offlinePlayer : null;

		String[] splits = identifier.split("_", 2);
		String name = splits[0];
		String args = splits.length > 1 ? splits[1] : null;
		boolean debug = false;

		if (name.equals("debug") && args != null) {
			splits = args.split("_", 2);
			name = splits[0];
			args = splits.length > 1 ? splits[1] : null;
			debug = true;
		}

		return switch (name) {
			case "variable" -> processVariable(player, args, debug);
			case "offlinevar" -> processOfflineVariable(offlinePlayer, args, debug);
			case "cooldown" -> processCooldown(player, args, debug);
			case "charges" -> processCharges(player, args, debug);
			case "mana" -> processMana(player, args, debug);
			case "buff" -> processBuff(player, args, debug);
			case "selectedspell" -> processSelectedSpell(player, args, debug);
			case "data" -> processDataElement(player, args, debug);
			case "item" -> processMagicItem(args, debug);
			default -> null;
		};
	}

	@Override
	public void clear() {
		unregisterEvents();
		plugin = null;
		variableDir = null;
		variableCache.clear();
	}

	private String error(String string, boolean debug) {
		return debug ? PLUGIN + ": " + string : "";
	}

	private enum VariableValue {
		MAX,
		MIN,
		CURRENT
	}

	private String processVariableCommon(OfflinePlayer player, String args,
			BiFunction<Variable, String, String> function, boolean debug) {
		if (args == null) return null;
		VariableManager manager = MagicSpells.getVariableManager();
		if (manager == null) return error("Variable manager not loaded.", debug);

		String[] splits = args.split("_", 2);
		VariableValue type = switch (splits[0]) {
			case "max" -> VariableValue.MAX;
			case "min" -> VariableValue.MIN;
			default -> VariableValue.CURRENT;
		};

		String varName;
		if (type == VariableValue.CURRENT) varName = args;
		else {
			if (splits.length < 2) return null;
			varName = splits[1];
		}

		String precision = null;
		if (varName.contains(",")) {
			splits = varName.split(",", 2);
			varName = splits[0];
			precision = splits[1];
		}

		varName = PlaceholderAPI.setBracketPlaceholders(player, varName).trim();
		Variable variable = manager.getVariable(varName);
		if (variable == null) return error("Variable '" + varName + "' wasn't found.", debug);

		Player live = player instanceof Player p ? p : (player != null ? player.getPlayer() : null);

		String value = null;
		switch (type) {
			case MAX -> value = String.valueOf(variable.getMaxValue(live));
			case MIN -> value = String.valueOf(variable.getMinValue(live));
			case CURRENT -> {
				if (variable instanceof GlobalVariable || variable instanceof GlobalStringVariable) {
					value = variable.getStringValue((String) null);
					break;
				}
				if (player == null) return error("Player target not found.", debug);
				value = function.apply(variable, varName);
			}
		}
		if (value == null) return null;

		return PlaceholderPrecision.apply(value, precision);
	}

	/**
	 * variable     [varname],(precision)
	 * variable max [varname],(precision)
	 * variable min [varname],(precision)
	 */
	private String processVariable(Player player, String args, boolean debug) {
		return processVariableCommon(player, args, (variable, varName) -> variable.getStringValue(player), debug);
	}

	/**
	 * offlinevar     [varname],(precision)
	 * offlinevar max [varname],(precision)
	 * offlinevar min [varname],(precision)
	 */
	private String processOfflineVariable(OfflinePlayer player, String args, boolean debug) {
		return processVariableCommon(player, args, (variable, varName) -> {
			if (player.isOnline()) return variable.getStringValue((Player) player);
			return variableCache.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(varName, "");
		}, debug);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		variableCache.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (plugin == null || variableDir == null) return;
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!variableDir.exists()) return;

			Player player = event.getPlayer();
			String uniqueId = Util.getUniqueId(player);
			File file = new File(variableDir, "PLAYER_" + uniqueId + ".txt");
			if (!file.exists() || file.isDirectory()) return;
			cacheVariableFile(player.getUniqueId(), file);
		});
	}

	private void cacheVariableFile(UUID uuid, File file) {
		VariableManager manager = MagicSpells.getVariableManager();
		if (manager == null) return;

		try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8)) {
			while (scanner.hasNext()) {
				String line = scanner.nextLine().trim();
				if (line.isEmpty()) continue;
				String[] splits = line.split("=", 2);
				if (splits.length < 2) continue;
				String variableName = splits[0];
				String value = splits[1];

				Variable variable = manager.getVariable(variableName);
				if (!(variable instanceof PlayerVariable) || !variable.isPermanent()) continue;

				variableCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(variableName, value);
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * cooldown     [spellname],(precision)
	 * cooldown now [spellname],(precision)
	 */
	private String processCooldown(Player player, String args, boolean debug) {
		if (args == null) return null;

		String[] splits = args.split("_", 2);
		boolean isNow = splits[0].equals("now");
		String spellName;
		if (isNow) {
			if (splits.length < 2) return null;
			spellName = splits[1];
		} else spellName = args;

		String precision = null;
		if (spellName.contains(",")) {
			splits = spellName.split(",", 2);
			spellName = splits[0];
			precision = splits[1];
		}

		spellName = PlaceholderAPI.setBracketPlaceholders(player, spellName).trim();
		Spell spell = MagicSpells.getSpellByInternalName(spellName);
		if (spell == null) return error("Spell '" + spellName + "' wasn't found.", debug);

		float cooldown;
		if (isNow) {
			if (player == null) return error("Player target not found.", debug);
			cooldown = spell.getCooldown(player);
		} else cooldown = spell.getCooldown();

		return PlaceholderPrecision.apply(String.valueOf(cooldown), precision);
	}

	/**
	 * charges          [spellname]
	 * charges consumed [spellname]
	 */
	private String processCharges(Player player, String args, boolean debug) {
		if (args == null) return null;

		String[] splits = args.split("_", 2);
		boolean isConsumed = splits[0].equals("consumed");
		String spellName;
		if (isConsumed) {
			if (splits.length < 2) return null;
			spellName = splits[1];
		} else spellName = args;
		spellName = PlaceholderAPI.setBracketPlaceholders(player, spellName).trim();
		Spell spell = MagicSpells.getSpellByInternalName(spellName);
		if (spell == null) return error("Spell '" + spellName + "' wasn't found.", debug);

		if (isConsumed) {
			if (player == null) return error("Player target not found.", debug);
			return String.valueOf(spell.getCharges(player));
		}
		return String.valueOf(spell.getCharges());
	}

	/**
	 * mana
	 * mana max
	 */
	private String processMana(Player player, String args, boolean debug) {
		if (player == null) return error("Player target not found.", debug);
		ManaHandler handler = MagicSpells.getManaHandler();
		if (handler == null) return error("Mana system is not enabled.", debug);
		boolean isMax = args != null && args.equals("max");

		return String.valueOf(isMax ? handler.getMaxMana(player) : handler.getMana(player));
	}

	/**
	 * buff     [spellname],(precision)
	 * buff now [spellname],(precision)
	 */
	private String processBuff(Player player, String args, boolean debug) {
		if (args == null) return null;

		String[] splits = args.split("_", 2);
		boolean isNow = splits[0].equals("now");
		String spellName;
		if (isNow) {
			if (splits.length < 2) return null;
			spellName = splits[1];
		} else spellName = args;

		String precision = null;
		if (spellName.contains(",")) {
			splits = spellName.split(",", 2);
			spellName = splits[0];
			precision = splits[1];
		}

		spellName = PlaceholderAPI.setBracketPlaceholders(player, spellName).trim();
		Spell spell = MagicSpells.getSpellByInternalName(spellName);
		if (!(spell instanceof BuffSpell buff)) return error("Buff spell '" + spellName + "' not found.", debug);

		float duration;
		if (isNow) {
			if (player == null) return error("Player target not found.", debug);
			duration = buff.getDuration(player);
		} else duration = buff.getDuration();

		return PlaceholderPrecision.apply(String.valueOf(duration), precision);
	}

	/**
	 * selectedspell
	 * selectedspell displayed
	 */
	private String processSelectedSpell(Player player, String args, boolean debug) {
		if (player == null) return error("Player target not found.", debug);
		boolean isDisplayed = args != null && args.equals("displayed");

		Spell spell = MagicSpells.getSpellbook(player).getActiveSpell(player.getInventory().getItemInMainHand());
		if (spell == null) return "";
		return isDisplayed ? Util.getMiniMessageFromLegacy(spell.getName()) : spell.getInternalName();
	}

	/**
	 * data [dataElement]
	 */
	private String processDataElement(Player player, String args, boolean debug) {
		if (args == null) return null;
		if (player == null) return error("Player target not found.", debug);
		String element = PlaceholderAPI.setBracketPlaceholders(player, args);
		var function = DataLivingEntity.getDataFunction(element);
		if (function == null) return error("Data element '" + element + "' wasn't found.", debug);
		return function.apply(player);
	}

	/**
	 * item ms        [magicItemName]
	 * item nbt       [magicItemName]
	 * item component [magicItemName]
	 * item amount    [magicItemName]
	 */
	private String processMagicItem(String args, boolean debug) {
		if (args == null) return null;
		String[] splits = args.split("_", 2);
		if (splits.length < 2) return null;

		String type = splits[0];
		String itemName = splits[1];
		MagicItem magicItem = MagicItems.getMagicItemByInternalName(itemName);
		if (magicItem == null) return error("Item '" + itemName + "' not found.", debug);
		ItemStack item = magicItem.getItemStack();

		return switch (type) {
			case "ms" -> magicItem.getMagicItemData().toString();
			case "nbt" -> {
				String data = item.getType().getKey().toString();
				ItemMeta meta = item.getItemMeta();
				if (meta != null) data += meta.getAsString();
				yield data;
			}
			case "component" -> {
				String data = item.getType().getKey().toString();
				ItemMeta meta = item.getItemMeta();
				if (meta != null) data += meta.getAsComponentString();
				yield data;
			}
			case "amount" -> String.valueOf(item.getAmount());
			default -> null;
		};
	}

}
