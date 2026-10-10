package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class CombustSpell extends TargetedSpell implements TargetedEntitySpell {

	private Map<UUID, CombustData> combusting;

	private ConfigData<Integer> fireTicks;
	private ConfigData<Double> fireTickDamage;

	private ConfigData<Boolean> checkPlugins;
	private ConfigData<Boolean> preventImmunity;
	private ConfigData<Boolean> powerAffectsFireTicks;
	private ConfigData<Boolean> powerAffectsFireTickDamage;

	public CombustSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		fireTicks = getConfigDataInt("fire-ticks", 100);
		fireTickDamage = getConfigDataDouble("fire-tick-damage", 1);

		checkPlugins = getConfigDataBoolean("check-plugins", true);
		preventImmunity = getConfigDataBoolean("prevent-immunity", true);
		powerAffectsFireTicks = getConfigDataBoolean("power-affects-fire-ticks", true);
		powerAffectsFireTickDamage = getConfigDataBoolean("power-affects-fire-tick-damage", true);

		combusting = new HashMap<>();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
			if (target.noTarget()) return noTarget(caster, args, target);

			boolean combusted = combust(caster, target.target(), target.power(), args);
			if (!combusted) return noTarget(caster, args);

			sendMessages(caster, target.target(), args);
			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		return combust(caster, target, power, args);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		return combust(null, target, power, args);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
	}

	private boolean combust(LivingEntity caster, final LivingEntity target, float power, String[] args) {
		if (caster != null && Boolean.TRUE.equals(checkPlugins.get(caster, target, power, args))) {
			MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, target, DamageCause.MAGIC, 1, this);
			EventUtil.call(event);
			if (event.isCancelled()) return false;
		}

		int duration = fireTicks.get(caster, target, power, args);
		if (Boolean.TRUE.equals(powerAffectsFireTicks.get(caster, target, power, args))) duration = Math.round(duration * power);
		target.setFireTicks(duration);

		combusting.put(target.getUniqueId(), new CombustData(caster, power, args));

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);

		MagicSpells.scheduleDelayedTask(() -> combusting.remove(target.getUniqueId()), duration + 2);

		return true;
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.getCause() != DamageCause.FIRE_TICK) return;

		Entity entity = event.getEntity();
		if (!(entity instanceof LivingEntity target)) return;

		CombustData data = combusting.get(target.getUniqueId());
		if (data == null) return;

		double fireTickDamage = this.fireTickDamage.get(data.caster, target, data.power, data.args);
		if (Boolean.TRUE.equals(powerAffectsFireTickDamage.get(data.caster, target, data.power, data.args))) fireTickDamage = fireTickDamage * data.power;

		EventUtil.call(new SpellApplyDamageEvent(this, data.caster, target, fireTickDamage, DamageCause.FIRE_TICK, ""));
		event.setDamage(fireTickDamage);

		if (Boolean.TRUE.equals(preventImmunity.get(data.caster, target, data.power, data.args))) {
			MagicSpells.scheduleDelayedTask(() -> target.setNoDamageTicks(0), 0);
		}
	}

	private record CombustData(LivingEntity caster, float power, String[] args) {}

}
