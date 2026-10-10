package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.ValidTargetChecker;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.MagicSpellsEntityRegainHealthEvent;

public class HealSpell extends TargetedSpell implements TargetedEntitySpell {

	private final ConfigData<Double> healAmount;
	private final ConfigData<Double> healPercent;

	private final boolean checkPlugins;
	private final boolean cancelIfFull;
	private final ConfigData<Boolean> powerAffectsHealAmount;

	private final String strMaxHealth;

	private final ValidTargetChecker checker;

	public HealSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		healAmount = getConfigDataDouble("heal-amount", 10);
		healPercent = getConfigDataDouble("heal-percent", 0);

		checkPlugins = getConfigBoolean("check-plugins", true);
		cancelIfFull = getConfigBoolean("cancel-if-full", true);
		powerAffectsHealAmount = getConfigDataBoolean("power-affects-heal-amount", true);

		strMaxHealth = getConfigString("str-max-health", "%t is already at max health.");

		checker = (LivingEntity entity) -> entity.getHealth() < Util.getMaxHealth(entity);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), checker, data.args());
					if (targetInfo.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);
		
					LivingEntity target = targetInfo.target();
					data = data.power(targetInfo.power());
		
					if (cancelIfFull && target.getHealth() == Util.getMaxHealth(target))
						return new CastResult(noTarget(data.caster(), formatMessage(strMaxHealth, "%t", getTargetName(target)), data.args()), data);
		
					boolean healed = heal(data.caster(), target, data.power(), data.args());
					if (!healed) return new CastResult(noTarget(data.caster(), data.args()), data);
		
					sendMessages(data.caster(), target, data.args());
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (validTargetList.canTarget(data.caster(), data.target()) && (!cancelIfFull || data.target().getHealth() < Util.getMaxHealth(data.target()))) {
				boolean ok = heal(data.caster(), data.target(), data.power(), data.args());
				return new CastResult(ok ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
			}
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}
		if (validTargetList.canTarget(data.target()) && (!cancelIfFull || data.target().getHealth() < Util.getMaxHealth(data.target()))) {
			boolean ok = heal(null, data.target(), data.power(), data.args());
			return new CastResult(ok ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
		}
		return new CastResult(PostCastAction.ALREADY_HANDLED, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public ValidTargetChecker getValidTargetChecker() {
		return checker;
	}

	private boolean heal(LivingEntity caster, LivingEntity target, float power, String[] args) {
		double health = target.getHealth();
		double amount;

		double healPercent = this.healPercent.get(caster, target, power, args);
		if (healPercent == 0) {
			amount = this.healAmount.get(caster, target, power, args);
			if (Boolean.TRUE.equals(powerAffectsHealAmount.get(caster, target, power, args))) amount *= power;
		} else amount = (Util.getMaxHealth(caster) - health) * (healPercent / 100);

		if (checkPlugins) {
			MagicSpellsEntityRegainHealthEvent event = new MagicSpellsEntityRegainHealthEvent(target, amount, RegainReason.CUSTOM);
			EventUtil.call(event);
			if (event.isCancelled()) return false;
			amount = event.getAmount();
		}

		health += amount;
		if (health > Util.getMaxHealth(target)) health = Util.getMaxHealth(target);
		target.setHealth(health);

		if (caster == null) playSpellEffects(EffectPosition.TARGET, target, power, args);
		else playSpellEffects(caster, target, power, args);
		return true;
	}

}
