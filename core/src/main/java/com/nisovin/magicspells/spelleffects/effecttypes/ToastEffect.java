package com.nisovin.magicspells.spelleffects.effecttypes;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.sneakyitems.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.sneakyitems.util.magicitems.MagicItems;

import io.papermc.paper.advancement.AdvancementDisplay.Frame;

public class ToastEffect extends SpellEffect {

	private ConfigData<String> text;
	private ConfigData<String> magicItemString;
	private ConfigData<Frame> frame;
	private ConfigData<Boolean> broadcast;

	@Override
	protected void loadFromConfig(ConfigurationSection config) {
		text = ConfigDataUtil.getString(config, "text", "");
		frame = ConfigDataUtil.getEnum(config, "frame", Frame.class, Frame.TASK);

		magicItemString = ConfigDataUtil.getString(config, "icon", "air");

		broadcast = ConfigDataUtil.getBoolean(config, "broadcast", false);
	}

	@Override
	protected Runnable playEffectEntity(Entity entity, SpellData data) {
        ItemStack icon = null;
		MagicItem magicItem = MagicItems.getMagicItemFromString(magicItemString.get(data));
		if (magicItem == null) MagicSpells.error("Invalid toast effect icon specified: '" + magicItemString + "'");
		else icon = magicItem.getItemStack();

		if (icon == null) return null;
        ItemStack icon_ = icon;
		if (broadcast.get(data)) Util.forEachPlayerOnline(player -> send(player, data, icon_));
		else if (entity instanceof Player player) send(player, data, icon);
		return null;
	}

	private void send(Player player, SpellData data, ItemStack icon) {
		Component textComponent = Util.getMiniMessage(text.get(data));
		MagicSpells.getVolatileCodeHandler().sendToastEffect(player, icon, frame.get(data), textComponent);
	}

}