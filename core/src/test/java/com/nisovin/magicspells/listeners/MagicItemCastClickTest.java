package com.nisovin.magicspells.listeners;

import com.nisovin.magicspells.*;
import com.nisovin.magicspells.util.CastItemClicks;
import com.nisovin.magicspells.util.CastItemSpellResolver;
import com.nisovin.magicspells.util.SpellData;
import com.sneakyitems.events.MagicItemClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MagicItemCastClickTest {

	@Test void vanillaEntityRightClickSkippedForStampedItem() throws Exception {
		try (Fixture f = new Fixture(true)) {
			MockedStatic<CastItemClicks> clicks = mockStatic(CastItemClicks.class, CALLS_REAL_METHODS);
			clicks.when(() -> CastItemClicks.isRegisteredMagicItem(any())).thenReturn(true);

			PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(f.player, mock(Entity.class), EquipmentSlot.HAND);
			EntityCastItemTest.dispatch(new RightClickListener(), event);
			assertFalse(event.isCancelled());
			assertTrue(f.tasks.isEmpty());
			clicks.close();
		}
	}

	@Test void magicItemRightEntityCastsAndCancels() throws Exception {
		try (Fixture f = new Fixture(true)) {
			RightClickListener listener = new RightClickListener();
			MagicItemClickEvent event = new MagicItemClickEvent(
					f.player,
					"test_wand",
					f.item,
					EquipmentSlot.HAND,
					MagicItemClickEvent.Click.RIGHT,
					MagicItemClickEvent.Trigger.ENTITY,
					null,
					null,
					mock(Entity.class),
					null,
					false);
			EntityCastItemTest.dispatch(listener, event);
			assertTrue(event.isCancelled());
			assertEquals(1, f.tasks.size());
			f.tasks.forEach(Runnable::run);
			verify(f.spell).hardCast(any(SpellData.class));
		}
	}

	@Test void magicItemLeftEntityMeleeCastsAndCancels() throws Exception {
		try (Fixture f = new Fixture(true)) {
			f.instance.castListener = new CastListener(f.instance);
			LeftClickListener listener = new LeftClickListener();
			MagicItemClickEvent event = new MagicItemClickEvent(
					f.player,
					"test_wand",
					f.item,
					EquipmentSlot.HAND,
					MagicItemClickEvent.Click.LEFT,
					MagicItemClickEvent.Trigger.ENTITY,
					null,
					null,
					mock(Entity.class),
					null,
					false);
			EntityCastItemTest.dispatch(listener, event);
			assertTrue(event.isCancelled());
			assertEquals(1, f.tasks.size());
			f.tasks.forEach(Runnable::run);
			verify(f.spell).hardCast(any(SpellData.class));
		}
	}

	static class Fixture implements AutoCloseable {
		final Spell spell = mock(Spell.class);
		final MagicSpells instance = mock(MagicSpells.class);
		final Spellbook book = mock(Spellbook.class);
		final Player player = mock(Player.class);
		final ItemStack item = mock(ItemStack.class);
		final List<Runnable> tasks = new ArrayList<>();
		final MockedStatic<MagicSpells> plugin = mockStatic(MagicSpells.class);
		final MockedStatic<CastItemSpellResolver> resolver = mockStatic(CastItemSpellResolver.class);

		Fixture(boolean allowed) {
			ItemMeta meta = mock(ItemMeta.class);
			when(item.getItemMeta()).thenReturn(meta);
			when(meta.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
			when(item.clone()).thenReturn(item);
			PlayerInventory inventory = mock(PlayerInventory.class);
			when(player.getInventory()).thenReturn(inventory);
			when(inventory.getItem(any(EquipmentSlot.class))).thenReturn(item);
			when(player.getName()).thenReturn("caster");
			when(instance.getName()).thenReturn("MagicSpells");
			when(instance.namespace()).thenReturn("magicspells");
			plugin.when(MagicSpells::getInstance).thenReturn(instance);
			plugin.when(MagicSpells::getSpells).thenReturn(Map.of("test", spell));
			when(book.hasSpell(spell)).thenReturn(true);
			when(book.canCast(spell)).thenReturn(allowed);
			plugin.when(() -> MagicSpells.getSpellbook(player)).thenReturn(book);
			plugin.when(() -> MagicSpells.scheduleDelayedTask(any(Runnable.class), anyLong()))
					.thenAnswer(i -> { tasks.add(i.getArgument(0)); return tasks.size(); });
			plugin.when(() -> MagicSpells.scheduleDelayedTask(any(), any(Runnable.class), anyLong()))
					.thenAnswer(i -> { tasks.add(i.getArgument(1)); return tasks.size(); });
			resolver.when(() -> CastItemSpellResolver.resolve(anyMap(), anyMap(), same(item))).thenReturn(spell);
		}

		public void close() {
			resolver.close();
			plugin.close();
		}
	}
}