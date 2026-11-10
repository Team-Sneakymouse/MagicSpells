package com.nisovin.magicspells.spells.passive;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.PassiveSpell;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;
import com.sneakyitems.util.magicitems.MagicItems;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

/** Real listener entrypoints; item parser and spell/server boundaries are isolated. */
class InvalidClickItemTest {
    @Test void unresolvedLeftClickItemNeverActivates() { check(new LeftClickItemListener(), true); }
    @Test void unresolvedRightClickItemNeverActivates() { check(new RightClickItemListener(), true); }
    @Test void unresolvedAnimationItemNeverActivates() { check(new PlayerAnimationListener(), true); }
    @Test void missingLeftClickItemRetainsUnrestrictedBehavior() { check(new LeftClickItemListener(), false); }
    @Test void missingRightClickItemRetainsUnrestrictedBehavior() { check(new RightClickItemListener(), false); }
    @Test void missingAnimationItemRetainsUnrestrictedBehavior() { check(new PlayerAnimationListener(), false); }

    private void check(PassiveListener real, boolean invalid) {
        PassiveListener listener = spy(real);
        PassiveSpell spell = mock(PassiveSpell.class);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        listener.setPassiveSpell(spell);
        doReturn(true).when(listener).isCancelStateOk(anyBoolean());
        doReturn(true).when(listener).hasSpell(player);
        doReturn(true).when(listener).canTrigger(player);
        try (MockedStatic<MagicItems> items = mockStatic(MagicItems.class);
             MockedStatic<MagicSpells> plugin = mockStatic(MagicSpells.class)) {
            listener.initialize(invalid ? "unresolved_item" : null);
            if (listener instanceof PlayerAnimationListener animation) {
                animation.onAnimate(new PlayerAnimationEvent(player, org.bukkit.event.player.PlayerAnimationType.ARM_SWING));
            } else {
                PlayerInteractEvent event = mock(PlayerInteractEvent.class);
                when(event.getPlayer()).thenReturn(player);
                when(event.hasItem()).thenReturn(true);
                when(event.getItem()).thenReturn(mock(ItemStack.class));
                if (listener instanceof LeftClickItemListener left) {
                    when(event.getAction()).thenReturn(Action.LEFT_CLICK_AIR);
                    left.onLeftClick(event);
                } else {
                    when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
                    ((RightClickItemListener) listener).onRightClick(event);
                }
            }
            verify(spell, times(invalid ? 0 : 1)).activate(player);
        }
    }
}
