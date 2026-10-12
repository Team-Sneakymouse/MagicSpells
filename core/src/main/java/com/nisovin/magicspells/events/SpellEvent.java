package com.nisovin.magicspells.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Entity;

import com.nisovin.magicspells.Spell;

public abstract class SpellEvent extends Event implements IMagicSpellsCompatEvent {

	protected static final HandlerList handlers = new HandlerList();

	protected Spell spell;

	protected Entity caster;
	
	public SpellEvent(Spell spell, Entity caster) {
		this.spell = spell;
		this.caster = caster;
	}
	
	/**
	 * Gets the spell involved in the event.
	 * @return the spell
	 */
	public Spell getSpell() {
		return spell;
	}
	
	/**
	 * Gets the entity casting the spell.
	 * @return the casting entity
	 */
	public Entity getCaster() {
		return caster;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
	
}
