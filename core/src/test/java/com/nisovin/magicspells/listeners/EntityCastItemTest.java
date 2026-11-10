package com.nisovin.magicspells.listeners;

import com.nisovin.magicspells.*;
import com.nisovin.magicspells.util.CastItemSpellResolver;
import com.nisovin.magicspells.util.SpellData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityCastItemTest {
    @Test void rightClickEntityCastsAndCancels() throws Exception { rightClick(true, EquipmentSlot.HAND); }
    @Test void rightClickOffhandUsesThatHand() throws Exception { rightClick(true, EquipmentSlot.OFF_HAND); }
    @Test void rightClickWithoutPermissionDoesNotHandle() throws Exception { rightClick(false, EquipmentSlot.HAND); }
    private void rightClick(boolean allowed, EquipmentSlot hand) throws Exception {
        try (Fixture f = new Fixture(allowed)) {
            RightClickListener listener = new RightClickListener();
            PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(f.player, mock(Entity.class), hand);
            dispatch(listener, event);
            assertEquals(allowed, event.isCancelled());
            assertEquals(allowed ? 1 : 0, f.tasks.size());
            f.tasks.forEach(Runnable::run);
            verify(f.spell, times(allowed ? 1 : 0)).hardCast(any(SpellData.class));
        }
    }
    @Test void meleeHitCastsAndCancels() throws Exception {
        try (Fixture f = new Fixture(true)) {
            LeftClickListener listener = new LeftClickListener();
            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getCause()).thenReturn(DamageCause.ENTITY_ATTACK);
            when(event.getDamager()).thenReturn(f.player);
            dispatch(listener, event);
            verify(event).setCancelled(true);
            assertEquals(1, f.tasks.size());
            f.tasks.forEach(Runnable::run);
            verify(f.spell).hardCast(any(SpellData.class));
        }
    }
    @Test void meleeSuppressesQueuedBoundInteractCast() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.plugin.when(MagicSpells::canCastWithFist).thenReturn(true);
            CastListener bound = new CastListener(f.instance);
            f.instance.castListener = bound;
            when(f.book.getActiveSpell(any(ItemStack.class))).thenReturn(f.spell);
            when(f.spell.canCastWithItem()).thenReturn(true);
            when(f.spell.hardCast(any(SpellData.class))).thenReturn(new Spell.SpellCastResult(
                    Spell.SpellCastState.NORMAL, Spell.PostCastAction.HANDLE_NORMALLY));
            org.bukkit.event.player.PlayerInteractEvent click = mock(org.bukkit.event.player.PlayerInteractEvent.class);
            when(click.getAction()).thenReturn(org.bukkit.event.block.Action.LEFT_CLICK_AIR);
            when(click.getPlayer()).thenReturn(f.player);
            bound.onPlayerInteract(click);
            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getCause()).thenReturn(DamageCause.ENTITY_ATTACK);
            when(event.getDamager()).thenReturn(f.player);
            dispatch(new LeftClickListener(), event);
            new ArrayList<>(f.tasks).forEach(Runnable::run);
            verify(f.spell, times(1)).hardCast(any(SpellData.class));
        }
    }
    @Test void rightClickEntityHonorsCooldown() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.plugin.when(MagicSpells::getGlobalCooldown).thenReturn(60000);
            RightClickListener listener = new RightClickListener();
            dispatch(listener, new PlayerInteractEntityEvent(f.player, mock(Entity.class), EquipmentSlot.HAND));
            PlayerInteractEntityEvent repeated = new PlayerInteractEntityEvent(f.player, mock(Entity.class), EquipmentSlot.HAND);
            dispatch(listener, repeated);
            assertFalse(repeated.isCancelled());
            assertEquals(1, f.tasks.size());
        }
    }
    @Test void unmatchedEntityItemDoesNotCastOrCancel() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.resolver.reset();
            PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(f.player, mock(Entity.class), EquipmentSlot.HAND);
            dispatch(new RightClickListener(), event);
            assertFalse(event.isCancelled());
            assertTrue(f.tasks.isEmpty());
        }
    }
    @Test void projectileDamageDoesNotTriggerMeleeItem() throws Exception {
        try (Fixture f = new Fixture(true)) {
            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getCause()).thenReturn(DamageCause.PROJECTILE);
            when(event.getDamager()).thenReturn(f.player);
            dispatch(new LeftClickListener(), event);
            verify(event, never()).setCancelled(anyBoolean());
            assertTrue(f.tasks.isEmpty());
        }
    }
    static void dispatch(Object listener, Event event) throws Exception {
        for (Method m : listener.getClass().getMethods()) {
            if (m.isAnnotationPresent(EventHandler.class) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isInstance(event)) { m.invoke(listener, event); return; }
        }
        fail("No entity event handler registered on " + listener.getClass().getSimpleName());
    }
    static class Fixture implements AutoCloseable {
        final Spell spell = mock(Spell.class);
        final MagicSpells instance = mock(MagicSpells.class);
        final Spellbook book = mock(Spellbook.class);
        final Player player = mock(Player.class);
        final List<Runnable> tasks = new ArrayList<>();
        final MockedStatic<MagicSpells> plugin = mockStatic(MagicSpells.class);
        final MockedStatic<CastItemSpellResolver> resolver = mockStatic(CastItemSpellResolver.class);
        Fixture(boolean allowed) {
            ItemStack item = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            when(item.getItemMeta()).thenReturn(meta);
            when(meta.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(item.clone()).thenReturn(item);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(item);
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
            // RightClickListener schedules with the Spell-owner overload.
            plugin.when(() -> MagicSpells.scheduleDelayedTask(any(), any(Runnable.class), anyLong()))
                    .thenAnswer(i -> { tasks.add(i.getArgument(1)); return tasks.size(); });
            resolver.when(() -> CastItemSpellResolver.resolve(anyMap(), anyMap(), same(item))).thenReturn(spell);
        }
        public void close() { resolver.close(); plugin.close(); }
    }
}
