# MenuSpell virtual inventory verification

Target: the repository-pinned Paper `26.2.build.116-stable` / Java 25. No Paper, Minecraft, NMS or Java target upgrade is required.

MenuSpell uses `MenuType.GENERIC_9X1` through `GENERIC_9X6` and `MenuType.create(player, Component)` to create a location-free `InventoryView`. Initial opens, option/page refreshes and shift-click drop refreshes populate its top inventory before opening it. The internal spell-name title and existing client-visible title replacement are retained. A weak inventory set registers each top inventory before opening; the central updater guard excludes its entire contents, including filler and navigation items, before tagging, rebuilding, expiration handling or writing contents. Normal inventories retain their existing updater path.

## API/source checks

- The pinned API JAR exposes `MenuType.create(HumanEntity, Component)` and all six chest sizes (`javap` verified).
- Official implementation inspected at Paper commit `37dc5450d9b7acb2e367e8f1fbe0f07bb01334d9`:
  - [CraftMenuType](https://github.com/PaperMC/Paper/blob/37dc5450d9b7acb2e367e8f1fbe0f07bb01334d9/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftMenuType.java): `create` builds a new view without a location.
  - [CraftBlockEntityInventoryViewBuilder](https://github.com/PaperMC/Paper/blob/37dc5450d9b7acb2e367e8f1fbe0f07bb01334d9/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/view/builder/CraftBlockEntityInventoryViewBuilder.java), [CraftDoubleChestInventoryViewBuilder](https://github.com/PaperMC/Paper/blob/37dc5450d9b7acb2e367e8f1fbe0f07bb01334d9/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/view/builder/CraftDoubleChestInventoryViewBuilder.java): absent locations create virtual containers rather than borrowing world chests.
  - [CraftInventoryView](https://github.com/PaperMC/Paper/blob/37dc5450d9b7acb2e367e8f1fbe0f07bb01334d9/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftInventoryView.java) retains its top inventory strongly for the view lifetime, so the weak exclusion set does not keep closed menus alive.

These source inspections and API-only Mockito tests are not live-server/client verification. There is no new YAML, command, permission or item/PDC format.

## Manual checklist (not yet run)

On an existing test server with the pinned Paper version and the normal MagicSpells dependencies:

- [ ] Open configured menus with 1–6 rows; compare titles, formatting, filler, option amounts, translated name/lore, modifiers and delayed opening with the previous build.
- [ ] Open menus while standing on a chest/double chest and verify the real container is untouched; walk away and keep interacting.
- [ ] With more than 50 auto-arranged options, navigate next/previous pages, including the first/last boundaries, spacer and refresh after stay-open option casts.
- [ ] Exercise left/right/middle, sneak-left/right, offhand swap and drop option spells; compare target, arguments, power, variable mods and close/stay-open outcomes.
- [ ] Exercise configured cursor-drop and player-inventory shift-click drop spells, successful refresh and failed/absent spell outcomes. Ordinary bottom-inventory clicks should still work; shift transfer into the menu and drags should still be cancelled.
- [ ] Include named magic-item options, filler/navigation magic items and expiring display items with updater enabled and tag mappings present. Open and refresh: display items must not be tagged, version-stamped, rewritten or removed by MagicItemUpdater. Real player/chest items must still update.
- [ ] Close via Escape, close another inventory, quit/rejoin and reload MagicSpells. Check no menu items spill into the world or player inventory, no duplication/loss, no unexpected sounds and no stale menu interception.

Rollback: restore the prior plugin JAR and reload/restart through the normal operator process. No persistent-data migration is involved. Nothing in this change merges or deploys the plugin.
