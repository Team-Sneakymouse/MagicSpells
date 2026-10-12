package com.nisovin.magicspells.spells;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.conversations.Prompt;
import org.bukkit.command.CommandSender;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MessageBlocker;
import com.nisovin.magicspells.util.ValidTargetList;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

// Conversations fallback (no ProtocolLib) uses for-removal API; see COMPILE-WARNINGS.md.
@SuppressWarnings("removal")
public class ExternalCommandSpell extends TargetedSpell implements TargetedEntitySpell {
	
	private static MessageBlocker messageBlocker;

	private ConfigData<List<String>> commandToExecuteData;
	private ConfigData<List<String>> commandToExecuteLaterData;

	private List<String> commandToBlock;
	private List<String> commandToExecute;
	private List<String> commandToExecuteLater;
	private List<String> temporaryPermissions;

	private int commandDelay;

	private boolean temporaryOp;
	private boolean blockChatOutput;
	private boolean requirePlayerTarget;
	private boolean doVariableReplacement;
	private boolean executeAsTargetInstead;
	private boolean executeOnConsoleInstead;
	private boolean useTargetVariablesInstead;
	private String strBlockedOutput;
	private String strCantUseCommand;

	private Prompt convoPrompt;
	private ConversationFactory convoFac;

	public ExternalCommandSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		commandToBlock = getConfigStringList("command-to-block", null);
		commandToExecuteData = getConfigDataStringList("command-to-execute", null);
		commandToExecuteLaterData = getConfigDataStringList("command-to-execute-later", null);
		temporaryPermissions = getConfigStringList("temporary-permissions", null);

		commandDelay = getConfigInt("command-delay", 0);

		temporaryOp = getConfigBoolean("temporary-op", false);
		blockChatOutput = getConfigBoolean("block-chat-output", false);
		requirePlayerTarget = getConfigBoolean("require-player-target", false);
		doVariableReplacement = getConfigBoolean("do-variable-replacement", false);
		executeAsTargetInstead = getConfigBoolean("execute-as-target-instead", false);
		executeOnConsoleInstead = getConfigBoolean("execute-on-console-instead", false);
		useTargetVariablesInstead = getConfigBoolean("use-target-variables-instead", false);

		strNoTarget = getConfigString("str-no-target", "No target found.");
		strBlockedOutput = getConfigString("str-blocked-output", "");
		strCantUseCommand = getConfigString("str-cant-use-command", "&4You don't have permission to do that.");

		if (requirePlayerTarget) validTargetList = new ValidTargetList(true, false);
		
		if (blockChatOutput) {
			if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
				if (messageBlocker == null) messageBlocker = new MessageBlocker();
			} else {
				convoPrompt = new StringPrompt() {
					
					@Override
					public String getPromptText(ConversationContext context) {
						return strBlockedOutput;
					}
					
					@Override
					public Prompt acceptInput(ConversationContext context, String input) {
						return Prompt.END_OF_CONVERSATION;
					}
					
				};
				convoFac = new ConversationFactory(MagicSpells.plugin)
					.withModality(true)
					.withFirstPrompt(convoPrompt)
					.withTimeout(1);
			}
		}
	}

	@Override
	public void turnOff() {
		if (messageBlocker == null) return;
		messageBlocker.turnOff();
		messageBlocker = null;
	}

	@Override
	public CastResult cast(SpellData data) {
		Player target = null;
					if (requirePlayerTarget) {
						TargetInfo<Player> targetInfo = getTargetedPlayer(data.caster(), data.power(), data.args());
						if (targetInfo.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);
		
						target = targetInfo.target();
						data = data.power(targetInfo.power());
					}
		
					process(data.caster(), target, data.power(), data.args());
		
					if (target != null) sendMessages(data.caster(), target, data.args());
					else sendMessages(data.caster(), data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	
	private void process(CommandSender sender, Player target, float power, String[] args) {
		// Get actual sender
		CommandSender actualSender;
		if (executeAsTargetInstead) actualSender = target;
		else if (executeOnConsoleInstead) actualSender = Bukkit.getConsoleSender();
		else actualSender = sender;
		if (actualSender == null) return;
		
		SpellData data = new SpellData(actualSender instanceof Player ? (Player) actualSender : null, power, args);

		commandToExecute = commandToExecuteData.get(data);
		commandToExecuteLater = commandToExecuteLaterData.get(data);

		// Grant permissions and op
		boolean opped = false;
		if (actualSender instanceof Player) {
			if (temporaryPermissions != null) {
				for (String perm : temporaryPermissions) {
					if (actualSender.hasPermission(perm)) continue;
					actualSender.addAttachment(MagicSpells.plugin, perm.trim(), true, 5);
				}
			}
			if (temporaryOp && !actualSender.isOp()) {
				opped = true;
				actualSender.setOp(true);
			}
		}
		
		// Perform commands
		try {
			if (commandToExecute != null && !commandToExecute.isEmpty()) {

				Conversation convo = null;
				if (sender instanceof Player player) {
					if (blockChatOutput && messageBlocker != null) {
						messageBlocker.addPlayer(player);
					} else if (convoFac != null) {
						convo = convoFac.buildConversation(player);
						convo.begin();
					}
				}
				
				int delay = 0;
				LivingEntity varOwner, varTarget;
				if (useTargetVariablesInstead) {
					varOwner = target;
					varTarget = sender instanceof Player player ? player : null;
				} else {
					varOwner = sender instanceof Player player ? player : null;
					varTarget = target;
				}

				for (String comm : commandToExecute) {
					if (comm == null || comm.isEmpty()) continue;
					if (doVariableReplacement) comm = MagicSpells.doReplacements(comm, varOwner, varTarget, args);
					if (args != null && args.length > 0) {
						for (int i = 0; i < args.length; i++) {
							comm = comm.replace("%" + (i + 1), args[i]);
						}
					}
					if (sender != null) comm = comm.replace("%a", sender.getName());
					if (target != null) comm = comm.replace("%t", target.getName());
					if (comm.startsWith("DELAY ")) {
						String[] split = comm.split(" ");
						delay += Integer.parseInt(split[1]);
					} else if (delay > 0) {
						final CommandSender s = actualSender;
						final String c = comm;
						MagicSpells.scheduleDelayedTask(() -> Bukkit.dispatchCommand(s, c), delay);
					} else {
						Bukkit.dispatchCommand(actualSender, comm);
					}
				}
				if (blockChatOutput && messageBlocker != null && sender instanceof Player player) messageBlocker.removePlayer(player);
				else if (convo != null) convo.abandon();
			}
		} catch (Exception e) {
			// Catch all exceptions to make sure we don't leave someone opped
			e.printStackTrace();
		}
		
		// Deop
		if (opped) actualSender.setOp(false);
		
		// Effects
		if (sender instanceof Player player) {
			if (target != null) playSpellEffects(player, target, data);
			else playSpellEffects(EffectPosition.CASTER, player, data);
		} else if (sender instanceof BlockCommandSender commandBlock) {
			playSpellEffects(EffectPosition.CASTER, commandBlock.getBlock().getLocation(), new SpellData(null, target, power, args));
		}
		// Add delayed command
		if (commandToExecuteLater != null && !commandToExecuteLater.isEmpty() && !commandToExecuteLater.get(0).isEmpty()) {
			MagicSpells.scheduleDelayedTask(new DelayedCommand(sender, target, power, args), commandDelay);
		}
	}

	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							if (requirePlayerTarget && data.target() instanceof Player player) {
								process(data.caster(), player, data.power(), data.args());
								return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
							}
							return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		} else {
			if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							if (requirePlayerTarget && data.target() instanceof Player player) {
								process(null, player, data.power(), data.args());
								return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
							}
							return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}
	}



	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		if (!requirePlayerTarget) {
			process(sender, null, 1f, args);
			return true;
		}
		return false;
	}
	
	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		if (event.getPlayer().isOp()) return;
		if (commandToBlock == null) return;
		if (commandToBlock.isEmpty()) return;
		String msg = event.getMessage();
		for (String comm : commandToBlock) {
			comm = comm.trim();
			if (comm.isEmpty()) continue;
			if (!msg.startsWith("/" + commandToBlock)) continue;

			event.setCancelled(true);
			sendMessage(strCantUseCommand, event.getPlayer(), MagicSpells.NULL_ARGS);
			return;
		}
	}
	
	public boolean requiresPlayerTarget() {
		return requirePlayerTarget;
	}
	
	private class DelayedCommand implements Runnable {

		private final CommandSender sender;
		private final SpellData data;
		private final Player target;

		private DelayedCommand(CommandSender sender, Player target, float power, String[] args) {
			this.sender = sender;
			this.target = target;
			data = new SpellData(sender instanceof LivingEntity le ? le : null, target, power, args);
		}
		
		@Override
		public void run() {
			// Get actual sender
			CommandSender actualSender;
			if (executeAsTargetInstead) actualSender = target;
			else if (executeOnConsoleInstead) actualSender = Bukkit.getConsoleSender();
			else actualSender = sender;
			if (actualSender == null) return;
			
			// Grant permissions
			boolean opped = false;
			if (actualSender instanceof Player) {
				if (temporaryPermissions != null) {
					for (String perm : temporaryPermissions) {
						if (actualSender.hasPermission(perm)) continue;
						actualSender.addAttachment(MagicSpells.plugin, perm, true, 5);
					}
				}
				if (temporaryOp && !actualSender.isOp()) {
					opped = true;
					actualSender.setOp(true);
				}
			}
			
			// Run commands
			try {
				Conversation convo = null;
				if (sender instanceof Player player) {
					if (blockChatOutput && messageBlocker != null) {
						messageBlocker.addPlayer(player);
					} else if (convoFac != null) {
						convo = convoFac.buildConversation(player);
						convo.begin();
					}
				}

				LivingEntity varOwner, varTarget;
				if (useTargetVariablesInstead) {
					varOwner = target;
					varTarget = sender instanceof Player player ? player : null;
				} else {
					varOwner = sender instanceof Player player ? player : null;
					varTarget = target;
				}

				String[] args = data.args();

				for (String comm : commandToExecuteLater) {
					if (comm == null || comm.isEmpty()) continue;
					if (doVariableReplacement) comm = MagicSpells.doReplacements(comm, varOwner, varTarget, args);
					if (args != null && args.length > 0) {
						for (int i = 0; i < args.length; i++) {
							comm = comm.replace("%" + (i + 1), args[i]);
						}
					}
					if (sender != null) comm = comm.replace("%a", sender.getName());
					if (target != null) comm = comm.replace("%t", target.getName());
					Bukkit.dispatchCommand(actualSender, comm);
				}
				if (blockChatOutput && messageBlocker != null && sender instanceof Player player) messageBlocker.removePlayer(player);
				else if (convo != null) convo.abandon();
			} catch (Exception e) {
				// Catch exceptions to make sure we don't leave someone opped
				e.printStackTrace();
			}
			
			// Deop
			if (opped) actualSender.setOp(false);

			// Graphical effect
			if (sender == null) return;
			if (sender instanceof Player player) playSpellEffects(EffectPosition.DISABLED, player, data);
			else if (sender instanceof BlockCommandSender commandBlock) playSpellEffects(EffectPosition.DISABLED, commandBlock.getBlock().getLocation(), data);
		}
		
	}

}
