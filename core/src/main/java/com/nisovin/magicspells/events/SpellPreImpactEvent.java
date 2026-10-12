package com.nisovin.magicspells.events;

import java.util.Arrays;

import org.bukkit.event.Cancellable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.handlers.DebugHandler;

public class SpellPreImpactEvent extends SpellEvent implements Cancellable {

	private Entity target;
	private float power;
	private Spell deliverySpell;
	private boolean redirect;
	private boolean cancelled;

	public SpellPreImpactEvent(Spell spellPayload, Spell deliverySpell, Entity caster, Entity target, float power) {
		super(spellPayload, caster);
		this.target = target;
		this.power = power;
		this.deliverySpell = deliverySpell;
		redirect = false;
		cancelled = false;
		if (DebugHandler.isSpellPreImpactEventCheckEnabled()) MagicSpells.plugin.getLogger().info(toString());
	}
	
	public Entity getTarget() {
		return target;
	}
	
	public boolean getRedirected() {
		return redirect;
	}
	
	public void setRedirected(boolean redirect) {
		this.redirect = redirect;
	}
	
	public float getPower() {
		return power;
	}
	
	public void setPower(float power) {
		this.power = power;
	}
	
	public Spell getDeliverySpell() {
		return deliverySpell;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}
	
	@Override
	public String toString() {
		String casterLabel = "Caster: " + (caster == null ? "null" : caster.toString());
		String targetLabel = "Target: " + (target == null ? "null" : target.toString());
		String spellLabel = "SpellPayload: " + (spell == null ? "null" : spell.toString());
		String payloadSpellLabel = "Delivery Spell: " + (deliverySpell == null ? "null" : deliverySpell.toString());
		return Arrays.deepToString(new String[]{ casterLabel, targetLabel, spellLabel, payloadSpellLabel });
	}
	
}
