package com.nisovin.magicspells.spells.passive;

import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.SpellFilter;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// Optional trigger variable of comma separated list of internal spell names to accept
public class SpellTargetedListener extends PassiveListener {

	private SpellFilter filter;

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;
		filter = SpellFilter.fromString(var);
	}

	@OverridePriority
	@EventHandler
	public void onSpellTarget(SpellTargetEvent event) {
		if (!isCancelStateOk(event.isCancelled())) return;

		Entity target = event.getTarget();
		if (!hasSpell(target) || !canTrigger(target)) return;

		if (filter != null && !filter.check(event.getSpell())) return;

		boolean casted = passiveSpell.activate(target, event.getCaster());
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

}
