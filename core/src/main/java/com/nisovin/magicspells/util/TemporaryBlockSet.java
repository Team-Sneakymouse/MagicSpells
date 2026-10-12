package com.nisovin.magicspells.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.events.MagicSpellsBlockPlaceEvent;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;

public class TemporaryBlockSet implements Runnable {

	private Random random;

	private Entity livingEntity;
	private Material original;
	private boolean callPlaceEvent;
	private final String internalName;
	private final boolean applyPhysics;

	private List<AlteredBlockManager.Change> changes;
	private List<Material> replaceMaterials;

	private BlockSetRemovalCallback callback;

	private boolean bypassDippGen;
	
	public TemporaryBlockSet(String internalName, Material original, Material replaceWith, boolean callPlaceEvent,
			Entity livingEntity, boolean bypassDippGen) {
		this(internalName, original, replaceWith, callPlaceEvent, livingEntity, bypassDippGen, false);
	}

	public TemporaryBlockSet(String internalName, Material original, Material replaceWith, boolean callPlaceEvent,
			Entity livingEntity, boolean bypassDippGen, boolean applyPhysics) {
		this.internalName = internalName;
		this.original = original;
		this.callPlaceEvent = callPlaceEvent;
		this.livingEntity = livingEntity;
		this.bypassDippGen = bypassDippGen;
		this.applyPhysics = applyPhysics;

		random = ThreadLocalRandom.current();
		changes = new ArrayList<>();
		replaceMaterials = new ArrayList<>();

		replaceMaterials.add(replaceWith);
	}

	public TemporaryBlockSet(String internalName, Material original, List<Material> replaceMaterials,
			boolean callPlaceEvent, Entity livingEntity, boolean bypassDippGen) {
		this(internalName, original, replaceMaterials, callPlaceEvent, livingEntity, bypassDippGen, false);
	}

	public TemporaryBlockSet(String internalName, Material original, List<Material> replaceMaterials,
			boolean callPlaceEvent, Entity livingEntity, boolean bypassDippGen, boolean applyPhysics) {
		this.internalName = internalName;
		this.original = original;
		this.replaceMaterials = replaceMaterials;
		this.callPlaceEvent = callPlaceEvent;
		this.livingEntity = livingEntity;
		this.bypassDippGen = bypassDippGen;
		this.applyPhysics = applyPhysics;

		random = new Random();
		changes = new ArrayList<>();
	}
	
	public void add(Block block) {
		if (block.getType() != original) return;
		int r = random.nextInt(replaceMaterials.size());
		Material replaceWith = replaceMaterials.get(r);

		if (!callPlaceEvent) {
			changes.add(MagicSpells.getAlteredBlockManager().apply(internalName, block, replaceWith, applyPhysics));
			return;
		}

		BlockState state = block.getState();
		AlteredBlockManager.Change change = MagicSpells.getAlteredBlockManager().apply(internalName, block, replaceWith,
				false);
		MagicSpellsBlockPlaceEvent event = null;
		if (livingEntity instanceof Player player) {
			event = new MagicSpellsBlockPlaceEvent(block, state, block, player.getInventory().getItemInMainHand(),
					player, true, bypassDippGen);
		}
		if (event != null) EventUtil.call(event);
		if (event != null && event.isCancelled()) change.undo(false);
		else changes.add(change);
	}
	
	public boolean contains(Block block) {
		for (AlteredBlockManager.Change change : changes) {
			if (change.block().equals(block)) return true;
		}
		return false;
	}
	
	public void removeAfter(int ticks) {
		removeAfter(ticks, null);
	}
	
	public void removeAfter(int ticks, BlockSetRemovalCallback callback) {
		if (changes.isEmpty()) return;
		this.callback = callback;
		MagicSpells.scheduleDelayedTask(this, ticks);
	}
	
	@Override
	public void run() {
		if (callback != null) callback.run(this);
		remove();
	}
	
	public void remove() {
		for (int i = changes.size() - 1; i >= 0; i--) {
			changes.get(i).undo(applyPhysics);
		}
		changes.clear();
		livingEntity = null;
	}
	
	public interface BlockSetRemovalCallback {
	
		void run(TemporaryBlockSet set);
	
	}
	
}
