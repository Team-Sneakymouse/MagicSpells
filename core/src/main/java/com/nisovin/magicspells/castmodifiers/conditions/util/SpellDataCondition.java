package com.nisovin.magicspells.castmodifiers.conditions.util;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.ManaChangeEvent;
import com.nisovin.magicspells.castmodifiers.Condition;
import com.nisovin.magicspells.castmodifiers.IModifier;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsGenericPlayerEvent;

/**
 * Condition that prefers {@link SpellData}-aware evaluation via {@link IModifier}.
 */
public abstract class SpellDataCondition extends Condition implements IModifier {

	protected abstract boolean evaluate(SpellData data);

	@Override
	public ModifierResult apply(SpellData data) {
		return new ModifierResult(data, evaluate(data));
	}

	@Override
	public ModifierResult applyTarget(SpellData data) {
		return new ModifierResult(data, evaluate(data));
	}

	@Override
	public ModifierResult applyLocation(SpellData data) {
		return new ModifierResult(data, evaluate(data));
	}

	@Override
	@Deprecated
	public ModifierResult apply(LivingEntity caster, SpellData data) {
		return apply(data.caster(caster));
	}

	@Override
	@Deprecated
	public ModifierResult apply(LivingEntity caster, LivingEntity target, SpellData data) {
		return applyTarget(data.caster(caster).target(target));
	}

	@Override
	@Deprecated
	public ModifierResult apply(LivingEntity caster, Location target, SpellData data) {
		return applyLocation(data.caster(caster).location(target));
	}

	@Override
	public boolean apply(SpellCastEvent event) {
		return evaluate(event.getSpellData());
	}

	@Override
	public boolean apply(SpellCastedEvent event) {
		return evaluate(new SpellData(event.getCaster(), event.getPower(), event.getSpellArgs()));
	}

	@Override
	public boolean apply(SpellTargetEvent event) {
		return evaluate(event.getSpellData());
	}

	@Override
	public boolean apply(SpellTargetLocationEvent event) {
		return evaluate(event.getSpellData());
	}

	@Override
	public boolean apply(ManaChangeEvent event) {
		return evaluate(new SpellData(event.getPlayer()));
	}

	@Override
	public boolean apply(MagicSpellsGenericPlayerEvent event) {
		return evaluate(new SpellData(event.getPlayer()));
	}

	@Override
	public boolean check(LivingEntity livingEntity) {
		return evaluate(new SpellData(livingEntity));
	}

	@Override
	public boolean check(LivingEntity livingEntity, LivingEntity target) {
		return evaluate(new SpellData(livingEntity, target));
	}

	@Override
	public boolean check(LivingEntity livingEntity, Location location) {
		return evaluate(new SpellData(livingEntity, location));
	}

}
