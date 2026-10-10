package com.nisovin.magicspells.util;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Objects;
import java.util.ArrayList;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import net.kyori.adventure.text.Component;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.PotionIdentity;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.CustomModelDataValues;
import com.nisovin.magicspells.util.itemreader.PotionHandler;
import com.nisovin.magicspells.util.itemreader.DurabilityHandler;
import com.nisovin.magicspells.util.itemreader.WrittenBookHandler;
import com.nisovin.magicspells.util.itemreader.LeatherArmorHandler;
import static com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute.*;

public class CastItem {

	private Material type = null;
	private String name = null;

	private int amount = 0;
	private int durability = -1;
	private CustomModelDataValues customModelData = null;
	private boolean unbreakable = false;

	private Color color = null;
	private PotionType potionType = null;
	private String title = null;
	private String author = null;

	private Map<Enchantment, Integer> enchants = null;
	private List<String> lore = null;

	private String magicitemName = null;
	private Map<String, String> persistentData = null;
	private Map<String, String> permanentData = null;

	private static final String REGULAR_PREFIX = "magicspelldata_";
	private static final String PERMANENT_PREFIX = "magicspellpermanentdata_";
	private static final NamespacedKey NAME_KEY = new NamespacedKey(MagicSpells.getInstance(), "magicitem");

	public CastItem() {

	}

	public CastItem(ItemStack item) {
		if (item == null) throw new NullPointerException("itemStack");
		ItemMeta meta = item.getItemMeta();

		type = item.getType();
		if (isTypeValid()) {
			if (!MagicSpells.ignoreCastItemNames()) {
				String nameLocal = Util.getLegacyFromComponent(meta.displayName());
				if (!meta.hasDisplayName()) name = null;
				else if (MagicSpells.ignoreCastItemNameColors()) name = Util.decolorize(nameLocal);
				else name = nameLocal;
			}
			if (!MagicSpells.ignoreCastItemAmount()) amount = item.getAmount();
			if (!MagicSpells.ignoreCastItemDurability(type) && ItemUtil.hasDurability(type)) durability = DurabilityHandler.getDurability(meta);
			if (!MagicSpells.ignoreCastItemCustomModelData()) customModelData = ItemUtil.getCustomModelDataValues(meta);
			if (!MagicSpells.ignoreCastItemBreakability()) unbreakable = meta.isUnbreakable();
			if (!MagicSpells.ignoreCastItemColor()) color = LeatherArmorHandler.getColor(meta);
			if (!MagicSpells.ignoreCastItemPotionType()) potionType = PotionHandler.getPotionType(meta);
			if (!MagicSpells.ignoreCastItemTitle()) title = WrittenBookHandler.getTitle(meta);
			if (!MagicSpells.ignoreCastItemAuthor()) author = WrittenBookHandler.getAuthor(meta);
			if (!MagicSpells.ignoreCastItemEnchants()) enchants = meta.getEnchants();
			if (!MagicSpells.ignoreCastItemLore() && meta.hasLore()) {
				List<String> loreLocal = new ArrayList<>();
				for (Component component : meta.lore()) {
					loreLocal.add(Util.getLegacyFromComponent(component));
				}
				lore = loreLocal;
			}
			if (!MagicSpells.ignoreCastItemPersistentData()) {
				persistentData = new HashMap<>();
				permanentData = new HashMap<>();

				PersistentDataContainer container = meta.getPersistentDataContainer();
				if (container.has(NAME_KEY, PersistentDataType.STRING)) {
					magicitemName = container.get(NAME_KEY, PersistentDataType.STRING);
				}

				String namespace = MagicSpells.getInstance().getName().toLowerCase();
				for (NamespacedKey key : container.getKeys()) {
					if (!key.getNamespace().equals(namespace)) continue;

					String keyStr = key.getKey();
					if (keyStr.startsWith(REGULAR_PREFIX)) {
						persistentData.put(keyStr.substring(REGULAR_PREFIX.length()), container.get(key, PersistentDataType.STRING));
					} else if (keyStr.startsWith(PERMANENT_PREFIX)) {
						permanentData.put(keyStr.substring(PERMANENT_PREFIX.length()), container.get(key, PersistentDataType.STRING));
					}
				}
				if (persistentData.isEmpty()) persistentData = null;
				if (permanentData.isEmpty()) permanentData = null;
			}
		}
	}

	public CastItem(String string) {
		MagicItemData data = MagicItems.getMagicItemDataFromString(string);
		if (data != null) {
			type = (Material) data.getAttribute(TYPE);
			if (isTypeValid()) {
				if (!MagicSpells.ignoreCastItemNames() && data.hasAttribute(NAME)) {
					String localName = Util.getLegacyFromComponent((Component) data.getAttribute(NAME));
					if (MagicSpells.ignoreCastItemNameColors()) name = Util.decolorize(localName);
					else name = localName;
				}

				if (!MagicSpells.ignoreCastItemAmount() && data.hasAttribute(AMOUNT))
					amount = (int) data.getAttribute(AMOUNT);

				if (!MagicSpells.ignoreCastItemDurability(type) && ItemUtil.hasDurability(type) && data.hasAttribute(DURABILITY))
					durability = (int) data.getAttribute(DURABILITY);

				if (!MagicSpells.ignoreCastItemCustomModelData() && data.hasAttribute(CUSTOM_MODEL_DATA))
					customModelData = (CustomModelDataValues) data.getAttribute(CUSTOM_MODEL_DATA);

				if (!MagicSpells.ignoreCastItemBreakability() && data.hasAttribute(UNBREAKABLE))
					unbreakable = (boolean) data.getAttribute(UNBREAKABLE);

				if (!MagicSpells.ignoreCastItemColor() && data.hasAttribute(COLOR))
					color = (Color) data.getAttribute(COLOR);

				if (!MagicSpells.ignoreCastItemPotionType() && data.hasAttribute(POTION_DATA))
					potionType = (PotionType) data.getAttribute(POTION_DATA);

				if (!MagicSpells.ignoreCastItemTitle() && data.hasAttribute(TITLE))
					title = (String) data.getAttribute(TITLE);

				if (!MagicSpells.ignoreCastItemAuthor() && data.hasAttribute(AUTHOR))
					author = (String) data.getAttribute(AUTHOR);

				if (!MagicSpells.ignoreCastItemEnchants() && data.hasAttribute(ENCHANTS))
					enchants = data.getAttribute(ENCHANTS);

				if (!MagicSpells.ignoreCastItemLore() && data.hasAttribute(LORE)) {
					List<String> loreLocal = new ArrayList<>();
					List<Component> components = data.getAttribute(LORE);
					for (Component component : components) {
						loreLocal.add(Util.getLegacyFromComponent(component));
					}
					lore = loreLocal;
				}

				if (!MagicSpells.ignoreCastItemPersistentData() && data.hasAttribute(MAGIC_ITEM_NAME)) {
					magicitemName = (String) data.getAttribute(MAGIC_ITEM_NAME);
				}

				if (!MagicSpells.ignoreCastItemPersistentData() && data.hasAttribute(PERSISTENT_DATA)) {
					persistentData = data.getAttribute(PERSISTENT_DATA);
				}

				if (!MagicSpells.ignoreCastItemPersistentData() && data.hasAttribute(PERMANENT_DATA)) {
					permanentData = data.getAttribute(PERMANENT_DATA);
				}
			}
		}
	}

	public boolean isTypeValid() {
		return type != null && !BlockUtils.isAir(type);
	}

	public Material getType() {
		return type;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof CastItem item) return equalsCastItem(item);
		if (o instanceof ItemStack item) return equalsCastItem(new CastItem(item));
		return false;
	}

	public boolean equalsCastItem(CastItem i) {
		if (i == null) return false;

		return type == i.type
			&& (MagicSpells.ignoreCastItemDurability(type) || durability == i.durability)
			&& (MagicSpells.ignoreCastItemAmount() || amount == i.amount)
			&& (MagicSpells.ignoreCastItemNames() || Objects.equals(name, i.name))
			&& (MagicSpells.ignoreCastItemCustomModelData() || Objects.equals(customModelData, i.customModelData))
			&& (MagicSpells.ignoreCastItemBreakability() || unbreakable == i.unbreakable)
			&& (MagicSpells.ignoreCastItemColor() || Objects.equals(color, i.color))
			&& (MagicSpells.ignoreCastItemPotionType() || Objects.equals(potionType, i.potionType))
			&& (MagicSpells.ignoreCastItemTitle() || Objects.equals(title, i.title))
			&& (MagicSpells.ignoreCastItemAuthor() || Objects.equals(author, i.author))
			&& (MagicSpells.ignoreCastItemEnchants() || Objects.equals(enchants, i.enchants))
			&& (MagicSpells.ignoreCastItemLore() || Objects.equals(lore, i.lore))
			&& (MagicSpells.ignoreCastItemPersistentData() || Objects.equals(permanentData, i.permanentData))
			&& (MagicSpells.ignoreCastItemPersistentData() || Objects.equals(magicitemName, i.magicitemName));
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, name, amount, durability, customModelData, unbreakable, color, potionType, title, author, enchants, lore, magicitemName, permanentData);
	}

	@Override
	public String toString() {
		if (type == null) return "";

		StringBuilder output = new StringBuilder();
		boolean previous = false;

		output.append(type.name());

		if (!MagicSpells.ignoreCastItemNames() && name != null) {
			output.append("{");

			output
				.append("\"name\":\"")
				.append(TxtUtil.escapeJSON(Util.getLegacyFromMiniMessage(name)))
				.append('"');

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemAmount()) {
			if (previous) output.append(',');
			else output.append("{");

			output
				.append("\"amount\":")
				.append(amount);

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemDurability(type) && ItemUtil.hasDurability(type)) {
			if (previous) output.append(',');
			else output.append("{");

			output
				.append("\"durability\":")
				.append(durability);

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemCustomModelData() && customModelData != null && !customModelData.isEmpty()) {
			if (previous) output.append(',');
			else output.append("{");

			output
				.append("\"custommodeldata\":")
				.append(customModelData.toJsonValue());

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemBreakability()) {
			if (previous) output.append(',');

			output
				.append("\"unbreakable\":")
				.append(unbreakable);

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemColor() && color != null) {
			if (previous) output.append(',');
            else output.append("{");

			String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

			output
				.append("\"color\":\"")
				.append(hex)
				.append('"');

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemPotionType() && potionType != null) {
			if (previous) output.append(',');
            else output.append("{");

			output
				.append("\"potiondata\":\"")
				.append(PotionIdentity.serialize(potionType.name()))
				.append('"');

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemTitle() && title != null) {
			if (previous) output.append(',');
            else output.append("{");

			output
				.append("\"title\":\"")
				.append(TxtUtil.escapeJSON(title))
				.append('"');

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemAuthor() && author != null) {
			if (previous) output.append(',');
            else output.append("{");

			output
				.append("\"author\":\"")
				.append(TxtUtil.escapeJSON(author))
				.append('"');

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemEnchants() && enchants != null) {
			if (previous) output.append(',');
            else output.append("{");

			boolean previousEnchantment = false;
			output.append("\"enchants\":{");
			for (Enchantment enchant : enchants.keySet()) {
				if (previousEnchantment) output.append(',');

				output
					.append('"')
					.append(enchant.getKey().getKey())
					.append("\":")
					.append(enchants.get(enchant));

				previousEnchantment = true;
			}
			output.append('}');

			previous = true;
		}

		if (!MagicSpells.ignoreCastItemLore() && lore != null) {
			if (previous) output.append(',');
            else output.append("{");

			boolean previousLore = false;
			output.append("\"lore\":[");
			for (String line : lore) {
				if (previousLore) output.append(',');

				output
					.append('"')
					.append(TxtUtil.escapeJSON(Util.getLegacyFromMiniMessage(line)))
					.append('"');

				previousLore = true;
			}

			output.append(']');
			previous = true;
		}

		if (previous) output.append("}");

		return output.toString();
	}

}
