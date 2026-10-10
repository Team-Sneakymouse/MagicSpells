package com.nisovin.magicspells.spells.targeted;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.damage.DamageType;
import org.bukkit.damage.DamageSource;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.LocationUtil;
import com.nisovin.magicspells.spells.DamageSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.compat.CompatBasics;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class PainSpell extends TargetedSpell implements TargetedEntitySpell, DamageSpell {

	private String spellDamageType;
	private DamageType damageType;
	private DamageCause damageCause;

	private ConfigData<Double> damage;

	private ConfigData<Boolean> ignoreArmor;
	private ConfigData<Boolean> checkPlugins;
	private ConfigData<Boolean> powerAffectsDamage;
	private ConfigData<Boolean> avoidDamageModification;
	private ConfigData<Boolean> tryAvoidingAntiCheatPlugins;
	
	public PainSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		spellDamageType = getConfigString("spell-damage-type", "");
		String damageTypeName = getConfigString("damage-type", "MAGIC");
		try {
			damageCause = DamageCause.valueOf(damageTypeName.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			DebugHandler.debugBadEnumValue(DamageCause.class, damageTypeName);
			damageCause = DamageCause.MAGIC;
		}

		damageType = resolveDamageType(damageTypeName);
		if (damageType == null) {
			MagicSpells.error("PainSpell '" + internalName + "' has an invalid damage-type '" + damageTypeName + "'. Defaulting to MAGIC.");
			damageType = DamageType.MAGIC;
		}

		damage = getConfigDataDouble("damage", 4);

		ignoreArmor = getConfigDataBoolean("ignore-armor", false);
		checkPlugins = getConfigDataBoolean("check-plugins", true);
		powerAffectsDamage = getConfigDataBoolean("power-affects-damage", true);
		avoidDamageModification = getConfigDataBoolean("avoid-damage-modification", true);
		tryAvoidingAntiCheatPlugins = getConfigDataBoolean("try-avoiding-anticheat-plugins", false);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
			if (target.noTarget()) return noTarget(caster, args, target);

			boolean done;
			if (caster instanceof Player) done = CompatBasics.exemptAction(() -> causePain(caster, target.target(), target.power(), args), (Player) caster, CompatBasics.activeExemptionAssistant.getPainExemptions());
			else done = causePain(caster, target.target(), target.power(), args);
			if (!done) return noTarget(caster, args);
			
			sendMessages(caster, target.target(), args);
			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		return causePain(caster, target, power, args);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		return causePain(null, target, power, args);
	}

	@Override
	public String getSpellDamageType() {
		return spellDamageType;
	}
	
	private boolean causePain(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (target == null) return false;
		if (target.isDead()) return false;

		double localDamage = damage.get(caster, target, power, args);
		if (Boolean.TRUE.equals(powerAffectsDamage.get(caster, target, power, args))) localDamage *= power;

		if (Boolean.TRUE.equals(checkPlugins.get(caster, target, power, args))) {
			MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, target, damageCause, localDamage, this);
			EventUtil.call(event);
			if (event.isCancelled()) return false;
			if (!Boolean.TRUE.equals(avoidDamageModification.get(caster, target, power, args))) localDamage = event.getDamage();
		}

		SpellApplyDamageEvent event = new SpellApplyDamageEvent(this, caster, target, localDamage, damageCause, spellDamageType);
		EventUtil.call(event);
		localDamage = event.getFinalDamage();

		if (Boolean.TRUE.equals(ignoreArmor.get(caster, target, power, args))) {
			double health = target.getHealth();
			if (health > Util.getMaxHealth(target)) health = Util.getMaxHealth(target);
			health -= localDamage;
			if (health < 0) health = 0;
			if (health > Util.getMaxHealth(target)) health = Util.getMaxHealth(target);
			if (health == 0 && caster instanceof Player) target.setKiller((Player) caster);

			target.setHealth(health);
			target.setLastDamage(localDamage);

			if (caster != null) MagicSpells.getVolatileCodeHandler().playHurtAnimation(target, LocationUtil.getRotatedLocation(caster.getLocation(), target.getLocation()).getYaw());
			else MagicSpells.getVolatileCodeHandler().playHurtAnimation(target, target.getLocation().getYaw());

			if (caster != null) playSpellEffects(caster, target, power, args);
			else playSpellEffects(EffectPosition.TARGET, target, power, args);

			return true;
		}

		DamageSource.Builder damageSourceBuilder = DamageSource.builder(damageType);
		if (caster != null && !Boolean.TRUE.equals(tryAvoidingAntiCheatPlugins.get(caster, target, power, args))) {
			damageSourceBuilder.withDirectEntity(caster).withCausingEntity(caster);
		}
		DamageSource damageSource = damageSourceBuilder.build();

		target.damage(localDamage, damageSource);

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);

		return true;
	}

	private DamageType resolveDamageType(String damageTypeName) {
		if (damageTypeName == null || damageTypeName.isEmpty()) return DamageType.MAGIC;

		String normalized = damageTypeName.trim();
		String lowerCase = normalized.toLowerCase(Locale.ROOT);

		NamespacedKey key = NamespacedKey.fromString(lowerCase);
		if (key == null) key = NamespacedKey.minecraft(lowerCase);

		if (key != null) {
			Registry<DamageType> damageTypeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE);
			if (damageTypeRegistry != null) {
				DamageType type = damageTypeRegistry.get(key);
				if (type != null) return type;
			}
		}

		try {
			Object value = DamageType.class.getField(normalized.toUpperCase(Locale.ROOT)).get(null);
			if (value instanceof DamageType dt) return dt;
		} catch (ReflectiveOperationException ignored) {
		}

		return null;
	}

}
