package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.ConfigReaderUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;

public class PotionEffectSpell extends TargetedSpell implements TargetedEntitySpell {

	private List<ConfigData<PotionEffect>> potionEffects;
	private List<?> potionEffectData;

	private ConfigData<PotionEffectType> type;

	private ConfigData<Integer> duration;
	private ConfigData<Integer> strength;

	private ConfigData<Boolean> icon;
	private ConfigData<Boolean> hidden;
	private ConfigData<Boolean> ambient;

	private boolean override;
	private boolean targeted;
	private boolean spellPowerAffectsDuration;
	private boolean spellPowerAffectsStrength;

	public PotionEffectSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		potionEffectData = getConfigList("potion-effects", null);

		type = ConfigDataUtil.getPotionEffectType(config.getMainConfig(), "spells." + internalName + ".type",
				PotionEffectType.SPEED);

		duration = getConfigDataInt("duration", 0);
		strength = getConfigDataInt("strength", 0);

		icon = getConfigDataBoolean("icon", true);
		hidden = getConfigDataBoolean("hidden", false);
		ambient = getConfigDataBoolean("ambient", false);
		targeted = getConfigBoolean("targeted", false);
		override = getConfigBoolean("override", false);
		spellPowerAffectsDuration = getConfigBoolean("spell-power-affects-duration", true);
		spellPowerAffectsStrength = getConfigBoolean("spell-power-affects-strength", true);
	}

	@Override
	public void initialize() {
		super.initialize();

		if (potionEffectData == null)
			return;

		potionEffects = new ArrayList<>();
		for (Object potionEffectObj : potionEffectData) {
			if (potionEffectObj instanceof String potionEffectString) {
				String[] data = potionEffectString.split(" ");
				if (data.length == 0)
					continue;

				PotionEffectType type = Util.getPotionEffectType(data[0]);
				if (type == null) {
					MagicSpells.error("Invalid potion effect string '" + potionEffectString + "' in PotionEffectSpell '"
							+ internalName + "'.");
					continue;
				}

				int duration = 0;
				if (data.length >= 2) {
					try {
						duration = Integer.parseInt(data[1]);
					} catch (NumberFormatException e) {
						MagicSpells.error("Invalid duration '" + duration + "' in potion effect string '"
								+ potionEffectString + "' in PotionEffectSpell '" + internalName + "'.");
						continue;
					}
				}

				int strength = 0;
				if (data.length >= 3) {
					try {
						strength = Integer.parseInt(data[2]);
					} catch (NumberFormatException e) {
						MagicSpells.error("Invalid strength '" + strength + "' in potion effect string '"
								+ potionEffectString + "' in PotionEffectSpell '" + internalName + "'.");
						continue;
					}
				}

				boolean ambient = data.length >= 4 && Boolean.parseBoolean(data[4]);
				boolean particles = data.length < 5 || !Boolean.parseBoolean(data[3]);
				boolean icon = data.length < 6 || Boolean.parseBoolean(data[5]);

				if (data.length > 6)
					MagicSpells.error("Trailing data found in potion effect string '" + potionEffectString
							+ "' in PotionEffectSpell '" + internalName + "'.");

				if (spellPowerAffectsDuration || spellPowerAffectsStrength) {
					int finalDuration = duration;
					int finalStrength = strength;

					ConfigData<PotionEffect> effect;
					if (!spellPowerAffectsStrength)
						effect = (caster, target, location, power, args) -> new PotionEffect(type,
								Math.round(finalDuration * power), finalStrength, ambient, particles, icon);
					else if (!spellPowerAffectsDuration)
						effect = (caster, target, location, power, args) -> new PotionEffect(type, finalDuration,
								Math.round(finalStrength * power), ambient, particles, icon);
					else
						effect = (caster, target, location, power, args) -> new PotionEffect(type,
								Math.round(finalDuration * power), Math.round(finalStrength * power), ambient,
								particles, icon);

					potionEffects.add(effect);
				} else {
					PotionEffect effect = new PotionEffect(type, duration, strength, ambient, particles, icon);
					potionEffects.add((caster, target, location, power, args) -> effect);
				}
			} else if (potionEffectObj instanceof Map<?, ?> potionEffectMap) {
				ConfigurationSection section = ConfigReaderUtil.mapToSection(potionEffectMap);

				ConfigData<PotionEffectType> type = ConfigDataUtil.getPotionEffectType(section, "type",
						PotionEffectType.SPEED);
				ConfigData<Integer> duration = ConfigDataUtil.getInteger(section, "duration", 0);
				ConfigData<Integer> strength = ConfigDataUtil.getInteger(section, "strength", 0);
				ConfigData<Boolean> ambient = ConfigDataUtil.getBoolean(section, "ambient", false);
				ConfigData<Boolean> hidden = ConfigDataUtil.getBoolean(section, "hidden", false);
				ConfigData<Boolean> icon = ConfigDataUtil.getBoolean(section, "icon", true);

				ConfigData<PotionEffect> effect = (caster, target, location, power, args) -> {
					int d = duration.get(caster, target, power, args);
					if (spellPowerAffectsDuration)
						d = Math.round(d * power);

					int s = strength.get(caster, target, power, args);
					if (spellPowerAffectsStrength)
						s = Math.round(s * power);

					return new PotionEffect(
							type.get(caster, target, power, args),
							d,
							s,
							ambient.get(caster, target, power, args),
							!hidden.get(caster, target, power, args),
							icon.get(caster, target, power, args));
				};

				potionEffects.add(effect);
			}
		}

		if (potionEffects.isEmpty())
			potionEffects = null;
		potionEffectData = null;
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			LivingEntity target;
			if (targeted) {
				TargetInfo<LivingEntity> targetInfo = getTargetedEntity(caster, power, args);
				if (targetInfo.noTarget())
					return noTarget(caster, args, targetInfo);

				target = targetInfo.target();
				power = targetInfo.power();
			} else {
				SpellTargetEvent targetEvent = new SpellTargetEvent(this, caster, caster, power, args);
				targetEvent.callEvent();

				if (targetEvent.isCastCancelled())
					return PostCastAction.ALREADY_HANDLED;
				else if (targetEvent.isCancelled())
					return noTarget(caster, args);

				target = targetEvent.getTarget();
				power = targetEvent.getPower();
			}

			handlePotionEffects(caster, target, power, args);
			playSpellEffects(caster, target, power, args);
			sendMessages(caster, target, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		handlePotionEffects(caster, target, power, args);
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		handlePotionEffects(null, target, power, args);
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	public void handlePotionEffects(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (potionEffects == null) {
			PotionEffectType type = this.type.get(caster, target, power, args);

			int duration = this.duration.get(caster, target, power, args);
			if (spellPowerAffectsDuration)
				duration = Math.round(duration * power);

			int strength = this.strength.get(caster, target, power, args);
			if (spellPowerAffectsStrength)
				strength = Math.round(strength * power);

			boolean ambient = this.ambient.get(caster, target, power, args);
			boolean particles = !this.hidden.get(caster, target, power, args);
			boolean icon = this.icon.get(caster, target, power, args);

			PotionEffect effect = new PotionEffect(type, duration, strength, ambient, particles, icon);

			callDamageEvent(caster, target, effect);

			if (override && target.hasPotionEffect(type))
				target.removePotionEffect(type);
			target.addPotionEffect(effect);

			return;
		}

		for (ConfigData<PotionEffect> effectData : potionEffects) {
			PotionEffect effect = effectData.get(caster, target, power, args);

			callDamageEvent(caster, target, effect);

			if (override && target.hasPotionEffect(effect.getType()))
				target.removePotionEffect(effect.getType());
			target.addPotionEffect(effect);
		}
	}

	private void callDamageEvent(LivingEntity caster, LivingEntity target, PotionEffect effect) {
		DamageCause cause = null;
		if (effect.getType() == PotionEffectType.POISON)
			cause = DamageCause.POISON;
		else if (effect.getType() == PotionEffectType.WITHER)
			cause = DamageCause.WITHER;

		if (cause != null)
			new SpellApplyDamageEvent(this, caster, target, effect.getAmplifier(), cause, "").callEvent();
	}

}
