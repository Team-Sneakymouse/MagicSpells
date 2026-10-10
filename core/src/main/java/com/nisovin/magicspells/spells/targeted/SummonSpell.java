package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class SummonSpell extends TargetedSpell implements TargetedEntitySpell, TargetedEntityFromLocationSpell {

	private Map<Player, Location> pendingSummons;
	private Map<Player, Long> pendingTimes;

	private int maxAcceptDelay;

	private boolean requireExactName;
	private boolean requireAcceptance;

	private String strUsage;
	private String acceptCommand;
	private String strSummonPending;
	private String strSummonExpired;
	private String strSummonAccepted;

	public SummonSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		maxAcceptDelay = getConfigInt("max-accept-delay", 90);

		requireExactName = getConfigBoolean("require-exact-name", false);
		requireAcceptance = getConfigBoolean("require-acceptance", true);

		strUsage = getConfigString("str-usage", "Usage: /cast summon <playername>, or /cast summon \nwhile looking at a sign with a player name on the first line.");
		acceptCommand = getConfigString("accept-command", "accept");
		strSummonPending = getConfigString("str-summon-pending", "You are being summoned! Type /accept to teleport.");
		strSummonExpired = getConfigString("str-summon-expired", "The summon has expired.");
		strSummonAccepted = getConfigString("str-summon-accepted", "You have been summoned.");

		if (requireAcceptance) {
			pendingSummons = new HashMap<>();
			pendingTimes = new HashMap<>();
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		// Get target name and landing location
					String targetName = "";
					Location landLoc = null;
					if (data.args() != null && data.args().length > 0) {
						targetName = data.args()[0];
						landLoc = player.getLocation().add(0, .25, 0);
					} else {
						Block block = getTargetedBlock(player, 10, data.args());
						if (block != null && (block.getType().name().contains("SIGN"))) {
							Sign sign = (Sign) block.getState();
							targetName = Util.getStringFromComponent(sign.getSide(Side.FRONT).line(0));
							landLoc = block.getLocation().add(.5, .25, .5);
						}
					}
					
					// Check usage
					if (targetName.isEmpty()) {
						// Fail -- show usage
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					
					// Check location
					if (!BlockUtils.isSafeToStand(landLoc.clone())) {
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					
					// Get player
					Player target = null;
					if (requireExactName) {
						target = PlayerNameUtils.getPlayer(targetName);
						if (target != null && !target.getName().equalsIgnoreCase(targetName)) target = null;
					} else {
						List<Player> players = Bukkit.getServer().matchPlayer(targetName);
						if (players.size() == 1) {
							target = players.get(0);
						}
					}
					if (target == null) return new CastResult(noTarget(player, data.args()), data);
		
					// Teleport player
					String displayName = Util.getStringFromComponent(player.displayName());
					if (requireAcceptance) {
						pendingSummons.put(target, landLoc);
						pendingTimes.put(target, System.currentTimeMillis());
						sendMessage(strSummonPending, target, data.args(), "%a", displayName);
					} else {
						target.teleportAsync(landLoc);
						sendMessage(strSummonAccepted, target, data.args(), "%a", displayName);
					}
		
					sendMessages(player, target, data.args());
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
		if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				boolean ok = data.target().teleport(data.caster());
		return new CastResult(ok ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		target.teleportAsync(from);
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		target.teleportAsync(from);
		return true;
	}

	@EventHandler(priority=EventPriority.LOW)
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		if (!requireAcceptance) return;
		if (!event.getMessage().equalsIgnoreCase('/' + acceptCommand)) return;
		if (!pendingSummons.containsKey(event.getPlayer())) return;

		Player player = event.getPlayer();
		if (maxAcceptDelay > 0 && pendingTimes.get(player) + maxAcceptDelay * TimeUtil.MILLISECONDS_PER_SECOND < System.currentTimeMillis()) {
			sendMessage(strSummonExpired, player, MagicSpells.NULL_ARGS);
		} else {
			player.teleportAsync(pendingSummons.get(player));
			sendMessage(strSummonAccepted, player, MagicSpells.NULL_ARGS);
		}
		pendingSummons.remove(player);
		pendingTimes.remove(player);
		event.setCancelled(true);
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String partial) {
		if (partial.contains(" ")) return null;
		return tabCompletePlayerName(sender, partial);
	}

}
