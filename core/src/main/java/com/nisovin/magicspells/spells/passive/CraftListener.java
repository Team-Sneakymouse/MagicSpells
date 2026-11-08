package com.nisovin.magicspells.spells.passive;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.CraftItemEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.sneakyitems.util.magicitems.MagicItems;
import com.sneakyitems.util.magicitems.MagicItemData;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

public class CraftListener extends PassiveListener {

	private final Set<MagicItemData> items = new HashSet<>();

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		for (String s : var.split("\\|")) {
			MagicItemData itemData = MagicItems.getMagicItemDataFromString(s);
			if (itemData == null) {
				MagicSpells.error("Invalid magic item '" + s + "' in craft trigger on passive spell '" + passiveSpell.getInternalName() + "'");
				continue;
			}

			itemData = itemData.clone();
			itemData.getIgnoredAttributes().add(MagicItemData.MagicItemAttribute.AMOUNT);

			items.add(itemData);
		}
	}

	@OverridePriority
	@EventHandler
	public void onCraft(CraftItemEvent event) {
		if (!isCancelStateOk(event.isCancelled())) return;

		HumanEntity caster = event.getWhoClicked();
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		if (!items.isEmpty()) {
			ItemStack item = event.getCurrentItem();
			if (item == null) return;

			if (!contains(item)) return;
		}

		boolean casted = passiveSpell.activate(caster);
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

	private boolean contains(ItemStack item) {
		for (MagicItemData data : items) {
			if (MagicItems.matches(data, item)) return true;
		}
		return false;
	}

}
