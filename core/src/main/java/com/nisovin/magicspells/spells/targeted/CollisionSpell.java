package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.TargetBooleanState;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class CollisionSpell extends TargetedSpell implements TargetedEntitySpell {

	private TargetBooleanState targetBooleanState;

	public CollisionSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		targetBooleanState = TargetBooleanState.getFromName(getConfigString("target-state", "toggle"));
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
					if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);
					LivingEntity target = info.target();
		
					target.setCollidable(targetBooleanState.getBooleanState(target.isCollidable()));
					playSpellEffects(data.caster(), target, info.power(), data.args());
					sendMessages(data.caster(), target, data.args());
		
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
							if (!(data.target() instanceof LivingEntity livingTarget)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							livingTarget.setCollidable(targetBooleanState.getBooleanState(livingTarget.isCollidable()));
							playSpellEffects(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							if (!(data.target() instanceof LivingEntity livingTarget)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							livingTarget.setCollidable(targetBooleanState.getBooleanState(livingTarget.isCollidable()));
							playSpellEffects(EffectPosition.TARGET, data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



}
