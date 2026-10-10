package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class GeyserSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<BlockData> geyserType;

	private ConfigData<Double> damage;
	private ConfigData<Double> velocity;

	private ConfigData<Integer> geyserHeight;
	private ConfigData<Integer> animationSpeed;

	private boolean ignoreArmor;
	private boolean checkPlugins;
	private ConfigData<Boolean> powerAffectsDamage;
	private boolean avoidDamageModification;

	public GeyserSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		damage = getConfigDataDouble("damage", 0);
		velocity = getConfigDataDouble("velocity", 10);

		geyserHeight = getConfigDataInt("geyser-height", 4);
		animationSpeed = getConfigDataInt("animation-speed", 2);

		ignoreArmor = getConfigBoolean("ignore-armor", false);
		checkPlugins = getConfigBoolean("check-plugins", true);
		powerAffectsDamage = getConfigDataBoolean("power-affects-damage", true);
		avoidDamageModification = getConfigBoolean("avoid-damage-modification", false);

		geyserType = getConfigDataBlockData("geyser-type", Material.WATER.createBlockData());
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
			if (target.noTarget()) return noTarget(caster, args, target);

			boolean ok = geyser(caster, target.target(), target.power(), args);
			if (!ok) return noTarget(caster, args);

			playSpellEffects(caster, target.target(), target.power(), args);
			sendMessages(caster, target.target(), args);

			return PostCastAction.NO_MESSAGES;
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	private boolean geyser(LivingEntity caster, LivingEntity target, float power, String[] args) {
		double damage = this.damage.get(caster, target, power, args);
		if (Boolean.TRUE.equals(powerAffectsDamage.get(caster, target, power, args))) damage *= power;

		if (caster != null && checkPlugins && damage > 0) {
			MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, target, DamageCause.MAGIC, damage, this);
			EventUtil.call(event);
			if (event.isCancelled()) return false;
			if (!avoidDamageModification) damage = event.getDamage();
		}

		if (damage > 0) {
			if (ignoreArmor) {
				double health = target.getHealth() - damage;
				if (health < 0) health = 0;
				target.setHealth(health);
				if (caster != null) MagicSpells.getVolatileCodeHandler().playHurtAnimation(target, LocationUtil.getRotatedLocation(caster.getLocation(), target.getLocation()).getYaw());
				else MagicSpells.getVolatileCodeHandler().playHurtAnimation(target, target.getLocation().getYaw());
			} else {
				if (caster != null) target.damage(damage, caster);
				else target.damage(damage);
			}
		}

		double velocity = this.velocity.get(caster, target, power, args) / 10;
		if (velocity > 0) target.setVelocity(new Vector(0, velocity * power, 0));

		int geyserHeight = this.geyserHeight.get(caster, target, power, args);
		if (geyserHeight > 0) {
			List<Entity> allNearby = target.getNearbyEntities(50, 50, 50);
			allNearby.add(target);

			List<Player> playersNearby = new ArrayList<>();
			for (Entity e : allNearby) {
				if (!(e instanceof Player)) continue;
				playersNearby.add((Player) e);
			}

			int animationSpeed = this.animationSpeed.get(caster, target, power, args);

			BlockData blockType = this.geyserType.get(caster, target, power, args);
			new GeyserAnimation(blockType, target.getLocation(), playersNearby, animationSpeed, geyserHeight);
		}

		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		geyser(caster, target, power, args);
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		geyser(null, target, power, args);
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
	}

	private static class GeyserAnimation extends SpellAnimation {

		private final BlockData blockData;
		private final List<Player> nearby;
		private final int geyserHeight;
		private final Location start;

		private GeyserAnimation(BlockData blockData, Location start, List<Player> nearby, int animationSpeed, int geyserHeight) {
			super(0, animationSpeed, true, false);

			this.blockData = blockData;
			this.start = start;
			this.nearby = nearby;
			this.geyserHeight = geyserHeight;
		}

		@Override
		protected void onTick(int tick) {
			if (blockData == null) {
				stop();
				return;
			}

			if (tick > geyserHeight << 1) {
				stop();
				return;
			}

			if (tick < geyserHeight) {
				Block block = start.clone().add(0, tick, 0).getBlock();
				if (!BlockUtils.isAir(block.getType())) return;
				for (Player p : nearby) p.sendBlockChange(block.getLocation(), blockData);
				return;
			}

			int n = geyserHeight - (tick - geyserHeight) - 1;
			Block block = start.clone().add(0, n, 0).getBlock();
			for (Player p : nearby) p.sendBlockChange(block.getLocation(), block.getBlockData());
		}

	}

}
