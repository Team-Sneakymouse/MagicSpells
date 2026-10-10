package com.nisovin.magicspells.spells.targeted;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.DamageSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.mana.ManaChangeReason;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityRegainHealthEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class DrainlifeSpell extends TargetedSpell implements TargetedEntitySpell, DamageSpell {

	private static final String STR_MANA = "mana";
	private static final String STR_HEALTH = "health";
	private static final String STR_HUNGER = "hunger";
	private static final String STR_EXPERIENCE = "experience";

	private static final int MAX_FOOD_LEVEL = 20;
	private static final int MIN_FOOD_LEVEL = 0;
	private static final double MIN_HEALTH = 0D;

	private String takeType;
	private String giveType;
	private String spellDamageType;

	private ConfigData<Double> takeAmt;
	private ConfigData<Double> giveAmt;

	private ConfigData<Integer> animationSpeed;

	private boolean instant;
	private boolean ignoreArmor;
	private boolean checkPlugins;
	private boolean showSpellEffect;
	private ConfigData<Boolean> powerAffectsAmount;
	private boolean avoidDamageModification;

	private String spellOnAnimationName;
	private Subspell spellOnAnimation;

	private DamageCause damageType;

	public DrainlifeSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		takeType = getConfigString("take-type", "health");
		giveType = getConfigString("give-type", "health");
		spellDamageType = getConfigString("spell-damage-type", "");

		takeAmt = getConfigDataDouble("take-amt", 2);
		giveAmt = getConfigDataDouble("give-amt", 2);

		animationSpeed = getConfigDataInt("animation-speed", 2);

		instant = getConfigBoolean("instant", true);
		ignoreArmor = getConfigBoolean("ignore-armor", false);
		checkPlugins = getConfigBoolean("check-plugins", true);
		showSpellEffect = getConfigBoolean("show-spell-effect", true);
		powerAffectsAmount = getConfigDataBoolean("power-affects-amount", true);
		avoidDamageModification = getConfigBoolean("avoid-damage-modification", true);

		spellOnAnimationName = getConfigString("spell-on-animation", "");

		String damageTypeName = getConfigString("damage-type", "MAGIC");
		try {
			damageType = DamageCause.valueOf(damageTypeName.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			DebugHandler.debugBadEnumValue(DamageCause.class, damageTypeName);
			damageType = DamageCause.MAGIC;
		}
	}

	@Override
	public void initialize() {
		super.initialize();

		spellOnAnimation = new Subspell(spellOnAnimationName);
		if (!spellOnAnimation.process()) {
			spellOnAnimation = null;
			if (!spellOnAnimationName.isEmpty())
				MagicSpells.error("DrainlifeSpell '" + internalName + "' has an invalid spell-on-animation defined!");
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
			if (target.noTarget())
				return noTarget(caster, args, target);

			boolean drained = drain(caster, target.target(), target.power(), args);
			if (!drained)
				return noTarget(caster, args);

			sendMessages(caster, target.target(), args);
			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		return drain(caster, target, power, args);
	}

	@Override
	public String getSpellDamageType() {
		return spellDamageType;
	}

	private boolean drain(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (caster == null)
			return false;
		if (target == null)
			return false;

		double take = takeAmt.get(caster, target, power, args);
		double give = giveAmt.get(caster, target, power, args);
		if (Boolean.TRUE.equals(powerAffectsAmount.get(caster, target, power, args))) {
			take *= power;
			give *= power;
		}

		Player playerTarget = target instanceof Player p ? p : null;

		switch (takeType) {
			case STR_HEALTH -> {
				if (checkPlugins) {
					MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster,
							target, damageType, take, this);
					if (!event.callEvent())
						return false;
					if (!avoidDamageModification)
						take = event.getDamage();
				}

				SpellApplyDamageEvent event = new SpellApplyDamageEvent(this, caster, target, take, damageType,
						spellDamageType);
				EventUtil.call(event);
				take = event.getFinalDamage();
				if (ignoreArmor) {
					double health = target.getHealth();
					if (health > Util.getMaxHealth(target))
						health = Util.getMaxHealth(target);
					health -= take;
					if (health < MIN_HEALTH)
						health = MIN_HEALTH;
					if (health > Util.getMaxHealth(target))
						health = Util.getMaxHealth(target);
					if (health == MIN_HEALTH && caster instanceof Player)
						target.setKiller((Player) caster);
					target.setHealth(health);
					target.setLastDamage(take);
					MagicSpells.getVolatileCodeHandler().playHurtAnimation(target,
							LocationUtil.getRotatedLocation(caster.getLocation(), target.getLocation()).getYaw());
				} else
					target.damage(take, caster);
			}
			case STR_MANA -> {
				if (playerTarget == null)
					break;
				boolean removed = MagicSpells.getManaHandler().removeMana(playerTarget, (int) Math.round(take),
						ManaChangeReason.OTHER);
				if (!removed)
					give = 0;
			}
			case STR_HUNGER -> {
				if (playerTarget == null)
					break;
				int food = playerTarget.getFoodLevel();
				if (give > food)
					give = food;
				food -= take;
				if (food < MIN_FOOD_LEVEL)
					food = MIN_FOOD_LEVEL;
				playerTarget.setFoodLevel(food);
			}
			case STR_EXPERIENCE -> {
				if (playerTarget == null)
					break;
				int exp = ExperienceUtils.getCurrentExp(playerTarget);
				if (give > exp)
					give = exp;
				ExperienceUtils.changeExp(playerTarget, (int) Math.round(-take));
			}
		}

		if (instant) {
			giveToCaster(caster, give);
			playSpellEffects(caster, target, power, args);
		} else
			playSpellEffects(EffectPosition.TARGET, target, new SpellData(caster, target, power, args));

		if (showSpellEffect)
			new DrainAnimation(caster, target, target.getLocation(), give, power, args);

		return true;
	}

	private void giveToCaster(LivingEntity caster, double give) {
		switch (giveType) {
			case STR_HEALTH -> {
				if (checkPlugins) {
					MagicSpellsEntityRegainHealthEvent event = new MagicSpellsEntityRegainHealthEvent(caster, give,
							EntityRegainHealthEvent.RegainReason.CUSTOM);
					if (!event.callEvent())
						return;

					give = event.getAmount();
				}

				double h = caster.getHealth() + give;
				if (h > Util.getMaxHealth(caster))
					h = Util.getMaxHealth(caster);
				caster.setHealth(h);
			}
			case STR_MANA -> {
				if (caster instanceof Player)
					MagicSpells.getManaHandler().addMana((Player) caster, (int) give, ManaChangeReason.OTHER);
			}
			case STR_HUNGER -> {
				if (caster instanceof Player) {
					int food = ((Player) caster).getFoodLevel();
					food += give;
					if (food > MAX_FOOD_LEVEL)
						food = MAX_FOOD_LEVEL;
					((Player) caster).setFoodLevel(food);
				}
			}
			case STR_EXPERIENCE -> {
				if (caster instanceof Player)
					ExperienceUtils.changeExp((Player) caster, (int) give);
			}
		}
	}

	private class DrainAnimation extends SpellAnimation {

		private final LivingEntity caster;
		private final SpellData data;
		private final Vector current;
		private final double giveAmt;
		private final World world;
		private final int range;

		private DrainAnimation(LivingEntity caster, LivingEntity target, Location start, double giveAmt, float power,
				String[] args) {
			super(animationSpeed.get(caster, target, power, args), true);

			this.caster = caster;
			this.giveAmt = giveAmt;

			data = new SpellData(caster, target, start, power, args);
			current = start.toVector();
			world = caster.getWorld();
			range = getRange(power);
		}

		@Override
		protected void onTick(int tick) {
			Vector v = current.clone().subtract(caster.getLocation().toVector()).normalize();
			current.subtract(v);

			Location playAt = current.toLocation(world).setDirection(v);
			playSpellEffects(EffectPosition.SPECIAL, playAt, data);
			if (current.distanceSquared(caster.getLocation().toVector()) < 4 || tick > range * 1.5) {
				stop(true);
				playSpellEffects(EffectPosition.DELAYED, caster, data);
				if (spellOnAnimation != null)
					spellOnAnimation.subcast(new SpellData(caster, data.power(), data.args()));
				if (!instant)
					giveToCaster(caster, giveAmt);
			}
		}

	}

}
