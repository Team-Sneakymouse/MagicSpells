package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
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
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
			if (target.noTarget()) return noTarget(caster, args, target);

			modifyCooldowns(caster, target.target(), target.power(), args);
			sendMessages(caster, target.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		modifyCooldowns(caster, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		if (!validTargetList.canTarget(caster, target)) return false;
		modifyCooldowns(caster, target, power, null);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		modifyCooldowns(null, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		if (!validTargetList.canTarget(target)) return false;
		modifyCooldowns(null, target, power, null);
		return true;
	}

	private void modifyCooldowns(LivingEntity caster, LivingEntity target, float power, String[] args) {
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
