# Updating MagicSpells to Paper/Minecraft 26.2

Assessment date: 2026-08-24. This is a repository-specific upgrade assessment, not an implementation. Sources are first-party Paper, Mojang, Gradle, Kotlin, and dependency project sources.

## Executive summary

This is a **real port**, not a version-string-only update. The required work is:

1. Move the build and runtime from Java 21 to **Java 25** and from Gradle 8.12 to Gradle 9.1 or newer. A coherent, currently supported combination is Gradle 9.5.0 plus Kotlin 2.4.10.
2. Change Paper coordinates from `1.21.4-R0.1-SNAPSHOT` to the new `26.2.build.*` scheme and update Paperweight and the other Gradle plugins.
3. Create and port a `:nms:v26_2` module. A mechanically renamed copy of `v1_21_4` has numerous 26.2 NMS compilation failures involving identifiers, registries, entity data, hurt sounds, advancements, and packets.
4. Fix one confirmed public-API compilation break in `Util#createInventoryView`: `InventoryView` now requires `getMenuType()`.
5. Declare dependencies that the code currently receives accidentally/transitively (`json-simple` and an annotation API), or replace those uses. They are absent from the 26.2 compile classpath.
6. Change `plugin.yml` API metadata to `26.2`, update and test all runtime integrations, then run compile, server smoke, spell-family, persistence, and world-upgrade tests.

Paper requires backups and warns that a world opened on 26.2 cannot be downgraded. The intermediate 26.1 update also changed Paper world/dimension storage layout, so a production migration from 1.21.4 must include a rehearsed world conversion and rollback by restoring a backup, not by starting an older server on converted data ([Paper 26.2 announcement](https://papermc.io/news/26-2/), [Paper 26.1 world-storage changes](https://papermc.io/news/26-1/)).

## 1. Java, Gradle, Kotlin, and build plugins

Paper's 26.2 project setup uses a **Java 25 toolchain** and `paper-api:26.2.build.+` ([Paper project setup](https://docs.papermc.io/paper/dev/project-setup/)). Apply Java 25 to all production subprojects and Kotlin compilation targets, and run the server with Java 25. The current Java 21 settings in `MSJavaPlugin`, `compileKotlin`, and `compileTestKotlin` are insufficient.

The wrapper must also change. Gradle 8.12 cannot run on Java 25; official Gradle support for running on Java 25 begins at **9.1.0** ([Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)). Gradle 9.5.0 is a conservative pairing because Kotlin 2.4.10 officially supports Gradle through 9.5.0 ([Kotlin/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [Gradle 9.5 release](https://docs.gradle.org/9.5.0/release-notes.html)).

The `buildSrc` Java 8 toolchain must be raised too. An audit build with Gradle 9.5 failed immediately because Gradle's API classes are Java 17 bytecode while `buildSrc` tried to compile against them with a Java 8 compiler. Use Java 17+ for `buildSrc`; using Java 25 everywhere is simplest.

Recommended version set as of the assessment date:

| Component | Current | Recommended for this port | Why/source |
|---|---:|---:|---|
| Java toolchain/runtime | 21 | 25 | Required by Paper 26.2 project setup |
| Gradle wrapper | 8.12 | 9.5.0 (9.1 minimum for Java 25 runtime) | Official Java compatibility matrix |
| Kotlin Gradle plugin | 1.9.23 | 2.4.10 | Kotlin 1.9 is not supported on Gradle 9; 2.4.10 supports Gradle 9.5 and Java 25 |
| Shaded Kotlin stdlib | 1.9.20 | 2.4.10 | Keep compiler and shaded runtime aligned |
| paperweight-userdev | 2.0.0-beta.13 | 2.0.0-beta.22 | Current official plugin release ([Plugin Portal](https://plugins.gradle.org/plugin/io.papermc.paperweight.userdev)) |
| Shadow | `io.github.goooler` 8.1.7 | `com.gradleup.shadow` 9.6.1 | Maintained Gradle 9-compatible plugin ([Plugin Portal](https://plugins.gradle.org/plugin/com.gradleup.shadow)) |
| run-paper | 2.3.1 | 3.0.2 | Current official release ([Plugin Portal](https://plugins.gradle.org/search?term=xyz.jpenilla.run-)) |
| Foojay resolver | 0.8.0 | 1.0.0 | Current official release and prepared for Gradle 9 ([Plugin Portal](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention)) |
| Maven Publish | vanniktech 0.34–0.37 (Maven Central) | Gradle `maven-publish` → sneakyrp | Self-hosted `https://maven.sneakyrp.com/releases`; no Central/signing plugin |

The audit successfully configured the project with Gradle 9.5.0, Java 25, Kotlin 2.4.10, Shadow 9.6.1, run-paper 3.0.2, Paperweight beta.22, and Foojay 1.0.0. Publishing targets sneakyrp with git-derived versions.

Also remove or make portable the committed `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64`; it is both Java 21-specific and Linux-specific.

## 2. Paper API and version metadata

Paper changed artifact versions in 26.1 from `-R0.1-SNAPSHOT` to `<mc-version>.build.<build>-<status>`. Gradle's rolling equivalent is `26.2.build.+`; a reproducible build can pin a stable build such as the then-current `26.2.build.116-stable` ([Paper 26.1 versioning explanation](https://papermc.io/news/26-1/), [26.2 Javadocs](https://jd.papermc.io/paper/26.2/)). Update all of these locations:

- root `compileOnly("io.papermc.paper:paper-api:...")`;
- `nms/v26_2/build.gradle` to `paperweight.paperDevBundle("26.2.build.+")` (the dev bundle already contains Paper API, so do not add another API dependency in that module);
- the unused/hard-coded `MSPaperweight` helper or remove it;
- `runServer.minecraftVersion("26.2")`;
- `settings.gradle.kts`, root publishing exclusions, `core` shadow dependency, README examples, and task descriptions that name `v1_21_4` or 1.21.4.

Paper documents `paperweight-userdev` as the only supported way to compile against server internals. For 26.1+, server jars are unobfuscated and Paper no longer supports reobfuscated plugins; `reobfJar` must not be used ([Paperweight userdev documentation](https://docs.papermc.io/paper/dev/userdev/)). The existing `paperweight-mappings-namespace: mojang` manifest entry is consistent with the source's Mojang names, but no reobfuscation output should be introduced.

Set the core `api-version` to `'26.2'`. Paper explicitly lists 26.2 as a valid value and refuses to load that plugin on older servers ([plugin.yml documentation](https://docs.papermc.io/paper/dev/plugin-yml/)). Add the same field to the independently loaded Factions, Memory, Shop, Teams, and Towny plugin descriptors; otherwise Paper treats those jars as legacy plugins and warns. This intentionally makes the release 26.2-only.

## 3. Confirmed public API work

### Compile failure: `InventoryView#getMenuType`

`Util#createInventoryView` implements an anonymous `InventoryView`. In 26.2, `getMenuType()` is abstract and nullable, so the implementation no longer compiles ([26.2 `InventoryView` Javadocs](https://jd.papermc.io/paper/26.2/org/bukkit/inventory/InventoryView.html)). Implement it with the correct crafting `MenuType`, or preferably stop hand-implementing a server-owned API interface and test the crafting path through an actual server inventory/view. Paper's API overview cautions plugins against implementing server interfaces unless explicitly designed for extension ([Paper API overview](https://jd.papermc.io/paper/26.2/)).

### Missing compile dependencies

The 26.2 compile audit also failed because `javax.annotation.Nullable` and `org.json.simple` are no longer available transitively. Replace `javax.annotation.Nullable` with the already-used JetBrains annotation, and either add/shade an explicit `json-simple` dependency or replace the old embedded `Metrics` implementation with the current bStats library. Depending on Paper's transitive graph for application code is fragile.

### Adventure 5

Paper 26.2 ships Adventure 5 and removes previously deprecated ClickEvent/HoverEvent APIs. `BookMeta` no longer extends Adventure's `Book`, although it retains its own component `title`, `author`, and `pages` methods ([Paper 26.2 developer notes](https://papermc.io/news/26-2/), [26.2 `BookMeta` Javadocs](https://jd.papermc.io/paper/26.2/org/bukkit/inventory/meta/BookMeta.html)). Repository search found no ClickEvent/HoverEvent use, and `ConjureBookSpell`'s fluent chain compiled against 26.2 because those methods now belong to `BookMeta`; no change is currently proven there. Book creation/opening still needs a runtime test.

### Other headline 26.2 changes

- Beds are no longer block entities and lose block-state PDC. This repository stores PDC on items/entities, not beds, so no data migration handler is presently indicated. The fake sleeping-player NMS code still constructs a bed block state and is affected by NMS registry changes, discussed below.
- `MagmaCube` no longer extends `Slime`; `SlimeSplitEvent#getEntity` returns `AbstractCubeMob`. No direct affected use was found.
- `PointedDripstone` is deprecated in favor of `Speleothem`, and several spawning/Vex methods changed. No direct affected use was found.

These conclusions come from the official 26.2 developer notes and a repository symbol search, not from claiming exhaustive runtime compatibility.

## 4. Required `v26_2` NMS port

Create `nms/v26_2`, package `com.nisovin.magicspells.volatilecode.v26_2`, and class `VolatileCode_v26_2`. `ManagerVolatile` derives exactly those names from `Bukkit.getMinecraftVersion()`, so a renamed module/class is required for the handler to load.

A clean audit copied the 1.21.4 implementation, renamed it mechanically, and compiled it against the 26.2 dev bundle. It failed in these concrete areas:

- `ResourceLocation` and advancement criterion types are gone/renamed (26.2 diagnostics expose `Identifier`);
- registry constants such as NMS `EntityType.ENDER_DRAGON`, `Blocks.WHITE_BED`, and the direct `ResourceLocation` factory are no longer available in the same form;
- nullable `SynchedEntityData#getNonDefaultValues()` results must be handled before constructing entity-data packets;
- `LivingEntity#getHurtSound0` is unavailable;
- advancement display now accepts `ItemLike`/`ItemStackTemplate`, not the NMS `ItemStack` used here;
- the old advancement `addCriterion`/`Criterion`/`ImpossibleTrigger` construction no longer resolves;
- `ClientboundUpdateAdvancementsPacket` now needs a fifth boolean and uses `AdvancementHolder`/`Identifier` collections.

The rest of this file must be treated as runtime-sensitive even where it compiles. It directly creates fake players, packets, TNT, dragons, items and entity metadata, and reflects private `LivingEntity` fields/methods. Test every `VolatileCodeHandle` operation. In particular, hard-coded entity-data accessor indices (`17`, `14`) can silently target the wrong metadata after an update; replace them with named accessors/API where possible.

Because Mojang removed server obfuscation in 26.1, reflection should use the unobfuscated 26.2 names, but those private names remain non-API and can change in any release ([Paper 26.1 internals notice](https://papermc.io/news/26-1/)).

## 5. Dependency compatibility audit

The build mixes shaded libraries, compile-only APIs, local jars, snapshots, and plugin implementation dependencies. Every integration must be tested on Java 25/Paper 26.2. Minimum actions:

| Dependency/integration | Current coordinate | 26.2 action |
|---|---|---|
| ProtocolLib | 5.1.0 | Update. The official current source marks 26.2 as its maximum tested version, while MagicSpells' coordinate predates it ([ProtocolLib source](https://github.com/dmulloy2/ProtocolLib/blob/master/src/main/java/com/comphenix/protocol/ProtocolLibrary.java)). Use a 26.2-capable 5.4+/development artifact consistently at compile and runtime. |
| PlaceholderAPI | 2.11.6 | Update to 2.12.3, whose official release specifically adds 26.2/version-parser support ([release](https://github.com/PlaceholderAPI/PlaceholderAPI/releases/tag/2.12.3)). |
| CoreProtect | 22.4 | Update compile/runtime API. CoreProtect's official repository advertises 26.2 support and current API docs identify API 12 for plugin 24+ ([repository](https://github.com/PlayPro/CoreProtect), [API docs](https://github.com/PlayPro/CoreProtect/blob/master/docs/api/index.md)). |
| WorldEdit | 7.4.0-SNAPSHOT | Pin a 26.2-capable 7.4.x build rather than an unbounded snapshot. The official 7.4.x changelog is the compatibility source ([changelog](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/CHANGELOG.txt)). |
| WorldGuard | old pinned 7.1 snapshot | Move to a Java-25/current 7.1 build compatible with the selected WorldEdit; remove the comment/pin that deliberately held Java 21. Test region checks and no-magic zones. |
| LibsDisguises | v10.0.25 | Update compile/runtime artifact to a current 26.x release and test all disguise paths ([official releases](https://github.com/libraryaddict/LibsDisguises/releases)). |
| GriefPrevention | 17.0.0 | The official project says 17+ has breaking changes and is not recommended for production; supported production is the 16.x legacy line ([official repository](https://github.com/GriefPrevention/GriefPrevention)). Decide deliberately which API line to support and compile/test against that exact runtime. |
| ACF | 0.5.0-SNAPSHOT | At least align with the project's current 0.5.1-SNAPSHOT, but recognize that ACF remains beta and is lightly maintained ([official repository](https://github.com/aikar/commands)). Exercise all command registration/completion/help paths. |
| EffectLib | commit `477d459` | Verify this exact fork/commit on 26.2 or update it. Its compatibility layer contains legacy/deprecated particle mappings ([official source](https://github.com/Slikey/EffectLib)). Exercise every effect type. |
| Vault API | 1.7.1 | API is largely server-version-neutral, but test with a Java-25-compatible Vault provider/economy implementation. Keep it compile-only. |
| Local jars: CMI, CMILib, SneakyVaults, PathFinder | fixed files in `libs/` | Obtain 26.2/Java-25-compatible builds from their owners and compile/test with exactly the jars intended for release. Their compatibility cannot be established from this repository. |
| SneakyCharacterManager / BagOfHolding, Towny, Factions, Citizens, Residence, PowerNBT, NoCheatPlus | optional hooks | Build a test matrix. A soft dependency prevents load-order failure; it does not make API calls binary-compatible. Disable each hook cleanly when the expected API is absent. |

Also review dependency scopes: plugin APIs that are provided by server plugins should normally be `compileOnly`, not `implementation`; only code deliberately included in `MagicSpells.jar` belongs in the Shadow configuration. Keep all shaded packages relocated and inspect the final jar for duplicate Kotlin/Adventure classes.

## 6. Verification strategy and release gate

There are currently no repository tests under `src/test` and no CI workflow. The port should not ship on compilation alone.

### Automated build gates

1. Add a Java 25 CI job running `clean build`, Kotlin/Java compilation for every module, plugin YAML/resource processing, and Shadow jar creation.
2. Add unit tests for configuration parsing, item/data-component serialization, variables, targeting math, and storage migrations. Prefer pure tests that do not mock server-owned interfaces.
3. Add an integration test or scripted `runServer` smoke run on a pinned stable Paper 26.2 build. Assert no startup exceptions, the `v26_2` volatile handler is selected (not `VolatileCodeDisabled`), commands register, configs load, and shutdown is clean.
4. Run dependency analysis and inspect the shaded jar: no Paper server/API classes, no duplicate Adventure, no unrelocated ACF/EffectLib/math/Kotlin packages, and no missing runtime JSON/annotation classes.
5. Run publishing tasks against a local/staging repository and inspect POM/module metadata after the Gradle/publishing-plugin upgrade.

### Runtime matrix

Exercise at minimum:

- every NMS method: fake slot, TNT simulation, falling-block damage, dragon death, velocity, inventory title, false-player create/update/remove (including sleeping/equipment), hurt animation/sound, toast, fake item spray, and potion color/reflection;
- representative spell families: command, targeted, projectile/homing, particles/effects, inventory/menu/crafting, books, item/data components, disguises, schematics, WorldGuard, economy, ProtocolLib, PlaceholderAPI, CoreProtect, and persistence;
- fresh server and upgraded copies of real 1.21.4 worlds/config/data; verify overworld/nether/end, altered blocks, spellbooks, variables, database/TXT storage, recipes, and plugin reload/restart behavior;
- optional integration matrix with each soft dependency absent, present at its supported 26.2 version, and combinations used in production;
- Java 25 startup and a client on 26.2, with packet-heavy tests watched for disconnects and protocol encoder errors.

### Deployment

Back up the entire server, rehearse conversion on a clone, freeze writes during the final migration, and retain the old server plus pre-upgrade backup as the rollback unit. Do not attempt an in-place downgrade after Paper 26.2 has opened the world. Paper notes that a `Missing file: /data/minecraft/game_rules.dat` log message is expected and safe to ignore on 26.2 ([Paper 26.2 announcement](https://papermc.io/news/26-2/)).

## Suggested implementation order

1. Upgrade Java/Gradle/Kotlin/build plugins and make the public-API modules compile against Paper 26.2.
2. Fix `InventoryView`, explicit JSON/annotation dependencies, and all warnings marked for removal that are cheap to migrate (registries, potion data, attribute modifiers, legacy effects).
3. Create and port `v26_2`, then add focused runtime tests for every volatile operation.
4. Update integration dependencies/scopes and plugin metadata.
5. Add automated tests/CI, build and inspect the shaded artifact, and test Maven publication.
6. Rehearse a complete server/world migration, execute the runtime matrix, and only then release the 26.2-only build.
