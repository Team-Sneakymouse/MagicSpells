package com.nisovin.magicspells.events;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.entity.Entity;
import org.bukkit.damage.DamageType;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

import com.nisovin.magicspells.Spell;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;

/**
 * Paper still requires {@link DamageModifier} maps for the non-{@code forRemoval}
 * {@link EntityDamageByEntityEvent} constructor; the simple {@code double} overload is
 * marked for removal.
 */
@SuppressWarnings("deprecation") // DamageModifier maps — required by Paper's stable ctor
public class MagicSpellsEntityDamageByEntityEvent extends EntityDamageByEntityEvent implements IMagicSpellsCompatEvent {

	private final Spell spell;

	public MagicSpellsEntityDamageByEntityEvent(Entity damager, Entity damagee, DamageCause cause, double damage, Spell spell) {
		super(damager, damagee, cause,
			DamageSource.builder(DamageType.GENERIC).withCausingEntity(damager).withDirectEntity(damager).build(),
			getModTemplate(damage), getModifierFunctionTemplate(0D), false);
		this.spell = spell;
	}

	private static Map<DamageModifier, Double> getModTemplate(double baseDamage) {
		return new HashMap<>(ImmutableMap.of(DamageModifier.BASE, baseDamage));
	}

	private static Map<DamageModifier, Function<Double, Double>> getModifierFunctionTemplate(final double baseDamage) {
		return new HashMap<>(ImmutableMap.of(DamageModifier.BASE, getConstantFunction(baseDamage)));
	}

	private static Function<Double, Double> getConstantFunction(final double value) {
		return arg0 -> value;
	}

	public Spell getSpell() {
		return spell;
	}

}
