# AtmMemOpt 引き継ぎドキュメント (`docs/handoff.md`)

- **最終更新:** 2026-11-27
- **プロジェクトパス:** `C:\Users\pengin0053\.gemini\antigravity\scratch\AtmMemOpt`
- **対象環境:** Minecraft 1.21.1 / NeoForge 21.1.266 / Java 21（個人用 ATM10 環境）
- **参照Modフォルダ（読み取り専用）:** `D:\PrismLauncher\instances\All the Mods 10 - ATM10\minecraft\mods`
- **最新ビルド成果物:** `build/libs/atmmemopt-0.1.0.jar`（Phase 4 完了時点 / 108.0 KB）

---

## 1. ビルド手順

システムのデフォルト `java` が JDK 17 のため、必ず **JDK 21** を指定してビルドする。

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd "C:\Users\pengin0053\.gemini\antigravity\scratch\AtmMemOpt"
.\gradlew.bat "-Dorg.gradle.native=false" --no-daemon build
```

> **Note:** サンドボックスや環境依存の `native-platform.dll` エラー回避のため `"-Dorg.gradle.native=false"` を付与（PowerShell ではクォート必須）。

---

## 2. 実装済みモジュール一覧（Module 1〜9）

全モジュールは `config/atmmemopt-common.toml` のフラグと `AtmMemOptMixinPlugin`（`LoadingModList` による対象Mod存在チェック）の二重制御で安全に ON/OFF 可能。

| # | モジュール名 | 対象Mod / クラス | 実装内容・仕様 | 状態 |
|---|---|---|---|---|
| **M1** | oωo-lib `DerivedComponentMap` 遅延化・解除 | `owo` (`DerivedComponentMap`, `ItemStack`) | `delegate` (`PatchedDataComponentMap`) を `@WrapOperation` で Lazy 化。さらに `deriveStackComponents` 未オーバーライドの `Item` は `ItemStack.<init>` 末尾で外側 `PatchedDataComponentMap` を差し替えず内部 `prototype` のみ `base` に復元し、`owo$derivedMap` を `static final MethodHandle#invokeExact` で `null` 化。 | 実機検証済（-228MB） |
| **M2** | `PatchedDataComponentMap` 空パッチ正規化 | バニラ (`PatchedDataComponentMap`) | `invasiveopts`（Accessories の `applyPatchHead`/`Tail` ロックと `restorePatch` 通知）および `ferritecore` と競合しない範囲で、`fromPatch(EMPTY)` 高速化、`applyPatch` の空パッチ時 `ensureMapOwnership()` スキップ、空→空の `restorePatch` / `copy` 正規化を実施。 | 実装済・修正済 |
| **M3** | Iron's Spellbooks `SpellContainer` 共有化 | `irons_spellbooks` (`SpellData`, `SpellSlot`, `SpellContainer`, `ISpellContainer`) | `(spell, spellLevel, locked)` の3要素キーで `SpellData` / `SpellSlot` / 空 `SpellSlot[]` / `SpellContainer` をインターン共有。`ISpellContainer#getOrCreate` の無駄な `new SpellContainer` 回避。`Mutable#toImmutable()` は防御コピー後に `ReferenceQueue` + `compute()` で原子的登録。 | 実機検証済 |
| **M4** | Iris / `glsl_transformer` キャッシュ解放 | `iris` (`PipelineManager`, `TransformPatcher`, `ASTParser`, `CachingParser`, `TwoTierCachingParser`) | `PipelineManager#preparePipeline` 完了後にデバウンス付き遅延自動パージ（デフォルト30秒、`synchronized` 保護下でマップのみクリア）。手動コマンド `/atmmemopt purge-shader-cache` では `tokenStream` / `input` も含め即時解放。 | 実機検証済 |
| **M5** | 診断コマンド ＆ `NativeImage` リーク追跡 | バニラ (`NativeImage`) | デフォルト **OFF**。有効時のみ `NativeImage` の2コンストラクタと `close()` を `@Inject` で追跡（`alltheleaks` と共存可）。`/atmmemopt status` と `/atmmemopt diag nativeimage [on\|off\|reset]` を提供。 | 実機検証済 |
| **M6** | `ItemEnchantments` インターン化・空統一 | バニラ (`ItemEnchantments`, `Mutable`) | `toImmutable()`, `withTooltip()`, `CODEC`, `STREAM_CODEC` をフック。空は `ItemEnchantments.EMPTY` へ統一。非空は **動的レジストリ混入防止のため `Holder` の参照一致 (`==`) と `WeakReference<Holder>` キー**で共有（1エンチャント専用 `SingleEnchantKey` 高速パス付き）。`LoggingOut` / `ServerStoppedEvent` / `TagsUpdatedEvent` で自動クリア。 | 修正・強化済 |
| **M7** | `VoxelShape` / `DoubleArrayList` / `BitSetDiscreteVoxelShape` 重複排除 | バニラ (`ArrayVoxelShape`, `Shapes`) | `DoubleArrayList` を `[0,1]` / `[0,0.5]` / `[0.5,1]` / `[0,0.5,1]` のゼロアロケーション高速パス＋要素数 2〜4 用プリミティブキーで共有。Phase 5 で内部の `BitSetDiscreteVoxelShape` 共有（上限 16,384）を追加。`ArrayVoxelShape` 本体は `voxelShapeMaxCacheEntries`（デフォルト 32,768）上限＋ `ReferenceQueue` で CHM 肥大化を防止。 | Phase 5 強化済 |
| **M8a** | JEI 検索フィルタ並列構築 ＆ `TOOLTIPS` 遅延化 | `jei` (`ElementSearch`, `ElementPrefixParser`) | `NAMES_AND_ALIASES`, `MOD_NAMES`, `TAGS`, `IDENTIFIERS` をワーカースレッドで並列構築（例外時はメインスレッドへフォールバック）。`COLORS`, `CREATIVE_TABS`, `TOOLTIPS` はメインスレッド維持。`jeiLazyTooltipIndex = true` で起動時の 15.9秒 `TOOLTIPS` 構築を初回 `#` 検索時まで遅延。 | 実機検証済（15.9s→0.8s） |
| **M8b** | JEIStuff ルート走査スキップ | `jeistuff` (`LootRecipes`) | ワールド入室時の 2,595 ルートテーブル同期走査（5.9秒）をスキップ。 | 実機検証済（-5.9s） |
| **M8c** | リソース＆データパック読み込み計測ログ | バニラ (`SimpleReloadInstance`) | `SimpleReloadInstance#of` 内のコンストラクタを `@WrapOperation` でラップ（`ThreadLocal` 不使用）。リロード完了時に Top 10 の `prepare` / `apply` 所要時間（ms）をログ出力。 | 実機検証済 |
| **M9a** | BlockEntityRenderer (BER) 距離カリング | バニラ (`BlockEntityRenderDispatcher`) | カメラ距離 `berCullingDistance`（デフォルト 48ブロック）超の BER 描画をゼロアロケーション距離判定でスキップ。Phase 5 で Config リロード即時反映（距離・ホワイトリスト再評価）と `/atmmemopt diag ber` 診断集計を追加。 | 実機検証済＋Phase 5 強化 |
| **M9b** | 非アクティブ時 FPS 制限 | バニラ (`Minecraft#getFramerateLimit`) | `!windowActive` 時に `unfocusedFpsLimit`（デフォルト 15 FPS）へ上限を制限。 | 実機検証済 |
| **M9c** | パーティクル距離カリング ＆ 非アクティブ抑制 | バニラ (`ParticleEngine`) | `createParticle` 冒頭でカメラ距離 `particleCullingDistance`（デフォルト 48ブロック）超または非アクティブ時のパーティクル生成をアロケーション前にスキップ。Config ホットリロード対応。 | Phase 6 実装済（未実機テスト） |
| **M9d** | エンティティ描画距離カリング | バニラ (`EntityRenderDispatcher`) | `shouldRender` 冒頭でゼロアロケーション距離カリング（一般 64 / `ItemEntity` 32 / `ExperienceOrb` 24 ブロック）。プレイヤー・ボス・`noCulling`・Config ホワイトリストを自動除外。`/atmmemopt diag entity` 診断付き。 | Phase 6 実装済（未実機テスト） |
| **M10** | `DataComponentPatch` インターン化 & CoW マップ共有 | バニラ (`DataComponentPatch`, `DataComponentPatch$Builder`, `PatchedDataComponentMap`) | `CODEC` / `STREAM_CODEC` / `Builder#build()` / `forget()` / `asPatch()` をフックし、不変の `DataComponentPatch`（1エントリ専用 `SinglePatchKey` 高速パス＋上限 65,536 の弱参照キャッシュ）を共有。`asPatch()` 時は `PatchedDataComponentMap.patch` も共有マップへ `copyOnWrite = true` で差し替え（`invasiveopts` と完全互換）。`LoggingOut` / `ServerStoppedEvent` / `TagsUpdatedEvent` で自動クリア。 | Phase 5 実装済（未実機テスト） |
| **M11** | `PalettedContainer` `ThreadingDetector` 共有・no-op 化 | バニラ (`PalettedContainer`, `ThreadingDetector`) | 全コンストラクタの `new ThreadingDetector("PalettedContainer")` を単一共有 no-op インスタンスへ差し替え、`acquire()` / `release()` のロック負荷を削減（`ThreadingDetectorMixin` で直接呼び出し時の安全網も実装）。 | Phase 5 実装済（未実機テスト） |
| **M12** | `ResourceLocation` 重複排除 ＆ `namespace` インターン化 | バニラ (`ResourceLocation`) | コンストラクタで `namespace` をインターン（`"minecraft"` はゼロアロケーション定数返却）、ファクトリ全経路で `ResourceLocation` 本体を弱参照キャッシュ（上限 131,072）により重複排除。 | Phase 5 実装済（未実機テスト） |
| **M13** | `Biome.Climate$Parameter` インターン化 | バニラ (`Climate$Parameter`) | `CODEC` / `point()` / `span()` で生成される不変レコード `Climate.Parameter(min, max)` を弱参照キャッシュ（上限 32,768）で共有。 | Phase 5 実装済（未実機テスト） |
| **M14** | `SpriteContents` 静止ミップマップ `NativeImage` 解放 | バニラ (`SpriteContents`) | 静止スプライト（`animatedTexture == null`）の GPU アトラス転送（`uploadFirstFrame` 内 `upload`）完了直後に `byMipLevel[1..N]` の `NativeImage` を `close()` して配列を 1 要素へ縮小（`originalImage` level 0 は維持）。 | Phase 7 実装済（未実機テスト） |
| **M15** | `BakedQuad` 頂点配列 (`int[]`) 重複排除 | バニラ (`BakedQuad`) | 6引数コンストラクタ末尾で同一の頂点配列 `int[]` を弱参照キャッシュ（上限 65,536、防御コピー保持）により共有。 | Phase 7 実装済（未実機テスト） |
| **M16** | `TagEntry` インターン化 | バニラ (`TagEntry`) | `CODEC` / `element()` / `optionalElement()` / `tag()` / `optionalTag()` で生成される不変 `TagEntry` を弱参照キャッシュ（上限 65,536）で共有。 | Phase 7 実装済（未実機テスト） |

---

## 3. コマンド一覧（クライアントコマンド）

- `/atmmemopt status` — Heap / Non-Heap / Metaspace / Old Gen / DirectBuffer 使用量と、M1〜M16 全モジュールの状態・削減カウントをチャット＋ログに出力。
- `/atmmemopt purge-shader-cache` — Iris / `glsl_transformer` のキャッシュとストリーム参照を即時解放し、解放件数を出力。
- `/atmmemopt purge-caches` — M3 / M4 / M6 / M7 / M10 / M12 / M13 / M15 / M16 のキャッシュを一括パージし、解放件数を出力。
- `/atmmemopt diag all` — `status` + `sprites` + `ber` + `entity`（＋M5有効時は `nativeimage`）を 1 回で一括出力。
- `/atmmemopt diag nativeimage [on|off|reset]` — 未解放 `NativeImage` の呼び出し元 TOP20 を出力（Mixin 自体は Config の `enableNativeImageDiagnostics = true` ＋再起動で有効化）。
- `/atmmemopt diag ber [reset]` — BER のクラス別描画回数・カリング回数・ホワイトリスト状態の TOP20 を出力。
- `/atmmemopt diag entity [reset]` — Entity のクラス別描画回数・カリング回数・ホワイトリスト状態の TOP20 を出力。
- `/atmmemopt diag sprites` — 全 `TextureAtlas` の `SpriteContents` が保持している未解放 `NativeImage` 数と推定ネイティブメモリ消費量（MB）をオーバーヘッドゼロで出力。

---

## 4. Config 一覧 (`config/atmmemopt-common.toml`)

```toml
[modules]
enableOwoDerivedMapOpt = true          # M1
enablePatchedComponentMapOpt = true    # M2
enableIronsSpellContainerOpt = true    # M3
enableIrisShaderCachePurge = true      # M4
irisAutoPurgeDelaySeconds = 30         # M4
enableNativeImageDiagnostics = false   # M5 (デフォルトOFF)
enableItemEnchantmentsOpt = true       # M6
enableVoxelShapeOpt = true             # M7
voxelShapeMaxCacheEntries = 32768      # M7 (0で本体キャッシュ無効化・DoubleArrayList/Discreteのみ維持)
enableJeiParallelFilter = true         # M8a
jeiLazyTooltipIndex = true             # M8a (初回 '#' 検索時まで遅延構築)
jeiSkipTooltipIndex = false            # M8a (完全スキップ)
enableJeiStuffSkipLootScan = true      # M8b
enableReloadProfiler = true            # M8c
enableBerDistanceCulling = true        # M9a
berCullingDistance = 48                # M9a
berCullingWhitelist = [ ... ]          # M9a (Beacon/EndGateway/Mekanism/Reactors/IE/Create等)
enableUnfocusedFpsLimit = true         # M9b
unfocusedFpsLimit = 15                 # M9b
enableParticleDistanceCulling = true   # M9c
particleCullingDistance = 48           # M9c
unfocusedSkipParticles = true          # M9c
enableEntityDistanceCulling = true     # M9d
entityCullingDistance = 64             # M9d
itemEntityCullingDistance = 32         # M9d
xpOrbCullingDistance = 24              # M9d
entityCullingWhitelist = [ ... ]       # M9d (Create/Boss/Dragon/Wither/Cataclysm等)
enableDataComponentPatchOpt = true     # M10
dataComponentPatchMaxCacheEntries = 65536 # M10
enablePalettedContainerOpt = true      # M11
enableResourceLocationOpt = true       # M12
resourceLocationMaxCacheEntries = 131072  # M12
enableClimateParameterOpt = true       # M13
enableSpriteMipmapRelease = true       # M14
enableBakedQuadVertexDedup = true      # M15
bakedQuadMaxCacheEntries = 65536       # M15
enableTagEntryOpt = true               # M16
```

---

## 5. これまでの実機テスト実績（Phase 4 時点）

- `DerivedComponentMap`（91.4MB）完全消滅、`ItemEnchantments` / `SpellContainer` / `CommonToken` すべて Spark 上位100位圏外へ。
- JEI 並列フィルタ構築: `15,945 ms → 815 ms`（約 **15.1 秒短縮**）、JEIStuff スキップ（**-5.9 秒**）。
- ZGC Major GC 間隔: `19.9 秒 → 105 秒`（プロファイル中 `3 分 54 秒`）へ改善。
- M9a BER カリング: `68,571,506` 回カリング動作を確認。

---

## 6. 残タスク・次の確認事項

1. **Phase 5 JAR のビルド & 実機テスト（最優先）**
   - ローカル ATM10 環境で `.\gradlew.bat "-Dorg.gradle.native=false" --no-daemon build` を実行し、`build/libs/atmmemopt-0.1.0.jar` を配置してテスト。
   - **確認項目:**
     - `/atmmemopt status` で M10〜M13 が ON になり、`internHits` / `detectorsAvoided` / `nsHits` / `instHits` が増加しているか。
     - Spark ヒープサマリで `#14 Reference2ObjectArrayMap` / `#26 DataComponentPatch` / `#30 EntrySet` / `#15 ResourceLocation` / `#56 ThreadingDetector` / `#39 Climate$Parameter` / `#49 BitSetDiscreteVoxelShape` が大幅に減少しているか。
     - `/atmmemopt diag ber` で拠点の BER 内訳が表示され、`/atmmemopt purge-caches` がエラーなく動作するか。
     - ワールド退出 → 再入室（動的レジストリ切替）で M10 / M13 のキャッシュクリアが問題なく機能するか。
2. **`NativeImage` 44.9万個（#27）の原因調査（推奨）**
   - `enableNativeImageDiagnostics = true` で一度だけ起動し、`/atmmemopt diag nativeimage` で未解放 `NativeImage` の呼び出し元 TOP20 を確認（物理メモリ / Swap 圧迫の調査）。
3. **関連ドキュメント**
   - Phase 1 バイトコード調査: `docs/investigation.md`
   - Phase 4 ロード時間解析: `docs/load_profile.md`
