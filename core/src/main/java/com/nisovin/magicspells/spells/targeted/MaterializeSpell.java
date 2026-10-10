package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsBlockPlaceEvent;
import com.nisovin.magicspells.events.MagicSpellsBlockBreakEvent;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;

public class MaterializeSpell extends TargetedSpell implements TargetedLocationSpell {

	/*
	 * These extra features were inspired by Shadoward12's Rune/Pattern-Tester
	 * spell,
	 * Thank You! Shadoward12!
	 */

	private Map<Block, AlteredBlockManager.Change> blocks;
	private boolean removeBlocks;

	// Normal Features
	private Set<Material> materials;
	private Material material;
	private int resetDelay;
	private boolean falling;
	private boolean applyPhysics;
	private boolean checkPlugins;
	boolean playBreakEffect;
	private String strFailed;

	// Pattern Configuration
	private boolean usePattern;
	private List<String> patterns;
	private Material[][] rowPatterns;
	private boolean restartPatternEachRow;
	private boolean randomizePattern;
	private boolean stretchPattern;

	// Cuboid Parameters
	private String area;
	private ConfigData<Integer> height;
	private ConfigData<Double> fallHeight;

	// Cuboid Variables;
	private int rowSize;
	private int columnSize;

	// Cuboid Checks;
	private boolean hasMiddle;

	public MaterializeSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		String blockType = getConfigString("block-type", "stone");
		material = Util.getMaterial(blockType);
		if (material == null || !material.isBlock())
			MagicSpells.error("MaterializeSpell '" + internalName + "' has an invalid block-type defined!");

		resetDelay = getConfigInt("reset-delay", 0);
		falling = getConfigBoolean("falling", false);
		applyPhysics = getConfigBoolean("apply-physics", true);
		checkPlugins = getConfigBoolean("check-plugins", true);
		playBreakEffect = getConfigBoolean("play-break-effect", true);
		strFailed = getConfigString("str-failed", "");

		usePattern = getConfigBoolean("use-pattern", false);
		patterns = getConfigStringList("patterns", null);
		restartPatternEachRow = getConfigBoolean("restart-pattern-each-row", false);
		randomizePattern = getConfigBoolean("randomize-pattern", false);
		stretchPattern = getConfigBoolean("stretch-pattern", false);

		area = getConfigString("area", "1x1");
		height = getConfigDataInt("height", 1);
		fallHeight = getConfigDataDouble("fall-height", 0.5);

		removeBlocks = getConfigBoolean("remove-blocks", true);
		blocks = new HashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		// First, lets split the "area" that was given.
		String[] areaParts = area.split("x", 2);

		// Let's define the size of the row and column to form a shape array;
		rowSize = Integer.parseInt(areaParts[0]);
		columnSize = Integer.parseInt(areaParts[1]);

		/*
		 * For this to work smoothly, we need to see if the shape array has a middle;
		 * It becomes very complicated when working with shape arrays without a block as
		 * a geometrical middle
		 * So unfortunately. Shape arrays without a block as its geometrical center
		 * cannot be accepted.
		 * 3x2, 9x8. Basically, if the product of the length and width is even. Don't
		 * use it.
		 */
		hasMiddle = ((rowSize * columnSize) % 2) == 1;

		if (!hasMiddle && patterns != null) {
			MagicSpells.error("MaterializeSpell " + internalName
					+ " is using a shape array without a geometrical center! A single block will spawn instead.");
		}

		// After the reset-delay passes, we need to remove all the blocks that were
		// materialized.
		// We store them within "materials" and "rowPatterns" as well
		boolean ready;

		materials = new HashSet<>();

		if (patterns != null)
			ready = parseBlocks(patterns);
		else
			ready = false;

		// If the parser failed, we'll have to force a string inside;
		if (!ready) {
			rowPatterns = new Material[1][1];
			rowPatterns[0][0] = material;
			materials.add(material);
		}
	}

	@Override
	public void turnOff() {
		for (AlteredBlockManager.Change change : blocks.values()) {
			change.undo(applyPhysics);
		}

		blocks.clear();
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		List<Block> lastTwo;
					try {
						lastTwo = getLastTwoTargetedBlocks(player, data.power(), data.args());
					} catch (IllegalStateException e) {
						DebugHandler.debugIllegalState(e);
						lastTwo = null;
					}
		
					if (lastTwo == null || lastTwo.size() != 2)
						return new CastResult(noTarget(player, data.args()), data);
					if (!BlockUtils.isAir(lastTwo.get(0).getType()) || BlockUtils.isAir(lastTwo.get(1).getType()))
						return new CastResult(noTarget(player, data.args()), data);
		
					Block block = lastTwo.get(0);
					Block against = lastTwo.get(1);
					SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, player, block.getLocation(), data.power(),
							data.args());
					EventUtil.call(event);
					if (event.isCancelled())
						return new CastResult(noTarget(player, strFailed, data.args()), data);
					block = event.getTargetLocation().getBlock();
					data = data.power(event.getPower());
		
					if (!hasMiddle) {
						boolean done = materialize(player, block, against, data.power(), data.args());
						if (!done)
							return new CastResult(noTarget(player, strFailed, data.args()), data);
						return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
					}
		
					// Unfortunately, shape array placement is world relative, will fix later.
					// This is the top-left edge of the shape array
					Location patternStart = against.getLocation();
		
					patternStart.setX(against.getX() - Math.ceil(rowSize / 2F));
					patternStart.setZ(against.getZ() - Math.ceil(columnSize / 2F));
		
					// spawnBlock is the current position in the loop where it will spawn the block
					Location spawnBlock = patternStart;
		
					Block air;
					Block ground;
		
					/*
					 * The row position dictates which block within a row pattern will be used
					 * when placing the new block.
					 */
					int rowPosition = 0;
		
					int height = this.height.get(player, null, data.power(), data.args());
		
					// If height is 0, the code ceases to function. Lets not have that.
					if (height == 0)
						height = 1;
		
					// Lets start at the bottom floor then work our way up; or down if height is
					// less than 0.
					for (int y = 0; y < height; y++) {
						/*
						 * The pattern position is the pattern being read for a specific row
						 * This should always reset when it goes over into a new height.
						 */
						int patternPosition = 0;
		
						// The block placement loop will start finish a row of coloumns then move down a
						// row..
						for (int z = 0; z < columnSize; z++) {
							// Everytime a shape row is finished, we need to start at the topleft and move
							// down 1 row.
							spawnBlock = patternStart.clone().add(0, y, z);
		
							// Lets parse the list of patterns for that row.
							if (patterns != null && patternPosition >= patterns.size())
								patternPosition = 0;
		
							int rowLength = getRowLength(patternPosition);
		
							// If they want the pattern to restart on each row, reset rowpositon to 0.
							if (restartPatternEachRow)
								rowPosition = 0;
		
							// Lets spawn a block on each column before we move down a row.
							for (int x = 0; x < rowSize; x++) {
								ground = spawnBlock.getBlock();
								air = ground.getRelative(BlockFace.UP);
		
								/*
								 * Now if we are looking for a block outside of the rowlist range.
								 * We need to go back to the start and repeat that row pattern
								 */
								if (rowPosition >= rowLength)
									rowPosition = 0;
		
								// Doesn't really become a pattern if you randomize it but ok!
								if (!stretchPattern || y < 1)
									material = blockGenerator(randomizePattern, patternPosition, rowPosition);
								else
									material = ground.getType();
		
								// Add one to the row position so that it will move to the next block.
								rowPosition++;
		
								// As soon as a block can't be spawned, it will return an error.
								boolean done = materialize(player, air, ground, data.power(), data.args());
								if (!done)
									return new CastResult(noTarget(player, strFailed, data.args()), data);
		
								// Done with placing that one block? Move on to the next one.
								spawnBlock.setX((ground.getX() + 1));
							}
							// If multiple patterns were requested, lets move to the next line.
							patternPosition++;
						}
					}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	@Override
	public CastResult castAtLocation(SpellData data) {
		if (data.hasCaster()) {
			if (!(data.caster() instanceof Player player))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			Block block = data.location().getBlock();
			Block against = data.location().clone().add(data.location().getDirection()).getBlock();
			if (block.equals(against))
				against = block.getRelative(BlockFace.DOWN);
			if (block.getType() == Material.AIR && materialize(player, block, against, data.power(), data.args()))
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}

		Block block = data.location().getBlock();
		if (block.getType() == Material.AIR && materialize(null, block, block, data.power(), data.args()))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		Block block2 = block.getRelative(BlockFace.UP);
		if (block2.getType() == Material.AIR && materialize(null, block2, block, data.power(), data.args()))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		return new CastResult(PostCastAction.ALREADY_HANDLED, data);
	}



	private int getRowLength(int patternPosition) {
		return rowPatterns[patternPosition].length;
	}

	private boolean parseBlocks(List<String> patternList) {
		if (patternList == null)
			return false;

		int patternSize = patternList.size();
		int iteration = 0;

		rowPatterns = new Material[patternSize][];

		// Lets parse all the rows within patternList
		for (String list : patternList) {
			String[] split = list.split(",");
			int arraySize = split.length;
			int blockPosition = 0;

			rowPatterns[iteration] = new Material[arraySize];

			for (String block : split) {
				Material mat = Util.getMaterial(block);
				if (mat == null)
					mat = Material.STONE;

				materials.add(mat);
				rowPatterns[iteration][blockPosition] = mat;
				blockPosition++;
			}

			iteration++;
		}
		return true;
	}

	private Material blockGenerator(boolean randomize, int patternPosition, int rowPosition) {
		Material mat;

		int randomIndex = random.nextInt(getRowLength(patternPosition));

		if (!randomize)
			mat = rowPatterns[patternPosition][rowPosition];
		else
			mat = rowPatterns[patternPosition][randomIndex];

		return mat;
	}

	private boolean materialize(Player player, Block block, Block against, float power, String[] args) {
		BlockState blockState = block.getState();

		boolean temporary = (removeBlocks || resetDelay > 0) && !falling;

		if (checkPlugins && player != null) {
			block.setType(material, false);
			MagicSpellsBlockPlaceEvent event = new MagicSpellsBlockPlaceEvent(block, blockState, against,
					player.getEquipment().getItemInMainHand(), player, true, bypassDippGen);
			EventUtil.call(event);
			blockState.update(true);
			if (event.isCancelled())
				return false;
		}
		AlteredBlockManager.Change change = null;
		if (!falling) {
			if (temporary) {
				change = MagicSpells.getAlteredBlockManager().apply(internalName, block, material, applyPhysics);
			} else {
				block.setType(material, applyPhysics);
			}
		} else {
			BlockData fallData = material.createBlockData();
			block.getLocation().getWorld().spawn(
					block.getLocation().add(0.5, fallHeight.get(player, null, power, args), 0.5),
					FallingBlock.class,
					fb -> fb.setBlockData(fallData));
		}

		Location loc = block.getLocation().add(0.5, 0.5, 0.5);
		SpellData data = new SpellData(player, loc, power, args);
		playSpellEffects(EffectPosition.TARGET, loc, data);
		if (player != null) {
			playSpellEffects(EffectPosition.CASTER, player, data);
			playSpellEffectsTrail(player.getLocation(), loc, data);
		}

		if (playBreakEffect)
			block.getWorld().playEffect(block.getLocation(), Effect.DESTROY_BLOCK, block.getBlockData());
		if (change != null)
			blocks.put(block, change);

		if (resetDelay > 0 && change != null) {
			AlteredBlockManager.Change finalChange = change;
			MagicSpells.scheduleDelayedTask(() -> {
				if (materials.contains(block.getType())) {
					blocks.remove(block);
					playSpellEffects(EffectPosition.DELAYED, block.getLocation(), data);
					if (checkPlugins && player != null) {
						MagicSpellsBlockBreakEvent event = new MagicSpellsBlockBreakEvent(block, player, bypassDippGen);
						EventUtil.call(event);
						if (event.isCancelled())
							return;
					}
					finalChange.undo(applyPhysics);
					playSpellEffects(EffectPosition.BLOCK_DESTRUCTION, block.getLocation(), data);
					if (playBreakEffect)
						block.getWorld().playEffect(block.getLocation(), Effect.DESTROY_BLOCK, block.getBlockData());
				}
			}, resetDelay);
		}
		return true;
	}

}
