# Particle location recipients: validation

## Target and preserved behavior

Target: Paper API and paperweight dev bundle **26.2.build.116-stable**, Java 25.
The implementation was checked using `javap -c -p` against the resolved API JAR
and `nms/v26_2/.gradle/caches/paperweight/taskCache/mappedServerJar.jar`:

- `ParticleBuilder.receivers(int, true)` first calls
  `World.getNearbyPlayers(location, radius, radius, radius)`, then accepts feet
  locations when `sqrt(dx*dx + dy*dy + dz*dz) <= radius`.
- The World default methods delegate to `getNearbyEntitiesByType`, which calls
  the general `getNearbyEntities` query **before** filtering to `Player`.
  `CraftWorld` constructs `BoundingBox.of(location, x, y, z)` and requests all
  intersecting entities from `ServerLevel.getEntities`.
- The broad phase uses strict bounding-box intersection (`min < other.max` and
  `max > other.min` on every axis), not inclusive point-in-cube membership.
  A standing player's feet exactly on the upper Y face are excluded even when
  the spherical distance equals the radius. The lower Y face can overlap.
- Radius zero is not automatically empty: a player's box must strictly straddle
  the emission point on all axes and its feet must equal that point. Normal
  standing feet touch their box's lower Y face and are excluded.
- Negative radius creates a normalized bounding box, but no finite nonnegative
  distance passes the subsequent comparison with the negative radius. The result
  stays an explicit empty list. Integer minimum/maximum are covered without
  integer multiplication or negation overflow.
- Retain the square root rather than replacing it with a squared comparison:
  floating-point rounding can include a distance whose square is just above
  `radius * radius`.
- `CraftWorld.getPlayers()` traverses `ServerLevel.players()`, not the entity
  index. A Player supplied by this world list remains eligible without an
  additional online/metadata/NPC filter. **Caveat:** an NPC-like Player that is
  present only in the entity index, but absent from the world's player list,
  is no longer enumerated. NPC plugin compatibility needs live verification.
- `ParticleBuilder.receivers(List)` copies the supplied list. Empty is deliberately
  non-null: null means broadcast to world players. Source remains null, and count,
  spreads, extra, particle data, and force retain their existing builder calls.
  The entity-effect emission path is unchanged.

`SpellEffect` clones the requested location and applies offsets before invoking
`playEffectLocation`. Selection uses that final location; there is no global
player scan, visibility filter, or new asynchronous work.

## Automated evidence

`core/src/test/java/com/nisovin/magicspells/spelleffects/effecttypes/ParticlesEffectTest.java`
uses real Paper builders with Java dynamic proxies, not a running game server.
The optimized World proxy rejects every unexpected query, including convenience
nearby-player calls and general entity queries. The reference proxy executes
Paper's actual default World methods and models only the server-side spatial
broad phase with strict bounding-box intersection.

TDD sequence:

1. Player-only traversal test failed against the original implementation with
   `Unexpected world query: ... World.getNearbyPlayers(Location,double,double,double)`.
2. Minimal player-distance selection passed that test.
3. Strict-hitbox/zero-radius regression failed against distance-only selection
   (extra upper-face and touching-hitbox recipients).
4. Adding strict bounding-box overlap passed. Subsequent characterization covers
   pinned builder equivalence, zero/negative/extreme radii, sqrt rounding,
   empty lists, world scope, NPC-like listed players, and final offset emission
   with all builder parameters and null source preserved.

Commands run successfully:

```sh
./gradlew :core:test --offline
./gradlew build --offline
```

The core suite contains **107 tests, zero failures/errors/skips**, including nine
particle tests. Full build also runs publication and shaded-plugin content guards.
Initial test discovery needed an explicit EffectLib test-runtime dependency
because the production dependency uses the separate `shadow` configuration.
Existing build output includes Gradle native-access warnings, Kotlin shadow
metadata duplicate warnings, a core Javadoc parameter warning, and a nonfatal
factions Javadoc diagnostic (the build config has `failOnError = false`).

## Deterministic synthetic scaling harness

Run with the test suite or:

```sh
./gradlew :core:test --offline --tests '*ParticlesEffectTest.repeatedEmissionsVisitOnlyFixedPlayersAsUnrelatedEntitiesIncrease'
```

For **100 emissions and two fixed players**, measured operation counts are:

| Unrelated synthetic boxes | Optimized player-box reads | Optimized unrelated visits | Modeled legacy unrelated visits |
| ---: | ---: | ---: | ---: |
| 0 | 200 | 0 | 0 |
| 1,000 | 200 | 0 | 100,000 |
| 10,000 | 200 | 0 | 1,000,000 |

Both paths assert the same recipient on every emission. This is an operation-count
regression harness, **not live Paper profiling or a timing/speedup claim**. The
legacy proxy deliberately enumerates synthetic nearby boxes; it does not model
chunk indexing, network work, or real allocation costs. The new traversal depends
on world-player count, which can be a tradeoff in player-heavy, entity-light worlds.

## Pending live checks (not performed)

- Compare particles at ordinary, diagonal and vertical boundaries, including
  nonzero height/relative offsets and radius zero/negative settings.
- Verify count, spread, speed, data-bearing particles, force and client visibility.
- Check real NPC plugins, especially entity-index-only fake players.
- Profile repeated location effects with fixed online players and increasing
  nearby armor stands/markers; compare server-thread allocation and time against
  the old build. Also measure many-player, few-entity worlds.

No YAML option or public API surface changed; no wiki surface update is required.
