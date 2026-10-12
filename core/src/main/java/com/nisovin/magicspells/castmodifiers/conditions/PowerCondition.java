package com.nisovin.magicspells.castmodifiers.conditions;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.ManaChangeEvent;
import com.nisovin.magicspells.castmodifiers.IModifier;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsGenericPlayerEvent;
import com.nisovin.magicspells.castmodifiers.conditions.util.OperatorCondition;

public class PowerCondition extends OperatorCondition implements IModifier {

	private float power;

	@Override
	public boolean initialize(String var) {
		if (var.length() < 2 || !super.initialize(var)) return false;

		try {
			power = Float.parseFloat(var.substring(1));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public boolean apply(SpellCastEvent event) {
		return power(event.getPower());
	}

	@Override
	public boolean apply(ManaChangeEvent event) {
		// No power to check
		return false;
	}

	@Override
	public boolean apply(SpellTargetEvent event) {
		return power(event.getPower());
	}

	@Override
	public boolean apply(SpellCastedEvent event) {
		return power(event.getPower());
	}

	@Override
	public boolean apply(SpellTargetLocationEvent event) {
		return power(event.getPower());
	}

	@Override
	public boolean apply(MagicSpellsGenericPlayerEvent event) {
		// No power to check
		return false;
	}

	@Override
	public ModifierResult apply(Entity caster, SpellData data) {
		return new ModifierResult(data, power(data.power()));
	}

	@Override
	public ModifierResult apply(Entity caster, Entity target, SpellData data) {
		return new ModifierResult(data, power(data.power()));
	}

	@Override
	public ModifierResult apply(Entity caster, Location target, SpellData data) {
		return new ModifierResult(data, power(data.power()));
	}

	@Override
	public boolean check(LivingEntity caster) {
		return false;
	}

	@Override
	public boolean check(LivingEntity caster, LivingEntity target) {
		return false;
	}

	@Override
	public boolean check(LivingEntity caster, Location location) {
		return false;
	}

	public boolean power(float spellPower) {
		if (equals) return spellPower == power;
		else if (moreThan) return spellPower > power;
		else if (lessThan) return spellPower < power;
		return false;
	}


	@Override
	@Deprecated
	public boolean check(Entity entity) {
		return entity instanceof LivingEntity living && check(living);
	}

	@Override
	@Deprecated
	public boolean check(Entity entity, Entity target) {
		return entity instanceof LivingEntity living
			&& target instanceof LivingEntity livingTarget
			&& check(living, livingTarget);
	}

	@Override
	@Deprecated
	public boolean check(Entity entity, Location location) {
		return entity instanceof LivingEntity living && check(living, location);
	}

}
