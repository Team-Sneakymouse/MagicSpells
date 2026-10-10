package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.entity.LivingEntity;

import net.kyori.adventure.text.Component;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class ResourcePackSpell extends TargetedSpell implements TargetedEntitySpell {

	private static final int HASH_LENGTH = 40;

	private final String url;
	private String hash;
	private final String hashVariable;
	private final Component prompt;
	private final boolean required;

	public ResourcePackSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		url = getConfigString("url", null);
		hash = getConfigString("hash", null);
		hashVariable = getConfigString("hash-variable", null);
		prompt = Util.getMiniMessage(getConfigString("prompt", ""));
		required = getConfigBoolean("required", false);
	}

	@Override
	public void initialize() {
		super.initialize();

		if (hash != null && hashVariable != null) {
			MagicSpells.error("ResourcePackSpell '" + internalName + "' has both a hash and a hash-variable defined. The hash-variable will be used.");
		}

		if (hash != null && hash.length() != HASH_LENGTH) {
			MagicSpells.error("ResourcePackSpell '" + internalName + "' has an incorrect hash length defined: '" + hash.length() + "' / " + HASH_LENGTH + ".");
		}
	}

	private void parseHashVariable() {
		if (hashVariable != null) {
			Variable var = MagicSpells.getVariableManager().getVariable(hashVariable);

			if (var != null && var instanceof GlobalStringVariable gvar) {
				hash = gvar.getStringValue((String) null);
			} else {
				MagicSpells.error("ResourcePackSpell '" + internalName + "' has an incorrect hash-var defined: '" + hashVariable + "'. The defined variable must be a globalstring variable.");
			}
		}
	}

	@EventHandler(ignoreCancelled = false)
	private void onResourcePack(PlayerResourcePackStatusEvent event) {
		if (required && event.getStatus().equals(PlayerResourcePackStatusEvent.Status.DECLINED) && !event.getPlayer().hasPermission("magicspells.bypassresourcepacks")) {
			event.getPlayer().kick(Component.text("Please enable resource packs in your server settings"));
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			this.parseHashVariable();
			TargetInfo<Player> info = getTargetedPlayer(caster, power, args);
			if (info.noTarget()) return noTarget(caster, args, info);
			Player target = info.target();

			try {
				target.setResourcePack(url, hash, target.hasPermission("magicspells.bypassresourcepacks") ? false : required, prompt);
			} catch (IllegalArgumentException e) {
				DebugHandler.debugIllegalArgumentException(e);
				return PostCastAction.ALREADY_HANDLED;
			}

			playSpellEffects(caster, target, info.power(), args);
			sendMessages(caster, target, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(caster, target)) return false;
		
		this.parseHashVariable();

		try {
			player.setResourcePack(url, hash, player.hasPermission("magicspells.bypassresourcepacks") ? false : required, prompt);
		} catch (IllegalArgumentException e) {
			DebugHandler.debugIllegalArgumentException(e);
			return false;
		}

		playSpellEffects(caster, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(target)) return false;
		
		this.parseHashVariable();

		try {
			player.setResourcePack(url, hash, player.hasPermission("magicspells.bypassresourcepacks") ? false : required, prompt);
		} catch (IllegalArgumentException e) {
			DebugHandler.debugIllegalArgumentException(e);
			return false;
		}

		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
	}

}
