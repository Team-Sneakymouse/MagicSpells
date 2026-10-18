package com.nisovin.magicspells.spells.buff;

import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.HashMap;
import java.util.Iterator;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.sneakyutils.util.AlteredBlockManager;

import io.papermc.paper.event.entity.EntityMoveEvent;

public class LilywalkSpell extends BuffSpell {

	private final Map<UUID, Lilies> entities;

	public LilywalkSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		entities = new HashMap<>();
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		Lilies lilies = new Lilies();
		lilies.move(entity.getLocation().getBlock());

		entities.put(entity.getUniqueId(), lilies);
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return entities.containsKey(entity.getUniqueId());
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		Lilies lilies = entities.remove(entity.getUniqueId());
		if (lilies == null) return;
		lilies.remove();
	}

	@Override
	protected void turnOff() {
		Util.forEachValueOrdered(entities, Lilies::remove);
		entities.clear();
	}

	private void handleMove(LivingEntity entity, Block block) {
		Lilies lilies = entities.get(entity.getUniqueId());
		if (lilies == null) return;
		if (isExpired(entity)) {
			turnOff(entity);
			return;
		}
		boolean moved = lilies.isMoved(block);
		if (!moved) return;
		lilies.move(block);
		addUseAndChargeCost(entity);
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerMove(PlayerMoveEvent event) {
		handleMove(event.getPlayer(), event.getTo().getBlock());
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onEntityMove(EntityMoveEvent event) {
		handleMove(event.getEntity(), event.getTo().getBlock());
	}
	
	@EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (entities.isEmpty()) return;
		final Block block = event.getBlock();
		if (block.getType() != Material.LILY_PAD) return;
		if (Util.containsValueParallel(entities, lilies -> lilies.contains(block))) event.setCancelled(true);
	}

	public Map<UUID, Lilies> getEntities() {
		return entities;
	}

	private class Lilies {

		private final Map<Block, AlteredBlockManager.Change> blocks = new HashMap<>();

		private Block center = null;

		private void move(Block center) {
			this.center = center;
			
			Iterator<Map.Entry<Block, AlteredBlockManager.Change>> iterator = blocks.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<Block, AlteredBlockManager.Change> entry = iterator.next();
				Block b = entry.getKey();
				if (b.equals(center)) continue;
				entry.getValue().undo(false);
				iterator.remove();
			}
			
			setToLily(center);
			setToLily(center.getRelative(BlockFace.NORTH));
			setToLily(center.getRelative(BlockFace.SOUTH));
			setToLily(center.getRelative(BlockFace.EAST));
			setToLily(center.getRelative(BlockFace.WEST));
			setToLily(center.getRelative(BlockFace.NORTH_WEST));
			setToLily(center.getRelative(BlockFace.NORTH_EAST));
			setToLily(center.getRelative(BlockFace.SOUTH_WEST));
			setToLily(center.getRelative(BlockFace.SOUTH_EAST));
		}
		
		private void setToLily(Block block) {
			if (blocks.containsKey(block)) return;
			if (!BlockUtils.isAir(block.getType())) return;
			
			BlockState state = block.getRelative(BlockFace.DOWN).getState();
			if ((state.getType() == Material.WATER) && BlockUtils.getWaterLevel(state) == 0) {
				blocks.put(block, MagicSpells.getAlteredBlockManager().apply(internalName, block, Material.LILY_PAD, false));
			}
		}
		
		private boolean isMoved(Block center) {
			return !Objects.equals(this.center, center);
		}
		
		private boolean contains(Block block) {
			return blocks.containsKey(block);
		}
		
		private void remove() {
			for (AlteredBlockManager.Change change : blocks.values()) {
				change.undo(false);
			}
			blocks.clear();
		}
		
	}

}
