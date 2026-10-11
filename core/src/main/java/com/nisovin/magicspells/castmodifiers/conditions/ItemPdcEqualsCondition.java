package com.nisovin.magicspells.castmodifiers.conditions;

import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ItemPdcUtil;
import com.nisovin.magicspells.util.ItemPdcKeys.Slot;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.castmodifiers.conditions.util.SpellDataCondition;

/**
 * ConditionVar: {@code slot;key;right} — string equality of PDC value vs expression/literal.
 */
public class ItemPdcEqualsCondition extends SpellDataCondition {

	private Slot slot;
	private String key;
	private ConfigData<String> right;

	@Override
	public boolean initialize(String var) {
		if (var == null || var.isEmpty()) return false;
		String[] parts = var.split(";", 3);
		if (parts.length != 3) return false;
		slot = Slot.parse(parts[0]);
		key = parts[1].trim();
		right = ConfigDataUtil.getString(ItemPdcUtil.stripQuotes(parts[2]));
		return slot != null && !key.isEmpty() && right != null;
	}

	@Override
	protected boolean evaluate(SpellData data) {
		ItemStack stack = ItemPdcUtil.resolveSlot(data, slot);
		String left = ItemPdcUtil.getString(stack, key);
		if (left == null) return false;
		String expected = right.get(data);
		return expected != null && left.equals(expected);
	}

}
