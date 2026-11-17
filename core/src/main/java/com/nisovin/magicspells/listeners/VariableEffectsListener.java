package com.nisovin.magicspells.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.coreprotect.CoreProtect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Objective;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.managers.VariableManager;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.CharacterStringVariable;
import com.nisovin.magicspells.variables.variabletypes.CharacterVariable;
import com.nisovin.magicspells.variables.variabletypes.PlayerStringVariable;
import com.nisovin.magicspells.variables.variabletypes.PlayerVariable;

import com.sneakyvariables.api.ChangeCause;
import com.sneakyvariables.api.Kind;
import com.sneakyvariables.api.OwnerRef;
import com.sneakyvariables.api.Scope;
import com.sneakyvariables.api.SneakyVariables;
import com.sneakyvariables.api.VariableChangeEvent;
import com.sneakyvariables.api.VariableOwner;

/**
 * Applies MagicSpells side effects (boss/exp bars, scoreboard, CoreProtect) when
 * SneakyVariables reports a value change on this server.
 */
public class VariableEffectsListener implements Listener {

	private static final double EPSILON = 1e-10;

	@EventHandler
	public void onVariableChange(VariableChangeEvent event) {
		VariableManager manager = MagicSpells.getVariableManager();
		if (manager == null) return;

		Variable variable = manager.getVariable(event.getName());
		if (variable == null) return;

		if (event.getOwner().scope() == Scope.GLOBAL) {
			manager.updateBossBar(variable, "-");
			manager.updateExpBar(variable, "-");
		} else {
			for (Player player : affectedPlayers(event.getOwner())) {
				String playerName = player.getName();
				manager.updateBossBar(variable, playerName);
				manager.updateExpBar(variable, playerName);
				updateObjective(variable, playerName, event);
			}
		}

		if (event.getCause() == ChangeCause.API && variable.isLogInCoreprotect())
			logCoreProtect(variable, event);
	}

	private Iterable<? extends Player> affectedPlayers(OwnerRef owner) {
		return switch (owner.scope()) {
			case GLOBAL -> Bukkit.getOnlinePlayers();
			case PLAYER -> {
				try {
					Player player = Bukkit.getPlayer(UUID.fromString(owner.id()));
					yield player != null ? List.of(player) : List.of();
				} catch (IllegalArgumentException e) {
					yield List.of();
				}
			}
			case CHARACTER -> {
				List<Player> matches = new ArrayList<>();
				for (Player player : Bukkit.getOnlinePlayers()) {
					VariableOwner character = SneakyVariables.api().character(player);
					if (character != null && owner.id().equals(character.ref().id()))
						matches.add(player);
				}
				yield matches;
			}
		};
	}

	private void updateObjective(Variable variable, String playerName, VariableChangeEvent event) {
		Objective objective = variable.getObjective();
		if (objective == null) return;
		if (!(variable instanceof PlayerVariable || variable instanceof CharacterVariable
				|| variable instanceof PlayerStringVariable || variable instanceof CharacterStringVariable))
			return;

		Object newValue = event.getNewValue();
		int score;
		if (newValue instanceof Number number) score = number.intValue();
		else {
			try {
				score = (int) Double.parseDouble(String.valueOf(newValue));
			} catch (NumberFormatException e) {
				score = 0;
			}
		}
		objective.getScore(playerName).setScore(score);
	}

	private void logCoreProtect(Variable variable, VariableChangeEvent event) {
		if (!Bukkit.getPluginManager().isPluginEnabled("CoreProtect")) return;

		Player actor = null;
		if (event.getOwner().scope() == Scope.PLAYER) {
			try {
				actor = Bukkit.getPlayer(UUID.fromString(event.getOwner().id()));
			} catch (IllegalArgumentException ignored) {
			}
		} else if (event.getOwner().scope() == Scope.CHARACTER) {
			for (Player player : affectedPlayers(event.getOwner())) {
				actor = player;
				break;
			}
		}

		Kind kind = event.getKind();
		if (kind.isNumber()) {
			double oldValue = toDouble(event.getOldValue());
			double newValue = toDouble(event.getNewValue());
			double change = newValue - oldValue;
			if (Math.abs(change) <= EPSILON) return;
			CoreProtect.getInstance().getAPI().logCommand(actor,
					"/ms_var " + event.getName() + ((change < 0D) ? " " : " +") + change);
			return;
		}

		String newValue = String.valueOf(event.getNewValue());
		String oldValue = String.valueOf(event.getOldValue());
		if (oldValue.equals(newValue)) return;
		CoreProtect.getInstance().getAPI().logCommand(actor, "/ms_var " + event.getName() + " =" + newValue);
	}

	private static double toDouble(Object value) {
		if (value instanceof Number number) return number.doubleValue();
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

}
