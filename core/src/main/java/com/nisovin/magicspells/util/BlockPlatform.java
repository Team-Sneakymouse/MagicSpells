package com.nisovin.magicspells.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;

import com.nisovin.magicspells.MagicSpells;
import com.sneakyutils.util.AlteredBlockManager;

public class BlockPlatform {

	private final String internalName;
	private Material platformType;
	private Material replaceType;
	private Block center;
	private int size;
	private boolean moving;
	private String type;
	private Map<Block, AlteredBlockManager.Change> changes;
		
	public BlockPlatform(String internalName, Material platformType, Material replaceType, Block center, int size,
			boolean moving, String type) {
		this.internalName = internalName;
		this.platformType = platformType;
		this.replaceType = replaceType;
		this.center = center;
		this.size = size;
		this.moving = moving;
		this.type = type;
		
		if (moving) changes = new HashMap<>();
		
		createPlatform();
	}
	
	public void createPlatform() {
		List<Block> platform = new ArrayList<>();
		
		// Get platform blocks
		if (type.equals("square")) {
			Block block;
			Block above;
			int cx = center.getX();
			int cy = center.getY();
			int cz = center.getZ();
			World world = center.getWorld();
			int max = world.getMaxHeight();
			for (int x = cx - size; x <= cx + size; x++) {
				for (int z = cz - size; z <= cz + size; z++) {
					block = world.getBlockAt(x, cy, z);
					above = block.getRelative(0, 1, 0);
					if ((block.getType() == replaceType && (cy >= max - 1 || (changes != null && changes.containsKey(above)) || above.getType() == Material.AIR)) || (changes != null && changes.containsKey(block))) {
						// Only add if it's a replaceable block and has air above, or if it is already part of the platform
						platform.add(block);
					}
				}
			}
		} else if (type.equals("cube")) {
			Block block;
			for (int x = center.getX() - size; x <= center.getX() + size; x++) {
				for (int y = center.getY() - size; y <= center.getY() + size; y++) {
					for (int z = center.getZ()-size; z <= center.getZ() + size; z++) {
						block = center.getWorld().getBlockAt(x, y, z);
						if (block.getType() == replaceType || (changes != null && changes.containsKey(block))) {
							// Only add if it's a replaceable block or if it is already part of the block set
							platform.add(block);
						}
					}
				}
			}
		}
		
		// Remove old platform blocks
		if (moving && changes != null) {
			for (Map.Entry<Block, AlteredBlockManager.Change> entry : new HashMap<>(changes).entrySet()) {
				Block block = entry.getKey();
				if (!platform.contains(block) && block.getType() == platformType) {
					entry.getValue().undo(false);
					changes.remove(block);
				}
			}
		}
		
		// Add new platform blocks
		for (Block block : platform) {
			if (changes == null || !changes.containsKey(block)) {
				if (moving) {
					changes.put(block, MagicSpells.getAlteredBlockManager().apply(internalName, block, platformType, false));
				} else {
					block.setType(platformType, false);
				}
			}
		}
	}
	
	public boolean movePlatform(Block center) {
		return movePlatform(center, false);
	}
	
	public boolean movePlatform(Block center, boolean force) {
		if (force || isMoved(center)) {
			this.center = center;
			createPlatform();
			return true;
		}
		return false;
	}
	
	public boolean isMoved(Block newCenter) {
		return isMoved(newCenter, true);
	}
	
	public boolean isMoved(Block newCenter, boolean allowDown) {
		if (!allowDown && newCenter.getY() < center.getY()) return false;
		return !newCenter.getLocation().equals(center.getLocation());
	}
	
	public boolean isMovedHorizontally(Block newCenter) {
		Location loc1 = center.getLocation();
		Location loc2 = newCenter.getLocation();
		return loc1.getBlockX() != loc2.getBlockX() || loc1.getBlockZ() != loc2.getBlockZ();
	}
	
	public boolean blockInPlatform(Block block) {
		return changes != null && changes.containsKey(block);
	}
	
	public void destroyPlatform() {		
		// Remove platform blocks
		if (moving && changes != null) {
			for (AlteredBlockManager.Change change : changes.values()) {
				change.undo(false);
			}
			changes.clear();
		}
		changes = null;
	}
	
	public Block getCenter () {
		return center;
	}
	
}
