package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class UndoSpell extends TargetedSpell implements TargetedLocationSpell {

	private ConfigData<Integer> radius;

	private boolean pointBlank;
	private ConfigData<Boolean> powerAffectsRadius;
	private boolean applyPhysics;

	private List<String> spellNames;
	private List<Spell> spells;

	public UndoSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		radius = getConfigDataInt("radius", 10);
		pointBlank = getConfigBoolean("point-blank", true);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", true);
		applyPhysics = getConfigBoolean("apply-physics", true);
		spellNames = getConfigStringList("spells", null);
		spells = new ArrayList<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		if (spellNames != null) {
			for (String spellName : spellNames) {
				Spell spell = MagicSpells.getSpellByInternalName(spellName);
				if (spell != null) {
					spells.add(spell);
				} else {
					MagicSpells.error(
							"UndoSpell '" + internalName + "' has an invalid spell defined in spells!");
					return;
				}
			}
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			Location loc = pointBlank ? caster.getLocation() : getTargetedBlock(caster, power, args).getLocation();
			if (loc == null) {
				return noTarget(caster, args);
			}
			return undo(caster, loc, power, args) ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED;
		}
		return PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return undo(caster, target, power, args);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return undo(null, target, power, args);
	}

	private boolean undo(LivingEntity caster, Location loc, float power, String[] args) {
		int rad = radius.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args)))
			rad *= power;

		World locWorld = loc.getWorld();
		if (locWorld == null) {
			MagicSpells.error("Location world is null for UndoSpell.");
			return false;
		}

		boolean filterBySpell = !spells.isEmpty();
		boolean success = false;
		AlteredBlockManager manager = MagicSpells.getAlteredBlockManager();

		for (int y = loc.getBlockY() - rad; y <= loc.getBlockY() + rad; y++) {
			for (int x = loc.getBlockX() - rad; x <= loc.getBlockX() + rad; x++) {
				for (int z = loc.getBlockZ() - rad; z <= loc.getBlockZ() + rad; z++) {
					Block block = locWorld.getBlockAt(x, y, z);
					List<AlteredBlockManager.Change> changes;
					if (filterBySpell) {
						changes = new ArrayList<>();
						for (Spell spell : spells) {
							changes.addAll(manager.getByBlockAndInternalName(block, spell.getInternalName()));
						}
					} else {
						changes = manager.getByBlock(block);
					}
					if (!changes.isEmpty()) {
						success = true;
						for (int i = changes.size() - 1; i >= 0; i--) {
							changes.get(i).undo(applyPhysics);
							playSpellEffects(EffectPosition.TARGET, block.getLocation(), power, args);
						}
					}
				}
			}
		}

		if (caster != null)
			playSpellEffects(EffectPosition.CASTER, caster.getLocation(), power, args);

		playSpellEffects(EffectPosition.SPECIAL, loc, power, args);
		return success;
	}
}
