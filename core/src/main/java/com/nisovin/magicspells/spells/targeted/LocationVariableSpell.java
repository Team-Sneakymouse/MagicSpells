package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.HashSet;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockLocation;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.LocationVariable;
import com.nisovin.magicspells.variables.variabletypes.LocationStringVariable;

public class LocationVariableSpell extends TargetedSpell implements TargetedLocationSpell {

	private final String variableName;
	private final String value;
	private final String shape;
	
	// Cuboid
	private final int offsetX;
	private final int offsetY;
	private final int offsetZ;
	
	// Cylinder
	private final double radius;
	private final int heightUp;
	private final int heightDown;

	public LocationVariableSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		variableName = getConfigString("variable", "");
		value = getConfigString("value", "0");
		shape = getConfigString("shape", "cuboid").toLowerCase();
		
		offsetX = getConfigInt("offset-x", 0);
		offsetY = getConfigInt("offset-y", 0);
		offsetZ = getConfigInt("offset-z", 0);
		
		radius = getConfigDouble("radius", 0);
		heightUp = getConfigInt("height-up", 0);
		heightDown = getConfigInt("height-down", 0);
	}

	@Override
	public CastResult cast(SpellData data) {
		Block block = getTargetedBlock(data.caster(), data.power(), data.args());
		if (block != null) {
			boolean ok = castAtLocation(data.caster(), block.getLocation(), data.power(), data.args());
			return new CastResult(ok ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
		}
		return new CastResult(noTarget(data.caster(), data.args()), data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	public PostCastAction cast(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtLocation(caster, target.getLocation(), power, args) ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED;
	}

		@Override
	public CastResult castAtLocation(SpellData data) {
		Variable var = MagicSpells.getVariableManager().getVariable(variableName);
		if (var == null) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!(var instanceof LocationVariable || var instanceof LocationStringVariable)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		
		Set<BlockLocation> blocks = new HashSet<>();
		if (shape.equals("cuboid")) {
		int x1 = data.location().getBlockX();
		int y1 = data.location().getBlockY();
		int z1 = data.location().getBlockZ();
		int x2 = x1 + offsetX;
		int y2 = y1 + offsetY;
		int z2 = z1 + offsetZ;
			
		int minX = Math.min(x1, x2);
		int maxX = Math.max(x1, x2);
		int minY = Math.min(y1, y2);
		int maxY = Math.max(y1, y2);
		int minZ = Math.min(z1, z2);
		int maxZ = Math.max(z1, z2);
			
		for (int x = minX; x <= maxX; x++) {
		for (int y = minY; y <= maxY; y++) {
		for (int z = minZ; z <= maxZ; z++) {
		blocks.add(new BlockLocation(data.location().getWorld().getName(), x, y, z));
		}
		}
		}
		} else if (shape.equals("cylinder")) {
		int centerX = data.location().getBlockX();
		int centerY = data.location().getBlockY();
		int centerZ = data.location().getBlockZ();
		int minY = centerY - heightDown;
		int maxY = centerY + heightUp;
		double radiusSq = radius * radius;
			
		int minX = (int) Math.floor(centerX - radius);
		int maxX = (int) Math.ceil(centerX + radius);
		int minZ = (int) Math.floor(centerZ - radius);
		int maxZ = (int) Math.ceil(centerZ + radius);
			
		for (int x = minX; x <= maxX; x++) {
		for (int z = minZ; z <= maxZ; z++) {
		double distSq = Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2);
		if (distSq <= radiusSq) {
		for (int y = minY; y <= maxY; y++) {
		blocks.add(new BlockLocation(data.location().getWorld().getName(), x, y, z));
		}
		}
		}
		}
		}
		
		if (blocks.isEmpty()) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		
		if (var instanceof LocationVariable lv) {
		lv.defineRegion(blocks, Double.parseDouble(value));
		} else if (var instanceof LocationStringVariable lsv) {
		lsv.defineRegion(blocks, value);
		}
		
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	
	}
@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}



}
