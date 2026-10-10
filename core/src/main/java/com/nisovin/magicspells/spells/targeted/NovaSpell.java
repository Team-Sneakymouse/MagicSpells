package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.block.data.BlockData;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class NovaSpell extends TargetedSpell implements TargetedLocationSpell, TargetedEntitySpell {

	private ConfigData<BlockData> blockData;

	private Vector relativeOffset;

	private Subspell spellOnEnd;
	private Subspell locationSpell;
	private Subspell spellOnWaveRemove;
	private String spellOnEndName;
	private String locationSpellName;
	private String spellOnWaveRemoveName;

	private ConfigData<Integer> radius;
	private ConfigData<Integer> startRadius;
	private ConfigData<Integer> heightPerTick;
	private ConfigData<Integer> novaTickInterval;
	private ConfigData<Integer> expandingRadiusChange;

	private ConfigData<Double> visibleRange;

	private boolean pointBlank;
	private boolean circleShape;
	private boolean removePreviousBlocks;

	public NovaSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		blockData = getConfigDataBlockData("type", Bukkit.createBlockData(Material.WATER));

		relativeOffset = getConfigVector("relative-offset", "0,0,0");

		spellOnEndName = getConfigString("spell-on-end", "");
		locationSpellName = getConfigString("spell", "");
		spellOnWaveRemoveName = getConfigString("spell-on-wave-remove", "");

		radius = getConfigDataInt("radius", 3);
		startRadius = getConfigDataInt("start-radius", 0);
		heightPerTick = getConfigDataInt("height-per-tick", 0);
		novaTickInterval = getConfigDataInt("expand-interval", 5);
		expandingRadiusChange = getConfigDataInt("expanding-radius-change", 1);

		visibleRange = getConfigDataDouble("visible-range", 20);

		pointBlank = getConfigBoolean("point-blank", true);
		circleShape = getConfigBoolean("circle-shape", false);
		removePreviousBlocks = getConfigBoolean("remove-previous-blocks", true);

	}

	@Override
	public void initialize() {
		super.initialize();

		locationSpell = new Subspell(locationSpellName);
		if (!locationSpell.process()) {
			if (!locationSpellName.isEmpty())
				MagicSpells.error("NovaSpell " + internalName + " has an invalid spell defined!");
			locationSpell = null;
		}

		spellOnWaveRemove = new Subspell(spellOnWaveRemoveName);
		if (!spellOnWaveRemove.process()) {
			if (!spellOnWaveRemoveName.isEmpty())
				MagicSpells.error("NovaSpell " + internalName + " has an invalid spell-on-wave-remove defined!");
			spellOnWaveRemove = null;
		}

		spellOnEnd = new Subspell(spellOnEndName);
		if (!spellOnEnd.process()) {
			if (!spellOnEndName.isEmpty())
				MagicSpells.error("NovaSpell " + internalName + " has an invalid spell-on-end defined!");
			spellOnEnd = null;
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState spellCastState, float power, String[] strings) {
		if (spellCastState == SpellCastState.NORMAL) {
			Location loc;
			if (pointBlank)
				loc = caster.getLocation();
			else
				loc = getTargetedBlock(caster, power, strings).getLocation();

			createNova(caster, null, loc, power, strings);
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		createNova(caster, target, target.getLocation(), power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(LivingEntity livingEntity, Location location, float v, String[] args) {
		createNova(livingEntity, null, location, v, args);
		return true;
	}

	private void createNova(LivingEntity caster, LivingEntity target, Location loc, float power, String[] args) {
		if (blockData == null)
			return;
		// Relative offset
		Location startLoc = loc.clone();
		Vector direction = caster.getLocation().getDirection().normalize();
		Vector horizOffset = new Vector(-direction.getZ(), 0.0, direction.getX()).normalize();
		startLoc.add(horizOffset.multiply(relativeOffset.getZ())).getBlock().getLocation();
		startLoc.add(direction.setY(0).normalize().multiply(relativeOffset.getX()));
		startLoc.add(0, relativeOffset.getY(), 0);

		SpellData data = new SpellData(caster, target, startLoc, power, args);

		// Get nearby players
		double visibleRange = Math.min(Math.max(this.visibleRange.get(data), 20), MagicSpells.getGlobalRadius());

		Collection<Player> nearbyPlayers = startLoc.getWorld().getNearbyPlayers(startLoc, visibleRange, visibleRange,
				visibleRange);

		int radius = this.radius.get(data);
		int startRadius = this.startRadius.get(data);
		int heightPerTick = this.heightPerTick.get(data);
		int novaTickInterval = this.novaTickInterval.get(data);
		int expandingRadiusChange = this.expandingRadiusChange.get(data);
		BlockData blockData = this.blockData.get(data);
		if (expandingRadiusChange < 1)
			expandingRadiusChange = 1;

		// Start tracker
		if (!circleShape)
			new NovaTrackerSquare(nearbyPlayers, startLoc.getBlock(), blockData, caster, radius, startRadius,
					heightPerTick, novaTickInterval, expandingRadiusChange, power, args);
		else
			new NovaTrackerCircle(nearbyPlayers, startLoc.getBlock(), blockData, caster, radius, startRadius,
					heightPerTick, novaTickInterval, expandingRadiusChange, power, args);
	}

	private class NovaTrackerSquare implements Runnable {

		private BlockData blockData;
		private Collection<Player> nearby;
		private Set<Block> blocks;
		private LivingEntity caster;
		private Block center;
		private float power;
		private String[] args;
		private int radiusNova;
		private int startRadius;
		private int heightPerTick;
		private int radiusChange;
		private int taskId;
		private int count;
		private int temp;

		private NovaTrackerSquare(Collection<Player> nearby, Block center, BlockData blockData, LivingEntity caster,
				int radius, int startRadius, int heightPerTick, int tickInterval, int activeRadiusChange, float power,
				String[] args) {
			this.nearby = nearby;
			this.center = center;
			this.blockData = blockData;
			this.caster = caster;
			this.power = power;
			this.args = args;
			this.radiusNova = radius;
			this.blocks = new HashSet<>();
			this.radiusChange = activeRadiusChange;
			this.startRadius = startRadius;
			this.heightPerTick = heightPerTick;

			this.count = 0;
			this.temp = 0;

			this.taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickInterval);
		}

		@Override
		public void run() {
			temp = count;
			temp += startRadius;
			temp *= radiusChange;
			count++;

			if (removePreviousBlocks) {
				for (Block b : blocks) {
					for (Player p : nearby)
						p.sendBlockChange(b.getLocation(), b.getBlockData());
					if (spellOnWaveRemove != null)
						spellOnWaveRemove.subcast(new SpellData(caster, b.getLocation().add(0.5, 0, 0.5), power, args));
				}
				blocks.clear();
			}

			if (temp > radiusNova + 1) {
				stop();
				return;
			}

			if (temp > radiusNova) {
				return;
			}

			int bx = center.getX();
			int y = center.getY();
			int bz = center.getZ();
			y += count * heightPerTick;

			for (int x = bx - temp; x <= bx + temp; x++) {
				for (int z = bz - temp; z <= bz + temp; z++) {
					if (Math.abs(x - bx) != temp && Math.abs(z - bz) != temp)
						continue;

					Block b = center.getWorld().getBlockAt(x, y, z);
					if (BlockUtils.isAir(b.getType()) || b.getType() == Material.TALL_GRASS) {
						Block under = b.getRelative(BlockFace.DOWN);
						if (BlockUtils.isAir(under.getType()) || under.getType() == Material.TALL_GRASS)
							b = under;
					} else if (BlockUtils.isAir(b.getRelative(BlockFace.UP).getType())
							|| b.getRelative(BlockFace.UP).getType() == Material.TALL_GRASS) {
						b = b.getRelative(BlockFace.UP);
					}

					if (!BlockUtils.isAir(b.getType()) && b.getType() != Material.TALL_GRASS)
						continue;

					if (blocks.contains(b))
						continue;
					for (Player p : nearby)
						p.sendBlockChange(b.getLocation(), blockData);
					blocks.add(b);
					if (locationSpell != null)
						locationSpell.subcast(new SpellData(caster, b.getLocation().add(0.5, 0, 0.5), power, args));
				}
			}

		}

		private void stop() {
			for (Block b : blocks) {
				for (Player p : nearby)
					p.sendBlockChange(b.getLocation(), b.getBlockData());
				if (spellOnEnd != null)
					spellOnEnd.subcast(new SpellData(caster, b.getLocation().add(0.5, 0, 0.5), power, args));
			}
			blocks.clear();
			MagicSpells.cancelTask(taskId);
		}

	}

	private class NovaTrackerCircle implements Runnable {

		private BlockData blockData;
		private Collection<Player> nearby;
		private Set<Block> blocks;
		private LivingEntity caster;
		private Block center;
		private float power;
		private String[] args;
		private int radiusNova;
		private int startRadius;
		private int heightPerTick;
		private int radiusChange;
		private int taskId;
		private int count;
		private int temp;

		private NovaTrackerCircle(Collection<Player> nearby, Block center, BlockData blockData, LivingEntity caster,
				int radius, int startRadius, int heightPerTick, int tickInterval, int activeRadiusChange, float power,
				String[] args) {
			this.nearby = nearby;
			this.center = center;
			this.blockData = blockData;
			this.caster = caster;
			this.power = power;
			this.args = args;
			this.radiusNova = radius;
			this.blocks = new HashSet<>();
			this.startRadius = startRadius;
			this.heightPerTick = heightPerTick;
			this.radiusChange = activeRadiusChange;

			this.count = 0;
			this.temp = 0;

			this.taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickInterval);
		}

		@Override
		public void run() {
			temp = count;
			temp += startRadius;
			temp *= radiusChange;
			count++;

			// Remove old blocks
			if (removePreviousBlocks) {
				for (Block b : blocks) {
					for (Player p : nearby)
						p.sendBlockChange(b.getLocation(), b.getBlockData());
					if (spellOnWaveRemove != null)
						spellOnWaveRemove.subcast(new SpellData(caster, b.getLocation().add(0.5, 0, 0.5), power, args));
				}
				blocks.clear();
			}

			if (temp > radiusNova + 1) {
				stop();
				return;
			}

			if (temp > radiusNova) {
				return;
			}

			// Generate the bottom block
			Location centerLocation = center.getLocation().clone();
			centerLocation.add(0.5, count * heightPerTick, 0.5);
			Block b;

			if (startRadius == 0 && temp == 0) {
				b = centerLocation.getWorld().getBlockAt(centerLocation);

				if (BlockUtils.isAir(b.getType()) || b.getType() == Material.TALL_GRASS) {
					Block under = b.getRelative(BlockFace.DOWN);
					if (BlockUtils.isAir(under.getType()) || under.getType() == Material.TALL_GRASS)
						b = under;
				} else if (BlockUtils.isAir(b.getRelative(BlockFace.UP).getType())
						|| b.getRelative(BlockFace.UP).getType() == Material.TALL_GRASS) {
					b = b.getRelative(BlockFace.UP);
				}

				if (!BlockUtils.isAir(b.getType()) && b.getType() != Material.TALL_GRASS)
					return;

				if (blocks.contains(b))
					return;
				for (Player p : nearby)
					p.sendBlockChange(b.getLocation(), blockData);
				blocks.add(b);
				if (locationSpell != null)
					locationSpell.subcast(new SpellData(caster, b.getLocation().add(0.5, 0, 0.5), power, args));
			}

			// Generate the circle
			Vector v;
			double angle, x, z;
			double amount = temp * 64;
			double inc = (2 * Math.PI) / amount;
			for (int i = 0; i < amount; i++) {
				angle = i * inc;
				x = temp * Math.cos(angle);
				z = temp * Math.sin(angle);
				v = new Vector(x, 0, z);
				b = center.getWorld().getBlockAt(centerLocation.add(v));
				centerLocation.subtract(v);

				if (BlockUtils.isAir(b.getType()) || b.getType() == Material.TALL_GRASS) {
					Block under = b.getRelative(BlockFace.DOWN);
					if (BlockUtils.isAir(under.getType()) || under.getType() == Material.TALL_GRASS)
						b = under;
				} else if (BlockUtils.isAir(b.getRelative(BlockFace.UP).getType())
						|| b.getRelative(BlockFace.UP).getType() == Material.TALL_GRASS) {
					b = b.getRelative(BlockFace.UP);
				}

				if (!BlockUtils.isAir(b.getType()) && b.getType() != Material.TALL_GRASS)
					continue;

				if (blocks.contains(b))
					continue;
				for (Player p : nearby)
					p.sendBlockChange(b.getLocation(), blockData);
				blocks.add(b);
				if (locationSpell != null)
					locationSpell.subcast(new SpellData(caster, b.getLocation().add(0.5, 0, 0.5), power, args));
			}

		}

		private void stop() {
			for (Block b : blocks) {
				for (Player p : nearby)
					p.sendBlockChange(b.getLocation(), b.getBlockData());
				if (spellOnEnd != null)
					spellOnEnd.subcast(new SpellData(caster, b.getLocation().add(0.5, 0, 0.5), power, args));
			}
			blocks.clear();
			MagicSpells.cancelTask(taskId);
		}

	}

}
