package com.nisovin.magicspells.spells.passive;

import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.util.SpellFilter;
import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.Spell.SpellCastState;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// Optional trigger variable of comma separated list of internal spell names to accept
public class SpellCastedListener extends PassiveListener {

	private SpellFilter filter;

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;
		filter = SpellFilter.fromString(var);
	}

	@OverridePriority
	@EventHandler
	public void onSpellCast(SpellCastedEvent event) {
		if (event.getSpellCastState() != SpellCastState.NORMAL) return;
		if (event.getPostCastAction() == PostCastAction.ALREADY_HANDLED) return;

		Entity caster = event.getCaster();
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		Spell spell = event.getSpell();
		if (spell.equals(passiveSpell)) return;
		if (filter != null && !filter.check(spell)) return;

		passiveSpell.activate(caster);
	}

}
