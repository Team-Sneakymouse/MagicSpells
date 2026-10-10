# Agent instructions (MagicSpells / DvZ)

Paper MagicSpells fork for Lords of Minecraft. Prefer this file plus workspace rules under `MagicSpells-Workspace/.cursor/rules/`.

## Commands

| Task | Command |
|------|---------|
| Build + unit tests | `./gradlew build` (from `MagicSpells/` or workspace root) |
| Unit tests only | `./gradlew :core:test` |
| One test class | `./gradlew :core:test --tests 'com.nisovin.magicspells.util.SplitParamsTest'` |
| Local Paper server | `./gradlew runServer` |
| Wiki indexes (workspace) | `python3 scripts/wiki/generate-all.py` |

## Testing policy (required)

1. If you change **pure / extractable** behavior (parsers, ConfigData helpers, serializers, math/selection, file I/O, index-backed config checks), **add or update** JUnit tests under `core/src/test/java/` in the same change.
2. Run **`./gradlew :core:test`** (or full `build`) and fix failures before finishing that work.
3. Do **not** treat `./gradlew runServer` as the only verification for pure-logic changes.
4. Full cast pipelines, listeners, NMS, and world-facing spells stay on **`runServer`** + `core/run/plugins/MagicSpells/spells-*.yml` (see workspace overview).

### What to test how

| Kind of code | Approach |
|--------------|----------|
| Bukkit-free `util/` / extracted helpers | Plain **JUnit 5** (see `ParamUtil` + `SplitParamsTest`) |
| Spell YAML surface (class + option keys) | **`SpellSnippetValidator`** tests (index-backed; no live server) |
| Live spell behavior / targeting / effects | **`runServer`** + `spells-test.yml` (or dedicated `spells-*.yml`) |
| Warning-migration spot-checks | Checklist: `MANUAL-TEST-WARNING-MIGRATION.md`; fixtures: `dev-fixtures/spells-warning-migration.yml` → copy into `core/run/plugins/MagicSpells/` |
| Targeted cast smoke (post-suppress) | Checklist: `MANUAL-TEST-TARGETED-CAST.md`; fixtures: `dev-fixtures/spells-targeted-cast-smoke.yml` |

Do **not** add MockBukkit for full cast graphs unless a dedicated harness exists. Prefer extracting logic out of Bukkit-heavy types (`ParamUtil` pattern) so tests stay cheap.

### Spell snippet checks

- Helper: `com.nisovin.magicspells.util.config.SpellSnippetValidator` (test sources).
- Index fixture: `core/src/test/resources/.../spell-option-index.json` (regenerate via `python3 scripts/wiki/export-test-spell-index.py` after wiki index updates).
- Use this when agents author or change example YAML / fixture spells and need “does this `spell-class` / option key exist?” confidence without Paper.

## Wiki

User-facing spell/effect/config changes are incomplete until `MagicSpells.wiki/` is updated (workspace wiki-documentation rule).

## Definition of done (logic / config-surface work)

- [ ] Tests added or updated under `core/src/test/java/` when the change is testable without a live world
- [ ] `./gradlew :core:test` passes
- [ ] Wiki updated when YAML/API surface changed
- [ ] No secrets committed
