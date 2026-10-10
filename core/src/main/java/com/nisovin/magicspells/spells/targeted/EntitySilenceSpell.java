package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.TargetBooleanState;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class EntitySilenceSpell extends TargetedSpell implements TargetedEntitySpell {
	
	private TargetBooleanState targetBooleanState;
	
	public EntitySilenceSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		targetBooleanState = TargetBooleanState.getFromName(getConfigString("target-state", "toggle"));
	}
	
	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> targetInfo = getTargetedEntity(caster, power, args);
			if (targetInfo.noTarget()) return noTarget(caster, args, targetInfo);
			LivingEntity target = targetInfo.target();

			target.setSilent(targetBooleanState.getBooleanState(target.isSilent()));
			playSpellEffects(caster, target, targetInfo.power(), args);
			sendMessages(caster, target, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}
	
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		target.setSilent(targetBooleanState.getBooleanState(target.isSilent()));
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		target.setSilent(targetBooleanState.getBooleanState(target.isSilent()));
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
	}

}
