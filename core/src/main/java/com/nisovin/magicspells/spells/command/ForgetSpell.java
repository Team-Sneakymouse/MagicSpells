package com.nisovin.magicspells.spells.command;

import java.util.List;
import java.util.ArrayList;

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
import com.nisovin.magicspells.util.PlayerNameUtils;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.events.SpellForgetEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;

// Advanced perm allows you to make others forget a spell
// Put * for the spell to forget all of them

public class ForgetSpell extends CommandSpell {

	private boolean allowSelfForget;

	private String strUsage;
	private String strNoSpell;
	private String strNoTarget;
	private String strResetSelf;
	private String strDoesntKnow;
	private String strCastTarget;
	private String strResetTarget;
	private String strCastSelfTarget;

	public ForgetSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		allowSelfForget = getConfigBoolean("allow-self-forget", true);

		strUsage = getConfigString("str-usage", "Usage: /cast forget <target> <spell>");
		strNoSpell = getConfigString("str-no-spell", "You do not know a spell by that name.");
		strNoTarget = getConfigString("str-no-target", "No such player.");
		strResetSelf = getConfigString("str-reset-self", "You have forgotten all of your spells.");
		strDoesntKnow = getConfigString("str-doesnt-know", "That person does not know that spell.");
		strCastTarget = getConfigString("str-cast-target", "%a has made you forget the %s spell.");
		strResetTarget = getConfigString("str-reset-target", "You have reset %t's spellbook.");
		strCastSelfTarget = getConfigString("str-cast-self-target", "You have forgotten the %s spell.");
	}
	
	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (data.args() == null || data.args().length == 0 || data.args().length > 2) {
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					Spellbook playerSpellbook = MagicSpells.getSpellbook(player);
					
					Player target;
					if (data.args().length == 1 && allowSelfForget) target = player;
					else if (data.args().length == 2 && playerSpellbook.hasAdvancedPerm("forget")) {
						List<Player> players = MagicSpells.plugin.getServer().matchPlayer(data.args()[0]);
						if (players.size() != 1) {
							sendMessage(strNoTarget, player, data.args());
							return new CastResult(PostCastAction.ALREADY_HANDLED, data);
						}
						target = players.get(0);
					} else {
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					
					String spellName = data.args().length == 1 ? data.args()[0] : data.args()[1];
					boolean all = false;
					Spell spell = null;
					if (spellName.equals("*")) all = true;
					else spell = MagicSpells.getSpellByInGameName(spellName);
		
					if (spell == null && !all) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					
					if (!all && !playerSpellbook.hasSpell(spell)) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					
					Spellbook targetSpellbook = MagicSpells.getSpellbook(target);
					if (!all && !targetSpellbook.hasSpell(spell)) {
						sendMessage(strDoesntKnow, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
		
					String playerDisplayName = Util.getStringFromComponent(player.displayName());
					String targetDisplayName = Util.getStringFromComponent(target.displayName());
					// Remove spell(s)
					if (!all) {
						targetSpellbook.removeSpell(spell);
						targetSpellbook.save();
						if (!player.equals(target)) {
							sendMessage(strCastTarget, target, data.args(), "%a", playerDisplayName, "%s", spell.getName(), "%t", targetDisplayName);
							sendMessage(strCastSelf, player, data.args(), "%a", playerDisplayName, "%s", spell.getName(), "%t", targetDisplayName);
							playSpellEffects(player, target, data.power(), data.args());
						} else {
							sendMessage(strCastSelfTarget, player, data.args(), "%s", spell.getName());
							playSpellEffects(EffectPosition.CASTER, player, data.power(), data.args());
						}
						return new CastResult(PostCastAction.NO_MESSAGES, data);
					}
		
					targetSpellbook.removeAllSpells();
					targetSpellbook.save();
		
					if (!player.equals(target)) {
						sendMessage(strResetTarget, player, data.args(), "%t", targetDisplayName);
						playSpellEffects(player, target, data.power(), data.args());
					} else {
						sendMessage(strResetSelf, player, data.args());
						playSpellEffects(EffectPosition.CASTER, player, data.power(), data.args());
					}
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	
	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		if (args == null || args.length != 2) {
			sender.sendMessage(strUsage);
			return false;
		}
		Player target = PlayerNameUtils.getPlayer(args[0]);
		if (target == null) {
			sender.sendMessage(strNoTarget);
			return false;
		}
		Spell spell = null;
		boolean all = false;
		if (args[1].equals("*")) all = true;
		else spell = MagicSpells.getSpellByInGameName(args[1]);

		if (spell == null && !all) {
			sender.sendMessage(strNoSpell);
			return false;
		}

		Spellbook targetSpellbook = MagicSpells.getSpellbook(target);
		if (!all && !targetSpellbook.hasSpell(spell)) {
			sender.sendMessage(strDoesntKnow);
			return false;
		}

		SpellForgetEvent forgetEvent = new SpellForgetEvent(spell, target);
		EventUtil.call(forgetEvent);
		if (forgetEvent.isCancelled()) return false;
		String targetDisplayName = Util.getStringFromComponent(target.displayName());
		if (!all) {
			targetSpellbook.removeSpell(spell);
			targetSpellbook.save();
			String consoleName = MagicSpells.getConsoleName();
			sendMessage(strCastTarget, target, args, "%a", consoleName, "%s", spell.getName(), "%t", targetDisplayName);
			sender.sendMessage(formatMessage(strCastSelf, "%a", consoleName, "%s", spell.getName(), "%t", targetDisplayName));
		} else {
			targetSpellbook.removeAllSpells();
			targetSpellbook.save();
			sender.sendMessage(formatMessage(strResetTarget, "%t", targetDisplayName));
		}
		return true;
	}
	
	@Override
	public List<String> tabComplete(CommandSender sender, String partial) {
		String[] args = Util.splitParams(partial);
		if (args.length == 1) {
			// Matching player name or spell name
			List<String> options = new ArrayList<>();
			List<String> players = tabCompletePlayerName(sender, args[0]);
			List<String> spells = tabCompleteSpellName(sender, args[0]);
			if (players != null) options.addAll(players);
			if (spells != null) options.addAll(spells);
			if (!options.isEmpty()) return options;
		}

		if (args.length == 2) return tabCompleteSpellName(sender, args[1]);
		return null;
	}

	public boolean shouldAllowSelfForget() {
		return allowSelfForget;
	}

	public void setAllowSelfForget(boolean allowSelfForget) {
		this.allowSelfForget = allowSelfForget;
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

	public String getStrResetSelf() {
		return strResetSelf;
	}

	public void setStrResetSelf(String strResetSelf) {
		this.strResetSelf = strResetSelf;
	}

	public String getStrDoesntKnow() {
		return strDoesntKnow;
	}

	public void setStrDoesntKnow(String strDoesntKnow) {
		this.strDoesntKnow = strDoesntKnow;
	}

	public String getStrCastTarget() {
		return strCastTarget;
	}

	public void setStrCastTarget(String strCastTarget) {
		this.strCastTarget = strCastTarget;
	}

	public String getStrResetTarget() {
		return strResetTarget;
	}

	public void setStrResetTarget(String strResetTarget) {
		this.strResetTarget = strResetTarget;
	}

	public String getStrCastSelfTarget() {
		return strCastSelfTarget;
	}

	public void setStrCastSelfTarget(String strCastSelfTarget) {
		this.strCastSelfTarget = strCastSelfTarget;
	}

}
