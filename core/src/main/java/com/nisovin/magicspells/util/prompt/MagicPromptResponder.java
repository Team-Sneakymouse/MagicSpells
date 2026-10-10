package com.nisovin.magicspells.util.prompt;

import org.bukkit.entity.Player;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.apache.commons.codec.language.bm.Rule;
import org.bukkit.configuration.ConfigurationSection;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Modules.ChatFilter.RuleResponse;
import com.nisovin.magicspells.Subspell;

import com.nisovin.magicspells.MagicSpells;

// Bukkit Conversations API is for-removal; see COMPILE-WARNINGS.md.
@SuppressWarnings("removal")
public class MagicPromptResponder {

	String variableName;
	Subspell spellOnEnd;
	
	public MagicPromptResponder(ConfigurationSection section) {
		variableName = section.getString("variable-name", null);
		String spellName = section.getString("spell-on-end", null);
		if (spellName != null && !spellName.isEmpty()) {
			spellOnEnd = new Subspell(spellName);
			if (!spellOnEnd.process()) {
				spellOnEnd = null;
				MagicSpells.error("Invalid spell-on-end defined in prompt: " + spellName);
			}
		}
	}
	
	public Prompt acceptValidatedInput(ConversationContext paramConversationContext, String paramString) {
		String playerName = null;
		Conversable who = ConversationContextUtil.getConversable(paramConversationContext.getAllSessionData());
		if (who instanceof Player player) {
			playerName = player.getName();
			
			// Apply CMI chat filter
			RuleResponse filter = CMI.getInstance().getChatFilterManager().getCorrectMessage(player, paramString);
			String filteredText = filter.getUpdatedMessage();
			
			// Execute filter commands if triggered
			filter.getRules().values().forEach(rule -> {
				if (Rule.ALL_STRINGS_RMATCHER.isMatch(paramString)) {
					filter.performCommands(player);
				}
			});
			
			// Cast spell if configured
			if (spellOnEnd != null) {
				spellOnEnd.cast(player, 1.0F);
			}
			
			// Try to save response to a variable, using filtered text
			MagicSpells.getVariableManager().set(variableName, playerName, filteredText);
			
			return Prompt.END_OF_CONVERSATION;
		}

		// Try to save response to a variable.
		MagicSpells.getVariableManager().set(variableName, playerName, paramString);

		return Prompt.END_OF_CONVERSATION;
	}
	
}
