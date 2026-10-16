package com.nisovin.magicspells.spelleffects.effecttypes;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import com.nisovin.magicspells.util.config.ConfigData;

import static org.junit.jupiter.api.Assertions.*;

class ParticlesEffectTest {

    @Test
    void selectsWorldPlayersWithoutAnyRegionalEntityQuery() {
        Scene scene = new Scene();
        Player near = scene.player(1, 0, 0);
        scene.player(20, 0, 0);
        new Effect(5).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertEquals(List.of(near), scene.receivers());
        assertEquals(1, scene.playerQueries);
    }

    @Test
    void retainsStrictHitboxBroadPhaseIncludingZeroRadius() {
        Scene scene = new Scene();
        // Feet exactly on the upper cube face: Paper's strict AABB overlap excludes this player.
        scene.player(0, 5, 0);
        Player lower = scene.player(0, -5, 0);
        Player sphereEdge = scene.player(3, 0, 4);
        scene.player(4, 0, 4); // inside cube, outside sphere
        scene.player(0, 0, 0, new BoundingBox(5, 0, 0, 6, 1, 1)); // touching only
        new Effect(5).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertEquals(List.of(lower, sphereEdge), scene.receivers());

        Scene zero = new Scene();
        zero.player(0, 0, 0); // feet touch the zero-volume box, not strict overlap
        Player straddling = zero.player(0, 0, 0, new BoundingBox(-1, -1, -1, 1, 1, 1));
        new Effect(0).playEffectLocation(new Location(zero.world, 0, 0, 0), null);
        assertEquals(List.of(straddling), zero.receivers());
    }

    @Test
    void matchesPinnedPaperBuilderForBoundaryAndExtremeRadii() {
        Scene scene = new Scene();
        scene.player(0, 0, 0);
        scene.player(0, -5, 0);
        scene.player(0, 5, 0);
        scene.player(3, 0, 4);
        scene.player(Math.nextUp(5.0), 0, 0);
        scene.player(0, 0, 0, new BoundingBox(-1, -1, -1, 1, 1, 1));
        scene.player(Integer.MAX_VALUE, 0, 0);
        scene.player(50000, 0, 0);
        for (int radius : new int[]{0, -1, Integer.MIN_VALUE, 1, 5, 50000, Integer.MAX_VALUE}) {
            List<Player> expected = Particle.FLAME.builder()
                    .location(new Location(scene.legacyWorld, 0, 0, 0)).receivers(radius, true).receivers();
            new Effect(radius).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
            assertEquals(expected, scene.receivers(), "radius=" + radius);
        }
    }

    @Test
    void preservesInclusiveSqrtRoundingAtSphereBoundary() {
        Scene scene = new Scene();
        Player roundedBoundary = scene.player(5, Math.sqrt(Math.ulp(25.0)), 0);
        new Effect(5).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertEquals(List.of(roundedBoundary), scene.receivers());
    }

    @Test
    void onlySelectsPlayersFromTheEmissionWorld() {
        Scene scene = new Scene();
        Scene elsewhere = new Scene();
        elsewhere.player(0, 0, 0);
        new Effect(5).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertEquals(List.of(), scene.receivers());
        assertEquals(0, elsewhere.playerQueries);
    }

    @Test
    void emitsAnExplicitEmptyListRatherThanWorldBroadcast() {
        Scene scene = new Scene();
        scene.player(100, 0, 0);
        new Effect(5).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertEquals(List.of(), scene.receivers());
        new Effect(-1).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertEquals(List.of(), scene.receivers());
    }

    @Test
    void includesNpcLikeWorldPlayersWithoutOnlineOrMetadataFiltering() {
        Scene scene = new Scene();
        // This proxy exposes no online/metadata/connection APIs: every listed Player is eligible.
        Player npcLike = scene.player(1, 0, 0);
        new Effect(5).playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertEquals(List.of(npcLike), scene.receivers());
    }

    @Test
    void suppliesColorDataForParticlesThatRequireIt() {
        Effect effect = new Effect(5);
        effect.color = constant(org.bukkit.Color.BLUE);
        effect.argbColor = constant(org.bukkit.Color.fromARGB(0x80, 0xFF, 0x00, 0x00));

        assertEquals(org.bukkit.Color.fromARGB(0x80, 0xFF, 0x00, 0x00),
                effect.getParticleData(Particle.ENTITY_EFFECT, new Location(null, 0, 0, 0), null));
        assertEquals(org.bukkit.Color.BLUE,
                effect.getParticleData(Particle.FLASH, new Location(null, 0, 0, 0), null));
        assertEquals(org.bukkit.Color.BLUE,
                effect.getParticleData(Particle.TINTED_LEAVES, new Location(null, 0, 0, 0), null));
    }

    @Test
    void suppliesSpellDataForEffectParticles() {
        Effect effect = new Effect(5);
        Particle.Spell spell = new Particle.Spell(org.bukkit.Color.AQUA, 0.75f);
        effect.spellOptions = constant(spell);

        assertSame(spell, effect.getParticleData(Particle.EFFECT, new Location(null, 0, 0, 0), null));
        assertSame(spell, effect.getParticleData(Particle.INSTANT_EFFECT, new Location(null, 0, 0, 0), null));
    }

    @Test
    void suppliesTrailDataWithTargetColorAndDuration() {
        Effect effect = new Effect(5);
        effect.trailColor = constant(org.bukkit.Color.YELLOW);
        effect.trailDuration = constant(40);
        effect.trailTarget = constant(ParticlesEffect.ParticlePosition.POSITION);
        effect.trailTargetOffset = constant(new org.bukkit.util.Vector(1, 2, 3));
        effect.trailTargetRelativeOffset = constant(new org.bukkit.util.Vector());

        Location origin = new Location(null, 10, 20, 30);
        Object data = effect.getParticleData(Particle.TRAIL, origin, null);
        assertInstanceOf(Particle.Trail.class, data);
        Particle.Trail trail = (Particle.Trail) data;
        assertEquals(org.bukkit.Color.YELLOW, trail.getColor());
        assertEquals(40, trail.getDuration());
        assertEquals(11.0, trail.getTarget().getX(), 1e-9);
        assertEquals(22.0, trail.getTarget().getY(), 1e-9);
        assertEquals(33.0, trail.getTarget().getZ(), 1e-9);
    }

    @Test
    void skipsSpawnWhenRequiredParticleDataIsMissing() {
        Scene scene = new Scene();
        Effect effect = new Effect(5);
        effect.particle = constant(Particle.TRAIL);
        effect.trailColor = constant(null);
        effect.trailDuration = constant(20);
        effect.trailTarget = constant(ParticlesEffect.ParticlePosition.POSITION);
        effect.trailTargetOffset = constant(new org.bukkit.util.Vector());
        effect.trailTargetRelativeOffset = constant(new org.bukkit.util.Vector());
        effect.trailOrigin = constant(ParticlesEffect.ParticlePosition.POSITION);

        effect.playEffectLocation(new Location(scene.world, 0, 0, 0), null);
        assertNull(scene.emission, "missing Trail data must not call spawnParticle");
    }

    @Test
    void selectsAtFinalOffsetLocationAndPreservesEmissionParameters() throws Exception {
        Scene scene = new Scene();
        scene.player(0, 0, 0);
        Player nearEmission = scene.player(100, 20, 30);
        Effect effect = new Effect(5) {
            @Override
            public Location applyOffsets(Location location, com.nisovin.magicspells.util.SpellData data) {
                return super.applyOffsets(location, new org.bukkit.util.Vector(100, 10, 30),
                        new org.bukkit.util.Vector(), 0, 10, 0, 0, 0);
            }
        };
        for (String name : List.of("chance", "delay")) {
            var field = com.nisovin.magicspells.spelleffects.SpellEffect.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(effect, name.equals("chance") ? constant(-1.0) : constant(0));
        }
        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.RED, 2);
        effect.particle = constant(Particle.DUST);
        effect.dustOptions = constant(dust);
        Location original = new Location(scene.world, 0, 0, 0);
        effect.playEffect(original, (com.nisovin.magicspells.util.SpellData) null);
        assertEquals(List.of(nearEmission), scene.receivers());
        assertEquals(new Location(scene.world, 0, 0, 0), original, "offset must not mutate caller location");
        assertArrayEquals(new Object[]{Particle.DUST, List.of(nearEmission), null,
                100.0, 20.0, 30.0, 7, 0.25, 0.5, 0.75, 0.125, dust, true}, scene.emission);
    }

    @Test
    void repeatedEmissionsVisitOnlyFixedPlayersAsUnrelatedEntitiesIncrease() {
        for (int unrelated : new int[]{0, 1000, 10000}) {
            Scene scene = new Scene();
            Player near = scene.player(1, 0, 0);
            scene.player(100, 0, 0);
            for (int i = 0; i < unrelated; i++) scene.unrelated.add(new BoundingBox(-1, -1, -1, 1, 1, 1));
            Effect effect = new Effect(5);
            for (int i = 0; i < 100; i++) {
                effect.playEffectLocation(new Location(scene.world, 0, 0, 0), null);
                assertEquals(List.of(near), scene.receivers());
            }
            assertEquals(100, scene.playerQueries);
            assertEquals(200, scene.playerBoxReads);
            assertEquals(0, scene.unrelatedVisits);
            for (int i = 0; i < 100; i++) {
                Particle.FLAME.builder().location(new Location(scene.legacyWorld, 0, 0, 0))
                        .receivers(5, true).spawn();
                assertEquals(List.of(near), scene.receivers());
            }
            assertEquals(100L * unrelated, scene.unrelatedVisits);
            System.out.printf("Synthetic 100 emissions, 2 players, unrelated=%d: optimized player-box reads=200, "
                    + "optimized unrelated visits=0; modeled legacy unrelated visits=%d%n", unrelated, scene.unrelatedVisits);
        }
    }

    static class Effect extends ParticlesEffect {
        Effect(int distance) {
            particle = constant(Particle.FLAME);
            radius = constant(distance);
            count = constant(7);
            xSpread = constant(0.25f);
            ySpread = constant(0.5f);
            zSpread = constant(0.75f);
            speed = constant(0.125f);
            force = true;
        }
    }

    static <T> ConfigData<T> constant(T value) {
        return (caster, target, location, power, args) -> value;
    }

    static class Scene {
        final List<Player> players = new ArrayList<>();
        final List<BoundingBox> unrelated = new ArrayList<>();
        Object[] emission;
        int playerQueries;
        int playerBoxReads;
        long unrelatedVisits;
        final World world = world(false);
        final World legacyWorld = world(true);

        World world(boolean allowRegionalQueries) {
            return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (allowRegionalQueries && method.getName().equals("getNearbyEntities")) {
                        BoundingBox bounds = BoundingBox.of((Location) args[0], (double) args[1],
                                (double) args[2], (double) args[3]);
                        List<org.bukkit.entity.Entity> result = new ArrayList<>();
                        for (Player player : players) if (bounds.overlaps(player.getBoundingBox())) result.add(player);
                        // Synthetic enumeration only: deliberately model the all-entity broad phase,
                        // not Paper's chunk index, allocations, or real server timings.
                        for (BoundingBox box : unrelated) {
                            unrelatedVisits++;
                            if (bounds.overlaps(box)) result.add(marker);
                        }
                        return result;
                    }
                    if (allowRegionalQueries && method.isDefault())
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    return switch (method.getName()) {
                        case "getPlayers" -> { playerQueries++; yield players; }
                        case "spawnParticle" -> { emission = args; yield null; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "particle-test-world";
                        default -> throw new AssertionError("Unexpected world query: " + method);
                    };
                });
        }

        final org.bukkit.entity.Entity marker = (org.bukkit.entity.Entity) Proxy.newProxyInstance(
                org.bukkit.entity.Entity.class.getClassLoader(), new Class<?>[]{org.bukkit.entity.Entity.class},
                (proxy, method, args) -> { throw new AssertionError("Marker methods must not be queried"); });

        Player player(double x, double y, double z) {
            return player(x, y, z, new BoundingBox(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3));
        }

        Player player(double x, double y, double z, BoundingBox box) {
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getLocation" -> new Location(world, x, y, z);
                        case "getBoundingBox" -> { playerBoxReads++; yield box; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "player(" + x + "," + y + "," + z + ")";
                        default -> throw new AssertionError("Unexpected player query: " + method);
                    });
            players.add(player);
            return player;
        }

        @SuppressWarnings("unchecked")
        List<Player> receivers() {
            assertNotNull(emission, "ParticleBuilder must emit");
            assertNotNull(emission[1], "null recipients would broadcast to the world");
            return (List<Player>) emission[1];
        }
    }
}
