# Shared soulbound / expiration migration

MagicSpells keeps `soulbound`, `expiration`, conjure overlays and registry-based
receive handling. `MagicItemBehaviors` and `MagicItemExpirationScheduler` are thin
compatibility adapters to SneakyUtils. Enforcement listeners, scheduler state,
expiration lore and `com.sneakyutils.events.ItemExpireEvent` belong to SneakyUtils.
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

**This consumer must remain draft until a genuine SneakyUtils artifact containing
this API is published and the normal Maven dependency pin in `core/build.gradle`
is updated to that actual published version.** The old pin intentionally remains
rather than inventing an available release. No release, merge or deployment is
authorized by this work. CI against the old pin cannot compile the new API.

For task-local verification only:

```sh
./gradlew build check -PsneakyUtilsJar=/absolute/path/to/provider.jar \
  --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -XX:ActiveProcessorCount=2' \
  -Pkotlin.compiler.execution.strategy=in-process
```

This override is compile-only plus test-only, never shaded. Record the exact
provider JAR checksum with test evidence; remove reliance on the override before
marking this PR ready.

Roll out/roll back matching provider and consumer versions together: the old
MagicSpells build registers enforcement itself, so mixing it with the new provider
would double-register behaviors. Persisted items require no conversion in either
direction. After final publication/pinning, rebuild both and exercise on an existing
Paper server (no server provisioning is part of this change): standalone provider
without MagicSpells; literal legacy items; both hands/armor/storage/ender chest;
soulbound permissions in Creative and Survival; expiration deadlines and lore;
SCM absent, enabled before/after and character inventory restoration; `expireitem`
passive activation once. These live checks remain pending, not established by mocks.
