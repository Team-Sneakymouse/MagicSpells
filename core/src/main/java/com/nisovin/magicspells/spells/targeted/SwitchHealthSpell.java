package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

public class SwitchHealthSpell extends TargetedSpell implements TargetedEntitySpell {

	private boolean requireLesserHealthPercent;
	private boolean requireGreaterHealthPercent;

	public SwitchHealthSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		requireLesserHealthPercent = getConfigBoolean("require-lesser-health-percent", false);
		requireGreaterHealthPercent = getConfigBoolean("require-greater-health-percent", false);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof LivingEntity caster))
			return new CastResult(noTarget(data.caster(), data.args()), data);
		TargetInfo<LivingEntity> target = getTargetedEntity(caster, data.power(), data.args());
					if (target.noTarget()) return new CastResult(noTarget(caster, data.args(), target), data);
		
					boolean ok = switchHealth(caster, target.target(), target.power(), data.args());
					if (!ok) return new CastResult(noTarget(caster, data.args()), data);
		
					sendMessages(caster, target.target(), data.args());
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
		if (!(data.caster() instanceof LivingEntity caster))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!(data.target() instanceof LivingEntity target))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				boolean ok = switchHealth(caster, target, data.power(), data.args());
		return new CastResult(ok ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
	}


	private boolean switchHealth(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (caster.isDead() || target.isDead()) return false;
		double casterPct = caster.getHealth() / Util.getMaxHealth(caster);
		double targetPct = target.getHealth() / Util.getMaxHealth(target);
		if (requireGreaterHealthPercent && casterPct < targetPct) return false;
		if (requireLesserHealthPercent && casterPct > targetPct) return false;
		caster.setHealth(targetPct * Util.getMaxHealth(caster));
		target.setHealth(casterPct * Util.getMaxHealth(target));
		playSpellEffects(caster, target, power, args);
		return true;
	}

}
