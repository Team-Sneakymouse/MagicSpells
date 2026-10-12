package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute;

public class UnconjureSpell extends InstantSpell {

	private final List<String> itemNames;
	private final List<UnconjuredItem> items;

	public UnconjureSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		itemNames = getConfigStringList("items", null);

		items = new ArrayList<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		if (itemNames == null) return;

		for (String itemString : itemNames) {
			UnconjuredItem unconjuredItem = new UnconjuredItem(itemString);
			if (unconjuredItem.magicItemData == null) {
				MagicSpells.error("UnconjureSpell '" + internalName + "' has an invalid magic item specified: " + itemString);
				continue;
			}
			items.add(unconjuredItem);
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		PlayerInventory inventory = player.getInventory();
		
					ItemStack[] contents = new ItemStack[1];
		
					// Search for the hovering item first.
					InventoryView invView = player.getOpenInventory();
					boolean stop = false;
					if (invView.getCursor() != null) {
						contents[0] = invView.getCursor();
						stop = filterItems(contents);
						invView.setCursor(contents[0]);
					}
					if (stop) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		
					contents = inventory.getContents();
					stop = filterItems(contents);
					inventory.setContents(contents);
					if (stop) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		
					contents = inventory.getArmorContents();
					filterItems(contents);
					inventory.setArmorContents(contents);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	private boolean filterItems(ItemStack[] oldItems) {
		boolean stop = false;
		for (UnconjuredItem unconjuredItem : items) {
			if (unconjuredItem.magicItemData == null) continue;
			MagicItemData unconjuredItemData = unconjuredItem.magicItemData;

			// Only look for an ItemStack with specified quantity.
			if (unconjuredItem.hasSpecialQuantity) {
				for (int i = 0; i < oldItems.length; i++) {
					if (oldItems[i] == null) continue;
					MagicItemData oldItemData = MagicItems.getMagicItemDataFromItemStack(oldItems[i]);
					if (oldItemData == null) continue;
					if (!MagicItems.matches(unconjuredItemData, oldItems[i])) continue;
					if (unconjuredItem.amount != oldItems[i].getAmount()) continue;
					oldItems[i] = null;
					// True is only returned if the search is for an item with specific
					// quantity. If the item is found, this won't search for the item in
					// other inventory places.
					stop = true;
					break;
				}
			}
			// Look for all items that match the ItemStack - ignore quantity.
			else {
				for (int i = 0; i < oldItems.length; i++) {
					if (oldItems[i] == null) continue;
					MagicItemData oldItemData = MagicItems.getMagicItemDataFromItemStack(oldItems[i]);
					if (oldItemData == null) continue;
					if (MagicItems.matches(unconjuredItemData, oldItems[i])) oldItems[i] = null;
				}
			}
		}
		return stop;
	}

	public List<String> getItemNames() {
		return itemNames;
	}

	public List<UnconjuredItem> getItems() {
		return items;
	}

	private static class UnconjuredItem {

		private boolean hasSpecialQuantity = false;
		private MagicItemData magicItemData;
		private int amount;

		public UnconjuredItem(String itemString) {
			String[] splits = itemString.split(" ");
			magicItemData = MagicItems.getMagicItemDataFromString(splits[0]);
			if (magicItemData == null) return;

			magicItemData = magicItemData.clone();
			magicItemData.getIgnoredAttributes().add(MagicItemAttribute.AMOUNT);

			if (splits.length == 1) return;

			try {
				amount = Integer.parseInt(splits[1]);
				hasSpecialQuantity = true;
			} catch (NumberFormatException e) {
				DebugHandler.debugNumberFormat(e);
			}
		}

	}

}
