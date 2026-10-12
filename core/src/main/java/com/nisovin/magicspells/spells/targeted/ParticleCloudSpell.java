package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.ColorUtil;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class ParticleCloudSpell extends TargetedSpell implements TargetedLocationSpell, TargetedEntitySpell {

	private Vector relativeOffset;

	private Component customName;

	private Particle particle;
	private String particleName;

	private Material material;
	private String materialName;

	private BlockData blockData;
	private ItemStack itemStack;

	private float dustSize;
	private String colorHex;
	private Color dustColor;
	private DustOptions dustOptions;

	private boolean none = true;
	private boolean item = false;
	private boolean dust = false;
	private boolean block = false;

	private ConfigData<Integer> color;
	private ConfigData<Integer> waitTime;
	private ConfigData<Integer> ticksDuration;
	private ConfigData<Integer> durationOnUse;
	private ConfigData<Integer> reapplicationDelay;

	private ConfigData<Float> radius;
	private ConfigData<Float> radiusOnUse;
	private ConfigData<Float> radiusPerTick;

	private boolean useGravity;
	private boolean canTargetEntities;
	private boolean canTargetLocation;

	private Set<PotionEffect> potionEffects;

	public ParticleCloudSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		relativeOffset = getConfigVector("relative-offset", "0,0.5,0");

		customName = Util.getMiniMessage(getConfigString("custom-name", null));

		particleName = getConfigString("particle-name", "EXPLOSION_NORMAL");
		particle = Util.getParticle(particleName);

		materialName = getConfigString("material", "");
		material = Util.getMaterial(materialName);

		dustSize = getConfigFloat("size", 1);
		colorHex = getConfigString("dust-color", "FF0000");
		dustColor = ColorUtil.getColorFromHexString(colorHex);
		if (dustColor != null) dustOptions = new DustOptions(dustColor, dustSize);

		if ((particle == Particle.BLOCK || particle == Particle.FALLING_DUST) && material != null && material.isBlock()) {
			block = true;
			blockData = material.createBlockData();
			none = false;
		} else if (particle == Particle.ITEM && material != null && material.isItem()) {
			item = true;
			itemStack = new ItemStack(material);
			none = false;
		} else if (particle == Particle.DUST && dustOptions != null) {
			dust = true;
			none = false;
		}

		if (particle == null) MagicSpells.error("ParticleCloudSpell '" + internalName + "' has a wrong particle-name defined! '" + particleName + "'");

		if ((particle == Particle.BLOCK || particle == Particle.FALLING_DUST) && (material == null || !material.isBlock())) {
			particle = null;
			MagicSpells.error("ParticleCloudSpell '" + internalName + "' has a wrong material defined! '" + materialName + "'");
		}

		if (particle == Particle.ITEM && (material == null || !material.isItem())) {
			particle = null;
			MagicSpells.error("ParticleCloudSpell '" + internalName + "' has a wrong material defined! '" + materialName + "'");
		}

		if (particle == Particle.DUST && dustColor == null) {
			particle = null;
			MagicSpells.error("ParticleCloudSpell '" + internalName + "' has a wrong dust-color defined! '" + colorHex + "'");
		}

		color = getConfigDataInt("color", 0xFF0000);
		waitTime = getConfigDataInt("wait-time-ticks", 10);
		ticksDuration = getConfigDataInt("duration-ticks", 3 * TimeUtil.TICKS_PER_SECOND);
		durationOnUse = getConfigDataInt("duration-ticks-on-use", 0);
		reapplicationDelay = getConfigDataInt("reapplication-delay-ticks", 3 * TimeUtil.TICKS_PER_SECOND);

		radius = getConfigDataFloat("radius", 5F);
		radiusOnUse = getConfigDataFloat("radius-on-use", 0F);
		radiusPerTick = getConfigDataFloat("radius-per-tick", 0F);

		useGravity = getConfigBoolean("use-gravity", false);
		canTargetEntities = getConfigBoolean("can-target-entities", true);
		canTargetLocation = getConfigBoolean("can-target-location", true);

		List<String> potionEffectStrings = getConfigStringList("potion-effects", null);
		if (potionEffectStrings == null) potionEffectStrings = new ArrayList<>();

		potionEffects = new HashSet<>();

		for (String effect: potionEffectStrings) {
			potionEffects.add(getPotionEffectFromString(effect));
		}
	}

	private static PotionEffect getPotionEffectFromString(String s) {
		String[] splits = s.split(" ");
		PotionEffectType type = Util.getPotionEffectType(splits[0]);

		int durationTicks = Integer.parseInt(splits[1]);
		int amplifier = Integer.parseInt(splits[2]);

		boolean ambient = Boolean.parseBoolean(splits[3]);
		boolean particles = Boolean.parseBoolean(splits[4]);
		boolean icon = Boolean.parseBoolean(splits[5]);

		return new PotionEffect(type, durationTicks, amplifier, ambient, particles, icon);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), data.args());
		if (targetInfo.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);

		data = data.target(targetInfo.target()).power(targetInfo.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return result;

		
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}
	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

		@Override
	public CastResult castAtLocation(SpellData data) {
		if (!canTargetLocation) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		AreaEffectCloud cloud = spawnCloud(data.caster(), null, data.location(), data.power(), data.args());
		if (data.caster() instanceof org.bukkit.projectiles.ProjectileSource source)
			cloud.setSource(source);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	
	}
@Override
	public boolean castAtLocation(Entity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}




	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!canTargetEntities || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							AreaEffectCloud cloud = spawnCloud(data.caster(), data.target(), data.target().getLocation(), data.power(), data.args());
							if (data.caster() instanceof org.bukkit.projectiles.ProjectileSource source)
								cloud.setSource(source);
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!canTargetEntities || !validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							spawnCloud(null, data.target(), data.target().getLocation(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private AreaEffectCloud spawnCloud(Entity caster, Entity target, Location loc, float power, String[] args) {
		Location location = loc.clone();
		Vector startDir = loc.getDirection().normalize();

		//apply relative offset
		Vector horizOffset = new Vector(-startDir.getZ(), 0, startDir.getX()).normalize();
		location.add(horizOffset.multiply(relativeOffset.getZ()));
		location.add(location.getDirection().clone().multiply(relativeOffset.getX()));
		location.setY(location.getY() + relativeOffset.getY());

		AreaEffectCloud cloud = location.getWorld().spawn(location, AreaEffectCloud.class);
		if (block) cloud.setParticle(particle, blockData);
		else if (item) cloud.setParticle(particle, itemStack);
		else if (dust) cloud.setParticle(particle, dustOptions);
		else if (none) cloud.setParticle(particle);

		cloud.setColor(Color.fromRGB(color.get(caster, target, power, args)));
		cloud.setRadius(radius.get(caster, target, power, args));
		cloud.setGravity(useGravity);
		cloud.setWaitTime(waitTime.get(caster, target, power, args));
		cloud.setDuration(ticksDuration.get(caster, target, power, args));
		cloud.setDurationOnUse(durationOnUse.get(caster, target, power, args));
		cloud.setRadiusOnUse(radiusOnUse.get(caster, target, power, args));
		cloud.setRadiusPerTick(radiusPerTick.get(caster, target, power, args));
		cloud.setReapplicationDelay(reapplicationDelay.get(caster, target, power, args));

		for (PotionEffect eff : potionEffects) {
			cloud.addCustomEffect(eff, true);
		}

		if (customName != null) {
			cloud.customName(customName);
			cloud.setCustomNameVisible(true);
		}

		return cloud;
	}

}
