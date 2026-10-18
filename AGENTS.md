# Agent instructions (MagicSpells / main)

Paper MagicSpells fork for Lords of Minecraft. Prefer this file plus workspace rules under `MagicSpells-Workspace/.cursor/rules/`.

<!-- BEGIN TEAM-POLICY MANAGED BOOTSTRAP -->
This project is enrolled in Team-Sneakymouse development policy. Before implementing, or when authorization or risk is unclear, read `.team-policy/manifest.json` and `.team-policy/policy/core.md` from this project root (`MagicSpells/`). Read `.team-policy/policy/scope-and-testing.md` when classifying risk or changing behavior. Read `.team-policy/workflows/development.md` for nontrivial multi-step work. Read `.team-policy/policy/sneakyutils.md` when adding or sharing Paper util/manager code. Report a missing payload or a manifest/payload revision mismatch rather than substituting remembered rules. Load optional skills from `.team-policy/skills/` only when relevant. Follow local build/architecture facts and explicit exceptions below. When host instructions and team policy repeat the same norm, follow the host wording. If they require incompatible actions, surface the conflict before expanding work. Distinguish running checks from implementing tests; a source edit does not automatically require a new test.
<!-- END TEAM-POLICY MANAGED BOOTSTRAP -->

## Project facts

| | |
|--|--|
| Project | MagicSpells (Team-Sneakymouse fork) |
| Shared policy | revision in `.team-policy/manifest.json` (this directory) |
| Workspace | parent `MagicSpells-Workspace/` (wiki submodule, cursor/codex rules, `Ms_Scripts` symlink) |
| Runtime targets | Paper 1.21.4 test server via `runServer`; see Gradle/NMS modules |
| Deployment | branches listed in `.github/workflows/publish.yml` publish to sneakyrp |

**Branches / sneakyrp publish:** live vs milestone vs feature — see **Branch roles** in `MagicSpells-Workspace/.cursor/rules/workspace-overview.mdc`; publisher list is `.github/workflows/publish.yml`.

For **gameplay YAML packs** (not this plugin), edit sibling `MagicSpells_Scripting/` via workspace symlink `Ms_Scripts`. Scripting agent rules live in `MagicSpells-Workspace/.cursor/rules/` (**script mode**); packs are not their own git repo and are **not** enrolled in this `.team-policy/` payload.

### Local exceptions

- **Stricter testing for pure/extractable logic:** if you change pure / extractable behavior (parsers, ConfigData helpers, serializers, math/selection, file I/O, index-backed config checks), add or update JUnit tests under `core/src/test/java/` in the same change and run `./gradlew :core:test` (or full `build`). Rationale: existing MagicSpells harness covers this surface; keep it green. Approver: pilot enrollment 2026-10-18. Bukkit-heavy cast pipelines remain on `runServer` evidence per the table below.

## Commands

| Task | Command |
|------|---------|
| Build + unit tests | `./gradlew build` (from `MagicSpells/` or workspace root) |
| Unit tests only | `./gradlew :core:test` |
| One test class | `./gradlew :core:test --tests 'com.nisovin.magicspells.util.SplitParamsTest'` |
| Local Paper server | `./gradlew runServer` |
| Wiki indexes (workspace) | `python3 scripts/wiki/generate-all.py` |

## Testing (local exception + shared risk gates)

1. If you change **pure / extractable** behavior (parsers, ConfigData helpers, serializers, math/selection, file I/O, index-backed config checks), **add or update** JUnit tests under `core/src/test/java/` in the same change.
2. Run **`./gradlew :core:test`** (or full `build`) and fix failures before finishing that work.
3. Do **not** treat `./gradlew runServer` as the only verification for pure-logic changes.
4. Full cast pipelines, listeners, NMS, and world-facing spells stay on **`runServer`** + `core/run/plugins/MagicSpells/spells-*.yml` (see workspace overview).
5. High-risk one-liners (permissions, persistence, concurrency) still follow shared policy risk gates even when the diff is small.

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
