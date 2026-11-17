package com.nisovin.magicspells.util.compat.placeholderapi;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
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
import com.sneakyitems.util.magicitems.MagicItem;
import com.sneakyitems.util.magicitems.MagicItems;

/**
 * Built-in PlaceholderAPI expansion ({@code %ms_...%}). Variables have their placeholders in SneakyVariables
 * ({@code %sneakyvariables_...%}), which replaced {@code %ms_variable_...%} and {@code %ms_offlinevar_...%}.
 * <p>
 * Ported from <a href="https://github.com/JasperLorelai/Expansion-MagicSpells">JasperLorelai/Expansion-MagicSpells</a>
 * (GPL-3.0) so MagicSpells no longer needs a separate expansion jar.
 */
public class MagicSpellsPlaceholderExpansion extends PlaceholderExpansion implements Cacheable {

	private static final String IDENTIFIER = "ms";
	private static final String PLUGIN = "MagicSpells";
	private static final String NAME = "magicspells";

	private MagicSpells plugin;

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

		return super.register();
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
		plugin = null;
	}

	private String error(String string, boolean debug) {
		return debug ? PLUGIN + ": " + string : "";
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
