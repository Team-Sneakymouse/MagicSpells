package com.nisovin.magicspells.spells;

import java.util.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Perm;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItemBehaviors;
import com.nisovin.magicspells.util.magicitems.MagicItemExpirationScheduler;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class MagicItemMenuSpell extends TargetedSpell implements TargetedEntitySpell {

	private Map<UUID, ItemMenuData> itemMenuData;

	private final int delay;
	private final String title;
	private final boolean stayOpen;

	private final ItemStack backItem;
	private final ItemStack previousPageItem;
	private final ItemStack nextPageItem;

	public MagicItemMenuSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		delay = getConfigInt("delay", 0);
		title = getConfigString("title", "ItemMenuSpell '" + internalName + "'");
		stayOpen = getConfigBoolean("stay-open", false);

		backItem = createItem("back-item", "Back", Material.RED_WOOL);
		previousPageItem = createItem("previous-page-item", "Previous Page", Material.GREEN_WOOL);
		nextPageItem = createItem("next-page-item", "Next Page", Material.GREEN_WOOL);
	}

	@Override
	public void initialize() {
		super.initialize();

		itemMenuData = new HashMap<>();
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (targetSelf) {
						openDelay(player, player, data.power(), data.args());
						return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
					}
		
					TargetInfo<Player> targetInfo = getTargetedPlayer(player, data.power(), data.args());
					if (targetInfo.noTarget()) return new CastResult(noTarget(player, data.args(), targetInfo), data);
					openDelay(player, targetInfo.target(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							openDelay(data.caster(), player, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							openDelay(null, player, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		if (args.length < 1) return false;
		Player player = Bukkit.getPlayer(args[0]);
		if (player == null) return false;
		openDelay(null, player, 1, null);
		return true;
	}

	private ItemStack createItem(String path, String defaultName, Material defaultMaterial) {
		MagicItem magicItem = MagicItems.getMagicItemFromString(getConfigString(path, null));
		if (magicItem != null) return magicItem.getItemStack().clone();

		ItemStack item = new ItemStack(defaultMaterial);
		ItemMeta meta = item.getItemMeta();

		meta.displayName(Component.text(defaultName).color(NamedTextColor.GOLD));
		item.setItemMeta(meta);

		return item;
	}

	private void openDelay(Entity caster, Player opener, float power, String[] args) {
		ItemMenuData data = new ItemMenuData(new SpellData(caster, opener, power, args), "", 0, 0);
		itemMenuData.put(opener.getUniqueId(), data);

		if (delay > 0) MagicSpells.scheduleDelayedTask(() -> open(opener, data), delay);
		else open(opener, data);
	}

	private void open(Player opener, ItemMenuData data) {
		SpellData spellData = data.spellData();
		String category = data.category();
		int page = data.page();

		List<ItemStack> entries = new ArrayList<>();
		List<String> categoryNames = new ArrayList<>();
		List<MagicItem> items = new ArrayList<>();

		for (String magicItemName : MagicItems.getMagicItems().keySet()) {
			MagicItem magicItem = MagicItems.getMagicItems().get(magicItemName);
			String lcase = new String(magicItemName).toLowerCase();
			if (lcase.startsWith(category + "-") || category.equals("")) {
				String[] split = null;
				if (category.equals("")) {
					split = lcase.split("-");
				} else {
					split = lcase.substring(category.length() + 1).split("-");
				}

				if (split.length == 1) {
					items.add(magicItem);
				} else {
					if (!categoryNames.contains(split[0])) {
						categoryNames.add(split[0]);

						ItemStack item = new ItemStack(magicItem.getItemStack());
						ItemMeta meta = item.getItemMeta();

						meta.displayName(Component.text(split[0]).color(NamedTextColor.GOLD));
						meta.lore(List.of(Component.text((category.equals("") ? "" : category + "-") + split[0])));
						meta.addItemFlags(ItemFlag.values());
						item.setItemMeta(meta);

						entries.add(item);
					}
				}
			}
		}

		for (int i = 0; i < Math.min((9 - categoryNames.size()%9), (50 - entries.size()%50)); i++) {
			entries.add(new ItemStack(Material.AIR));
		}

		for (MagicItem magicItem : items) {
			entries.add(magicItem.getItemStack());
		}

		Inventory inv = Bukkit.createInventory(opener, 54, Component.text(internalName));

		for (int i = (page * 50); i < Math.min(entries.size(), (page + 1) * 50); i++) {
			inv.setItem(i%50, entries.get(i));
		}

		if (!category.equals("")) {
			inv.setItem(51, backItem);
		}

		if (page > 0) {
			inv.setItem(52, previousPageItem);
		}

		if (entries.size() > ((page + 1) * 50)) {
			inv.setItem(53, nextPageItem);
		}

		opener.openInventory(inv);
		// Root opens with Component title from createInventory. An empty NMS title packet
		// (category "") clobbers that open and can leave the client with no usable menu.
		if (!category.isEmpty()) Util.setInventoryTitle(opener, category);

		if (spellData.caster() != null) playSpellEffects(spellData.caster(), spellData.target(), spellData);
		else playSpellEffects(EffectPosition.TARGET, spellData.target(), spellData);

		ItemMenuData newItemMenuData = new ItemMenuData(spellData, category, categoryNames.size()%50, page);
		itemMenuData.put(opener.getUniqueId(), newItemMenuData);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		itemMenuData.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onItemClick(InventoryClickEvent event) {
		if (!Util.getStringFromComponent(event.getView().title()).equals(internalName)) return;
		Player player = (Player) event.getWhoClicked();
		event.setCancelled(true);
		ItemStack item = event.getCurrentItem();
		if (item == null) return;
		ItemMeta itemMeta = item.getItemMeta();

		ItemMenuData data = itemMenuData.get(player.getUniqueId());
		String category = data.category();
		SpellData spellData = data.spellData();
		int subCategoryAmount = data.subCategoryAmount();
		int page = data.page();

		if (event.getRawSlot() > subCategoryAmount && event.getRawSlot() < 50) {
			ItemStack give = item.clone();
			MagicItemBehaviors.applyMissingFromRegistry(give, player);
			MagicItemExpirationScheduler.scheduleFromItem(player, give);
			player.getInventory().addItem(give);
		} else {
			ItemMenuData newItemMenuData = null;

			if (event.getRawSlot() == 51) {
				String upperCategory = "";
				if (category.contains("-")) {
					upperCategory = category.substring(0, category.lastIndexOf("-"));
				}
				newItemMenuData = new ItemMenuData(spellData, upperCategory, 0, 0);
			} else if (event.getRawSlot() == 52) {
				newItemMenuData = new ItemMenuData(spellData, category, 0, page - 1);
			} else if (event.getRawSlot() == 53) {
				newItemMenuData = new ItemMenuData(spellData, category, 0, page + 1);
			} else {
				List<Component> lore = itemMeta.lore();
				if (lore == null || lore.isEmpty()) return;
				// Plain text — category paths must not pick up MiniMessage serialization noise.
				String path = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
						.serialize(lore.get(0));
				if (path.isEmpty()) return;
				newItemMenuData = new ItemMenuData(spellData, path, 0, 0);
			}
			itemMenuData.put(player.getUniqueId(), newItemMenuData);
			open(player, newItemMenuData);
		}
	}

	public record ItemMenuData(SpellData spellData, String category, int subCategoryAmount, int page) {

	}
}
