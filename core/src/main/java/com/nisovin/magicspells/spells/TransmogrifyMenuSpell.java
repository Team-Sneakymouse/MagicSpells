package com.nisovin.magicspells.spells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.DataUtil;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItemIgnoredAttributes;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.reagent.SpellReagents;

import static com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute.ITEM_MODEL;
import static com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute.MAGIC_ITEM_NAME;

/**
 * Opens a GUI of item-model options for the magic item in the caster's main hand.
 * Applies {@code minecraft:item_model} on click after verifying the held item is unchanged.
 */
public class TransmogrifyMenuSpell extends InstantSpell {

	private static final String OPTION_TAG = "transmogrifyOption";
	private static final String REVERT_OPTION_ID = "revert";
	private static final int PAGE_SIZE = 52;

	private final Map<UUID, MenuSession> sessions = new HashMap<>();
	private final Map<String, ModelOption> options = new LinkedHashMap<>();

	private final String title;
	private final int delay;
	private final int minRows;
	private final boolean stayOpen;
	private final ItemStack filler;
	private final ItemStack previousPageItem;
	private final ItemStack nextPageItem;
	private final ConfigData<List<String>> modelCost;
	private final String strNotMagicItem;
	private final String strItemChanged;

	public TransmogrifyMenuSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		title = getConfigString("title", "Transmogrify " + spellName);
		delay = getConfigInt("delay", 1);
		minRows = Math.max(1, Math.min(6, getConfigInt("min-rows", 1)));
		stayOpen = getConfigBoolean("stay-open", false);
		filler = createItem("filler", null);
		previousPageItem = createItem("previous-page-item", "Previous Page");
		nextPageItem = createItem("next-page-item", "Next Page");
		modelCost = getConfigDataStringList("model-cost", null);
		strNotMagicItem = getConfigString("str-not-magic-item", "Hold a magic item in your main hand.");
		strItemChanged = getConfigString("str-item-changed", "Your held item changed — transmogrify cancelled.");

		// Built-in revert option first
		ModelOption revert = new ModelOption();
		revert.id = REVERT_OPTION_ID;
		revert.revert = true;
		revert.displayName = readOptionName("revert", "&7Default look");
		revert.lore = getConfigStringList("revert.lore", null);
		if (configKeyExists("revert.cost")) {
			revert.hasOwnCost = true;
			revert.cost = getConfigDataStringList("revert.cost", null);
		}
		options.put(REVERT_OPTION_ID, revert);

		Set<String> modelKeys = getConfigKeys("models");
		if (modelKeys == null || modelKeys.isEmpty()) {
			MagicSpells.error("TransmogrifyMenuSpell '" + spellName + "' has no models defined!");
		} else {
			for (String key : modelKeys) {
				String path = "models." + key + ".";
				String modelString = getConfigString(path + "item-model", null);
				if (modelString == null || modelString.isBlank()) {
					MagicSpells.error("TransmogrifyMenuSpell '" + internalName
							+ "' has no item-model defined for model: " + key);
					continue;
				}
				NamespacedKey modelKey = NamespacedKey.fromString(modelString.trim());
				if (modelKey == null) {
					MagicSpells.error("TransmogrifyMenuSpell '" + internalName
							+ "' has invalid item-model '" + modelString + "' for model: " + key);
					continue;
				}

				ModelOption option = new ModelOption();
				option.id = key;
				option.itemModel = modelKey;
				option.displayName = readOptionName("models." + key, null);
				option.lore = getConfigStringList(path + "lore", null);
				if (configKeyExists(path + "cost")) {
					option.hasOwnCost = true;
					option.cost = getConfigDataStringList(path + "cost", null);
				}
				options.put(key, option);
			}
		}

		if (options.size() <= 1) {
			MagicSpells.error("TransmogrifyMenuSpell '" + spellName + "' has no valid model options!");
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player)) {
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		ItemStack held = player.getInventory().getItemInMainHand();
		String magicItemName = getMagicItemName(held);
		if (magicItemName == null) {
			sendMessage(strNotMagicItem, player, data.args());
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}

		MenuSession session = new MenuSession(
				player.getInventory().getHeldItemSlot(),
				held.clone(),
				magicItemName,
				data.power(),
				data.args(),
				0,
				Map.of()
		);
		sessions.put(player.getUniqueId(), session);

		if (delay > 0) MagicSpells.scheduleDelayedTask(() -> openMenu(player), delay);
		else openMenu(player);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	private void openMenu(Player opener) {
		if (!opener.isOnline()) return;
		MenuSession current = sessions.get(opener.getUniqueId());
		if (current == null) return;

		List<ModelOption> optionList = new ArrayList<>(options.values());
		int total = optionList.size();
		int page = Math.max(0, current.page);
		if (page * PAGE_SIZE >= total && page > 0) {
			page = Math.max(0, (total - 1) / PAGE_SIZE);
			current = new MenuSession(current.heldSlot, current.snapshot, current.magicItemName,
					current.power, current.args, page, Map.of());
			sessions.put(opener.getUniqueId(), current);
		}

		int size;
		if (total > PAGE_SIZE) size = 54;
		else {
			int rows = Math.max(minRows, (int) Math.ceil(total / 9.0));
			size = Math.min(54, Math.max(9, rows * 9));
		}

		TransmogrifyMenuHolder holder = new TransmogrifyMenuHolder(internalName);
		Inventory inv = Bukkit.createInventory(holder, size, Component.text(internalName));
		holder.setInventory(inv);

		Map<Integer, String> slotOptions = new HashMap<>();
		int start = page * PAGE_SIZE;
		int end = Math.min(total, start + PAGE_SIZE);
		for (int i = start; i < end; i++) {
			ModelOption option = optionList.get(i);
			int slot = i % PAGE_SIZE;
			ItemStack preview = buildPreview(current.snapshot, option, opener, current.args);
			inv.setItem(slot, preview);
			slotOptions.put(slot, option.id);
		}

		if (filler != null) {
			ItemStack fillerClone = filler.clone();
			for (int i = 0; i < size; i++) {
				if (total > PAGE_SIZE && (i == 52 || i == 53)) continue;
				if (inv.getItem(i) == null) inv.setItem(i, fillerClone.clone());
			}
		}

		if (page > 0) inv.setItem(52, previousPageItem);
		if (total > (page + 1) * PAGE_SIZE) inv.setItem(53, nextPageItem);

		sessions.put(opener.getUniqueId(), new MenuSession(current.heldSlot, current.snapshot, current.magicItemName,
				current.power, current.args, page, slotOptions));

		opener.openInventory(inv);
		Util.setInventoryTitle(opener, title);
		playSpellEffects(EffectPosition.SPECIAL, opener, new SpellData(opener, current.power, current.args));
	}

	private ItemStack buildPreview(ItemStack snapshot, ModelOption option, Player opener, String[] args) {
		ItemStack preview = snapshot.clone();
		preview.setAmount(1);
		ItemMeta meta = preview.getItemMeta();
		if (meta == null) {
			DataUtil.setString(preview, OPTION_TAG, option.id);
			return preview;
		}

		if (option.revert) applyRevertModel(meta, getMagicItemName(snapshot));
		else if (option.itemModel != null) meta.setItemModel(option.itemModel);
		preview.setItemMeta(meta);

		meta = preview.getItemMeta();
		if (meta != null) {
			applyPreviewDisplay(meta, option, opener, args);
			if (option.lore != null && !option.lore.isEmpty()) {
				List<Component> lore = new ArrayList<>();
				for (String line : option.lore) lore.add(translate(opener, line, args));
				meta.lore(lore);
			}
			preview.setItemMeta(meta);
		}

		DataUtil.setString(preview, OPTION_TAG, option.id);

		meta = preview.getItemMeta();
		if (meta != null) {
			applyPreviewDisplay(meta, option, opener, args);
			preview.setItemMeta(meta);
		}

		return preview;
	}

	private void applyPreviewDisplay(ItemMeta meta, ModelOption option, Player opener, String[] args) {
		Component name = null;
		if (option.displayName != null && !option.displayName.isEmpty()) {
			name = translate(opener, option.displayName, args);
		} else if (option.revert) {
			name = translate(opener, "&7Default look", args);
		}
		if (name == null) return;

		meta.customName(name);
		meta.itemName(name);
	}

	/**
	 * Reads a display name from config when the key exists; otherwise returns the default.
	 * A configured but empty string means no override (keep the held item name).
	 */
	private String readOptionName(String path, String defaultValue) {
		if (!configKeyExists(path + ".name")) return defaultValue;
		return getConfigString(path + ".name", defaultValue != null ? defaultValue : "");
	}

	private Component translate(Player player, String text, String[] args) {
		return Util.getMiniMessage(MagicSpells.doReplacements(text, player, args));
	}

	@EventHandler
	public void onInvClick(InventoryClickEvent event) {
		if (!(event.getView().getTopInventory().getHolder() instanceof TransmogrifyMenuHolder holder)) return;
		if (!holder.spellName.equals(internalName)) return;
		if (!(event.getWhoClicked() instanceof Player player)) return;

		if (event.getClickedInventory() != event.getView().getTopInventory()) {
			if (event.isShiftClick()) event.setCancelled(true);
			return;
		}

		event.setCancelled(true);

		MenuSession session = sessions.get(player.getUniqueId());
		if (session == null) {
			player.closeInventory();
			return;
		}

		int slot = event.getRawSlot();
		List<ModelOption> optionList = new ArrayList<>(options.values());
		int total = optionList.size();
		if (slot == 52 && session.page > 0) {
			sessions.put(player.getUniqueId(), new MenuSession(session.heldSlot, session.snapshot, session.magicItemName,
					session.power, session.args, session.page - 1, Map.of()));
			openMenu(player);
			return;
		}
		if (slot == 53 && total > (session.page + 1) * PAGE_SIZE) {
			sessions.put(player.getUniqueId(), new MenuSession(session.heldSlot, session.snapshot, session.magicItemName,
					session.power, session.args, session.page + 1, Map.of()));
			openMenu(player);
			return;
		}

		String optionId = session.slotOptions.get(slot);
		if (optionId == null || optionId.isEmpty()) {
			ItemStack clicked = event.getCurrentItem();
			if (clicked != null && !clicked.getType().isAir()) optionId = DataUtil.getString(clicked, OPTION_TAG);
		}
		if (optionId == null || optionId.isEmpty()) return;

		ModelOption option = options.get(optionId);
		if (option == null) return;

		ItemStack current = player.getInventory().getItem(session.heldSlot);
		if (!isSameHeldItem(session, current)) {
			sendMessage(strItemChanged, player, session.args);
			return;
		}

		SpellData spellData = new SpellData(player, session.power, session.args);
		SpellReagents reagents = resolveCost(option, player, spellData);
		if (reagents != null && !reagents.hasAll(player)) {
			sendMessage(strMissingReagents, player, session.args);
			return;
		}

		if (reagents != null) removeReagents(player, reagents);

		ItemStack updatedItem = current.clone();
		ItemMeta meta = updatedItem.getItemMeta();
		if (meta == null) return;

		if (option.revert) applyRevertModel(meta, session.magicItemName);
		else meta.setItemModel(option.itemModel);
		updatedItem.setItemMeta(meta);

		if (option.revert) {
			DataUtil.remove(updatedItem, "transmogrified");
			MagicItemIgnoredAttributes.remove(updatedItem, ITEM_MODEL);
		} else {
			DataUtil.setString(updatedItem, "transmogrified", option.itemModel.asString());
			MagicItemIgnoredAttributes.add(updatedItem, ITEM_MODEL);
		}

		player.getInventory().setItem(session.heldSlot, updatedItem);

		playSpellEffects(EffectPosition.CASTER, player, spellData);

		if (stayOpen) {
			sessions.put(player.getUniqueId(), new MenuSession(session.heldSlot, updatedItem.clone(), session.magicItemName,
					session.power, session.args, session.page, Map.of()));
			openMenu(player);
		} else {
			sessions.remove(player.getUniqueId());
			MagicSpells.scheduleDelayedTask(player::closeInventory, 0);
		}
	}

	@EventHandler
	public void onInvDrag(InventoryDragEvent event) {
		if (!(event.getView().getTopInventory().getHolder() instanceof TransmogrifyMenuHolder holder)) return;
		if (!holder.spellName.equals(internalName)) return;
		event.setCancelled(true);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		sessions.remove(event.getPlayer().getUniqueId());
	}

	private SpellReagents resolveCost(ModelOption option, Player player, SpellData spellData) {
		List<String> costList;
		if (option.hasOwnCost) {
			costList = option.cost != null ? option.cost.get(spellData) : null;
			if (costList == null) costList = List.of();
		} else {
			costList = modelCost != null ? modelCost.get(spellData) : null;
			if (costList == null || costList.isEmpty()) return null;
		}
		if (costList.isEmpty()) return new SpellReagents();
		return SpellReagents.fromList(costList, internalName, player, consumeFromBagOfHolding);
	}

	private boolean isSameHeldItem(MenuSession session, ItemStack current) {
		if (current == null || current.getType().isAir()) return false;
		String name = getMagicItemName(current);
		if (name == null || !name.equals(session.magicItemName)) return false;
		return Util.isSimilarNoFlags(stripItemModel(session.snapshot), stripItemModel(current));
	}

	private static ItemStack stripItemModel(ItemStack item) {
		ItemStack clone = item.clone();
		ItemMeta meta = clone.getItemMeta();
		if (meta != null) {
			meta.setItemModel(null);
			clone.setItemMeta(meta);
		}
		return clone;
	}

	private void applyRevertModel(ItemMeta meta, String magicItemName) {
		MagicItem definition = MagicItems.getMagicItemByInternalName(magicItemName);
		if (definition != null && definition.getMagicItemData() != null
				&& definition.getMagicItemData().hasAttribute(ITEM_MODEL)) {
			meta.setItemModel((NamespacedKey) definition.getMagicItemData().getAttribute(ITEM_MODEL));
		} else {
			meta.setItemModel(null);
		}
	}

	private static String getMagicItemName(ItemStack item) {
		if (item == null || item.getType().isAir()) return null;
		MagicItemData data = MagicItems.getMagicItemDataFromItemStack(item);
		if (data == null || !data.hasAttribute(MAGIC_ITEM_NAME)) return null;
		return (String) data.getAttribute(MAGIC_ITEM_NAME);
	}

	private ItemStack createItem(String path, String defaultName) {
		if (isConfigSection(path)) {
			MagicItem magicItem = MagicItems.getMagicItemFromSection(getConfigSection(path));
			if (magicItem != null && magicItem.getItemStack() != null) return magicItem.getItemStack().clone();
		} else {
			MagicItem magicItem = MagicItems.getMagicItemFromString(getConfigString(path, ""));
			if (magicItem != null && magicItem.getItemStack() != null) return magicItem.getItemStack().clone();
		}

		if (defaultName == null) return null;

		ItemStack item = new ItemStack(Material.GREEN_WOOL);
		ItemMeta meta = item.getItemMeta();
		meta.displayName(Component.text(defaultName).color(NamedTextColor.GOLD)
				.decoration(TextDecoration.ITALIC, false));
		item.setItemMeta(meta);
		return item;
	}

	private record MenuSession(int heldSlot, ItemStack snapshot, String magicItemName, float power, String[] args,
			int page, Map<Integer, String> slotOptions) {
	}

	private static final class TransmogrifyMenuHolder implements InventoryHolder {

		private final String spellName;
		private Inventory inventory;

		private TransmogrifyMenuHolder(String spellName) {
			this.spellName = spellName;
		}

		@Override
		public Inventory getInventory() {
			return inventory;
		}

		private void setInventory(Inventory inventory) {
			this.inventory = inventory;
		}

	}

	private static class ModelOption {
		private String id;
		private boolean revert;
		private NamespacedKey itemModel;
		private String displayName;
		private List<String> lore;
		private boolean hasOwnCost;
		private ConfigData<List<String>> cost;
	}

}
