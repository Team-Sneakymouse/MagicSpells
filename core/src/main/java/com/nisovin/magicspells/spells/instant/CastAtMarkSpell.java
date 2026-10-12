package com.nisovin.magicspells.spells.instant;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;

public class CastAtMarkSpell extends InstantSpell {

	private final String markSpellName;
	private final String spellToCastName;

	private String strNoMark;

	private MarkSpell markSpell;
	private Subspell spellToCast;

	private boolean initialized = false;

	public CastAtMarkSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		strNoMark = getConfigString("str-no-mark", "You do not have a mark specified");
		markSpellName = getConfigString("mark-spell", "");
		spellToCastName = getConfigString("spell", "");
	}
	
	@Override
	public void initialize() {
		super.initialize();

		if (initialized) return;

		Spell spell = MagicSpells.getSpellByInternalName(markSpellName);
		if (!(spell instanceof MarkSpell)) {
			MagicSpells.error("CastAtMarkSpell '" + internalName + "' has an invalid mark-spell defined!");
			return;
		}
		
		markSpell = (MarkSpell) spell;
		
		spellToCast = new Subspell(spellToCastName);
		if (!spellToCast.process()) {
			MagicSpells.error("CastAtMarkSpell '" + internalName + "' has an invalid spell defined!");
			return;
		}
		
		initialized = true;
	}

	@Override
	public void turnOff() {
		super.turnOff();

		markSpell = null;
		spellToCast = null;
		initialized = false;
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!initialized)
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		Location effectiveMark = markSpell.getEffectiveMark(data.caster());
		if (effectiveMark == null) {
			sendMessage(data.caster(), strNoMark);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
		spellToCast.subcast(new SpellData(data.caster(), effectiveMark, data.power(), data.args()));
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	public String getStrNoMark() {
		return strNoMark;
	}

	public void setStrNoMark(String strNoMark) {
		this.strNoMark = strNoMark;
	}

	public MarkSpell getMarkSpell() {
		return markSpell;
	}

	public void setMarkSpell(MarkSpell markSpell) {
		this.markSpell = markSpell;
	}

	public Subspell getSpellToCast() {
		return spellToCast;
	}

	public void setSpellToCast(Subspell spellToCast) {
		this.spellToCast = spellToCast;
	}

}
