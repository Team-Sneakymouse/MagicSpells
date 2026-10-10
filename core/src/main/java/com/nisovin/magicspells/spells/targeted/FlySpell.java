package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.TargetBooleanState;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class FlySpell extends TargetedSpell implements TargetedEntitySpell {

	private final Set<UUID> wasAllowedFlight;

	private final boolean setFlying;
	private final TargetBooleanState targetBooleanState;

	public FlySpell(MagicConfig config, String spellName) {
		super(config, spellName);

		wasAllowedFlight = new HashSet<>();

		setFlying = getConfigBoolean("set-flying", true);
		targetBooleanState = TargetBooleanState.getFromName(getConfigString("target-state", "toggle"));
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<Player> targetInfo = getTargetedPlayer(data.caster(), data.power(), data.args());
					if (targetInfo.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);
					Player target = targetInfo.target();
		
					setFlyingState(target);
					playSpellEffects(data.caster(), target, targetInfo.power(), data.args());
					sendMessages(data.caster(), target, data.args());
		
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
							setFlyingState(player);
							playSpellEffects(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							setFlyingState(player);
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

	@Override
	protected void turnOff() {
		Player player;
		for (UUID uuid : wasAllowedFlight) {
			player = Bukkit.getPlayer(uuid);
			if (player == null) continue;
			if (player.getGameMode() == GameMode.CREATIVE) continue;
			if (player.getGameMode() == GameMode.SPECTATOR) continue;
			player.setAllowFlight(false);
		}
		wasAllowedFlight.clear();
	}

	private void setFlyingState(Player player) {
		boolean newState = targetBooleanState.getBooleanState(player.isFlying() || player.getAllowFlight());
		UUID uuid = player.getUniqueId();
		if (newState) {
			if (!player.getAllowFlight()) {
				player.setAllowFlight(true);
				wasAllowedFlight.add(uuid);
			}
			if (setFlying) player.teleportAsync(player.getLocation().add(0, 0.25, 0));
		}
		else {
			boolean wasAllowed = wasAllowedFlight.remove(uuid);
			if (wasAllowed) player.setAllowFlight(false);
		}
		if (setFlying) player.setFlying(newState);
	}

}
