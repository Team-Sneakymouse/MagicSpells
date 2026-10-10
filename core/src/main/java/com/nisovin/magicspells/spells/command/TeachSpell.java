package com.nisovin.magicspells.spells.command;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.CommandSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.events.SpellLearnEvent;
import com.nisovin.magicspells.events.SpellLearnEvent.LearnSource;

public class TeachSpell extends CommandSpell {

	private boolean requireKnownSpell;

	private String strUsage;
	private String strNoSpell;
	private String strNoTarget;
	private String strCantTeach;
	private String strCantLearn;
	private String strCastTarget;
	private String strAlreadyKnown;

	public TeachSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		requireKnownSpell = getConfigBoolean("require-known-spell", true);

		strUsage = getConfigString("str-usage", "Usage: /cast teach <target> <spell>");
		strNoSpell = getConfigString("str-no-spell", "You do not know a spell by that name.");
		strNoTarget = getConfigString("str-no-target", "No such player.");
		strCantTeach = getConfigString("str-cant-teach", "You can't teach that spell.");
		strCantLearn = getConfigString("str-cant-learn", "That person cannot learn that spell.");
		strCastTarget = getConfigString("str-cast-target", "%a has taught you the %s spell.");
		strAlreadyKnown = getConfigString("str-already-known", "That person already knows that spell.");
	}
	
	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (data.args() == null || data.args().length != 2) {
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					List<Player> players = MagicSpells.plugin.getServer().matchPlayer(data.args()[0]);
					if (players.size() != 1) {
						sendMessage(strNoTarget, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					Spell spell = MagicSpells.getSpellByInGameName(data.args()[1]);
					Player target = players.get(0);
					if (spell == null) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					Spellbook spellbook = MagicSpells.getSpellbook(player);
					if (!spellbook.hasSpell(spell) && requireKnownSpell) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					if (!spellbook.canTeach(spell)) {
						sendMessage(strCantTeach, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					Spellbook targetSpellbook = MagicSpells.getSpellbook(target);
					if (!targetSpellbook.canLearn(spell)) {
						sendMessage(strCantLearn, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					if (targetSpellbook.hasSpell(spell)) {
						sendMessage(strAlreadyKnown, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					boolean cancelled = callEvent(spell, target, player);
					if (cancelled) {
						sendMessage(strCantLearn, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					targetSpellbook.addSpell(spell);
					targetSpellbook.save();
		
					String playerDisplayName = Util.getStringFromComponent(player.displayName());
					String targetDisplayName = Util.getStringFromComponent(target.displayName());
		
					sendMessage(spell.getStrOnTeach() == null ? strCastTarget : spell.getStrOnTeach(), target, data.args(), "%a", playerDisplayName, "%s", spell.getName(), "%t", targetDisplayName);
					sendMessage(strCastSelf, player, data.args(), "%a", playerDisplayName, "%s", spell.getName(), "%t", targetDisplayName);
		
					playSpellEffects(player, target, data.power(), data.args());
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	
	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		if (args == null || args.length != 2) {
			sender.sendMessage(strUsage);
			return true;
		}
		List<Player> players = MagicSpells.plugin.getServer().matchPlayer(args[0]);
		if (players.size() != 1) {
			sender.sendMessage(strNoTarget);
			return true;
		}
		Spell spell = MagicSpells.getSpellByInGameName(args[1]);
		if (spell == null) {
			sender.sendMessage(strNoSpell);
			return true;
		}
		Spellbook targetSpellbook = MagicSpells.getSpellbook(players.get(0));
		if (!targetSpellbook.canLearn(spell)) {
			sender.sendMessage(strCantLearn);
			return true;
		}
		if (targetSpellbook.hasSpell(spell)) {
			sender.sendMessage(strAlreadyKnown);
			return true;
		}
		boolean cancelled = callEvent(spell, players.get(0), sender);
		if (cancelled) {
			sender.sendMessage(strCantLearn);
			return true;
		}

		targetSpellbook.addSpell(spell);
		targetSpellbook.save();

		String displayName = Util.getStringFromComponent(players.get(0).displayName());

		String consoleName = MagicSpells.getConsoleName();
		sendMessage(spell.getStrOnTeach() == null ? strCastTarget : spell.getStrOnTeach(), players.get(0), args, "%a", consoleName, "%s", spell.getName(), "%t", displayName);
		sender.sendMessage(formatMessage(strCastSelf, "%a", consoleName, "%s", spell.getName(), "%t", displayName));
		return true;
	}
	
	@Override
	public List<String> tabComplete(CommandSender sender, String partial) {
		String[] args = Util.splitParams(partial);
		if (args.length == 1) return tabCompletePlayerName(sender, args[0]);
		if (args.length == 2) return tabCompleteSpellName(sender, args[1]);
		return null;
	}
	
	private boolean callEvent(Spell spell, Player learner, Object teacher) {
		SpellLearnEvent event = new SpellLearnEvent(spell, learner, LearnSource.TEACH, teacher);
		EventUtil.call(event);
		return event.isCancelled();
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

	public String getStrNoTarget() {
		return strNoTarget;
	}

	public void setStrNoTarget(String strNoTarget) {
		this.strNoTarget = strNoTarget;
	}

	public String getStrCantTeach() {
		return strCantTeach;
	}

	public void setStrCantTeach(String strCantTeach) {
		this.strCantTeach = strCantTeach;
	}

	public String getStrCantLearn() {
		return strCantLearn;
	}

	public void setStrCantLearn(String strCantLearn) {
		this.strCantLearn = strCantLearn;
	}

	public String getStrCastTarget() {
		return strCastTarget;
	}

	public void setStrCastTarget(String strCastTarget) {
		this.strCastTarget = strCastTarget;
	}

	public String getStrAlreadyKnown() {
		return strAlreadyKnown;
	}

	public void setStrAlreadyKnown(String strAlreadyKnown) {
		this.strAlreadyKnown = strAlreadyKnown;
	}

}
