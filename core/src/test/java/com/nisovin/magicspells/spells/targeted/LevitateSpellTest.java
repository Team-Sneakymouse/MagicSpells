package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.util.config.ConfigData;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** API-only fixture: real cast methods and repeating runner, with entity/scheduler boundaries only.
 * Allocation bypasses unrelated Spell/plugin bootstrap; no mock server or cast graph is installed.
 */
class LevitateSpellTest {
    TestSpell spell;
    final Map<Integer, Runnable> scheduled = new LinkedHashMap<>();
    final List<Integer> cancelled = new ArrayList<>();
    Object oldServer, oldPlugin;
    int nextId;
    final Actor caster = new Actor();
    final Actor target = new Actor();

    @BeforeEach void setup() throws Exception {
        oldServer = get(Bukkit.class, null, "server");
        oldPlugin = get(MagicSpells.class, null, "plugin");
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (o, m, a) -> switch (m.getName()) {
            case "scheduleSyncRepeatingTask" -> { int id = ++nextId; scheduled.put(id, (Runnable) a[1]); yield id; }
            case "cancelTask" -> { cancelled.add((Integer) a[0]); scheduled.remove((Integer) a[0]); yield null; }
            default -> throw new AssertionError(m);
        });
        set(Bukkit.class, null, "server", proxy(Server.class, (o, m, a) -> {
            if (m.getName().equals("getScheduler")) return scheduler;
            throw new AssertionError(m);
        }));
        set(MagicSpells.class, null, "plugin", allocate(MagicSpells.class));
        spell = allocate(TestSpell.class);
        set(LevitateSpell.class, spell, "levitating", new HashMap<>());
        option("duration", 1000); option("tickRate", 5);
        option("maxDistance", LevitateSpell.DEFAULT_MAX_DISTANCE); option("minDistance", 1.0);
        option("distanceChange", 0.0); option("yOffset", 0.0);
        set(LevitateSpell.class, spell, "toggle", true);
        set(Spell.class, spell, "validTargetList", new ValidTargetList(true, true) {
            @Override public boolean canTarget(Entity c, Entity t) { return t != null; }
        });
        target.world = caster.world;
        target.x = 5;
        spell.selected = target.entity;
    }

    @AfterEach void restore() throws Exception {
        spell.turnOff();
        set(Bukkit.class, null, "server", oldServer);
        set(MagicSpells.class, null, "plugin", oldPlugin);
    }

    @Test void defaultDistanceIsTwentyBlocks() {
        assertEquals(20.0, LevitateSpell.DEFAULT_MAX_DISTANCE);
    }

    @Test void worldSeparationCancelsAndCannotResumeAfterReturn() {
        castTarget();
        Runnable stale = scheduled.values().iterator().next();
        World original = target.world;
        target.world = world();
        tick();
        assertTerminated();
        target.world = original;
        stale.run(); tick();
        assertEquals(0, target.velocities);
    }

    @Test void distanceLimitCancelsAndCannotResumeAfterReturn() {
        castTarget();
        Runnable stale = scheduled.values().iterator().next();
        target.x = 20;
        tick();
        assertEquals(1, target.velocities, "exact bound is allowed");
        target.x = Math.nextUp(20.0);
        tick();
        assertTerminated();
        target.x = 5;
        stale.run(); tick();
        assertEquals(1, target.velocities);
    }

    @Test void customDistanceLimitIsRespected() throws Exception {
        option("maxDistance", 35.0);
        castTarget();
        target.x = 35;
        tick();
        assertEquals(1, target.velocities);
        target.x = 35.1;
        tick();
        assertTerminated();
    }

    @Test void playerRecastStopsBeforeAcquiringAnotherTarget() {
        spell.cast(new SpellData(caster.entity, 1, null));
        spell.selected = null;
        assertEquals(Spell.PostCastAction.ALREADY_HANDLED,
                spell.cast(new SpellData(caster.entity, 1, null)).action());
        assertTerminated();
        assertEquals(1, spell.acquisitions, "toggle off must not need a target");
    }

    @Test void targetedRecastStopsEvenWithoutAValidNewTarget() {
        castTarget();
        assertEquals(Spell.PostCastAction.ALREADY_HANDLED,
                spell.castAtEntity(new SpellData(caster.entity, 1, null)).action());
        assertTerminated();
    }

    @Test void legacyPlayerRecastPreservesToggleOnFailedCastState() {
        castTarget();
        assertEquals(Spell.PostCastAction.ALREADY_HANDLED,
                spell.castSpell(caster.entity, Spell.SpellCastState.ON_COOLDOWN, 1, null));
        assertTerminated();
        assertEquals(0, spell.acquisitions);
    }

    @Test void toggleDisabledReplacesRatherThanOrphansOldRunner() throws Exception {
        set(LevitateSpell.class, spell, "toggle", false);
        castTarget();
        Runnable stale = scheduled.values().iterator().next();
        castTarget();
        assertEquals(1, scheduled.size());
        assertEquals(List.of(1), cancelled);
        stale.run(); tick();
        assertEquals(1, target.velocities);
        assertTrue(spell.isBeingLevitated(target.entity));
    }

    @Test void invalidOrDeadParticipantsTerminateWithoutVelocity() throws Exception {
        for (boolean casterSide : new boolean[]{false, true}) {
            for (boolean dead : new boolean[]{false, true}) {
                Actor actor = casterSide ? caster : target;
                actor.valid = true; actor.dead = false;
                castTarget();
                if (dead) actor.dead = true;
                else actor.valid = false;
                int before = cancelled.size();
                tick();
                assertTrue(scheduled.isEmpty());
                assertFalse(spell.isBeingLevitated(target.entity));
                assertEquals(before + 1, cancelled.size());
                assertEquals(0, target.velocities);
                actor.valid = true; actor.dead = false;
            }
        }
    }

    @Test void expiryDoesNotApplyOneLastVelocity() throws Exception {
        option("duration", 5);
        castTarget(); tick();
        assertEquals(1, target.velocities, "initial zero-delay tick must still pull");
        assertEquals(1, scheduled.size());
        tick();
        assertTerminated();
        assertEquals(1, target.velocities, "expired tick must not apply another pull");
    }

    @Test void durationPreservesEveryScheduledPull() throws Exception {
        option("duration", 10);
        castTarget(); tick(); tick();
        assertEquals(2, target.velocities);
        tick();
        assertTerminated();
        assertEquals(2, target.velocities);
    }

    @Test void canonicalPlayerEntryCanToggleOffDuringCooldown() {
        spell.cast(Spell.SpellCastState.NORMAL, new SpellData(caster.entity, 1, null));
        spell.selected = null;
        assertEquals(Spell.PostCastAction.ALREADY_HANDLED,
                spell.cast(Spell.SpellCastState.ON_COOLDOWN, new SpellData(caster.entity, 1, null)).action());
        assertTerminated();
        assertEquals(1, spell.acquisitions);
    }

    @Test void multiCasterTargetRemovalAndTurnOffCancelEveryRunner() {
        Actor other = new Actor(); other.world = caster.world;
        castTarget();
        spell.castAtEntity(new SpellData(other.entity, target.entity, 1, null));
        assertEquals(2, scheduled.size());
        spell.removeLevitate(target.entity);
        assertTrue(scheduled.isEmpty());
        assertFalse(spell.isBeingLevitated(target.entity));
        castTarget();
        spell.castAtEntity(new SpellData(other.entity, target.entity, 1, null));
        spell.turnOff(); spell.turnOff();
        assertTrue(scheduled.isEmpty());
        assertEquals(4, cancelled.size());
    }

    void castTarget() { spell.castAtEntity(new SpellData(caster.entity, target.entity, 1, null)); }
    void tick() { new ArrayList<>(scheduled.values()).forEach(Runnable::run); }
    void assertTerminated() {
        assertTrue(scheduled.isEmpty(), "repeating task must be cancelled");
        assertFalse(spell.isBeingLevitated(target.entity), "caster map must be cleaned");
        assertEquals(1, cancelled.size());
    }
    void option(String name, Object value) throws Exception {
        ConfigData<Object> constant = (c, t, l, power, args) -> value;
        set(LevitateSpell.class, spell, name, constant);
    }

    static class TestSpell extends LevitateSpell {
        LivingEntity selected;
        int acquisitions;
        TestSpell() { super(null, "unused"); }
        @Override protected TargetInfo<LivingEntity> getTargetedEntity(Entity caster, float power, String[] args) {
            acquisitions++;
            return new TargetInfo<>(selected, power, false);
        }
        @Override protected void playSpellEffects(Entity caster, Entity target, SpellData data) {}
        @Override public void playTrackingLinePatterns(EffectPosition pos, Location origin, Location target,
                Entity originEntity, Entity targetEntity, SpellData data) {}
        @Override public void sendMessages(Entity caster, Entity target, String[] args) {}
    }

    static class Actor {
        World world = world();
        double x;
        boolean valid = true, dead;
        int velocities;
        final UUID id = UUID.randomUUID();
        final LivingEntity entity = proxy(LivingEntity.class, (o, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "getWorld" -> world;
            case "getLocation", "getEyeLocation" -> new Location(world, x, 0, 0);
            case "isValid" -> valid;
            case "isDead" -> dead;
            case "setFallDistance" -> null;
            case "setVelocity" -> { velocities++; yield null; }
            case "equals" -> o == a[0];
            case "hashCode" -> id.hashCode();
            case "toString" -> id.toString();
            default -> throw new AssertionError(m);
        });
    }
    static World world() {
        return proxy(World.class, (o, m, a) -> switch (m.getName()) {
            case "equals" -> o == a[0];
            case "hashCode" -> System.identityHashCode(o);
            case "toString", "getName" -> "levitate-test-world";
            default -> throw new AssertionError(m);
        });
    }
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
    static <T> T allocate(Class<T> type) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return type.cast(((Unsafe) f.get(null)).allocateInstance(type));
    }
    static Object get(Class<?> type, Object owner, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
}
