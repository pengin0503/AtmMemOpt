# AtmMemOpt

Personal memory & performance optimization mod for **All the Mods 10 (ATM10)**.

- **Minecraft:** 1.21.1 / **NeoForge:** 21.1.266 / **Java:** 21
- **Status:** Phase 8/9 + Stage 3 — M22/M23 interning, M24/M25/M26 diagnostics, and JUnit 5 unit tests implemented; code edited only (Stage 3 compilation not yet verified), ready for build & in-game verification. See `stage3-report.md`.
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
| M7 | Vanilla (`VoxelShape`) | Deduplicates `DoubleArrayList` / `BitSetDiscreteVoxelShape` / `ArrayVoxelShape`; shape cache capped at 32,768 entries with `ReferenceQueue` eviction |
| M8a | JEI | Parallel search-filter index build; `TOOLTIPS` index deferred to the first `#` search (measured 15.9 s → 0.8 s) |
| M8b | JEIStuff | Skips the 2,595-entry loot-table scan on world join (~5.9 s saved) |
| M8c | Vanilla reload | Logs the top 10 slowest resource/data-pack reload listeners |
| M9a | Vanilla rendering | Distance-culls BlockEntityRenderer beyond 48 blocks (large multiblocks whitelisted, config hot-reload & `/atmmemopt diag ber` included) |
| M9b | Vanilla client | Caps FPS at 15 while the window is unfocused |
| M9c | Vanilla particles | Distance-culls particle spawning beyond 48 blocks (before allocation) and suppresses new particles while unfocused |
| M9d | Vanilla entities | Distance-culls non-whitelisted entities (64 blocks general / 32 blocks `ItemEntity` / 24 blocks `ExperienceOrb`; players, bosses, `noCulling` auto-excluded) |
| M10 | Vanilla (`DataComponentPatch`) | Interns immutable `DataComponentPatch` instances (single-entry fast path + bounded weak cache) and shares backing `Reference2ObjectMap` copy-on-write on `asPatch()` |
| M11 | Vanilla (`PalettedContainer`) | Replaces per-container `ThreadingDetector` (`Semaphore` + `ReentrantLock`) with a shared no-op detector |
| M12 | Vanilla (`ResourceLocation`) | Interns `namespace` strings (zero-alloc `"minecraft"` fast path) and deduplicates `ResourceLocation` instances in a bounded weak cache |
| M13/18 | Vanilla (`Climate.Parameter` / `RTree`) | Interns immutable `Biome` `Climate.Parameter(min, max)` instances and `Climate.Parameter[]` parameter-space arrays |
| M14 | Vanilla (`SpriteContents`) | Closes and drops static sprite mipmap `NativeImage` levels (1..N) after GPU atlas upload (keeps `originalImage` level 0 intact) |
| M15/17 | Vanilla (`BakedQuad` / `FaceBakery`) | Deduplicates identical `BakedQuad` vertex `int[]` arrays and interns `BakedQuad` instances in bounded weak caches |
| M16 | Vanilla (`TagEntry`) | Interns immutable `TagEntry` instances across datapack tags |
| M20 | Vanilla (`Mob`) | Throttles `serverAiStep()` for distant non-critical mobs (1/2 beyond 48 blocks, 1/4 beyond 64 blocks; bosses, villagers, pets, named/persistent, active-target mobs excluded) |
| M21 | Vanilla (`ItemEntity` / `ExperienceOrb`) | Expands drop/XP merge search radius (default 1.5 blocks) to merge dense drops faster |
| M22 | Vanilla (`Style`) | Interns **plain** immutable `Style` instances (no click/hover event, font or insertion) from the `Style#create` factory path; `Style.EMPTY` fast path, bounded weak cache (default 65,536) |
| M23 | Vanilla (`TagKey` / `ResourceKey`) | Interns exact-class `TagKey` / `ResourceKey` instances by canonical (registry, `ResourceLocation`) pair in bounded weak caches (shared cap 32,768); no `Holder` keys |
| M24 | Vanilla (`BitmapProvider` font images) | **Diagnostics only** in Stage 3 (default OFF): counts font glyph `NativeImage`s; no post-upload release because vanilla provider reuses the image on later bakes |
| M25 | Vanilla reload listeners | **Diagnostics only** (default OFF): per-listener heap-used deltas alongside M8c timings; measures `AuxRecipeManager`/`RecipeManager` retention without touching recipe data |
| M26 | Vanilla (`ModelBakery`) | **Audit only** (default OFF): counts staging maps still reachable after client model reload; cleanup is not implemented and no ModelBakery state is modified |

Measured results (Phase 4, spark heap summary): the former top heap consumers (`DerivedComponentMap`, `ItemEnchantments`, `SpellContainer`, `CommonToken`) all dropped out of the top 100; JEI filter build 15.9 s → 0.8 s; ZGC major-GC interval 19.9 s → 105 s.

- Phase 4 in-game test (2026-11-27): JEI filter build 15.9 s → **815 ms**; heap held at 8,476 MB / 12,000 MB.

## Commands

Client commands (`/atmmemopt`):
- `/atmmemopt status` — heap / metaspace / direct-buffer usage plus per-module actual Mixin state and savings counters (M1〜M26)
- `/atmmemopt purge-shader-cache` — immediately releases the Iris `glsl_transformer` caches
- `/atmmemopt purge-caches` — purges all weak/shader caches across M3, M4, M6, M7, M10, M12, M13, M15, M16, M22, and M23
- `/atmmemopt diag all` — runs `status` + `sprites` + `ber` + `entity` (+ `nativeimage` if M5 is enabled) in a single command
- `/atmmemopt diag nativeimage [on|off|reset]` — top 20 allocation sites of unclosed `NativeImage`s (requires M5 enabled + restart)
- `/atmmemopt diag ber [reset]` — top 20 dispatched/culled BlockEntityRenderer classes (with whitelist status)
- `/atmmemopt diag entity [reset]` — top 20 rendered/culled Entity classes (with whitelist status)
- `/atmmemopt diag sprites` — zero-overhead breakdown of `TextureAtlas` / `SpriteContents` live `NativeImage` counts and estimated native memory usage

Server / OP commands (permission level 2):
- `/atmmemopt-server status` (or `/atmmemopt status` on Dedicated Server) — JVM memory summary and server/common module counters
- `/atmmemopt-server purge-caches` (or `/atmmemopt purge-caches` on Dedicated Server) — purges server/common weak caches

## Configuration

`config/atmmemopt-common.toml` — one flag per module (`enableOwoDerivedMapOpt`, `enableDataComponentPatchOpt`, `enablePalettedContainerOpt`, `enableResourceLocationOpt`, `enableClimateParameterOpt`, …) plus tunables such as `itemEnchantmentsMaxCacheEntries` (32768), `voxelShapeMaxCacheEntries` (32768), `dataComponentPatchMaxCacheEntries` (65536), `resourceLocationMaxCacheEntries` (131072), `berCullingDistance` (48), and `unfocusedFpsLimit` (15). Stage 3 adds `enablePlainStyleInterning` (true) / `plainStyleMaxCacheEntries` (65536), `enableTagAndResourceKeyInterning` (true) / `tagKeyMaxCacheEntries` (32768, shared cap for both caches), and three opt-in diagnostics flags: `enableFontNativeImageRelease`, `enableReloadArtifactAudit`, `enableModelBakeryCleanup` (all default false; the latter two only measure, and M24 never closes images in this stage). See `docs/handoff.md` §4 for the full list.

## Building

Requires JDK 21. Target mod `compileOnly` dependencies are resolved automatically from Modrinth Maven and CurseMaven (you can optionally pass `-Patm10_mods_dir=<path>` to compile against local ATM10 mod JARs instead).

```bash
./gradlew --no-daemon build
```

Output: `build/libs/atmmemopt-0.1.0.jar`. Full build notes in `docs/handoff.md` §1.

## Documentation

- `docs/handoff.md` — module specs, config reference, measured results, and the Phase 4 in-game test checklist
- `docs/investigation.md` — Phase 1 bytecode investigation of the target classes
- `docs/load_profile.md` — Phase 4 load-time analysis
- `stage3-report.md` — Stage 3 (M22–M26) implementation / audit-only notes and unverified-compilation disclaimer
