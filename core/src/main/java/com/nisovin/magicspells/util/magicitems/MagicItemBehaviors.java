package com.nisovin.magicspells.util.magicitems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import com.nisovin.magicspells.events.MagicItemExpireEvent;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute;

public final class MagicItemBehaviors {

	private MagicItemBehaviors() {
	}

	public static void applySoulbound(ItemStack item, Player owner) {
		if (owner == null)
			return;
		applySoulbound(item, owner.getUniqueId());
	}

	public static void applySoulbound(ItemStack item, UUID ownerUuid) {
		if (item == null || ownerUuid == null || !item.hasItemMeta())
			return;
		if (hasSoulbound(item))
			return;

		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(
				MagicItemBehaviorKeys.soulboundOwner(),
				PersistentDataType.STRING,
				ownerUuid.toString());
		item.setItemMeta(meta);
	}

	public static void applySoulboundOverlay(ItemStack item, Player owner) {
		if (owner == null)
			return;
		applySoulboundOverlay(item, owner.getUniqueId());
	}

	public static void applySoulboundOverlay(ItemStack item, UUID ownerUuid) {
		if (item == null || ownerUuid == null || !item.hasItemMeta())
			return;

		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(
				MagicItemBehaviorKeys.soulboundOwner(),
				PersistentDataType.STRING,
				ownerUuid.toString());
		item.setItemMeta(meta);
	}

	public static void applyExpiration(ItemStack item, double durationMs) {
		if (item == null || durationMs <= 0 || !item.hasItemMeta())
			return;
		if (hasExpiration(item))
			return;

		addExpiresLine(item, durationMs);
	}

	public static void applyExpirationOverlay(ItemStack item, double durationMs) {
		if (item == null || durationMs <= 0 || !item.hasItemMeta())
			return;

		addExpiresLine(item, durationMs);
	}

	public static void applyFromData(ItemStack item, @Nullable MagicItemData data, @Nullable Player owner) {
		applyFromData(item, data, (LivingEntity) owner);
	}

	public static void applyFromData(ItemStack item, @Nullable MagicItemData data, @Nullable LivingEntity owner) {
		if (item == null || data == null)
			return;

		if (data.hasAttribute(MagicItemAttribute.SOULBOUND)
				&& Boolean.TRUE.equals(data.getAttribute(MagicItemAttribute.SOULBOUND))
				&& owner != null) {
			applySoulbound(item, owner.getUniqueId());
		}

		if (data.hasAttribute(MagicItemAttribute.EXPIRATION)) {
			double durationMs = (Double) data.getAttribute(MagicItemAttribute.EXPIRATION);
			if (durationMs > 0) {
				applyExpiration(item, durationMs);
				if (owner instanceof Player player)
					MagicItemExpirationScheduler.scheduleFromItem(player, item);
			}
		}
	}

	public static void applyConjureOverlay(ItemStack item, boolean soulbound, double expirationMs,
			@Nullable LivingEntity owner) {
		if (item == null)
			return;

		if (soulbound && owner != null)
			applySoulboundOverlay(item, owner.getUniqueId());
		if (expirationMs > 0) {
			applyExpirationOverlay(item, expirationMs);
			if (owner instanceof Player player)
				MagicItemExpirationScheduler.scheduleFromItem(player, item);
		}
	}

	public static void applyMissingFromRegistry(ItemStack item, @Nullable Player owner) {
		if (item == null || owner == null || !item.hasItemMeta())
			return;

		String internalName = item.getItemMeta().getPersistentDataContainer()
				.get(MagicItemBehaviorKeys.magicItem(), PersistentDataType.STRING);
		if (internalName == null)
			return;

		MagicItem magicItem = MagicItems.getMagicItemByInternalName(internalName);
		if (magicItem == null)
			return;

		applyFromData(item, magicItem.getMagicItemData(), owner);
		MagicItemExpirationScheduler.scheduleFromItem(owner, item);
	}

	public static boolean hasSoulbound(ItemStack item) {
		if (item == null || !item.hasItemMeta())
			return false;
		return item.getItemMeta().getPersistentDataContainer()
				.has(MagicItemBehaviorKeys.soulboundOwner(), PersistentDataType.STRING);
	}

	public static boolean hasExpiration(ItemStack item) {
		if (item == null || !item.hasItemMeta())
			return false;
		return item.getItemMeta().getPersistentDataContainer()
				.has(MagicItemBehaviorKeys.expiresAt(), PersistentDataType.LONG);
	}

	public static String getExpiresText(long expiresAt) {
		if (expiresAt < System.currentTimeMillis())
			return ChatColor.GRAY + "Expired";
		double hours = (expiresAt - System.currentTimeMillis()) / ((double) TimeUtil.MILLISECONDS_PER_HOUR);
		if (hours / 24 >= 15)
			return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + ((long) hours / TimeUtil.HOURS_PER_WEEK)
					+ ChatColor.GRAY + " weeks";
		if (hours / 24 >= 3)
			return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + ((long) hours / TimeUtil.HOURS_PER_DAY)
					+ ChatColor.GRAY + " days";
		if (hours >= 2)
			return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + (long) hours + ChatColor.GRAY + " hours";
		if (hours >= 1)
			return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + '1' + ChatColor.GRAY + " hour";
		double minutes = (expiresAt - System.currentTimeMillis()) / ((double) TimeUtil.MILLISECONDS_PER_MINUTE);
		if (minutes >= 2)
			return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + (long) minutes + ChatColor.GRAY + " minutes";
		if (minutes >= 1)
			return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + '1' + ChatColor.GRAY + " minute";
		double seconds = (expiresAt - System.currentTimeMillis()) / ((double) TimeUtil.MILLISECONDS_PER_SECOND);
		if (seconds >= 2)
			return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + (long) seconds + ChatColor.GRAY + " seconds";
		return ChatColor.GRAY + "Expires in " + ChatColor.WHITE + '1' + ChatColor.GRAY + " second";
	}

	public enum ExpirationResult {

		NO_UPDATE,
		UPDATE,
		EXPIRED

	}

	public static ExpirationResult updateExpiresLineIfNeeded(ItemStack item) {
		return updateExpiresLineIfNeeded(item, null);
	}

	public static ExpirationResult updateExpiresLineIfNeeded(ItemStack item, @Nullable Player owner) {
		if (item == null || !item.hasItemMeta())
			return ExpirationResult.NO_UPDATE;

		ItemMeta meta = item.getItemMeta();
		Long expiresAt = meta.getPersistentDataContainer()
				.get(MagicItemBehaviorKeys.expiresAt(), PersistentDataType.LONG);
		if (expiresAt == null)
			return ExpirationResult.NO_UPDATE;

		if (expiresAt < System.currentTimeMillis()) {
			if (owner != null)
				Bukkit.getPluginManager().callEvent(new MagicItemExpireEvent(owner, item));
			return ExpirationResult.EXPIRED;
		}

		List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
		if (lore == null)
			lore = new ArrayList<>();

		ExpirationResult result;
		if (!lore.isEmpty()) {
			Component lastLine = lore.get(lore.size() - 1);
			if (lastLine instanceof TextComponent textComponent
					&& textComponent.content().contains("Expires in ")) {
				lore.set(lore.size() - 1, Util.getMiniMessage(getExpiresText(expiresAt)));
				meta.lore(lore);
				item.setItemMeta(meta);
				result = ExpirationResult.UPDATE;
			} else {
				lore.add(Util.getMiniMessage(getExpiresText(expiresAt)));
				meta.lore(lore);
				item.setItemMeta(meta);
				result = ExpirationResult.UPDATE;
			}
		} else {
			lore.add(Util.getMiniMessage(getExpiresText(expiresAt)));
			meta.lore(lore);
			item.setItemMeta(meta);
			result = ExpirationResult.UPDATE;
		}

		if (owner != null)
			MagicItemExpirationScheduler.scheduleFromItem(owner, item);
		return result;
	}

	private static void addExpiresLine(ItemStack item, double expireMilis) {
		ItemMeta meta = item.getItemMeta();
		List<Component> lore = null;
		if (meta.hasLore())
			lore = meta.lore();
		if (lore == null)
			lore = new ArrayList<>();

		long expiresAt = System.currentTimeMillis() + (long) expireMilis;
		lore.add(Util.getMiniMessage(getExpiresText(expiresAt)));
		meta.getPersistentDataContainer().set(MagicItemBehaviorKeys.expiresAt(), PersistentDataType.LONG, expiresAt);
		meta.lore(lore);
		item.setItemMeta(meta);
	}

}
