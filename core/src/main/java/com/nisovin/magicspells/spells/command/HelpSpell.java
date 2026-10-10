package com.nisovin.magicspells.spells.command;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.CommandSpell;

public class HelpSpell extends CommandSpell {
	
	private boolean requireKnownSpell;

	private String strUsage;
	private String strNoSpell;
	private String strDescLine;
	private String strCostLine;

	public HelpSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		requireKnownSpell = getConfigBoolean("require-known-spell", true);

		strUsage = getConfigString("str-usage", "Usage: /cast " + name + " <spell>");
		strNoSpell = getConfigString("str-no-spell", "You do not know a spell by that name.");
		strDescLine = getConfigString("str-desc-line", "%s - %d");
		strCostLine = getConfigString("str-cost-line", "Cost: %c");
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (data.args() == null || data.args().length == 0) {
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					Spell spell = MagicSpells.getSpellByInGameName(Util.arrayJoin(data.args(), ' '));
					Spellbook spellbook = MagicSpells.getSpellbook(player);
		
					if (spell == null || requireKnownSpell && !spellbook.hasSpell(spell)) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					sendMessage(strDescLine, player, data.args(), "%s", spell.getName(), "%d", spell.getDescription());
					if (spell.getCostStr() != null && !spell.getCostStr().isEmpty()) {
						sendMessage(strCostLine, player, data.args(), "%c", spell.getCostStr());
					}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}


	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		return false;
	}
	
	@Override
	public List<String> tabComplete(CommandSender sender, String partial) {
		String [] args = Util.splitParams(partial);
		if (sender instanceof Player && args.length == 1) return tabCompleteSpellName(sender, partial);
		return null;
	}

	public boolean shouldRequireKnownSpell() {
		return requireKnownSpell;
	}

	public void setRequireKnownSpell(boolean requireKnownSpell) {
		this.requireKnownSpell = requireKnownSpell;
	}

	public String getStrUsage() {
		return strUsage;
	}

	public void setStrUsage(String strUsage) {
		this.strUsage = strUsage;
	}

	public String getStrNoSpell() {
		return strNoSpell;
	}

	public void setStrNoSpell(String strNoSpell) {
		this.strNoSpell = strNoSpell;
	}

	public String getStrDescLine() {
		return strDescLine;
	}

	public void setStrDescLine(String strDescLine) {
		this.strDescLine = strDescLine;
	}

	public String getStrCostLine() {
		return strCostLine;
	}

	public void setStrCostLine(String strCostLine) {
		this.strCostLine = strCostLine;
	}

}
