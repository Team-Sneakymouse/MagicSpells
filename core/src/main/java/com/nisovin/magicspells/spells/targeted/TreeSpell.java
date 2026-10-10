package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.SpellAnimation;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

public class TreeSpell extends TargetedSpell implements TargetedLocationSpell {

	private TreeType treeType;

	private ConfigData<Integer> speed;
	
	public TreeSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		try {
			treeType = TreeType.valueOf(getConfigString("tree-type", "tree").toUpperCase().replace(" ", "_"));
		}
		catch (IllegalArgumentException ignored) {
			treeType = TreeType.TREE;
		}

		speed = getConfigDataInt("animation-speed", 20);
	}

	@Override
	public CastResult cast(SpellData data) {
		Block target = getTargetedBlock(data.caster(), data.power(), data.args());

		if (target != null && !BlockUtils.isAir(target.getType())) {
			SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(target.getLocation()));
			EventUtil.call(event);
			if (event.isCancelled())
				target = null;
			else {
				data = event.getSpellData();
				target = data.location().getBlock();
			}
		}

		if (target == null || BlockUtils.isAir(target.getType()))
			return new CastResult(noTarget(data.caster(), data.args()), data);

		CastResult result = castAtLocation(data.location(target.getLocation()));
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	private boolean growTree(LivingEntity caster, Block target, float power, String[] args) {
		target = target.getRelative(BlockFace.UP);
		if (!BlockUtils.isAir(target.getType())) return false;

		Location loc = target.getLocation();

		int speed = this.speed.get(caster, null, power, args);
		if (speed > 0) {
			List<BlockState> blockStates = new ArrayList<>();
			// Predicate false = defer placement for GrowAnimation; capture intended states.
			boolean grown = target.getWorld().generateTree(loc, ThreadLocalRandom.current(), treeType, state -> {
				blockStates.add(state);
				return false;
			});
			if (grown && !blockStates.isEmpty()) {
				new GrowAnimation(loc.getBlockX(), loc.getBlockZ(), blockStates, speed);
				return true;
			}
			return false;
		}

		return target.getWorld().generateTree(loc, ThreadLocalRandom.current(), treeType);
	}
	
	@Override
	public CastResult castAtLocation(SpellData data) {
		boolean ret = growTree(data.caster(), data.location().getBlock(), data.power(), data.args());
		if (!ret)
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (data.hasCaster())
			playSpellEffects(data.caster(), data.location(), data);
		else
			playSpellEffects(EffectPosition.TARGET, data.location(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return castAtLocation(new SpellData(null, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	private static class GrowAnimation extends SpellAnimation {
		
		private List<BlockState> blockStates;

		private int blocksPerTick;

		private GrowAnimation(final int centerX, final int centerZ, final List<BlockState> blocks, int speed) {
			super(speed < 20 ? 20 / speed : 1, true);
			
			this.blockStates = blocks;
			this.blocksPerTick = speed/20 + 1;
			blockStates.sort((o1, o2) -> {
				if (o1.getY() < o2.getY()) return -1;
				if (o1.getY() > o2.getY()) return 1;

				int dist1 = Math.abs(o1.getX() - centerX) + Math.abs(o1.getZ() - centerZ);
				int dist2 = Math.abs(o2.getX() - centerX) + Math.abs(o2.getZ() - centerZ);
				return Integer.compare(dist1, dist2);
			});
		}

		@Override
		protected void onTick(int tick) {
			for (int i = 0; i < blocksPerTick; i++) {
				BlockState state = blockStates.remove(0);
				state.update(true);
				if (blockStates.isEmpty()) {
					stop(true);
					break;
				}
			}
		}
		
	}
	
}
