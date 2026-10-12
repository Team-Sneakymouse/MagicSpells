package com.nisovin.magicspells.spells.passive;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityToggleSwimEvent;

import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// No trigger variable is currently used
public class StartSwimListener extends PassiveListener {

	@Override
	public void initialize(String var) {

	}

	@OverridePriority
	@EventHandler
	public void onSwim(EntityToggleSwimEvent event) {
		if (!(event.getEntity() instanceof LivingEntity caster)) return;
		if (!isCancelStateOk(event.isCancelled())) return;
		if (!event.isSwimming()) return;
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		boolean casted = passiveSpell.activate(caster);
		if (cancelDefaultAction(casted)) {
			// setCancelled is a no-op on modern Paper; sprinting clear is the supported workaround.
			@SuppressWarnings("deprecation")
			boolean ignored = cancelSwim(event, caster);
		}
	}

	@SuppressWarnings("deprecation") // EntityToggleSwimEvent#setCancelled is a documented no-op
	private static boolean cancelSwim(EntityToggleSwimEvent event, Entity caster) {
		event.setCancelled(true);
		if (caster instanceof Player player) player.setSprinting(false);
		return true;
	}

}
