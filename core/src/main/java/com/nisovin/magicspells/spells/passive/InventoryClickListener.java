package com.nisovin.magicspells.spells.passive;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.sneakyitems.util.magicitems.MagicItems;
import com.sneakyitems.util.magicitems.MagicItemData;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

public class InventoryClickListener extends PassiveListener {

	private MagicItemData itemCurrent = null;
	private MagicItemData itemCursor = null;
	private InventoryAction action = null;

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] splits = var.split(" ");

		if (!splits[0].equals("null")) {
			try {
				action = InventoryAction.valueOf(splits[0].toUpperCase());
			} catch (IllegalArgumentException e) {
				MagicSpells.error("Invalid inventory action '" + splits[0] + "' in inventoryclick trigger on passive spell '" + passiveSpell.getInternalName() + "'");
			}
		}

		if (splits.length > 1 && !splits[1].isEmpty() && !splits[1].equals("null")) {
			itemCurrent = MagicItems.getMagicItemDataFromString(splits[1]);

			if (itemCurrent == null) {
				MagicSpells.error("Invalid magic item '" + splits[1] + "' in inventoryclick trigger on passive spell '" + passiveSpell.getInternalName() + "'");
			}
		}

		if (splits.length > 2 && !splits[2].isEmpty() && !splits[2].equals("null")) {
			itemCursor = MagicItems.getMagicItemDataFromString(splits[2]);

			if (itemCursor == null) {
				MagicSpells.error("Invalid magic item '" + splits[2] + "' in inventoryclick trigger on passive spell '" + passiveSpell.getInternalName() + "'");
			}
		}
	}

	@OverridePriority
	@EventHandler
	public void onInvClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (!hasSpell(player) || !canTrigger(player)) return;

		// Valid action, but not used.
		if (action != null && !event.getAction().equals(action)) return;

		// Valid clicked item, but not used.
		if (itemCurrent != null) {
			ItemStack item = event.getCurrentItem();
			if (item == null) return;

			if (!MagicItems.matches(itemCurrent, item)) return;
		}

		// Valid cursor item, but not used.
		if (itemCursor != null) {
			ItemStack item = event.getCursor();
			if (item == null) return;

			if (!MagicItems.matches(itemCursor, item)) return;
		}

		boolean casted = passiveSpell.activate(player);
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

}
