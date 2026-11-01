package com.nisovin.magicspells.util.magicitems;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedItemBehaviorOwnershipTest {
    @Test void magicSpellsDelegatesEnforcementAndConsumesUtilityEvent() throws Exception {
        Path sources = Path.of("src/main/java/com/nisovin/magicspells");
        String bootstrap = Files.readString(sources.resolve("MagicSpells.java"));
        assertFalse(bootstrap.contains("new MagicItemExpirationListener"));
        assertFalse(bootstrap.contains("new MagicItemSoulboundListener"));
        int ready = bootstrap.indexOf("pm.callEvent(new MagicSpellsLoadedEvent(this));");
        int purge = bootstrap.indexOf("ItemExpirationScheduler.purgeExpiredForPlayer(player);", ready);
        int schedule = bootstrap.indexOf("ItemExpirationScheduler.scheduleAllForPlayer(player);", ready);
        assertTrue(ready >= 0 && purge > ready && schedule > purge,
                "consumer-only reload must rescan through provider after passive listeners are ready");
        assertTrue(Files.readString(sources.resolve("spells/passive/ExpireItemListener.java"))
                .contains("import com.sneakyutils.events.ItemExpireEvent;"));
        assertFalse(Files.exists(sources.resolve("events/MagicItemExpireEvent.java")));
        String behaviors = Files.readString(sources.resolve("util/magicitems/MagicItemBehaviors.java"));
        assertTrue(behaviors.contains("ItemBehaviors.applySoulbound"));
        assertFalse(behaviors.contains("System.currentTimeMillis"));
    }
}
