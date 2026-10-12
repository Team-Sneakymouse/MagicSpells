package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;

import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.nisovin.magicspells.spelleffects.effecttypes.EntityEffect;
import com.nisovin.magicspells.util.*;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public class PulserSpell extends TargetedSpell implements TargetedLocationSpell {

	final Map<Block, Pulser> pulsers;
	private BlockData blockData;

	private final int interval;
	private final int capPerPlayer;
	private final ConfigData<Integer> yOffset;
	private final ConfigData<Integer> totalPulses;

	private final ConfigData<Double> maxDistance;

	private final boolean checkFace;
	private final boolean unbreakable;
	private final boolean onlyCountOnSuccess;
	private final boolean cancelOnDeath;

	private final List<String> spellNames;
	private final List<String> spellNamesOnRightClick;
	private final List<String> spellNamesOnPlace;
	private List<Subspell> spells;
	private List<Subspell> spellsOnRightClick;
	private List<Subspell> spellsOnPlace;

	private final String spellNameOnBreak;
	private Subspell spellOnBreak;

	private final String strAtCap;

	private final PulserTicker ticker;

	public PulserSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		String materialName = getConfigString("block-type", "DIAMOND_BLOCK");
		blockData = Bukkit.createBlockData(materialName.toLowerCase());
		if (blockData == null || !blockData.getMaterial().isBlock()) {
			MagicSpells.error("PulserSpell '" + internalName + "' has an invalid block-type defined");
			blockData = null;
		}

		yOffset = getConfigDataInt("y-offset", 0);
		interval = getConfigInt("interval", 30);
		totalPulses = getConfigDataInt("total-pulses", 5);
		capPerPlayer = getConfigInt("cap-per-player", 10);

		maxDistance = getConfigDataDouble("max-distance", 30);

		checkFace = getConfigBoolean("check-face", true);
		unbreakable = getConfigBoolean("unbreakable", false);
		onlyCountOnSuccess = getConfigBoolean("only-count-on-success", false);
		cancelOnDeath = getConfigBoolean("cancel-on-death", true);

		spellNames = getConfigStringList("spells", null);
		spellNamesOnRightClick = getConfigStringList("spells-on-right-click", null);
		spellNamesOnPlace = getConfigStringList("spells-on-place", null);
		spellNameOnBreak = getConfigString("spell-on-break", "");

		strAtCap = getConfigString("str-at-cap", "You have too many effects at once.");

		pulsers = new HashMap<>();
		ticker = new PulserTicker();
	}

	@Override
	public void initialize() {
		super.initialize();

		spells = new ArrayList<>();
		spellsOnRightClick = new ArrayList<>();
		spellsOnPlace = new ArrayList<>();
		if (spellNames != null && !spellNames.isEmpty()) {
			for (String spellName : spellNames) {
				Subspell spell = new Subspell(spellName);
				if (!spell.process())
					continue;
				spells.add(spell);
			}
		}
		if (spellNamesOnRightClick != null && !spellNamesOnRightClick.isEmpty()) {
			for (String spellName : spellNamesOnRightClick) {
				Subspell spell = new Subspell(spellName);
				if (!spell.process())
					continue;
				spellsOnRightClick.add(spell);
			}
		}
		if (spellNamesOnPlace != null && !spellNamesOnPlace.isEmpty()) {
			for (String spellName : spellNamesOnPlace) {
				Subspell spell = new Subspell(spellName);
				if (!spell.process())
					continue;
				spellsOnPlace.add(spell);
			}
		}

		if (!spellNameOnBreak.isEmpty()) {
			spellOnBreak = new Subspell(spellNameOnBreak);
			if (!spellOnBreak.process()) {
				MagicSpells.error("PulserSpell '" + internalName + "' has an invalid spell-on-break defined");
				spellOnBreak = null;
			}
		}

		if (spells.isEmpty())
			MagicSpells.error("PulserSpell '" + internalName + "' has no spells defined!");
	}

	@Override
	public CastResult cast(SpellData data) {
		if (capPerPlayer > 0) {
			int count = 0;
			for (Pulser pulser : pulsers.values()) {
				if (!pulser.caster.equals(data.caster()))
					continue;

				count++;
				if (count >= capPerPlayer) {
					sendMessage(strAtCap, data.caster(), data.args());
					return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				}
			}
		}
		List<Block> lastTwo = getLastTwoTargetedBlocks(data.caster(), data.power(), data.args());
		Block target = null;

		if (lastTwo != null && lastTwo.size() == 2)
			target = lastTwo.get(0);
		if (target == null)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		int yOffset = this.yOffset.get(data.caster(), null, data.power(), data.args());
		if (yOffset > 0)
			target = target.getRelative(BlockFace.UP, yOffset);
		else if (yOffset < 0)
			target = target.getRelative(BlockFace.DOWN, yOffset);
		if (!BlockUtils.isAir(target.getType()))
			return new CastResult(noTarget(data.caster(), data.args()), data);

		SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(target.getLocation()));
		EventUtil.call(event);
		if (event.isCancelled())
			return new CastResult(noTarget(data.caster(), data.args()), data);

		data = event.getSpellData();
		createPulser(data.caster(), data.location().getBlock(), data.caster().getLocation(), data.power(), data.args());
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
		if (data.hasCaster() && capPerPlayer > 0) {
			int count = 0;
			for (Pulser pulser : pulsers.values()) {
				if (!pulser.caster.equals(data.caster()))
					continue;

				count++;
				if (count >= capPerPlayer) {
					sendMessage(strAtCap, data.caster());
					return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				}
			}
		}

		Block block = data.location().getBlock();
		int yOffset = this.yOffset.get(data.caster(), null, data.power(), data.args());
		if (yOffset > 0)
			block = block.getRelative(BlockFace.UP, yOffset);
		else if (yOffset < 0)
			block = block.getRelative(BlockFace.DOWN, yOffset);

		if (BlockUtils.isAir(block.getType())) {
			createPulser(data.caster(), block, data.location(), data.power(), data.args());
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		if (checkFace) {
			block = block.getRelative(BlockFace.UP);
			if (BlockUtils.isAir(block.getType())) {
				createPulser(data.caster(), block, data.location(), data.power(), data.args());
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
			}
		}
		return new CastResult(PostCastAction.ALREADY_HANDLED, data);
	}



	private void createPulser(Entity caster, Block block, Location from, float power, String[] args) {
		if (blockData == null)
			return;
		AlteredBlockManager.Change change = MagicSpells.getAlteredBlockManager().apply(internalName, block, blockData,
				false);
		pulsers.put(block, new Pulser(caster, block, from, power, args, change));
		ticker.start();
		if (caster != null)
			playSpellEffects(caster, block.getLocation().add(0.5, 0.5, 0.5), power, args);
		else
			playSpellEffects(EffectPosition.TARGET, block.getLocation().add(0.5, 0.5, 0.5), power, args);

		if (spellsOnPlace != null && !spellsOnPlace.isEmpty()) {
			Location loc = block.getLocation().add(0.5, 0.5, 0.5);
			for (Subspell spell : spellsOnPlace) {
				spell.subcast(new SpellData(caster, loc, power, args));
			}
		}
	}

	private boolean interactionDebounce = false;

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockInteract(PlayerInteractEvent event) {
		if (interactionDebounce)
			return;
		Player player = event.getPlayer();
		if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
			return;

		Block block = event.getClickedBlock();
		if (block == null)
			return;

		Pulser pulser = pulsers.get(block);
		if (pulser == null)
			return;

		interactionDebounce = true;
		pulser.pulse(Pulser.ActivationType.CLICK, player);
		MagicSpells.scheduleDelayedTask(() -> interactionDebounce = false, 5);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Pulser pulser = pulsers.get(event.getBlock());
		if (pulser == null)
			return;
		event.setCancelled(true);
		if (unbreakable)
			return;
		pulser.stop();
		pulsers.remove(event.getBlock());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		if (pulsers.isEmpty())
			return;
		Iterator<Block> iter = event.blockList().iterator();
		while (iter.hasNext()) {
			Block b = iter.next();
			Pulser pulser = pulsers.get(b);
			if (pulser == null)
				continue;
			iter.remove();

			if (unbreakable)
				continue;
			pulser.stop();
			pulsers.remove(b);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPiston(BlockPistonExtendEvent event) {
		if (pulsers.isEmpty())
			return;
		for (Block b : event.getBlocks()) {
			Pulser pulser = pulsers.get(b);
			if (pulser == null)
				continue;
			event.setCancelled(true);
			if (unbreakable)
				continue;
			pulser.stop();
			pulsers.remove(b);
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		if (pulsers.isEmpty())
			return;
		Player player = event.getEntity();
		Iterator<Pulser> iter = pulsers.values().iterator();
		while (iter.hasNext()) {
			Pulser pulser = iter.next();
			if (pulser.caster == null)
				continue;
			if (!pulser.caster.equals(player))
				continue;
			if (!pulser.cancelOnDeath)
				continue;
			pulser.stop();
			iter.remove();
		}
	}

	@Override
	public void turnOff() {
		for (Pulser p : new ArrayList<>(pulsers.values())) {
			p.stop();
		}
		pulsers.clear();
		ticker.stop();
	}

	public class Pulser {

		final Entity caster;
		private final Block block;
		private final Location location;
		private final SpellData data;
		private final String[] args;
		private final float power;
		private int pulseCount;
		private boolean cancelOnDeath;
		private final AlteredBlockManager.Change change;

		private final double maxDistanceSq;
		private final int totalPulses;

		private List<Entity> effectEntities = new ArrayList<>();
		private List<Integer> effectTasks = new ArrayList<>();

		private enum ActivationType {
			TICK, CLICK;
		}

		private Pulser(Entity caster, Block block, Location from, float power, String[] args,
				AlteredBlockManager.Change change) {
			this.caster = caster;
			this.block = block;
			this.location = block.getLocation().add(0.5, 0.5, 0.5);
			this.power = power;
			this.args = args;
			this.pulseCount = 0;
			this.cancelOnDeath = PulserSpell.this.cancelOnDeath;
			this.change = change;

			data = new SpellData(caster, this.location, power, args);

			totalPulses = PulserSpell.this.totalPulses.get(data);

			double maxDistance = PulserSpell.this.maxDistance.get(data);
			maxDistanceSq = maxDistance * maxDistance;

			if (effects == null)
				return;
			List<SpellEffect> effectsList = effects.get(EffectPosition.SPECIAL);
			if (effectsList == null)
				return;
			for (SpellEffect effect : effectsList) {
				if (effect instanceof EntityEffect entityEffect) {
					int delay = effect.getDelay().get(data);

					if (delay <= 0)
						effectEntities.add(effect.playEntityEffect(location, data));
					else
						effectTasks.add(playEntityEffectDelayed(entityEffect, location, data, effectEntities, delay));
				} else
					effect.playEffect(location, data);
			}
		}

		private static int playEntityEffectDelayed(EntityEffect effect, Location location, SpellData data,
				List<Entity> list, Integer delay) {
			double chance = effect.getChance().get(data);
			if (chance > 0 && chance < 1 && random.nextDouble() > chance)
				return -1;

			ModifierResult result = effect.checkModifiers(data, location);
			if (!result.check())
				return -1;
			data = result.data();

			SpellData finalData = data;
			return MagicSpells.scheduleDelayedTask(
					() -> list.add(effect.playEntityEffectLocationReal(location, finalData)), delay);
		}

		private boolean pulse(ActivationType activationType, @Nullable Entity caster) {
			if (caster == null) {
				if (blockData.equals(block.getBlockData())
						&& block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
					return activate(activationType, caster);
				stop();
				return true;
			} else if (caster.isValid()) {
				if (blockData.equals(block.getBlockData())
						&& block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
					if (maxDistanceSq > 0 && (!LocationUtil.isSameWorld(location, caster)
							|| location.distanceSquared(caster.getLocation()) > maxDistanceSq)) {
						stop();
						return true;
					}
					return activate(activationType, caster);
				}
			} else {
				if (!this.cancelOnDeath) {
					return false;
				}
			}
			stop();
			return true;
		}

		public boolean activate(ActivationType activationType, @Nullable Entity _caster) {
			boolean activated = false;
			Entity spellCaster = caster;
			if (_caster != null)
				spellCaster = _caster;
			if (activationType.equals(ActivationType.TICK)) {
				for (Subspell spell : spells) {
					activated = spell.subcast(new SpellData(spellCaster, location, power, args)) || activated;
				}
			} else {
				for (Subspell spell : spellsOnRightClick) {
					activated = spell.subcast(new SpellData(spellCaster, location, power, args)) || activated;
				}
			}
			playSpellEffects(EffectPosition.DELAYED, location, data);
			if (totalPulses > 0 && (activated || !onlyCountOnSuccess)) {
				pulseCount += 1;
				if (pulseCount >= totalPulses) {
					stop();
					return true;
				}
			}
			return false;
		}

		public void stop() {
			for (Entity effect : effectEntities) {
				if (effect != null)
					effect.remove();
			}
			for (Integer taskID : effectTasks) {
				if (taskID != null && taskID >= 0)
					MagicSpells.cancelTask(taskID);
			}

			if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
				block.getChunk().load();
			change.undo(false);
			playSpellEffects(EffectPosition.BLOCK_DESTRUCTION, block.getLocation(), data);
			if (spellOnBreak != null)
				spellOnBreak.subcast(new SpellData(caster, location, power, args));
		}

	}

	private class PulserTicker implements Runnable {

		private int taskId = -1;

		private void start() {
			if (taskId < 0)
				taskId = MagicSpells.scheduleRepeatingTask(this, 0, interval);
		}

		private void stop() {
			if (taskId > 0) {
				MagicSpells.cancelTask(taskId);
				taskId = -1;
			}
		}

		@Override
		public void run() {
			for (Map.Entry<Block, Pulser> entry : new HashMap<>(pulsers).entrySet()) {
				boolean remove = entry.getValue().pulse(Pulser.ActivationType.TICK, null);
				if (remove)
					pulsers.remove(entry.getKey());
			}
			if (pulsers.isEmpty())
				stop();
		}

	}

}
