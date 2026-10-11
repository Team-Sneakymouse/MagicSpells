package com.nisovin.magicspells.castmodifiers.conditions;

import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ItemPdcUtil;
import com.nisovin.magicspells.util.ItemPdcKeys.Slot;
import com.nisovin.magicspells.castmodifiers.conditions.util.SpellDataCondition;

/**
 * ConditionVar: {@code slot;key} — true if the slotted item has the magic-item PDC key.
 */
public class HasItemPdcCondition extends SpellDataCondition {

	private Slot slot;
	private String key;

	@Override
	public boolean initialize(String var) {
		if (var == null || var.isEmpty()) return false;
		String[] parts = var.split(";", 2);
		if (parts.length != 2) return false;
		slot = Slot.parse(parts[0]);
		key = parts[1].trim();
		return slot != null && !key.isEmpty();
	}

	@Override
	protected boolean evaluate(SpellData data) {
		ItemStack stack = ItemPdcUtil.resolveSlot(data, slot);
		return ItemPdcUtil.hasKey(stack, key);
	}

}
