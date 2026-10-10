package com.nisovin.magicspells.shop;

import java.util.HashMap;
import java.util.Set;
import java.util.regex.Pattern;

import com.nisovin.magicspells.util.RegexUtil;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.compat.CompatBasics;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.nisovin.magicspells.util.ExperienceUtils;

public class CurrencyHandler {
	
	private static final Pattern PATTERN_CURRENCY_ITEM_BASIC = Pattern.compile("^[0-9]+$");
	private static final Pattern PATTERN_CURRENCY_ITEM_ADVANCED = Pattern.compile("^[0-9]+:[0-9]+$");
	
	private HashMap<String,String> currencies = new HashMap<>();
	private String defaultCurrency;
	private Economy economy;
	
	public CurrencyHandler(Configuration config) {
		ConfigurationSection sec = config.getConfigurationSection("currencies");
		if (sec == null) {
			this.defaultCurrency = "money";
			this.currencies.put("money", "vault");
		} else {
			Set<String> keys = sec.getKeys(false);
			for (String key : keys) {
				if (this.defaultCurrency == null) this.defaultCurrency = key;
				this.currencies.put(key.toLowerCase(), sec.getString(key).toLowerCase());
			}
			if (this.defaultCurrency == null) {
				this.defaultCurrency = "money";
				this.currencies.put("money", "vault");
			}
		}

		// Set up vault hook
		if (this.currencies.containsValue("vault") && CompatBasics.pluginEnabled("Vault")) {
			RegisteredServiceProvider<Economy> provider = CompatBasics.getServiceProvider(Economy.class);
			if (provider != null) this.economy = provider.getProvider();
		}
	}
	
	public boolean has(Player player, double amount) {
		return has(player, amount, this.defaultCurrency);
	}
	
	public boolean has(Player player, double amount, String currency) {
		String c = currency == null ? null : this.currencies.get(currency.toLowerCase());
		if (c == null) c = this.currencies.get(this.defaultCurrency);
		
		if (c == null) return false;
		if (c.equalsIgnoreCase("vault") && this.economy != null) return this.economy.has(player, amount);
		if (c.equalsIgnoreCase("levels")) return player.getLevel() >= (int)amount;
		if (c.equalsIgnoreCase("experience") || c.equalsIgnoreCase("xp")) return ExperienceUtils.hasExp(player, (int) amount);
		if (RegexUtil.matches(PATTERN_CURRENCY_ITEM_BASIC, c)) {
			return inventoryContains(player.getInventory(), currencyItem(Util.getMaterial(c), (int) amount, (short) 0));
		}
		if (RegexUtil.matches(PATTERN_CURRENCY_ITEM_ADVANCED, c)) {
			String[] s = c.split(":");
			short data = Short.parseShort(s[1]);
			return inventoryContains(player.getInventory(), currencyItem(Util.getMaterial(s[0]), (int) amount, data));
		}
		return false;
	}
	
	public void remove(Player player, double amount) {
		remove(player, amount, this.defaultCurrency);
	}
	
	public void remove(Player player, double amount, String currency) {
		String c = currency == null ? null : this.currencies.get(currency.toLowerCase());
		if (c == null) c = this.currencies.get(this.defaultCurrency);
		
		if (c == null) {
		} else if (c.equalsIgnoreCase("vault") && this.economy != null) {
			this.economy.withdrawPlayer(player, amount);
		} else if (c.equalsIgnoreCase("levels")) {
			player.setLevel(player.getLevel() - (int)amount);
		} else if (c.equalsIgnoreCase("experience") || c.equalsIgnoreCase("xp")) {
			ExperienceUtils.changeExp(player, -(int)amount);
		} else if (RegexUtil.matches(PATTERN_CURRENCY_ITEM_BASIC, c)) {
			removeFromInventory(player.getInventory(), currencyItem(Util.getMaterial(c), (int) amount, (short) 0));
		} else if (RegexUtil.matches(PATTERN_CURRENCY_ITEM_ADVANCED, c)) {
			String[] s = c.split(":");
			short data = Short.parseShort(s[1]);
			removeFromInventory(player.getInventory(), currencyItem(Util.getMaterial(s[0]), (int) amount, data));
		}
	}
	
	public boolean isValidCurrency(String currency) {
		return currency != null && this.currencies.containsKey(currency);
	}

	/** Legacy numeric-id currencies may encode durability/data; map onto Damageable when possible. */
	private static ItemStack currencyItem(Material type, int amount, short data) {
		ItemStack item = new ItemStack(type, amount);
		if (data != 0) {
			ItemMeta meta = item.getItemMeta();
			if (meta instanceof Damageable damageable) {
				damageable.setDamage(data);
				item.setItemMeta(meta);
			}
		}
		return item;
	}

	private static int stackDamage(ItemStack stack) {
		ItemMeta meta = stack.getItemMeta();
		if (meta instanceof Damageable damageable) return damageable.getDamage();
		return 0;
	}
	
	private boolean inventoryContains(Inventory inventory, ItemStack item) {
		int count = 0;
		int wantDamage = stackDamage(item);
		ItemStack[] items = inventory.getContents();
		for (int i = 0; i < items.length; i++) {
			if (items[i] != null && items[i].getType() == item.getType() && stackDamage(items[i]) == wantDamage) {
				count += items[i].getAmount();
			}
			if (count >= item.getAmount()) return true;
		}
		return false;
	}
	
	private void removeFromInventory(Inventory inventory, ItemStack item) {
		int amt = item.getAmount();
		int wantDamage = stackDamage(item);
		ItemStack[] items = inventory.getContents();
		for (int i = 0; i < items.length; i++) {
			if (items[i] == null) continue;
			if (items[i].getType() == item.getType() && stackDamage(items[i]) == wantDamage) {
				if (items[i].getAmount() > amt) {
					items[i].setAmount(items[i].getAmount() - amt);
					break;
				} else if (items[i].getAmount() == amt) {
					items[i] = null;
					break;
				} else {
					amt -= items[i].getAmount();
					items[i] = null;
				}
			}
		}
		inventory.setContents(items);
	}
	
}
