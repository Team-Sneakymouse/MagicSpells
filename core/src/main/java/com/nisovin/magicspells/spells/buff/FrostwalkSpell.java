package com.nisovin.magicspells.spells.buff;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import io.papermc.paper.event.entity.EntityMoveEvent;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.BlockPlatform;
import com.nisovin.magicspells.util.config.ConfigData;

public class FrostwalkSpell extends BuffSpell {

	private final Map<UUID, BlockPlatform> entities;

	private ConfigData<Integer> size;

	private boolean leaveFrozen;

	public FrostwalkSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		size = getConfigDataInt("size", 2);

		leaveFrozen = getConfigBoolean("leave-frozen", false);
		
		entities = new HashMap<>();
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		entities.put(entity.getUniqueId(), new BlockPlatform(internalName, Material.ICE, Material.WATER, entity.getLocation().getBlock().getRelative(0, -1, 0), size.get(entity, null, power, args), !leaveFrozen, "square", blockCauses));
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return entities.containsKey(entity.getUniqueId());
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		BlockPlatform platform = entities.get(entity.getUniqueId());
		if (platform == null) return;

		platform.destroyPlatform();
		entities.remove(entity.getUniqueId());
	}

	@Override
	protected void turnOff() {
		Util.forEachValueOrdered(entities, BlockPlatform::destroyPlatform);
		entities.clear();
	}

	private void handleMove(LivingEntity entity, Location to, Location from) {
		if (!isActive(entity)) return;
		if (isExpired(entity)) {
			turnOff(entity);
			return;
		}

		Block block;
		boolean teleportUp = false;

		double locationToY = to.getY();
		double locationFromY = from.getY();

		Block locationToBlock = to.getBlock();

		if (locationToY > locationFromY && locationToY % 1 > .62 && locationToBlock.getType() == Material.WATER && BlockUtils.isAir(locationToBlock.getRelative(0, 1, 0).getType())) {
			block = locationToBlock;
			teleportUp = true;
		}
		else block = locationToBlock.getRelative(0, -1, 0);
		boolean moved = entities.get(entity.getUniqueId()).movePlatform(block);
		if (!moved) return;

		addUseAndChargeCost(entity);

		if (teleportUp) {
			Location loc = entity.getLocation().clone();
			loc.setY(to.getBlockY() + 1);
			entity.teleportAsync(loc);
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerMove(PlayerMoveEvent event) {
		handleMove(event.getPlayer(), event.getTo(), event.getFrom());
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onEntityMove(EntityMoveEvent event) {
		handleMove(event.getEntity(), event.getTo(), event.getFrom());
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (entities.isEmpty()) return;
		Block block = event.getBlock();
		if (block.getType() != Material.ICE) return;
		if (Util.containsValueParallel(entities, platform -> platform.blockInPlatform(block))) event.setCancelled(true);
	}

	public Map<UUID, BlockPlatform> getEntities() {
		return entities;
	}

	public boolean isLeaveFrozen() {
		return leaveFrozen;
	}

	public void setLeaveFrozen(boolean leaveFrozen) {
		this.leaveFrozen = leaveFrozen;
	}

}
