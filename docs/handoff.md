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
| **M7** | `VoxelShape` / `DoubleArrayList` 重複排除 | バニラ (`ArrayVoxelShape`, `Shapes`) | `DoubleArrayList` を `[0,1]` / `[0,0.5]` / `[0.5,1]` / `[0,0.5,1]` のゼロアロケーション高速パス＋要素数 2〜4 用プリミティブキーで共有。`ArrayVoxelShape` 本体は `voxelShapeMaxCacheEntries`（デフォルト 32,768）上限＋ `ReferenceQueue` で CHM 肥大化を防止。 | Phase 4 スリム化済 |
| **M8a** | JEI 検索フィルタ並列構築 ＆ `TOOLTIPS` 遅延化 | `jei` (`ElementSearch`, `ElementPrefixParser`) | `NAMES_AND_ALIASES`, `MOD_NAMES`, `TAGS`, `IDENTIFIERS` をワーカースレッドで並列構築（例外時はメインスレッドへフォールバック）。`COLORS`, `CREATIVE_TABS`, `TOOLTIPS` はメインスレッド維持。`jeiLazyTooltipIndex = true` で起動時の 15.9秒 `TOOLTIPS` 構築を初回 `#` 検索時まで遅延（約 15 秒短縮見込み）。 | Phase 4 強化済 |
| **M8b** | JEIStuff ルート走査スキップ | `jeistuff` (`LootRecipes`) | ワールド入室時の 2,595 ルートテーブル同期走査（5.9秒）をスキップ。 | 実機検証済（-5.9s） |
| **M8c** | リソース＆データパック読み込み計測ログ | バニラ (`SimpleReloadInstance`) | `SimpleReloadInstance#of` 内のコンストラクタを `@WrapOperation` でラップ（`ThreadLocal` 不使用）。リロード完了時に Top 10 の `prepare` / `apply` 所要時間（ms）をログ出力。 | 実機検証済 |
| **M9a** | BlockEntityRenderer (BER) 距離カリング | バニラ (`BlockEntityRenderDispatcher`) | カメラ距離 `berCullingDistance`（デフォルト 48ブロック）超の BER 描画をゼロアロケーション距離判定でスキップ。`shouldRenderOffScreen(be) == true` および主要マルチブロック/コントラプション（Config ホワイトリスト、`ClassValue` O(1) キャッシュ）を自動除外。 | Phase 4 実装済（未実機テスト） |
| **M9b** | 非アクティブ時 FPS 制限 | バニラ (`Minecraft#getFramerateLimit`) | `!windowActive` 時に `unfocusedFpsLimit`（デフォルト 15 FPS）へ上限を制限。 | Phase 4 実装済（未実機テスト） |

---

## 3. コマンド一覧（クライアントコマンド）

- `/atmmemopt status` — Heap / Non-Heap / Metaspace / Old Gen / DirectBuffer 使用量と、M1〜M9 全モジュールの状態・削減カウントをチャット＋ログに出力。
- `/atmmemopt purge-shader-cache` — Iris / `glsl_transformer` のキャッシュとストリーム参照を即時解放し、解放件数を出力。
- `/atmmemopt diag nativeimage [on|off|reset]` — 未解放 `NativeImage` の呼び出し元 TOP20 を出力（Mixin 自体は Config の `enableNativeImageDiagnostics = true` ＋再起動で有効化）。

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
voxelShapeMaxCacheEntries = 32768      # M7 (0で本体キャッシュ無効化・DoubleArrayListのみ維持)
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
```

---

## 5. これまでの実機テスト実績（Phase 3 時点）

- `DerivedComponentMap`（91.4MB）完全消滅、`PatchedDataComponentMap` 半減（606万 → 308万、計約 228MB 削減）。
- `ItemEnchantments`, `SpellContainer`, `ArrayVoxelShape`, `CommonToken` がすべて Spark 上位100位圏外へ。
- ヒープ全体 `8.7GB → 8.4GB`、ZGC Major GC 間隔が `19.9秒 → 52.3秒`（約 2.6 倍）へ改善。
- `latest.log` 解析で `AtmMemOpt` 起因のエラーは **0件** を確認。

---

## 6. 残タスク・次の確認事項

1. **Phase 4 JAR の実機テスト（最優先）**
   - `build/libs/atmmemopt-0.1.0.jar`（Phase 4 ビルド）を ATM10 の `mods` フォルダへ配置して起動・テストする。
   - **確認項目:**
     - JEI フィルタ構築が `15.9秒 → 約1.0秒` に短縮されているか（ログの `JEI parallel filter build finished` を確認）。
     - JEI で `#` プレフィックス検索を初めて行った際、オンデマンドで `TOOLTIPS` が構築され検索できるか。
     - `/atmmemopt status` で `M7 cacheSize` が 32,768 以下に収まり、Spark 上の `ShapeKey` / `CHM$Node` 占有（約 60MB）が大幅に減っているか。
     - `M9`: 48ブロック以遠の通常 BER がカリングされているか、拠点の大型マルチブロック（Mekanism / Reactors / Create 等）が不自然に消えないか（消える場合は `berCullingWhitelist` にクラス名部分文字列を追加）。
     - `M9`: ウィンドウ非アクティブ時に FPS が 15 に低下し、フォーカス復帰で元に戻るか。
     - Phase 4 起動後の `latest.log` に `AtmMemOpt` 起因のエラーがないか。
2. **（任意）追加最適化候補**
   - 実機テスト後の Spark ヒープダンプおよび CPU プロファイルを見て、新たな上位占有オブジェクトや Render / Tick ボトルネックがあれば Phase 5 として対応。
   - リロード計測が不要になったら `enableReloadProfiler = false` に設定して運用可能。
3. **関連ドキュメント**
   - Phase 1 バイトコード調査: `docs/investigation.md`
   - Phase 4 ロード時間解析: `docs/load_profile.md`
