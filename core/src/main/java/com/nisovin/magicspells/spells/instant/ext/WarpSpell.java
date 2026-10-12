package com.nisovin.magicspells.spells.instant.ext;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Modules.Warps.CmiWarp;

public class WarpSpell extends InstantSpell {
	private String targetWarpName;
	private String warpNameContains;

	private boolean rememberOffset;

	public WarpSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		targetWarpName = getConfigString("warp-name", null);
		warpNameContains = getConfigString("warp-name-contains", null);

		rememberOffset = getConfigBoolean("remember-offset", true);
	}

	@Override
	public CastResult cast(SpellData data) {
		CmiWarp targetWarp = CMI.getInstance().getWarpManager().getWarp(targetWarpName);

		if (targetWarp == null) {
			MagicSpells.error("WarpSpell '" + internalName + "' has an invalid warp defined!");
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}

		Location twLoc = ((Location) targetWarp.getLoc()).clone();
		Location cLoc = data.caster().getLocation();

		if (rememberOffset) {
			Location nearestWarpLoc = null;
			Double smallestDistSq = 0D;

			for (CmiWarp warp : CMI.getInstance().getWarpManager().getWarps().values()) {
				if (warpNameContains != null && !warp.getName().contains(warpNameContains)) continue;

				Location wLoc = (Location) warp.getLoc();

				if (!cLoc.getWorld().getName().equals(wLoc.getWorld().getName())) continue;

				Double distSq = cLoc.distanceSquared(wLoc);

				if (nearestWarpLoc == null || distSq < smallestDistSq) {
					nearestWarpLoc = wLoc;
					smallestDistSq = distSq;
				}
			}

			if (nearestWarpLoc != null) {
				twLoc = new Location(twLoc.getWorld(),
					twLoc.getBlockX() + (cLoc.getX() - nearestWarpLoc.getBlockX()),
					twLoc.getBlockY() + (cLoc.getY() - nearestWarpLoc.getBlockY()),
					twLoc.getBlockZ() + (cLoc.getZ() - nearestWarpLoc.getBlockZ())
				);
			}
		}

		twLoc.setPitch(cLoc.getPitch());
		twLoc.setYaw(cLoc.getYaw());
		data.caster().teleport(twLoc);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

}
