package com.nisovin.magicspells.spells.instant;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.util.config.FunctionData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;

public class ThrowBlockSpell extends InstantSpell implements TargetedLocationSpell {

	public static List<ThrownBlock> thrownBlocks;
	private Map<Entity, ThrownBlock> fallingBlocks;

	private Material material;

	private ConfigData<Integer> tntFuse;
	private ConfigData<Integer> duration;

	private ConfigData<Float> yOffset;
	private ConfigData<Float> velocity;
	private ConfigData<Float> rotationOffset;
	private ConfigData<Float> verticalAdjustment;

	private boolean dropItem;
	private boolean stickyBlocks;
	private boolean checkPlugins;
	private boolean preventBlocks;
	private boolean callTargetEvent;
	private boolean ensureSpellCast;
	private ConfigData<Boolean> powerAffectsDamage;
	private boolean projectileHasGravity;
	private boolean applySpellPowerToVelocity;
	private boolean removeBlockOnLandSuccess;

	private final String spellOnLandName;

	private Subspell spellOnLand;

	public ThrowBlockSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		String materialName = getConfigString("block-type", "stone");
		if (materialName.toLowerCase().startsWith("primedtnt:")) {
			String[] split = materialName.split(":", 2);
			material = null;

			try {
				int fuse = Integer.parseInt(split[1]);
				tntFuse = (caster, target, location, power, args) -> fuse;
			} catch (NumberFormatException e) {
				tntFuse = FunctionData.build(split[1], Double::intValue, 0);
				if (tntFuse == null)
					MagicSpells
							.error("Invalid tnt fuse '" + split[1] + "' for ThrowBlockSpell '" + internalName + "'.");
			}
		} else {
			material = Util.getMaterial(materialName);
			tntFuse = null;
		}

		duration = getConfigDataInt("duration", 0);

		rotationOffset = getConfigDataFloat("rotation-offset", 0F);

		yOffset = getConfigDataFloat("y-offset", 0F);
		velocity = getConfigDataFloat("velocity", 1);
		verticalAdjustment = getConfigDataFloat("vertical-adjustment", 0.5F);

		dropItem = getConfigBoolean("drop-item", false);
		stickyBlocks = getConfigBoolean("sticky-blocks", false);
		checkPlugins = getConfigBoolean("check-plugins", true);
		preventBlocks = getConfigBoolean("prevent-blocks", false);
		callTargetEvent = getConfigBoolean("call-target-event", true);
		ensureSpellCast = getConfigBoolean("ensure-spell-cast", true);
		powerAffectsDamage = getConfigDataBoolean("power-affects-damage", true);
		projectileHasGravity = getConfigBoolean("gravity", true);
		applySpellPowerToVelocity = getConfigBoolean("apply-spell-power-to-velocity", false);
		removeBlockOnLandSuccess = getConfigBoolean("remove-block-on-land-success", false);

		spellOnLandName = getConfigString("spell-on-land", "");
	}

	@Override
	public void initialize() {
		super.initialize();

		if (material == null || !material.isBlock() && tntFuse == null) {
			MagicSpells.error("ThrowBlockSpell '" + internalName + "' has an invalid block-type defined!");
		}

		spellOnLand = new Subspell(spellOnLandName);
		if (!spellOnLand.process()) {
			if (!spellOnLandName.isEmpty())
				MagicSpells.error("ThrowBlockSpell '" + internalName + "' has an invalid spell-on-land defined!");
			spellOnLand = null;
		}

		thrownBlocks = new ArrayList<>();
		fallingBlocks = new HashMap<>();
		if (material != null)
			registerEvents(new ThrowBlockListener(this));
		else if (tntFuse != null)
			registerEvents(new TntListener());
	}

	@Override
	public void turnOff() {
		if (fallingBlocks != null) {
			for (ThrownBlock thrownBlock : thrownBlocks) {
				thrownBlock.undo();
			}
			for (Entity block : fallingBlocks.keySet()) {
				if (block != null)
					block.remove();
			}
			fallingBlocks.clear();
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			Vector v = getVector(caster, caster.getLocation(), power, args);
			Location l = caster.getEyeLocation().add(v);
			l.add(0, yOffset.get(caster, null, power, args), 0);
			ThrownBlock info = spawnFallingBlock(caster, l, v, power, args);
			playSpellEffects(EffectPosition.CASTER, caster, info.data);
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		Vector v = getVector(caster, target, power, args);
		ThrownBlock info = spawnFallingBlock(caster,
				target.clone().add(0, yOffset.get(caster, null, power, args), 0), v, power, args);
		playSpellEffects(EffectPosition.CASTER, target, info.data);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		Vector v = getVector(null, target, power, args);
		ThrownBlock info = spawnFallingBlock(null, target.clone().add(0, yOffset.get(null, null, power, args), 0),
				v, power, args);
		playSpellEffects(EffectPosition.CASTER, target, info.data);
		return true;
	}

	private Vector getVector(LivingEntity caster, Location loc, float power, String[] args) {
		Vector v = loc.getDirection();

		float verticalAdjustment = this.verticalAdjustment.get(caster, null, power, args);
		if (verticalAdjustment != 0)
			v.setY(v.getY() + verticalAdjustment);

		float rotationOffset = this.rotationOffset.get(caster, null, power, args);
		if (rotationOffset != 0)
			Util.rotateVector(v, rotationOffset);

		float velocity = this.velocity.get(caster, null, power, args);
		if (applySpellPowerToVelocity)
			velocity *= power;

		return v.normalize().multiply(velocity);
	}

	private ThrownBlock spawnFallingBlock(LivingEntity caster, Location location, Vector velocity, float power,
			String[] args) {
		Entity entity = null;
		ThrownBlock info = new ThrownBlock(material.createBlockData(), caster, power, args);

		if (material != null) {
			FallingBlock block = location.getWorld().spawn(location, FallingBlock.class);
			block.setBlockData(material.createBlockData());
			block.addScoreboardTag(MagicSpells.ENTITY_TAG);
			block.addScoreboardTag(MagicSpells.EXPIRATION_TIME_MILLIS_TAG + ':' + (System.currentTimeMillis() + 10000));

			block.setGravity(projectileHasGravity);
			playSpellEffects(EffectPosition.PROJECTILE, block, info.data);
			playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, caster.getLocation(),
					block.getLocation(), caster, block, info.data);
			block.setVelocity(velocity);
			block.setDropItem(dropItem);
			if (ensureSpellCast || stickyBlocks)
				new ThrowBlockMonitor(block, info);
			entity = block;

			int duration = this.duration.get(caster, power, args);
			if (duration > 0) {
				thrownBlocks.add(info);
				MagicSpells.scheduleDelayedTask(() -> {
					if (fallingBlocks.containsKey(block) && block.isValid()) {
						block.remove();
						fallingBlocks.remove(block);
					}
					if (thrownBlocks.contains(info)) {
						info.undo();
						thrownBlocks.remove(info);
					}
				}, duration);
			}
		} else if (tntFuse != null) {
			TNTPrimed tnt = location.getWorld().spawn(location, TNTPrimed.class);
			tnt.setGravity(projectileHasGravity);
			playSpellEffects(EffectPosition.PROJECTILE, tnt, info.data);
			playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, caster.getLocation(),
					tnt.getLocation(), caster, tnt, info.data);

			int tntFuse = this.tntFuse.get(caster, null, power, args);
			tnt.setFuseTicks(tntFuse);
			tnt.setVelocity(velocity);
			entity = tnt;
		}

		if (entity == null)
			return info;

		if (fallingBlocks != null) {
			fallingBlocks.put(entity, info);
		}

		return info;
	}

	private class ThrowBlockMonitor implements Runnable {

		private FallingBlock block;
		private ThrownBlock info;
		private int task;
		private int counter = 0;

		private ThrowBlockMonitor(FallingBlock fallingBlock, ThrownBlock ThrownBlock) {
			block = fallingBlock;
			info = ThrownBlock;
			task = MagicSpells.scheduleRepeatingTask(this, TimeUtil.TICKS_PER_SECOND, 1);
		}

		@Override
		public void run() {
			if (stickyBlocks && !block.isDead()) {
				if (block.getVelocity().lengthSquared() < .01) {
					if (!preventBlocks) {
						Block b = block.getLocation().getBlock();
						if (b.getType() == Material.AIR) {
							info.change = MagicSpells.getAlteredBlockManager().apply(internalName, b,
									block.getBlockData(), true);
							info.targetBlock = b;
						}
					}
					if (!info.spellActivated && spellOnLand != null) {
						boolean success = spellOnLand.subcast(new SpellData(info.caster, block.getLocation(), info.power, info.args));
						info.spellActivated = true;
						if (success && removeBlockOnLandSuccess)
							info.undo();
					}
					block.remove();
				}
			}
			if (ensureSpellCast && block.isDead()) {
				if (!info.spellActivated && spellOnLand != null) {
					boolean success = spellOnLand.subcast(new SpellData(info.caster, block.getLocation(), info.power, info.args));
					info.spellActivated = true;
					if (success && removeBlockOnLandSuccess)
						info.undo();
				}
				MagicSpells.cancelTask(task);
			}
			if (counter++ > 1500)
				MagicSpells.cancelTask(task);
		}

	}

	private class ThrowBlockListener implements Listener {

		private ThrowBlockSpell thisSpell;

		private ThrowBlockListener(ThrowBlockSpell throwBlockSpell) {
			thisSpell = throwBlockSpell;
		}

		@EventHandler(ignoreCancelled = true)
		private void onDamage(EntityDamageByEntityEvent event) {
			ThrownBlock info;
			if (preventBlocks)
				info = fallingBlocks.get(event.getDamager());
			else
				info = fallingBlocks.remove(event.getDamager());
			if (info == null || !(event.getEntity() instanceof LivingEntity target))
				return;

			float power = info.power;
			if (callTargetEvent && info.caster != null) {
				SpellTargetEvent evt = new SpellTargetEvent(thisSpell, info.caster, target, power, info.args);
				EventUtil.call(evt);
				if (evt.isCancelled()) {
					event.setCancelled(true);
					return;
				}

				power = evt.getPower();
			}

			double damage = event.getDamage();
			if (Boolean.TRUE.equals(powerAffectsDamage.get(info.caster, target, power, info.args)))
				damage *= power;

			if (checkPlugins && info.caster != null) {
				MagicSpellsEntityDamageByEntityEvent evt = new MagicSpellsEntityDamageByEntityEvent(info.caster, target,
						DamageCause.MAGIC, damage, ThrowBlockSpell.this);
				EventUtil.call(evt);
				if (evt.isCancelled()) {
					event.setCancelled(true);
					return;
				}
			}
			event.setDamage(damage);

			if (spellOnLand != null && !info.spellActivated) {
				boolean success = spellOnLand.subcast(new SpellData(info.caster, target.getLocation(), power, info.args));
				info.spellActivated = true;
				if (success && removeBlockOnLandSuccess) {
					info.undo();
					event.getDamager().remove();
				}
			}
		}

		@EventHandler(ignoreCancelled = true)
		private void onBlockLand(EntityChangeBlockEvent event) {
			ThrownBlock info = fallingBlocks.get(event.getEntity());
			boolean removed = fallingBlocks.keySet().remove(event.getEntity());

			if (removed) {
				event.getEntity().remove();
				event.setCancelled(true);
				if (!preventBlocks) {
					Block landBlock = BlockUtils
							.findNearestAirRecursive(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 3);
					if (landBlock != null) {
						if (info != null) {
							info.change = MagicSpells.getAlteredBlockManager().apply(internalName, landBlock,
									event.getBlockData(), false);
							info.targetBlock = landBlock;
						} else {
							landBlock.setBlockData(event.getBlockData(), false);
						}
					}
				}

				if (spellOnLand != null && info != null && !info.spellActivated) {
					Location subcastLoc = info.targetBlock != null ? info.targetBlock.getLocation().add(0.5, 0.5, 0.5)
							: event.getBlock().getLocation().add(0.5, 0.5, 0.5);
					boolean success = spellOnLand.subcast(new SpellData(info.caster, subcastLoc, info.power, info.args));
					info.spellActivated = true;
					if (success && removeBlockOnLandSuccess)
						info.undo();
				}
			}
		}

	}

	private class TntListener implements Listener {

		@EventHandler
		private void onExplode(EntityExplodeEvent event) {
			Entity entity = event.getEntity();
			ThrownBlock info = fallingBlocks.get(entity);
			if (info == null)
				return;
			if (preventBlocks) {
				event.blockList().clear();
				event.setYield(0F);
				event.setCancelled(true);
				event.getEntity().remove();
			}
			if (spellOnLand != null && !info.spellActivated) {
				boolean success = spellOnLand.subcast(new SpellData(info.caster, entity.getLocation(), info.power, info.args));
				info.spellActivated = true;
				if (success && removeBlockOnLandSuccess)
					info.undo();
			}
		}

	}

	public class ThrownBlock {

		private final LivingEntity caster;
		private final SpellData data;
		private final String[] args;
		private final float power;

		private boolean spellActivated;

		public final BlockData blockData;
		public Block targetBlock;
		public AlteredBlockManager.Change change;

		public ThrownBlock(BlockData blockData, LivingEntity caster, float power, String[] args) {
			this.blockData = blockData;
			this.caster = caster;
			this.power = power;
			this.args = args;

			data = new SpellData(caster, power, args);

			spellActivated = false;
		}

		public boolean undo() {
			if (change != null) {
				change.undo(false);
				change = null;
				targetBlock = null;
				return true;
			}
			return false;
		}
	}

}
