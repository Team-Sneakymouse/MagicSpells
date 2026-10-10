package com.nisovin.magicspells.spells.instant;

import java.util.Map;
import java.util.List;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItemIgnoredAttributes;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class EnchantSpell extends InstantSpell {

	private ConfigData<Map<Enchantment, Integer>> enchantments;

	private boolean safeEnchants;

	public EnchantSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		safeEnchants = getConfigBoolean("safe-enchants", true);

		List<String> enchantmentList = getConfigStringList("enchantments", null);
		if (enchantmentList != null && !enchantmentList.isEmpty()) {
			enchantments = ConfigDataUtil.getEnchantmentsConfigData(enchantmentList);
		} else {
			MagicSpells.error("EnchantSpell '" + internalName + "' has invalid enchantments defined!");
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		ItemStack targetItem = data.caster().getEquipment().getItemInMainHand();
					if (targetItem == null) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					enchant(targetItem, new SpellData(data.caster(), data.power(), data.args()));
					playSpellEffects(EffectPosition.CASTER, data.caster(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}


	private void enchant(ItemStack item, SpellData spellData) {
		if (enchantments == null) return;
		Map<Enchantment, Integer> resolved = enchantments.get(spellData);
		if (resolved == null || resolved.isEmpty()) return;
		boolean applied = false;
		for (Map.Entry<Enchantment, Integer> entry : resolved.entrySet()) {
			if (enchant(item, entry.getKey(), entry.getValue()))
				applied = true;
		}
		if (applied && MagicItemIgnoredAttributes.isTaggedMagicItem(item))
			MagicItemIgnoredAttributes.add(item, MagicItemData.MagicItemAttribute.ENCHANTS);
	}

	private boolean enchant(ItemStack item, Enchantment enchant, int level) {
		if (!enchant.canEnchantItem(item)) return false;
		if (safeEnchants && level > enchant.getMaxLevel()) level = enchant.getMaxLevel();
		if (level <= 0) {
			item.removeEnchantment(enchant);
			return true;
		}
		if (safeEnchants) item.addEnchantment(enchant, level);
		else item.addUnsafeEnchantment(enchant, level);
		return true;
	}

	public ConfigData<Map<Enchantment, Integer>> getEnchantments() {
		return enchantments;
	}

	public boolean allowUnsafeEnchants() {
		return !safeEnchants;
	}

	public void setSafeEnchants(boolean safeEnchants) {
		this.safeEnchants = safeEnchants;
	}

}
