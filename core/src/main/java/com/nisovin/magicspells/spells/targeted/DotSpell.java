package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.DamageSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class DotSpell extends TargetedSpell implements TargetedEntitySpell, DamageSpell {

	private Map<UUID, Dot> activeDots;

	private ConfigData<Integer> delay;
	private ConfigData<Integer> interval;
	private ConfigData<Integer> duration;

	private ConfigData<Double> damage;

	private ConfigData<Boolean> ignoreArmor;
	private ConfigData<Boolean> checkPlugins;
	private ConfigData<Boolean> powerAffectsDamage;
	private ConfigData<Boolean> avoidDamageModification;
	private ConfigData<Boolean> tryAvoidingAntiCheatPlugins;

	private String spellDamageType;
	private DamageCause damageType;

	public DotSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		delay = getConfigDataInt("delay", 1);
		interval = getConfigDataInt("interval", 20);
		duration = getConfigDataInt("duration", 200);

		damage = getConfigDataDouble("damage", 2);

		ignoreArmor = getConfigDataBoolean("ignore-armor", false);
		checkPlugins = getConfigDataBoolean("check-plugins", true);
		powerAffectsDamage = getConfigDataBoolean("power-affects-damage", true);
		avoidDamageModification = getConfigDataBoolean("avoid-damage-modification", true);
		tryAvoidingAntiCheatPlugins = getConfigDataBoolean("try-avoiding-anticheat-plugins", false);

		spellDamageType = getConfigString("spell-damage-type", "");
		String damageTypeName = getConfigString("damage-type", "MAGIC");
		try {
			damageType = DamageCause.valueOf(damageTypeName.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			DebugHandler.debugBadEnumValue(DamageCause.class, damageTypeName);
			damageType = DamageCause.MAGIC;
		}

		activeDots = new HashMap<>();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> targetInfo = getTargetedEntity(caster, power, args);
			if (targetInfo.noTarget()) return noTarget(caster, args, targetInfo);

			applyDot(caster, targetInfo.target(), targetInfo.power(), args);
			sendMessages(caster, targetInfo.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		applyDot(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		if (!validTargetList.canTarget(caster, target)) return false;
		applyDot(caster, target, power, null);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		applyDot(null, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		if (!validTargetList.canTarget(target)) return false;
		applyDot(null, target, power, null);
		return true;
	}

	@Override
	public String getSpellDamageType() {
		return spellDamageType;
	}

	public boolean isActive(LivingEntity entity) {
		return activeDots.containsKey(entity.getUniqueId());
	}

	public void cancelDot(LivingEntity entity) {
		if (!isActive(entity)) return;
		Dot dot = activeDots.get(entity.getUniqueId());
		dot.cancel();
	}

	private void applyDot(LivingEntity caster, LivingEntity target, float power, String[] args) {
		Dot dot = activeDots.get(target.getUniqueId());
		if (dot != null) {
			dot.caster = caster;
			dot.power = power;
			dot.args = args;

			dot.init();
		} else {
			dot = new Dot(caster, target, power, args);
			activeDots.put(target.getUniqueId(), dot);
		}

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);
	}

	@EventHandler
	private void onDeath(PlayerDeathEvent event) {
		Dot dot = activeDots.get(event.getEntity().getUniqueId());
		if (dot != null) dot.cancel();
	}

	private class Dot implements Runnable {

		private final LivingEntity target;
		private LivingEntity caster;
		private String[] args;
		private float power;

		private int taskId = -1;
		private int duration;
		private int interval;
		private int dur = 0;

		private Dot(LivingEntity caster, LivingEntity target, float power, String[] args) {
			this.caster = caster;
			this.target = target;
			this.power = power;
			this.args = args;

			init();
		}

		private void init() {
			if (taskId != -1) MagicSpells.cancelTask(taskId);

			interval = DotSpell.this.interval.get(caster, target, power, args);
			duration = DotSpell.this.duration.get(caster, target, power, args);
			dur = 0;

			taskId = MagicSpells.scheduleRepeatingTask(this, delay.get(caster, target, power, args), interval);
		}

		@Override
		public void run() {
			dur += interval;
			if (dur > duration) {
				cancel();
				return;
			}

			if (target.isDead() || !target.isValid()) {
				cancel();
				return;
			}

			SpellData data = new SpellData(caster, target, power, args);

			double localDamage = damage.get(data);
			if (Boolean.TRUE.equals(powerAffectsDamage.get(data))) localDamage *= power;

			if (Boolean.TRUE.equals(checkPlugins.get(data))) {
				MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, target, damageType, localDamage, DotSpell.this);
				EventUtil.call(event);
				if (event.isCancelled()) return;
				if (!Boolean.TRUE.equals(avoidDamageModification.get(data))) localDamage = event.getDamage();
			}

			SpellApplyDamageEvent event = new SpellApplyDamageEvent(DotSpell.this, caster, target, localDamage, damageType, spellDamageType);
			EventUtil.call(event);
			localDamage = event.getFinalDamage();

			if (Boolean.TRUE.equals(ignoreArmor.get(data))) {
				double maxHealth = Util.getMaxHealth(target);
				double health = target.getHealth();

				if (health > maxHealth) health = maxHealth;
				health -= localDamage;

				if (health < 0) {
					health = 0;
					if (caster instanceof Player) target.setKiller((Player) caster);
				}

				if (health > maxHealth) health = maxHealth;

				target.setHealth(health);
				target.setLastDamage(localDamage);

				if (caster != null) MagicSpells.getVolatileCodeHandler().playHurtAnimation(target, LocationUtil.getRotatedLocation(caster.getLocation(), target.getLocation()).getYaw());
				else MagicSpells.getVolatileCodeHandler().playHurtAnimation(target, target.getLocation().getYaw());
			} else {
				if (Boolean.TRUE.equals(tryAvoidingAntiCheatPlugins.get(data))) target.damage(localDamage);
				else target.damage(localDamage, caster);
			}

			playSpellEffects(EffectPosition.DELAYED, target, data);
			target.setNoDamageTicks(0);
		}

		private void cancel() {
			MagicSpells.cancelTask(taskId);
			activeDots.remove(target.getUniqueId());
		}

	}

}
