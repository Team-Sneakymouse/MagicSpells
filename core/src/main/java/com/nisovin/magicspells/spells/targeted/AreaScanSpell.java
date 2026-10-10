package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.block.data.BlockData;

import de.slikey.effectlib.util.VectorUtils;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.util.managers.VariableManager;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

public class AreaScanSpell extends TargetedSpell implements TargetedLocationSpell {

	private Set<BlockData> blocks;
	private Set<BlockData> deniedBlocks;

	private final ConfigData<Integer> xRadius;
	private final ConfigData<Integer> yRadius;
	private final ConfigData<Integer> zRadius;
	private final ConfigData<Integer> maxBlocks;
	private final ConfigData<Integer> xInnerRadius;
	private final ConfigData<Integer> yInnerRadius;
	private final ConfigData<Integer> zInnerRadius;

	private final ConfigData<Float> tolerance;
	private final ConfigData<Float> innerTolerance;

	private final ConfigData<Shape> shape;

	private final Vector absoluteOffset;
	private final Vector relativeOffset;

	private String spellToCast;
	private final String xVariable;
	private final String yVariable;
	private final String zVariable;

	private Subspell spell;

	private final boolean pointBlank;
	private final boolean blockCoords;
	private final boolean failIfNoTargets;
	private final ConfigData<Boolean> powerAffectsRadius;
	private final ConfigData<Boolean> powerAffectsMaxBlocks;

	public AreaScanSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		ConfigData<Integer> radius = getConfigDataInt("radius", 0);
		xRadius = getConfigDataInt("x-radius", radius);
		yRadius = getConfigDataInt("y-radius", radius);
		zRadius = getConfigDataInt("z-radius", radius);

		ConfigData<Integer> innerRadius = getConfigDataInt("inner-radius", -1);
		xInnerRadius = getConfigDataInt("inner-x-radius", innerRadius);
		yInnerRadius = getConfigDataInt("inner-y-radius", innerRadius);
		zInnerRadius = getConfigDataInt("inner-z-radius", innerRadius);

		maxBlocks = getConfigDataInt("max-blocks", 0);

		tolerance = getConfigDataFloat("tolerance", 1);
		innerTolerance = getConfigDataFloat("inner-tolerance", tolerance);

		absoluteOffset = getConfigVector("absolute-offset", "0,0,0");
		relativeOffset = getConfigVector("relative-offset", "0,0,0");

		shape = getConfigDataEnum("shape", Shape.class, Shape.BOX);

		xVariable = getConfigString("x-variable", null);
		yVariable = getConfigString("y-variable", null);
		zVariable = getConfigString("z-variable", null);
		spellToCast = getConfigString("spell", null);

		pointBlank = getConfigBoolean("point-blank", false);
		blockCoords = getConfigBoolean("block-coords", false);
		failIfNoTargets = getConfigBoolean("fail-if-not-found", true);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", true);
		powerAffectsMaxBlocks = getConfigDataBoolean("power-affects-max-blocks", true);

		List<String> blockStrings = getConfigStringList("blocks", null);
		if (blockStrings != null && !blockStrings.isEmpty()) {
			blocks = new HashSet<>();

			for (String blockDataString : blockStrings) {
				try {
					blocks.add(Bukkit.createBlockData(blockDataString));
				} catch (IllegalArgumentException e) {
					MagicSpells
							.error("Invalid block '" + blockDataString + "' in AreaScanSpell '" + internalName + "'.");
				}
			}

			if (blocks.isEmpty())
				blocks = null;
		}

		List<String> deniedBlockStrings = getConfigStringList("denied-blocks", null);
		if (deniedBlockStrings != null && !deniedBlockStrings.isEmpty()) {
			deniedBlocks = new HashSet<>();

			for (String blockDataString : deniedBlockStrings) {
				try {
					deniedBlocks.add(Bukkit.createBlockData(blockDataString));
				} catch (IllegalArgumentException e) {
					MagicSpells.error(
							"Invalid denied block '" + blockDataString + "' in AreaScanSpell '" + internalName + "'.");
				}

			}

			if (deniedBlocks.isEmpty())
				deniedBlocks = null;
		}
	}

	@Override
	public void initialize() {
		super.initialize();

		if (spellToCast != null && !spellToCast.isEmpty()) {
			spell = new Subspell(spellToCast);

			if (!spell.process()) {
				MagicSpells.error(
						"AreaScanSpell '" + internalName + "' has an invalid 'spell' '" + spellToCast + "' defined!");
				spell = null;
			}

			spellToCast = null;
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			Location origin;
			if (pointBlank)
				origin = caster.getLocation();
			else {
				Block target = getTargetedBlock(caster, power, args);
				if (target == null)
					return noTarget(caster, args);

				origin = target.getLocation();
			}

			if (!scan(caster, origin, power, args))
				return noTarget(caster, args);
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return scan(caster, target.clone(), power, args);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return scan(null, target.clone(), power, args);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return scan(caster, target.clone(), power, null);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(Location target, float power) {
		return scan(null, target.clone(), power, null);
	}

	private boolean scan(LivingEntity caster, Location origin, float power, String[] args) {
		if (blockCoords)
			origin.set(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());

		if (relativeOffset.getX() != 0 || relativeOffset.getY() != 0 || relativeOffset.getZ() != 0)
			origin.add(VectorUtils.rotateVector(relativeOffset, origin));

		origin.add(absoluteOffset);

		int xRadius = this.xRadius.get(caster, null, power, args);
		int yRadius = this.yRadius.get(caster, null, power, args);
		int zRadius = this.zRadius.get(caster, null, power, args);
		if (xRadius < 0 || yRadius < 0 || zRadius < 0)
			return false;

		int xInnerRadius = this.xInnerRadius.get(caster, null, power, args);
		int yInnerRadius = this.yInnerRadius.get(caster, null, power, args);
		int zInnerRadius = this.zInnerRadius.get(caster, null, power, args);

		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args))) {
			xRadius = Math.round(xRadius * power);
			yRadius = Math.round(yRadius * power);
			zRadius = Math.round(zRadius * power);

			xInnerRadius = Math.round(xInnerRadius * power);
			yInnerRadius = Math.round(yInnerRadius * power);
			zInnerRadius = Math.round(zInnerRadius * power);
		}

		xRadius = Math.min(xRadius, MagicSpells.getGlobalRadius());
		yRadius = Math.min(yRadius, MagicSpells.getGlobalRadius());
		zRadius = Math.min(zRadius, MagicSpells.getGlobalRadius());

		xInnerRadius = Math.min(xInnerRadius, MagicSpells.getGlobalRadius());
		yInnerRadius = Math.min(yInnerRadius, MagicSpells.getGlobalRadius());
		zInnerRadius = Math.min(zInnerRadius, MagicSpells.getGlobalRadius());

		int count = this.maxBlocks.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsMaxBlocks.get(caster, null, power, args)))
			count = Math.round(count * power);

		SpellData data = new SpellData(caster, origin, power, args);

		Shape shape = this.shape.get(caster, null, power, args);
		float xRadiusInv = shape == Shape.X_CYLINDER || xRadius == 0 ? 0 : 1f / (xRadius * xRadius);
		float yRadiusInv = shape == Shape.Y_CYLINDER || yRadius == 0 ? 0 : 1f / (yRadius * yRadius);
		float zRadiusInv = shape == Shape.Z_CYLINDER || zRadius == 0 ? 0 : 1f / (zRadius * zRadius);
		float xInnerRadiusInv = shape == Shape.X_CYLINDER ? 0 : 1f / (xInnerRadius * xInnerRadius);
		float yInnerRadiusInv = shape == Shape.Y_CYLINDER ? 0 : 1f / (yInnerRadius * yInnerRadius);
		float zInnerRadiusInv = shape == Shape.Z_CYLINDER ? 0 : 1f / (zInnerRadius * zInnerRadius);

		float tolerance = this.tolerance.get(caster, null, power, args);
		float innerTolerance = this.innerTolerance.get(caster, null, power, args);

		boolean cull = xInnerRadius >= 0 && yInnerRadius >= 0 && zInnerRadius >= 0;
		boolean boxCull = shape == Shape.BOX && cull;

		VariableManager manager = MagicSpells.getVariableManager();
		String playerCaster = caster instanceof Player player ? player.getName() : null;

		int minRadius = boxCull ? Math.min(xInnerRadius, Math.min(yInnerRadius, zInnerRadius)) : 0;
		int maxRadius = Math.max(xRadius, Math.max(yRadius, zRadius));

		boolean found = false;
		loop: for (int d = minRadius; d <= maxRadius; d++) {
			int xBound = Math.min(xRadius, d);
			if (boxCull && xBound <= xInnerRadius)
				continue;

			for (int xOffset = -xBound; xOffset <= xBound; xOffset++) {
				int yBound = Math.min(yRadius, d);
				if (boxCull && yBound <= yInnerRadius)
					continue;

				for (int yOffset = -yBound; yOffset <= yBound; yOffset++) {
					int zBound = Math.min(zRadius, d);
					if (boxCull && zBound <= zInnerRadius)
						continue;

					for (int zOffset = -zBound; zOffset <= zBound; zOffset++) {
						if (xOffset != d && xOffset != -d && yOffset != d && yOffset != -d && zOffset != d
								&& zOffset != -d)
							continue;

						if (shape != Shape.BOX) {
							int xOffsetSq = xOffset * xOffset;
							int yOffsetSq = yOffset * yOffset;
							int zOffsetSq = zOffset * zOffset;

							if (xRadiusInv * xOffsetSq + yRadiusInv * yOffsetSq + zRadiusInv * zOffsetSq > tolerance)
								continue;
							if (cull && xInnerRadiusInv * xOffsetSq + yInnerRadiusInv * yOffsetSq
									+ zInnerRadiusInv * zOffsetSq <= innerTolerance) {
								switch (shape) {
									case ELLIPSOID -> {
										continue;
									}
									case X_CYLINDER -> {
										if (xOffset >= -xInnerRadius && xOffset <= xInnerRadius)
											continue;
									}
									case Y_CYLINDER -> {
										if (yOffset >= -yInnerRadius && yOffset <= yInnerRadius)
											continue;
									}
									case Z_CYLINDER -> {
										if (zOffset >= -zInnerRadius && zOffset <= zInnerRadius)
											continue;
									}
								}
							}
						}

						Location target = origin.clone().add(xOffset, yOffset, zOffset);
						if (!check(target.getBlock().getBlockData()))
							continue;

						if (playerCaster != null) {
							if (xVariable != null)
								manager.set(xVariable, playerCaster, target.getX());
							if (yVariable != null)
								manager.set(yVariable, playerCaster, target.getY());
							if (zVariable != null)
								manager.set(zVariable, playerCaster, target.getZ());
						}

						SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, caster, target, power,
								args);
						if (!event.callEvent())
							continue;

						float subPower = event.getPower();
						target = event.getTargetLocation();
						found = true;

						if (spell != null)
							spell.subcast(caster, target, subPower, args);

						SpellData effectData = power == subPower ? data : new SpellData(caster, origin, subPower, args);
						playSpellEffects(EffectPosition.TARGET, target, effectData);
						playSpellEffectsTrail(origin, target, effectData);

						if (count == 1)
							break loop;
						else if (count > 0)
							count--;
					}
				}
			}
		}

		boolean success = found || !failIfNoTargets;
		if (success && caster != null)
			playSpellEffects(EffectPosition.CASTER, caster, data);

		return success;
	}

	private boolean check(BlockData data) {
		if (deniedBlocks != null)
			for (BlockData bd : deniedBlocks)
				if (data.matches(bd))
					return false;

		if (blocks != null)
			for (BlockData bd : blocks)
				if (data.matches(bd))
					return true;

		return blocks == null;
	}

	private enum Shape {
		BOX,
		X_CYLINDER,
		Y_CYLINDER,
		Z_CYLINDER,
		ELLIPSOID
	}

}
