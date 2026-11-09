# Cast-item listener verification

## Behavior

- Passive `leftclickitem`, `rightclickitem`, and `playeranimate` keep an explicit item restriction even when every configured item fails to resolve. Such a trigger never activates instead of accepting every item. Missing/empty trigger variables retain their intentionally unrestricted behavior. Mixed lists still accept successfully resolved entries through the existing matcher.
- `right-click-cast-item(s)` and their tag equivalents also handle right-clicks on entities, using the event's hand. Handled interactions are cancelled.
- `left-click-cast-item(s)` and their tag equivalents also handle direct player melee attacks (`ENTITY_ATTACK`), using the main hand. Handled attacks are cancelled, preventing vanilla damage. Projectile and sweep damage are not extra triggers.
- Entity triggers share the existing click listener's learned-spell, permission, global-cooldown, creator-name arguments, cloned cast item and deferred `hardCast` path. Cancellation means an eligible cast was queued, not that the eventual spell succeeded. Existing cancelled-event handling is unchanged: these click listeners do not ignore cancelled events.
- A handled melee trigger uses the existing bound-cast suppression window (150 ms), preventing its associated delayed arm-swing/interaction cast from also casting a bound spell. This does not introduce a new permanent player registry.

## Automated evidence

JUnit 5 / Mockito tests call the real listeners; server, spell execution and item parser/resolver boundaries are mocked. No MockBukkit or test server was added. The invalid-item regressions were observed failing with unwanted activation before the fix. Entity tests first failed for absent handlers; the overlap regression first failed with two casts instead of one.

The build pins the published `SneakyVoiceChat:2026.11.02-d30ec916109c` release; no dependency override is required. Production target is the source build's Paper 26.2 / Java 25, not the older AGENTS.md runtime table.

## In-game checklist (not performed)

- Configure unresolved passive item references and try air/block clicks plus arm swings with several unrelated items: no passive activation. Confirm missing variables still behave as before and a mixed valid/invalid list accepts only valid entries.
- Test exact and tagged entity click items on living entities, armor stands and other interactable entities; test main/off hand separately. Confirm cancellation prevents vanilla interactions only when an eligible cast was queued.
- Test melee items with cast-on-animate enabled and disabled, including a bound spell on the same item; count one intended click-item cast per attack and no duplicate bound cast. Verify failed/missing permissions, cooldowns and wrong items do not suppress ordinary attacks.
- Check projectile/sweep attacks do not trigger extra casts; verify existing air/block click gates, creator-name arguments and cooldown-ignore spells.
- Validate interaction behavior alongside protection plugins, which can cancel the entity events before MagicSpells, and check rapid attacks around the inherited suppression window.

The wiki checkout is not part of this worktree; carry the behavior notes to the wiki when publishing.
