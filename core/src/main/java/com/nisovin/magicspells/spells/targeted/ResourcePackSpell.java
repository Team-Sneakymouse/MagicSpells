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
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
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
	public CastResult cast(SpellData data) {
		TargetInfo<Player> targetInfo = getTargetedPlayer(data.caster(), data.power(), data.args());
		if (targetInfo.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);

		data = data.target(targetInfo.target()).power(targetInfo.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return result;

		sendMessages(data.caster(), data.target(), data.args());
		return new CastResult(PostCastAction.NO_MESSAGES, data);
	}
	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							this.parseHashVariable();
							try {
								player.setResourcePack(url, hash, player.hasPermission("magicspells.bypassresourcepacks") ? false : required, prompt);
							} catch (IllegalArgumentException e) {
								DebugHandler.debugIllegalArgumentException(e);
								return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							}
							playSpellEffects(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							this.parseHashVariable();
							try {
								player.setResourcePack(url, hash, player.hasPermission("magicspells.bypassresourcepacks") ? false : required, prompt);
							} catch (IllegalArgumentException e) {
								DebugHandler.debugIllegalArgumentException(e);
								return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							}
							playSpellEffects(EffectPosition.TARGET, data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

}
