package com.nisovin.magicspells.spells.instant;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItemTagging;
import com.nisovin.magicspells.util.magicitems.MagicItemTagging.TagMapping;
import com.nisovin.magicspells.util.magicitems.MagicItemUpdater;

/**
 * Registers attribute→internal-name mappings used to stamp {@code magicitem} PDC onto legacy stacks.
 * Automatic tagging runs through {@link MagicItemUpdater}'s shared listeners (tag then update).
 * Casting this spell still forces a scan of the caster's inventories.
 */
public class ItemTagSpell extends InstantSpell {

	private final List<TagMapping> mappings = new ArrayList<>();

	public ItemTagSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		ConfigurationSection mappingSection = getConfigSection("mapping");
		if (mappingSection != null) {
			for (String key : mappingSection.getKeys(false)) {
				ConfigurationSection itemSection = mappingSection.getConfigurationSection(key);
				if (itemSection == null)
					continue;

				MagicItem magicItem = MagicItems.getMagicItemFromSection(itemSection);
				if (magicItem == null)
					continue;

				// Ignore amount by default for tagging.
				// MAGIC_ITEM_NAME is always set from the mapping section by PersistentDataHandler; items
				// being tagged usually have no (or wrong) magicitem PDC yet, so matching would fail.
				magicItem.getMagicItemData().getIgnoredAttributes().add(MagicItemData.MagicItemAttribute.AMOUNT);
				magicItem.getMagicItemData().getIgnoredAttributes().add(MagicItemData.MagicItemAttribute.MAGIC_ITEM_NAME);

				String internalName = itemSection.getString("internal-name", key);
				mappings.add(new TagMapping(internalName, magicItem.getMagicItemData(), magicItem));
			}
		}
	}

	@Override
	protected void initialize() {
		super.initialize();
		MagicItemTagging.register(this, mappings);
	}

	@Override
	protected void turnOff() {
		MagicItemTagging.unregister(this);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (caster instanceof Player player) {
			MagicItemUpdater.processPlayerInventories(player);
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

}
