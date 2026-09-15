package com.nisovin.magicspells.spells.passive;

import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// No trigger variable is used here
public class ShootListener extends PassiveListener {

	@Override
	public void initialize(String var) {

	}

	@OverridePriority
	@EventHandler
	public void onShoot(final EntityShootBowEvent event) {
		if (!isCancelStateOk(event.isCancelled())) return;

		LivingEntity caster = event.getEntity();
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		boolean casted = passiveSpell.activate(caster, event.getForce());
		if (cancelDefaultAction(casted)) {
			event.setCancelled(true);
			event.getProjectile().remove();
		}
	}

	@OverridePriority
	@EventHandler
	public void onTridentThrow(final ProjectileLaunchEvent event) {
		if (!(event.getEntity() instanceof Trident)) return;
		if (!isCancelStateOk(event.isCancelled())) return;
		if (!(event.getEntity().getShooter() instanceof LivingEntity caster)) return;
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		boolean casted = passiveSpell.activate(caster, 1F);
		if (cancelDefaultAction(casted)) {
			event.setCancelled(true);
			event.getEntity().remove();
		}
	}

}
