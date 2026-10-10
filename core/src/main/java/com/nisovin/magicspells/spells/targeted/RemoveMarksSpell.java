package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicLocation;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.instant.MarkSpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class RemoveMarksSpell extends TargetedSpell implements TargetedLocationSpell {

	private ConfigData<Float> radius;

	private boolean pointBlank;
	private ConfigData<Boolean> powerAffectsRadius;

	private MarkSpell markSpell;
	private String markSpellName;

	public RemoveMarksSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		radius = getConfigDataFloat("radius", 10F);

		pointBlank = getConfigBoolean("point-blank", false);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", true);

		markSpellName = getConfigString("mark-spell", "");
	}

	@Override
	public void initialize() {
		super.initialize();

		Spell spell = MagicSpells.getSpellByInternalName(markSpellName);
		if (spell instanceof MarkSpell) {
			markSpell = (MarkSpell) spell;
			return;
		}

		MagicSpells.error("RemoveMarksSpell '" + internalName + "' has an invalid mark-spell defined!");
	}

	@Override
	public CastResult cast(SpellData data) {
		Location loc = null;
					if (pointBlank) loc = data.caster().getLocation();
					else {
						Block b = getTargetedBlock(data.caster(), data.power(), data.args());
						if (b != null && !BlockUtils.isAir(b.getType())) loc = b.getLocation();
					}
					if (loc == null) return new CastResult(noTarget(data.caster(), data.args()), data);
					removeMarks(data.caster(), loc, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}


	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		removeMarks(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		removeMarks(null, target, power, args);
		return true;
	}

	private void removeMarks(LivingEntity caster, Location loc, float power, String[] args) {
		float radSq = radius.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args))) radSq *= power;

		radSq *= radSq;

		Map<UUID, MagicLocation> marks = markSpell.getMarks();
		Iterator<UUID> iter = marks.keySet().iterator();
		World locWorld = loc.getWorld();

		while (iter.hasNext()) {
			MagicLocation l = marks.get(iter.next());
			if (!l.getWorld().equals(locWorld.getName())) continue;
			if (l.getLocation().distanceSquared(loc) < radSq) iter.remove();
		}

		markSpell.setMarks(marks);
		if (caster != null) playSpellEffects(caster, loc, power, args);
		else playSpellEffects(EffectPosition.TARGET, loc, power, args);
	}

}
