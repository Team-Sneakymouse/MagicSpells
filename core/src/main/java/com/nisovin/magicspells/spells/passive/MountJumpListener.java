package com.nisovin.magicspells.spells.passive;

import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import org.bukkit.event.entity.HorseJumpEvent;

import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// No trigger variable is currently used.
// Cancelling this event causes the entity to be teleported back
// to the location they jumped from. This may cause unintended effects
// such as velocity being reset for the entity.
// The effect of the player's jump attempt is not visible to other
// players, but it is visible to the player doing the jump action.
public class MountJumpListener extends PassiveListener {

	@Override
	public void initialize(String var) {

	}

	@OverridePriority
	@EventHandler
	public void onHorseJump(HorseJumpEvent event) {
		handleMountJump(event.getEntity(), event);
	}

	private void handleMountJump(Entity mount, Cancellable event) {
		MagicSpells.debug("MountJumpListener mount jump: " + mount.getType().name());
		if (mount.getPassengers().isEmpty()) return;

		for (Entity passenger : mount.getPassengers()) {
			if (!(passenger instanceof LivingEntity)) continue;

			handleEvent((LivingEntity) passenger, event);
		}
	}

	private void handleEvent(Entity caster, Cancellable event) {
		if (!isCancelStateOk(event.isCancelled())) return;
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		boolean casted = passiveSpell.activate(caster);
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

}
