# Shared soulbound / expiration migration

Magic-item definitions, matching, fingerprinting, inventory updating, and legacy
ItemTag mappings live in **SneakyItems**. MagicsSpells registers its `magic-items:`
into that registry (PDC namespace `magicspells`) and keeps conjure overlays plus
`soulbound`/`expiration` definition flags. `MagicItemBehaviors` /
`MagicItemExpirationScheduler` in SneakyItems are thin adapters to SneakyUtils.
Enforcement listeners, scheduler state, expiration lore and
`com.sneakyutils.events.ItemExpireEvent` belong to SneakyUtils.
The provider defers its initial online-player scan until the next tick so dependent
listeners can register. MagicSpells-only reloads purge/reschedule through the provider
API after passives and `MagicSpellsLoadedEvent` are ready; no consumer scheduler is added.
`expireitem` consumes that event directly. The old MagicItemExpireEvent is removed;
there is no bridge and external event consumers must recompile if any are added.

## Immutable storage contract

- `magicspells:soulbound_owner`: STRING UUID, preserved as the exact stored string.
- `magicspells:expires_at`: LONG Unix epoch milliseconds, preserved as the exact long.

No item migration, rebinding, deadline recomputation or namespace rewrite occurs.
The updater's `MagicItemBehaviorState` snapshots/restores both values around
replacement metadata. Tests include an uppercase UUID and a deadline larger than
an exactly representable double, to detect normalization and precision loss.

Creative bypass is now `sneakyutils.nosoulbound`, still requiring Creative mode.
Update permission grants separately; do not rewrite item data.

## Publication / merge gate

**Publication gate fulfilled:** SneakyUtils PR #8 merged as
`f8f8c4cb7512d82671733f53c980febe66fa4db0`; publish run `41307785625`
succeeded. Maven metadata, the published POM and JAR confirm
`io.github.team-sneakymouse:sneakyutils:2026.11.01-f8f8c4cb7512`
from `https://maven.sneakyrp.com/releases`. The JAR contains `ItemBehaviors`
and `ItemExpireEvent`; its SHA-256 is
`036234a01a1162047d370f007b119412fb4a703492d9083ba759f4b2cdf2f36b`.

`core/build.gradle` pins that version for compile-only and test-only use.
The temporary local JAR override has been removed; the provider is never shaded.
Normal published-dependency verification:

```sh
./gradlew build check \
  --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -XX:ActiveProcessorCount=2' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Result: BUILD SUCCESSFUL; JUnit XML reports 198 tests, zero failures, errors or
skips. The resolved Maven-cache JAR matches the published checksum, publication
and deployable-content guards pass, and the deployable JAR contains no
`com/sneakyutils/` classes. No merge or deployment is authorized by this work.

Roll out/roll back matching provider and consumer versions together: the old
MagicSpells build registers enforcement itself, so mixing it with the new provider
would double-register behaviors. Persisted items require no conversion in either
direction. After final publication/pinning, rebuild both and exercise on an existing
Paper server (no server provisioning is part of this change): standalone provider
without MagicSpells; literal legacy items; both hands/armor/storage/ender chest;
soulbound permissions in Creative and Survival; expiration deadlines and lore;
SCM absent, enabled before/after and character inventory restoration; `expireitem`
passive activation once. These live checks remain pending, not established by mocks.
