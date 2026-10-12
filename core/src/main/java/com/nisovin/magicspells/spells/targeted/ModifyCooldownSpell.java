package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.SpellFilter;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class ModifyCooldownSpell extends TargetedSpell implements TargetedEntitySpell {

	private final SpellFilter filter;

	private final ConfigData<Float> seconds;
	private final ConfigData<Float> multiplier;

	private final ConfigData<Boolean> powerAffectsSeconds;
	private final ConfigData<Boolean> powerAffectsMultiplier;

	public ModifyCooldownSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		seconds = getConfigDataFloat("seconds", 1F);
		multiplier = getConfigDataFloat("multiplier", 1F);

		powerAffectsSeconds = getConfigDataBoolean("power-affects-seconds", true);
		powerAffectsMultiplier = getConfigDataBoolean("power-affects-multiplier", true);

		filter = getConfigSpellFilter();
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), data.args());
					if (target.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), target), data);
		
					modifyCooldowns(data.caster(), target.target(), target.power(), data.args());
					sendMessages(data.caster(), target.target(), data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							modifyCooldowns(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							modifyCooldowns(null, data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private void modifyCooldowns(Entity caster, Entity target, float power, String[] args) {
		float sec = seconds.get(caster, target, power, args);
		if (Boolean.TRUE.equals(powerAffectsSeconds.get(caster, target, power, args))) sec *= power;

		float mult = multiplier.get(caster, target, power, args);
		if (Boolean.TRUE.equals(powerAffectsMultiplier.get(caster, target, power, args))) mult /= power;

		for (Spell spell : MagicSpells.spells()) {
			if (!spell.onCooldown(target)) continue;
			if (!filter.check(spell)) continue;

			float cd = spell.getCooldown(target) - sec;
			cd *= mult;
			if (cd < 0) cd = 0;
			spell.setCooldown(target, cd, false);
		}

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);
	}

}
