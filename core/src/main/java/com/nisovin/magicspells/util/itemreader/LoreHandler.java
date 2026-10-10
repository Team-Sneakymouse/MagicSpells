package com.nisovin.magicspells.util.itemreader;

import java.util.List;
import java.util.ArrayList;

import net.kyori.adventure.text.Component;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import static com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute.LORE;

public class LoreHandler {

	private static final String CONFIG_NAME = LORE.toString();

	public static void process(ConfigurationSection config, ItemMeta meta, MagicItemData data) {
		if (!config.contains(CONFIG_NAME)) return;

		List<Component> lore = new ArrayList<>();
		if (config.isList(CONFIG_NAME)) {
			for (String line : config.getStringList(CONFIG_NAME)) {
				lore.add(Util.getMiniMessage(line));
			}
		} else if (config.isString(CONFIG_NAME)) {
			lore.add(Util.getMiniMessage(config.getString(CONFIG_NAME)));
		}
		if (lore.isEmpty()) return;
		meta.lore(lore);
		data.setAttribute(LORE, lore);
	}

	public static void processItemMeta(ItemMeta meta, MagicItemData data) {
		if (!data.hasAttribute(LORE)) return;
		List<Component> lore = data.getAttribute(LORE);
		meta.lore(lore);
	}
	
}
