package com.nisovin.magicspells.spells.targeted;

import org.bukkit.potion.PotionEffect;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class CrippleSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Integer> strength;
	private ConfigData<Integer> duration;
	private ConfigData<Integer> portalCooldown;

	private boolean useSlownessEffect;
	private boolean applyPortalCooldown;
	private ConfigData<Boolean> powerAffectsDuration;
	private ConfigData<Boolean> powerAffectsPortalCooldown;

	public CrippleSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		strength = getConfigDataInt("effect-strength", 5);
		duration = getConfigDataInt("effect-duration", 100);
		portalCooldown = getConfigDataInt("portal-cooldown-ticks", 100);

		useSlownessEffect = getConfigBoolean("use-slowness-effect", true);
		applyPortalCooldown = getConfigBoolean("apply-portal-cooldown", false);
		powerAffectsDuration = getConfigDataBoolean("power-affects-duration", true);
		powerAffectsPortalCooldown = getConfigDataBoolean("power-affects-portal-cooldown", true);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
					if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);
		
					cripple(data.caster(), info.target(), info.power(), data.args());
					sendMessages(data.caster(), info.target(), data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							cripple(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							cripple(null, data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private void cripple(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (target == null) return;

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);

		if (useSlownessEffect) {
			int strength = this.strength.get(caster, target, power, args);
			int duration = this.duration.get(caster, target, power, args);
			if (Boolean.TRUE.equals(powerAffectsDuration.get(caster, target, power, args))) duration = Math.round(duration * power);

			target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, strength));
		}

		if (applyPortalCooldown) {
			int portalCooldown = this.portalCooldown.get(caster, target, power, args);
			if (Boolean.TRUE.equals(powerAffectsPortalCooldown.get(caster, target, power, args))) portalCooldown = Math.round(portalCooldown * power);

			if (target.getPortalCooldown() < portalCooldown) target.setPortalCooldown(portalCooldown);
		}
	}

}
