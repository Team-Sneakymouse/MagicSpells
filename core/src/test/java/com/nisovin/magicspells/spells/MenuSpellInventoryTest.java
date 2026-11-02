package com.nisovin.magicspells.spells;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.magicitems.MagicItemUpdater;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuSpellInventoryTest {
    private static InventoryView createdView;
    private static final List<String> createdTypes = new ArrayList<>();
    private static Player createdFor;
    private static Component createdTitle;
    private MenuSpell spell;
    private Player player;
    private Inventory top;

    @BeforeAll
    static void menuRegistry() {
        // Replace only the native registry boundary, not the cast graph.
        try (var registries = mockStatic(RegistryAccess.class)) {
            RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
            registries.when(RegistryAccess::registryAccess).thenReturn(access);
            var registry = Registry.MENU;
            doAnswer(call -> {
                String key = call.getArgument(0, Key.class).value();
                return Proxy.newProxyInstance(MenuType.class.getClassLoader(),
                        new Class<?>[]{MenuType.Typed.class}, (proxy, method, arguments) -> {
                            if (method.getName().equals("create")) {
                                createdTypes.add(key);
                                createdFor = (Player) arguments[0];
                                createdTitle = (Component) arguments[1];
                                return createdView;
                            }
                            return null;
                        });
            }).when(registry).getOrThrow(any(Key.class));
            assertNotNull(MenuType.GENERIC_9X1);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        spell = mock(MenuSpell.class, call -> switch (call.getMethod().getName()) {
            case "onInvClick", "applyOptionsToInventory" -> call.callRealMethod();
            default -> RETURNS_DEFAULTS.answer(call);
        });
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        top = mock(Inventory.class);
        when(top.getSize()).thenReturn(54);
        createdView = mock(InventoryView.class);
        when(createdView.getTopInventory()).thenReturn(top);
        createdTypes.clear();
        set(spell, "internalName", "menu");
        set(spell, "title", "Visible title");
        set(spell, "autoArrange", constant(false));
        set(spell, "requireEntityTarget", constant(false));
        set(spell, "requireLocationTarget", constant(false));
        set(spell, "options", Map.of());
        set(spell, "menuData", new HashMap<>());
    }

    @Test
    void nextPageReopensAPaperInventoryView() throws Exception {
        set(spell, "autoArrange", constant(true));
        ItemStack next = mock(ItemStack.class);
        set(spell, "nextPageItem", next);
        Map<UUID, Object> data = new HashMap<>();
        data.put(player.getUniqueId(), menuData(0));
        set(spell, "menuData", data);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.title()).thenReturn(Component.text("menu"));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(top);
        when(event.getSlot()).thenReturn(53);
        when(event.getCurrentItem()).thenReturn(next);
        try (var bukkit = mockStatic(Bukkit.class); var util = mockStatic(Util.class)) {
            bukkit.when(() -> Bukkit.createInventory(eq(player), eq(54), any(Component.class))).thenReturn(top);
            util.when(() -> Util.getStringFromComponent(any())).thenReturn("menu");
            spell.onInvClick(event);
            verify(event).setCancelled(true);
            verify(player).openInventory(createdView);
            assertEquals(List.of("generic_9x6"), createdTypes);
            var page = data.get(player.getUniqueId()).getClass().getDeclaredMethod("page");
            page.setAccessible(true);
            assertEquals(1, page.invoke(data.get(player.getUniqueId())));
            util.verify(() -> Util.setInventoryTitle(player, "Visible title"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void initialOpenUsesVirtualViewWithSameRowsAndTitles(int rows) throws Exception {
        set(spell, "rows", rows);
        when(top.getSize()).thenReturn(rows * 9);
        try (var bukkit = mockStatic(Bukkit.class); var util = mockStatic(Util.class)) {
            bukkit.when(() -> Bukkit.createInventory(eq(player), eq(rows * 9), any(Component.class))).thenReturn(top);
            var open = MenuSpell.class.getDeclaredMethod("openMenu", Player.class, Player.class,
                    org.bukkit.entity.LivingEntity.class, org.bukkit.Location.class, float.class, String[].class);
            open.setAccessible(true);
            open.invoke(spell, null, player, null, null, 1f, new String[]{"argument"});
            verify(player).openInventory(createdView);
            assertEquals(List.of("generic_9x" + rows), createdTypes);
            assertSame(player, createdFor);
            assertEquals(Component.text("menu"), createdTitle);
            util.verify(() -> Util.setInventoryTitle(player, "Visible title"));
        }
    }

    @Test
    void updaterDoesNotReadOrWriteMenuContents() throws Exception {
        var create = MenuSpell.class.getDeclaredMethod("createMenuView", Player.class, int.class);
        create.setAccessible(true);
        create.invoke(spell, player, 54);
        clearInvocations(top);
        try (var magic = mockStatic(MagicSpells.class)) {
            MagicSpells plugin = mock(MagicSpells.class);
            when(plugin.getName()).thenReturn("MagicSpells");
            when(plugin.namespace()).thenReturn("magicspells");
            magic.when(MagicSpells::getInstance).thenReturn(plugin);
            magic.when(MagicSpells::enableUpdateItemData).thenReturn(true);
            MagicItemUpdater.processInventory(top, player);
            var event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            when(event.getInventory()).thenReturn(top);
            when(event.getPlayer()).thenReturn(player);
            new MagicItemUpdater.PersistentDataUpdater().onInvOpen(event);
            verify(top, never()).getContents();
            verify(top, never()).setContents(any());

            Inventory ordinary = mock(Inventory.class);
            ItemStack[] contents = {null};
            when(ordinary.getContents()).thenReturn(contents);
            MagicItemUpdater.processInventory(ordinary, player);
            verify(ordinary).getContents();
            verify(ordinary).setContents(contents);
            assertFalse(MenuSpell.isMenuInventory(ordinary));
            MagicItemUpdater.processInventory(null, player);
        }
    }

    @Test
    void shiftClickDropReopensAPaperInventoryView() throws Exception {
        var drop = mock(com.nisovin.magicspells.Subspell.class);
        when(drop.subcast(any(com.nisovin.magicspells.util.SpellData.class))).thenReturn(true);
        spell = mock(MenuSpell.class, call -> switch (call.getMethod().getName()) {
            case "onInvClick", "applyOptionsToInventory" -> call.callRealMethod();
            case "initSubspell" -> drop;
            default -> RETURNS_DEFAULTS.answer(call);
        });
        set(spell, "internalName", "menu");
        set(spell, "title", "Visible title");
        set(spell, "autoArrange", constant(false));
        set(spell, "bypassNormalCast", constant(true));
        set(spell, "options", Map.of());
        set(spell, "menuData", new HashMap<>(Map.of(player.getUniqueId(), menuData(0))));
        var section = mock(org.bukkit.configuration.ConfigurationSection.class);
        when(section.getString("token")).thenReturn("drop");
        set(spell, "spellsOnDropNonOption", (ConfigData<org.bukkit.configuration.ConfigurationSection>)
                (c, t, l, p, a) -> section);
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(mock(org.bukkit.Material.class));
        when(item.getAmount()).thenReturn(2);
        var itemData = mock(com.nisovin.magicspells.util.magicitems.MagicItemData.class);
        when(itemData.getAttribute(com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute.MAGIC_ITEM_NAME))
                .thenReturn("token");
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.title()).thenReturn(Component.text("menu"));
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(mock(Inventory.class));
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.SHIFT_LEFT);
        when(event.getCurrentItem()).thenReturn(item);
        try (var bukkit = mockStatic(Bukkit.class); var util = mockStatic(Util.class);
             var items = mockStatic(com.nisovin.magicspells.util.magicitems.MagicItems.class)) {
            bukkit.when(() -> Bukkit.createInventory(eq(player), eq(54), any(Component.class))).thenReturn(top);
            util.when(() -> Util.getStringFromComponent(any())).thenReturn("menu");
            items.when(() -> com.nisovin.magicspells.util.magicitems.MagicItems.getMagicItemDataFromItemStack(item))
                    .thenReturn(itemData);
            spell.onInvClick(event);
            verify(event).setCancelled(true);
            verify(player).openInventory(createdView);
            verify(drop).subcast(any(com.nisovin.magicspells.util.SpellData.class));
            util.verify(() -> Util.setInventoryTitle(player, "Visible title"));
        }
    }

    private static ConfigData<Boolean> constant(boolean value) {
        return (c, t, l, p, a) -> value;
    }

    private static Object menuData(int page) throws Exception {
        var ctor = Class.forName(MenuSpell.class.getName() + "$MenuData").getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(null, null, 1f, new String[]{"argument"}, page);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
