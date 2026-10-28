# Levitate lifecycle fix

## Regression

Commit `3ebf61e8cc12b670cf0b32b4d529f3173e66dc82` (2026-10-10),
“Migrate remaining spell bodies to CastResult cast(SpellData),” deleted the
`toggle && isLevitating(caster)` branch from `castSpell` without moving it to
`cast(SpellData)`. This is the source-level cause of the recent player-recast
regression, not merely a difference between normal casts and targeted subcasts.
The player pipeline is `Spell.onCast` → `cast(state, data)` → `cast(data)`.

Cancellation now happens before target acquisition for normal casts, the
state-aware player entrypoint, the legacy bridge, and targeted subcasts. As
before the migration, an active toggle can be stopped during cooldown; this does
not authorize a new cast in a failed state. `toggle: false` replaces an active
runner, cancelling it before scheduling its replacement.

## Configuration / next-release wiki update

Apply these changes to **Levitate Spell** in the project wiki when this PR is
merged (the wiki currently describes released `main`):

- `max-distance`: default **20**, previously 200. The inclusive maximum
  caster-to-target distance, in blocks. An explicit configured value still wins.
- Crossing worlds or exceeding `max-distance` **terminates** levitation on the
  next runner tick. Returning to the same world or moving back within range does
  not restart it; a new cast is required.
- `toggle`: default `true`. A second cast stops the caster's active levitation,
  including targeted subcasts, without requiring a new valid target.
- With `toggle: false`, a successful recast replaces the previous levitation
  rather than leaving an untracked repeating task behind.
- Death/invalidity of either participant and duration expiry cancel the task and
  clear its caster registry entry. No velocity is applied after termination.

No configuration file is rewritten. Existing `max-distance: 200` settings remain
200; remove the override or set `max-distance: 20` to use the new limit.

## Verification

`LevitateSpellTest` uses the real cast methods and runner with API entity and
scheduler proxies, bypassing unrelated plugin bootstrap. It checks actual task
cancellation, registry cleanup, and inert stale callbacks; it is not a live Paper
server or client-physics test. No MockBukkit dependency is added.

Manual Paper checklist (not executed on this host):

1. Cast a long-duration levitate with `toggle: true`, then look away and cast
   again. The old target must be released; repeat via targeted subcast and while
   on cooldown. Test normal targeting and messages on a fresh cast.
2. With no `max-distance` override, test exactly 20 blocks and then more than 20.
   Return inside 20: the target must remain released until a new cast. Repeat
   with a configured 35-block bound.
3. Teleport either participant to a different world and wait one configured
   runner interval. Return: no levitation resumes. Test player disconnect and
   target death/removal as well.
4. With `toggle: false`, recast onto another target: only the new target should
   move. Verify item-switch, damage, duration, plugin disable and target-removal
   cancellation still release all applicable runners.

No deployment, merge, server restart, or live world mutation is part of this PR.
