package com.nisovin.magicspells.spelleffects.effecttypes;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.EntityData;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.sneakyitems.util.magicitems.MagicItem;
import com.sneakyitems.util.magicitems.MagicItems;

public class ArmorStandEffect extends SpellEffect {

	public static final String ENTITY_TAG = "MS_ARMOR_STAND";

	private EntityData entityData;

	private boolean gravity;

	private String customName;
	private boolean customNameVisible;

	private ItemStack headItem;
	private ItemStack mainhandItem;
	private ItemStack offhandItem;

	@Override
	protected void loadFromConfig(ConfigurationSection config) {
		ConfigurationSection section = config.getConfigurationSection("armorstand");
		if (section == null)
			return;

		entityData = new EntityData(section);
		entityData.setEntityType((caster, target, location, power, args) -> EntityType.ARMOR_STAND);

		gravity = section.getBoolean("gravity", false);

		customName = section.getString("custom-name", "");
		customNameVisible = section.getBoolean("custom-name-visible", false);

		String strMagicItem = section.getString("head", "");
		MagicItem magicItem = MagicItems.getMagicItemFromString(strMagicItem);
		if (magicItem != null)
			headItem = magicItem.getItemStack();

		strMagicItem = section.getString("mainhand", "");
		magicItem = MagicItems.getMagicItemFromString(strMagicItem);
		if (magicItem != null)
			mainhandItem = magicItem.getItemStack();

		strMagicItem = section.getString("offhand", "");
		magicItem = MagicItems.getMagicItemFromString(strMagicItem);
		if (magicItem != null)
			offhandItem = magicItem.getItemStack();

	}

	@Override
	protected ArmorStand playArmorStandEffectLocation(Location location, SpellData data) {
		return (ArmorStand) entityData.spawn(location, data, entity -> {
			ArmorStand armorStand = (ArmorStand) entity;

			armorStand.addScoreboardTag(MagicSpells.ENTITY_TAG);
			armorStand.addScoreboardTag(ENTITY_TAG);
			armorStand.addScoreboardTag(
					MagicSpells.EXPIRATION_TIME_MILLIS_TAG + ':' + (System.currentTimeMillis() + 100000));
			armorStand.setGravity(gravity);
			armorStand.setSilent(true);
			armorStand.customName(Util.getMiniMessage(MagicSpells.doReplacements(customName, data)));
			armorStand.setCustomNameVisible(customNameVisible);

			armorStand.setItem(EquipmentSlot.HEAD, headItem);
			armorStand.setItem(EquipmentSlot.HAND, mainhandItem);
			armorStand.setItem(EquipmentSlot.OFF_HAND, offhandItem);
		});
	}

}
