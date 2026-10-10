package com.nisovin.magicspells.spells.targeted;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.nisovin.magicspells.util.managers.AlteredBlockManager;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsBlockBreakEvent;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class DestroySpell extends TargetedSpell implements TargetedLocationSpell, TargetedEntityFromLocationSpell {

	private final Random random = ThreadLocalRandom.current();

	private Set<Material> blockTypesToThrow;
	private Set<Material> blockTypesToRemove;
	private Set<Material> blockTypesToIgnore;
	private Map<FallingBlock, Long> fallingBlocks;

	private ConfigData<Integer> vertRadius;
	private ConfigData<Integer> horizRadius;
	private ConfigData<Integer> fallingBlockMaxHeight;
	private ConfigData<Integer> duration;

	private ConfigData<Double> velocity;

	private ConfigData<Float> fallingBlockDamage;
	private ConfigData<Float> throwChance;

	private boolean checkPlugins;
	private boolean preventLandingBlocks;
	private boolean resolveDamagePerBlock;
	private boolean resolveVelocityPerBlock;
	private boolean resolveMaxHeightPerBlock;
	private ConfigData<Boolean> powerAffectsRadius;
	private boolean affectsContainers;

	private ConfigData<Shape> shape;
	private VelocityType velocityType;

	public DestroySpell(MagicConfig config, String spellName) {
		super(config, spellName);

		fallingBlocks = new HashMap<>();

		vertRadius = getConfigDataInt("vert-radius", 3);
		horizRadius = getConfigDataInt("horiz-radius", 3);
		fallingBlockMaxHeight = getConfigDataInt("falling-block-max-height", 0);
		duration = getConfigDataInt("duration", 0);

		velocity = getConfigDataDouble("velocity", 0);

		fallingBlockDamage = getConfigDataFloat("falling-block-damage", 0);
		throwChance = getConfigDataFloat("throw-chance", 100F);

		checkPlugins = getConfigBoolean("check-plugins", true);
		preventLandingBlocks = getConfigBoolean("prevent-landing-blocks", false);
		resolveDamagePerBlock = getConfigBoolean("resolve-damage-per-block", false);
		resolveVelocityPerBlock = getConfigBoolean("resolve-velocity-per-block", false);
		resolveMaxHeightPerBlock = getConfigBoolean("resolve-max-height-per-block", false);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", false);
		affectsContainers = getConfigBoolean("affects-containers", false);

		shape = getConfigDataEnum("shape", Shape.class, Shape.CUBE);

		String vType = getConfigString("velocity-type", "none");

		switch (vType) {
			case "up" -> velocityType = VelocityType.UP;
			case "random" -> velocityType = VelocityType.RANDOM;
			case "randomup", "random_up" -> velocityType = VelocityType.RANDOM_UP;
			case "down" -> velocityType = VelocityType.DOWN;
			case "toward" -> velocityType = VelocityType.TOWARD;
			case "away" -> velocityType = VelocityType.AWAY;
			default -> velocityType = VelocityType.NONE;
		}

		List<String> toThrow = getConfigStringList("block-types-to-throw", null);
		if (toThrow != null && !toThrow.isEmpty()) {
			blockTypesToThrow = EnumSet.noneOf(Material.class);
			for (String s : toThrow) {
				Material m = Util.getMaterial(s);
				if (m == null)
					continue;
				blockTypesToThrow.add(m);
			}
		}

		List<String> toRemove = getConfigStringList("block-types-to-remove", null);
		if (toRemove != null && !toRemove.isEmpty()) {
			blockTypesToRemove = EnumSet.noneOf(Material.class);
			for (String s : toRemove) {
				Material m = Util.getMaterial(s);
				if (m == null)
					continue;
				blockTypesToRemove.add(m);
			}
		}

		List<String> toIgnore = getConfigStringList("block-types-to-ignore", null);
		if (toIgnore != null && !toIgnore.isEmpty()) {
			blockTypesToIgnore = EnumSet.noneOf(Material.class);
			for (String s : toIgnore) {
				Material m = Util.getMaterial(s);
				if (m == null)
					continue;
				blockTypesToIgnore.add(m);
			}
		}

		registerEvents(new FallingBlockListener());
		MagicSpells.scheduleRepeatingTask(() -> {
			if (fallingBlocks.isEmpty())
				return;
			fallingBlocks.keySet().removeIf(fallingBlock -> !fallingBlock.isValid());
		}, 600, 600);
	}

	@Override
	public void turnOff() {
		for (FallingBlock fb : fallingBlocks.keySet()) {
			fb.remove();
		}
		fallingBlocks.clear();

		MagicSpells.getAlteredBlockManager().undoAll(internalName, false);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			Block b = getTargetedBlock(caster, power, args);
			if (b != null && !BlockUtils.isAir(b.getType())) {
				SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, caster, b.getLocation(), power,
						args);
				EventUtil.call(event);
				if (event.isCancelled())
					b = null;
				else
					b = event.getTargetLocation().getBlock();
			}
			if (b != null && !BlockUtils.isAir(b.getType())) {
				Location loc = b.getLocation().add(0.5, 0.5, 0.5);
				doIt(caster, null, caster.getLocation(), loc, power, args);
				playSpellEffects(caster, loc, power, args);
			}
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		doIt(caster, null, caster.getLocation(), target, power, args);
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		doIt(caster, target, from, target.getLocation(), power, args);
		playSpellEffects(caster, from, target, new SpellData(caster, target, from, power, args));
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		doIt(null, target, from, target.getLocation(), power, args);
		playSpellEffects(from, target, new SpellData(null, target, power, args));
		return true;
	}

	private void doIt(LivingEntity caster, LivingEntity target, Location source, Location targetLocation, float power,
			String[] args) {
		int centerX = targetLocation.getBlockX();
		int centerY = targetLocation.getBlockY();
		int centerZ = targetLocation.getBlockZ();

		List<Block> blocksToThrow = new ArrayList<>();
		List<Block> blocksToRemove = new ArrayList<>();

		int vertRadius = this.vertRadius.get(caster, target, power, args);
		int horizRadius = this.horizRadius.get(caster, target, power, args);

		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, target, power, args))) {
			vertRadius = Math.round(vertRadius * power);
			horizRadius = Math.round(horizRadius * power);
		}

		float throwChance = this.throwChance.get(caster, target, power, null) / 100;
		int duration = this.duration.get(caster, target, power, args);
		Shape shape = this.shape.get(caster, target, power, args);
		SpellData spellData = new SpellData(caster, target, targetLocation, power, args);

		double horizRadiusSq = (double) horizRadius * horizRadius;
		double vertRadiusSq = (double) vertRadius * vertRadius;

		for (int y = centerY - vertRadius; y <= centerY + vertRadius; y++) {
			for (int x = centerX - horizRadius; x <= centerX + horizRadius; x++) {
				for (int z = centerZ - horizRadius; z <= centerZ + horizRadius; z++) {
					if (shape == Shape.SPHERE) {
						double dx = x - centerX;
						double dy = y - centerY;
						double dz = z - centerZ;
						double horizComponent = horizRadiusSq == 0
								? (dx == 0 && dz == 0 ? 0 : 2)
								: (dx * dx + dz * dz) / horizRadiusSq;
						double vertComponent = vertRadiusSq == 0 ? (dy == 0 ? 0 : 2) : (dy * dy) / vertRadiusSq;
						if (horizComponent + vertComponent > 1)
							continue;
					}

					Block b = targetLocation.getWorld().getBlockAt(x, y, z);
					if (b.getType() == Material.BEDROCK)
						continue;
					if (BlockUtils.isAir(b.getType()))
						continue;

					if (locationModifiers != null) {
						ModifierResult modifierResult = locationModifiers.applyLocation(spellData.location(b.getLocation()));
						if (!modifierResult.check())
							continue;
						spellData = modifierResult.data();
					}

					if (blockTypesToIgnore != null && blockTypesToIgnore.contains(b.getType())) {
						continue;
					}

					if (!affectsContainers && BlockUtils.isContainer(b))
						continue;

					boolean isThrowType = (blockTypesToThrow == null
							&& !(blockTypesToRemove != null && blockTypesToRemove.contains(b.getType())))
							|| (blockTypesToThrow != null && blockTypesToThrow.contains(b.getType()));

					if (isThrowType && b.getType().isSolid()) {
						if (throwChance >= 1 || random.nextFloat() <= throwChance)
							blocksToThrow.add(b);
						else
							blocksToRemove.add(b);
					} else if (blockTypesToRemove != null && blockTypesToRemove.contains(b.getType())) {
						blocksToRemove.add(b);
					}
				}
			}
		}

		for (Block b : blocksToRemove) {
			if (checkPlugins && caster instanceof Player) {
				MagicSpellsBlockBreakEvent event = new MagicSpellsBlockBreakEvent(b, (Player) caster, bypassDippGen);
				EventUtil.call(event);
				if (event.isCancelled())
					continue;
			}

			if (duration > 0) {
				AlteredBlockManager.Change change = MagicSpells.getAlteredBlockManager().apply(internalName, b,
						Material.AIR, false);
				MagicSpells.scheduleDelayedTask(() -> change.undo(false), duration);
			} else {
				b.setType(Material.AIR, false);
			}
		}

		double velocity = resolveVelocityPerBlock ? 0 : this.velocity.get(caster, target, power, args);
		float fallingBlockDamage = resolveDamagePerBlock ? 0 : this.fallingBlockDamage.get(caster, target, power, args);
		int fallingBlockHeight = resolveMaxHeightPerBlock ? 0
				: this.fallingBlockMaxHeight.get(caster, target, power, args);
		for (Block b : blocksToThrow) {
			if (checkPlugins && caster instanceof Player) {
				MagicSpellsBlockBreakEvent event = new MagicSpellsBlockBreakEvent(b, (Player) caster, bypassDippGen);
				EventUtil.call(event);
				if (event.isCancelled())
					continue;
			}

			BlockData blockData = b.getBlockData();

			if (duration > 0) {
				AlteredBlockManager.Change change = MagicSpells.getAlteredBlockManager().apply(internalName, b,
						Material.AIR, false);
				MagicSpells.scheduleDelayedTask(() -> change.undo(false), duration);
			} else {
				b.setType(Material.AIR, false);
			}

			Location l = b.getLocation().clone().add(0.5, 0.5, 0.5);
			SpellData data = new SpellData(caster, target, l, power, args);
			FallingBlock fb = b.getWorld().spawn(l, FallingBlock.class);
			fb.setBlockData(blockData);
			fb.addScoreboardTag(MagicSpells.ENTITY_TAG);
			fb.addScoreboardTag(MagicSpells.EXPIRATION_TIME_MILLIS_TAG + ':' + (System.currentTimeMillis() + 10000));

			fallingBlocks.put(fb, duration > 0 ? System.currentTimeMillis() + (duration * 50L) : null);

			fb.setDropItem(false);
			playSpellEffects(EffectPosition.PROJECTILE, fb, data);
			playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, source, fb.getLocation(), null, fb,
					data);

			Vector v;
			if (resolveVelocityPerBlock)
				velocity = this.velocity.get(caster, target, power, args);
			if (velocityType == VelocityType.UP) {
				v = new Vector(0, velocity, 0);
				v.setY(v.getY() + ((Math.random() - 0.5) / 4));
			} else if (velocityType == VelocityType.RANDOM) {
				v = new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5);
				v.normalize().multiply(velocity);
			} else if (velocityType == VelocityType.RANDOM_UP) {
				v = new Vector(Math.random() - 0.5, Math.random() / 2, Math.random() - 0.5);
				v.normalize().multiply(velocity);
				fb.setVelocity(v);
			} else if (velocityType == VelocityType.DOWN)
				v = new Vector(0, -velocity, 0);
			else if (velocityType == VelocityType.TOWARD)
				v = source.toVector().subtract(l.toVector()).normalize().multiply(velocity);
			else if (velocityType == VelocityType.AWAY)
				v = l.toVector().subtract(source.toVector()).normalize().multiply(velocity);
			else
				v = new Vector(0, (Math.random() - 0.5) / 4, 0);

			fb.setVelocity(v);

			if (resolveDamagePerBlock)
				fallingBlockDamage = this.fallingBlockDamage.get(caster, target, power, args);
			if (fallingBlockDamage > 0) {
				if (resolveMaxHeightPerBlock)
					fallingBlockHeight = this.fallingBlockMaxHeight.get(caster, target, power, args);
				MagicSpells.getVolatileCodeHandler().setFallingBlockHurtEntities(fb, fallingBlockDamage,
						fallingBlockHeight);
			}
		}

	}

	private class FallingBlockListener implements Listener {

		@EventHandler
		public void onBlockLand(EntityChangeBlockEvent event) {
			if (event.getEntity() instanceof FallingBlock fallingBlock) {
				if (fallingBlocks.containsKey(fallingBlock)) {
					Long endTime = fallingBlocks.get(fallingBlock);
					fallingBlocks.remove(fallingBlock);

					event.getEntity().remove();
					event.setCancelled(true);
					if (!preventLandingBlocks) {
						Block landBlock = BlockUtils
								.findNearestAirRecursive(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 3);
						if (landBlock != null) {
							if (endTime != null) {
								long duration = (endTime - System.currentTimeMillis()) / 50;

								if (duration >= 1) {
									AlteredBlockManager.Change change = MagicSpells.getAlteredBlockManager()
											.apply(internalName, landBlock, event.getBlockData(), false);
									MagicSpells.scheduleDelayedTask(() -> change.undo(false), duration);
								} else {
									landBlock.setBlockData(event.getBlockData(), false);
								}
							} else {
								landBlock.setBlockData(event.getBlockData(), false);
							}
						}
					}
				}
			}
		}
	}

	public enum VelocityType {

		NONE,
		UP,
		RANDOM,
		RANDOM_UP,
		DOWN,
		TOWARD,
		AWAY

	}

	public enum Shape {

		CUBE,
		SPHERE

	}

}
