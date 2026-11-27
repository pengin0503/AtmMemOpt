# AtmMemOpt

Personal memory & performance optimization mod for **All the Mods 10 (ATM10)**.

- **Minecraft:** 1.21.1 / **NeoForge:** 21.1.266 / **Java:** 21
- **Status:** Phase 4 — `atmmemopt-0.1.0.jar` built; in-game verification of the Phase 4 modules (M8a/M9) in progress
- **License:** MIT

> ⚠️ Built and tuned for one specific ATM10 (v8.6, 400+ mods) setup. It patches internals of other mods via mixins, so it may break on different mod sets or versions. Use at your own risk. Every module can be switched off individually (see Configuration).

## What it does

AtmMemOpt reduces heap pressure and GC churn in a large modpack by interning / sharing immutable objects that some mods (and vanilla) allocate in bulk, trimming startup work in JEI, and cutting a few render costs. All modules are gated twice — by a config flag and by `AtmMemOptMixinPlugin`, which checks that the target mod is actually loaded — so a module never applies to a missing or changed target.

| Module | Target | Effect |
|---|---|---|
| M1 | owo-lib (`DerivedComponentMap`) | Unwraps the derived-component wrapper around item stacks; measured −228 MB of heap |
| M2 | Vanilla (`PatchedDataComponentMap`) | Normalizes empty patches; fast paths for empty `applyPatch` / `restorePatch` / `copy` (compatible with Accessories' `invasiveopts` and FerriteCore) |
| M3 | Iron's Spells 'n Spellbooks | Interns `SpellData` / `SpellSlot` / `SpellContainer` by (spell, level, locked) key |
| M4 | Iris | Auto-purges the `glsl_transformer` parser caches 30 s after shader load; manual purge command included |
| M5 | Vanilla (`NativeImage`) | Diagnostics only (default OFF): tracks unclosed `NativeImage` allocations |
| M6 | Vanilla (`ItemEnchantments`) | Interns enchantment maps (reference-identity `Holder` keys + single-enchant fast path); unifies empty instances |
| M7 | Vanilla (`VoxelShape`) | Deduplicates `DoubleArrayList` / `ArrayVoxelShape`; shape cache capped at 32,768 entries with `ReferenceQueue` eviction |
| M8a | JEI | Parallel search-filter index build; `TOOLTIPS` index deferred to the first `#` search (was ~15.9 s of startup) |
| M8b | JEIStuff | Skips the 2,595-entry loot-table scan on world join (~5.9 s saved) |
| M8c | Vanilla reload | Logs the top 10 slowest resource/data-pack reload listeners |
| M9a | Vanilla rendering | Distance-culls BlockEntityRenderer beyond 48 blocks (large multiblocks whitelisted) |
| M9b | Vanilla client | Caps FPS at 15 while the window is unfocused |

Measured results (Phase 3, spark heap summary): the former top heap consumers (`DerivedComponentMap`, `ItemEnchantments`, `SpellContainer`, `ArrayVoxelShape`, `CommonToken`) all dropped out of the top 100; total heap 8.7 GB → 8.4 GB; ZGC major-GC interval 19.9 s → 52.3 s.

## Commands (client)

- `/atmmemopt status` — heap / metaspace / direct-buffer usage plus per-module state and savings counters
- `/atmmemopt purge-shader-cache` — immediately releases the Iris `glsl_transformer` caches
- `/atmmemopt diag nativeimage [on|off|reset]` — top 20 allocation sites of unclosed `NativeImage`s (requires M5 enabled + restart)

## Configuration

`config/atmmemopt-common.toml` — one flag per module (`enableOwoDerivedMapOpt`, `enableIronsSpellContainerOpt`, `enableVoxelShapeOpt`, …) plus tunables such as `voxelShapeMaxCacheEntries` (32768), `berCullingDistance` (48), and `unfocusedFpsLimit` (15). See `docs/handoff.md` §4 for the full list.

## Building

Requires JDK 21. Dependencies on the target mods are `compileOnly` file references into a local ATM10 instance — set `atm10_mods_dir` in `gradle.properties` to your own ATM10 `mods` folder first.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10"
.\gradlew.bat "-Dorg.gradle.native=false" --no-daemon build
```

Output: `build/libs/atmmemopt-0.1.0.jar`. Full build notes in `docs/handoff.md` §1.

## Documentation

- `docs/handoff.md` — module specs, config reference, measured results, and the Phase 4 in-game test checklist
- `docs/investigation.md` — Phase 1 bytecode investigation of the target classes
- `docs/load_profile.md` — Phase 4 load-time analysis
