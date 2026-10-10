package com.nisovin.magicspells.util.prompt;

import java.util.Map;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;

// Bukkit Conversations API is for-removal; see COMPILE-WARNINGS.md.
@SuppressWarnings("removal")
public class ConversationContextUtil {

	public static final String CONVERSABLE_KEY = "magicspells.conversable";
	
	
	public static Conversable getConversable(ConversationContext context) {
		return getConversable(context.getAllSessionData());
	}
	
	public static Conversable getConversable(Map<Object, Object> context) {
		return (Conversable)context.get(CONVERSABLE_KEY);
	}
	
	public static void setConversable(ConversationContext context, Conversable conversable) {
		setConversable(context.getAllSessionData(), conversable);
	}
	
	public static void setConversable(Map<Object, Object> context, Conversable conversable) {
		context.put(CONVERSABLE_KEY, conversable);
	}
	
}
