package com.nisovin.magicspells.spells.passive;

import java.util.List;
import java.util.EnumSet;
import java.util.ArrayList;

import org.bukkit.Registry;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent.*;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

public class PotionEffectListener extends PassiveListener {

	private List<PotionEffectType> types;
	private EnumSet<Action> actions;
	private EnumSet<Cause> causes;

	@Override
	public void initialize(String var) {
		types = new ArrayList<>();
		actions = EnumSet.noneOf(Action.class);
		causes = EnumSet.noneOf(Cause.class);

		if (var != null && !var.isEmpty()) {
			var = var.toUpperCase();
			String[] splits = var.split(" ");
			if (!splits[0].equals("*")) {
				for (String s : splits[0].split(",")) {
					PotionEffectType type = Util.getPotionEffectType(s);

					if (type != null) {
						types.add(type);
					} else {
						MagicSpells.error("Invalid effect '" + s + "' in potioneffect trigger on passive spell '" + passiveSpell.getInternalName() + "'");
					}
				}
			} else {
				for (PotionEffectType type : Registry.MOB_EFFECT) {
					types.add(type);
				}
			}

			if (splits.length > 1 && !splits[1].equals("*")) {
				for (String s : splits[1].split(",")) {
					try {
						Action action = Action.valueOf(s);
						actions.add(action);
					} catch (IllegalArgumentException e) {
						MagicSpells.error("Invalid action '" + s + "' in potioneffect trigger on passive spell '" + passiveSpell.getInternalName() + "'");
					}
				}
			} else actions = EnumSet.allOf(Action.class);

			if (splits.length > 1 && !splits[2].equals("*")) {
				for (String s : splits[2].split(",")) {
					try {
						Cause cause = Cause.valueOf(s);
						causes.add(cause);
					} catch (IllegalArgumentException e) {
						MagicSpells.error("Invalid cause '" + s + "' in potioneffect trigger on passive spell '" + passiveSpell.getInternalName() + "'");
					}
				}
			} else causes = EnumSet.allOf(Cause.class);
		}
	}

	@EventHandler
	public void onPotionEffect(EntityPotionEffectEvent event) {
		if (!(event.getEntity() instanceof LivingEntity entity)) return;
		if (!isCancelStateOk(event.isCancelled())) return;

		if (!actions.contains(event.getAction()) || !causes.contains(event.getCause())) return;

		if (!hasSpell(entity) || !canTrigger(entity)) return;

		PotionEffectType type = null;
		PotionEffect effect;
		switch (event.getAction()) {
			case ADDED -> {
				effect = event.getNewEffect();
				if (effect != null) type = effect.getType();
			}
			case CHANGED -> type = event.getModifiedType();
			case REMOVED, CLEARED -> {
				effect = event.getOldEffect();
				if (effect != null) type = effect.getType();
			}
		}

		if (type == null || !types.contains(type)) return;

		boolean casted = passiveSpell.activate(entity);
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

}
