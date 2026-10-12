package com.nisovin.magicspells.events;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.Spell;

public class SpellApplyDamageEvent extends SpellEvent {

	private final LivingEntity target;
	private final double damage;
	private final DamageCause cause;
	private final long timestamp;
	private float modifier;

	public SpellApplyDamageEvent(Spell spell, Entity caster, LivingEntity target, double damage, DamageCause cause, String spellDamageType) {
		super(spell, caster);

		this.target = target;
		this.damage = damage;
		this.cause = cause;

		timestamp = System.currentTimeMillis();

		modifier = 1.0f;
	}

	public void applyDamageModifier(float modifier) {
		this.modifier *= modifier;
	}

	public LivingEntity getTarget() {
		return target;
	}

	public double getDamage() {
		return damage;
	}

	public DamageCause getCause() {
		return cause;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public float getDamageModifier() {
		return modifier;
	}

	public double getFinalDamage() {
		return damage * modifier;
	}

}
