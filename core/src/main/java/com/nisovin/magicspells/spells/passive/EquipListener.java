package com.nisovin.magicspells.spells.passive;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.sneakyitems.util.magicitems.MagicItems;
import com.sneakyitems.util.magicitems.MagicItemData;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

public class EquipListener extends PassiveListener {

	private final Set<MagicItemData> items = new HashSet<>();

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.split("\\|");
		for (String s : split) {
			s = s.trim();

			MagicItemData itemData = MagicItems.getMagicItemDataFromString(s);
			if (itemData == null) {
				MagicSpells.error("Invalid magic item '" + s + "' in equip trigger on passive spell '" + passiveSpell.getInternalName() + "'");
				continue;
			}

			items.add(itemData);
		}
	}

	@OverridePriority
	@EventHandler
	public void onEquip(PlayerArmorChangeEvent event) {
		Player caster = event.getPlayer();
		if (!canTrigger(caster) || !hasSpell(caster)) return;

		if (!items.isEmpty()) {
			ItemStack newItem = event.getNewItem();
			if (newItem == null) return;

			ItemStack oldItem = event.getOldItem();

			if (!contains(oldItem, newItem)) return;
		}

		passiveSpell.activate(caster);
	}

	private boolean contains(ItemStack oldItem, ItemStack newItem) {
		for (MagicItemData data : items)
			if ((oldItem == null || !MagicItems.matches(data, oldItem)) && MagicItems.matches(data, newItem))
				return true;

		return false;
	}

}
