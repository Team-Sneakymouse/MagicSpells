package com.nisovin.magicspells.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a magic item with an expiration timer is removed from a player's
 * possession because it has expired (scheduled purge, inventory interaction,
 * join/load scan, etc.).
 */
public class MagicItemExpireEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final Player player;
	private final ItemStack itemStack;

	public MagicItemExpireEvent(@NotNull Player player, @NotNull ItemStack itemStack) {
		this.player = player;
		this.itemStack = itemStack;
	}

	@NotNull
	public Player getPlayer() {
		return player;
	}

	@NotNull
	public ItemStack getItemStack() {
		return itemStack;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
