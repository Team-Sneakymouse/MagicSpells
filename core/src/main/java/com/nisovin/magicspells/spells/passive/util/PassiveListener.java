package com.nisovin.magicspells.spells.passive.util;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.PassiveSpell;

public abstract class PassiveListener implements Listener {

	protected PassiveSpell passiveSpell;

	protected EventPriority priority;

	public PassiveSpell getPassiveSpell() {
		return passiveSpell;
	}

	public void setPassiveSpell(PassiveSpell passiveSpell) {
		this.passiveSpell = passiveSpell;
	}

	public EventPriority getEventPriority() {
		return priority;
	}

	public void setEventPriority(EventPriority priority) {
		this.priority = priority;
	}

	public boolean canTrigger(LivingEntity livingEntity) {
		return passiveSpell.getTriggerList().canTarget(livingEntity, true);
	}

	public boolean canTrigger(LivingEntity livingEntity, boolean ignoreGameMode) {
		return passiveSpell.getTriggerList().canTarget(livingEntity, ignoreGameMode);
	}
		
	public boolean cancelDefaultAction(boolean casted) {
		return cancelDefaultAction(null, null, casted);
	}

	public boolean cancelDefaultAction(LivingEntity caster, boolean casted) {
		return cancelDefaultAction(caster, null, casted);
	}

	public boolean cancelDefaultAction(LivingEntity caster, LivingEntity target, boolean casted) {
		if (passiveSpell == null) return true;
		if (casted && passiveSpell.cancelDefaultAction(caster, target, 1f, null)) return true;
		if (!casted && passiveSpell.cancelDefaultActionWhenCastFails(caster, target, 1f, null)) return true;
		return false;
	}
	
	public boolean isCancelStateOk(boolean cancelled) {
		return isCancelStateOk(null, null, cancelled);
	}

	public boolean isCancelStateOk(LivingEntity caster, boolean cancelled) {
		return isCancelStateOk(caster, null, cancelled);
	}

	public boolean isCancelStateOk(LivingEntity caster, LivingEntity target, boolean cancelled) {
		if (passiveSpell == null) return false;
		// require-cancelled-event means we only want cancelled triggers; ignore-cancelled
		// must not reject those or the two options can never both be satisfied.
		if (passiveSpell.requireCancelledEvent(caster, target, 1f, null)) return cancelled;
		if (passiveSpell.ignoreCancelled(caster, target, 1f, null) && cancelled) return false;
		return true;
	}

	public boolean hasSpell(LivingEntity entity) {
		if (entity instanceof Player player) return MagicSpells.getSpellbook(player).hasSpell(passiveSpell);
		return true;
	}
	
	public abstract void initialize(String var);

	public void turnOff() {
		// No op
	}
	
	@Override
	public boolean equals(Object other) {
		return this == other; // Don't want to make things equal unless they are the same object
	}

	protected void setCooldownPerSubject(LivingEntity caster, LivingEntity target, String subject) {
		passiveSpell.setCooldownPerSubject(caster, target, subject);
	}

	protected boolean isOnCooldownPerSubject(LivingEntity caster, String subject) {
		return passiveSpell.isOnCooldownPerSubject(caster, subject);
	}
	
}
