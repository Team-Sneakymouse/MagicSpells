package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class OffsetLocationSpell extends TargetedSpell implements TargetedLocationSpell {

	private Vector relativeOffset;
	private Vector absoluteOffset;

	private float forcedPitch;
	private boolean forcePitch = false;

	private Subspell spellToCast;
	private String spellToCastName;

	public OffsetLocationSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		relativeOffset = getConfigVector("relative-offset", "0,0,0");
		absoluteOffset = getConfigVector("absolute-offset", "0,0,0");

		if (configKeyExists("forced-pitch")) {
			forcedPitch = getConfigFloat("forced-pitch", 0);
			forcePitch = true;
		}

		spellToCastName = getConfigString("spell", "");
	}

	@Override
	public void initialize() {
		super.initialize();

		spellToCast = new Subspell(spellToCastName);
		if (!spellToCast.process()) {
			MagicSpells.error("OffsetLocationSpell '" + internalName + "' has an invalid spell defined!");
			spellToCast = null;
		}
	}

	private Location applyOffsetLocation(Location base) {
		if (forcePitch)
			return Util.applyOffsets(base.clone(), relativeOffset, absoluteOffset, forcedPitch);
		return Util.applyOffsets(base.clone(), relativeOffset, absoluteOffset);
	}

	@Override
	public CastResult cast(SpellData data) {
		Location baseTargetLocation;
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
		if (info.cancelled())
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		if (!info.empty())
			baseTargetLocation = info.target().getLocation();
		else
			baseTargetLocation = getTargetedBlock(data.caster(), data.power(), data.args()).getLocation();

		Location loc = applyOffsetLocation(baseTargetLocation);
		data = data.location(loc);

		if (spellToCast != null)
			spellToCast.subcast(data);
		playSpellEffects(data.caster(), data.location(), data);

		if (!info.empty()) {
			data = data.target(info.target());
			sendMessages(data.caster(), data.target(), data.args());
			return new CastResult(PostCastAction.NO_MESSAGES, data);
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
		if (spellToCast != null)
			spellToCast.subcast(data.location(applyOffsetLocation(data.location())));
		if (data.hasCaster())
			playSpellEffects(data.caster(), data.location(), data);
		else
			playSpellEffects(EffectPosition.TARGET, data.location(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



}
