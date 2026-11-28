# AtmMemOpt ロード時間プロファイル解析レポート (`docs/load_profile.md`)

- **解析対象ログ:** `D:\PrismLauncher\instances\All the Mods 10 - ATM10\minecraft\logs\latest.log`（2026-11-27 実機計測）

---

## 1. JEI 並列フィルタ構築（90,166 アイテム）

```text
[AtmMemOpt] JEI parallel filter build finished in 15945 ms (items=90166, threads=4, skipTooltips=false)
per-index ms:
  NAMES_AND_ALIASES =  1002 ms
  MOD_NAMES         =   667 ms
  TAGS              =   667 ms
  TOOLTIPS          = 15943 ms  <-- 全体の 99.99%
  CREATIVE_TABS     =     0 ms
  COLORS            =     0 ms
  IDENTIFIERS       =    16 ms
```

### 分析と対策
- 並列化により `TOOLTIPS` 以外の6インデックスはすべて **1.0 秒以内** に完了している。
- ボトルネックの 99.99% がメインスレッド上の `TOOLTIPS`（90,166 アイテムのツールチップ生成）である。
- **Phase 4 対策:** `jeiLazyTooltipIndex = true`（デフォルト ON）を導入し、ワールド入室時は `TOOLTIPS` インデックスの構築をスキップ（空で登録）し、ユーザーが実際に `$` プレフィックスでツールチップ検索を行った初回のみオンデマンド構築する。これにより **JEI フィルタ構築を 15.9秒 → 約 1.0秒（約 14.9秒短縮）** に削減する。

---

## 2. Resource / Datapack Reload Top 10 内訳

### 2-1. クライアントリソースリロード（合計 22,530 ms / 128 listeners）

| 順位 | 合計 (ms) | prepare (ms) | apply (ms) | リスナー |
|---:|---:|---:|---:|---|
| #01 | 18,176 | 10,168 | 8,007 | `ClientModLoader$$Lambda`（Mod 初期化本体） |
| #02 | 13,802 | 11,828 | 1,973 | `ModelManager`（ブロック・アイテムモデルベイク） |
| #03 | 3,525 | 3,525 | 0 | `TextureManager` |
| #04 | 3,494 | 3,488 | 6 | `GuideME Reload Listener` (`guideme.internal.GuideReloadListener`) |
| #05 | 2,270 | 2,262 | 8 | `FancyMenuResourceReload$1` |
| #06 | 1,536 | 1,390 | 145 | `FontManager` |
| #07 | 1,033 | 0 | 1,033 | `EntityRenderDispatcher` |
| #08 | 1,016 | 524 | 491 | `HighlightHandler` (`resourcefullib`) |
| #09 | 999 | 0 | 999 | `LanguageManager` |
| #10 | 688 | 437 | 251 | `Shader Loader` (`GameRenderer$1`) |

### 2-2. サーバーデータパックリロード（合計 22,412 ms / 136 listeners）

| 順位 | 合計 (ms) | prepare (ms) | apply (ms) | リスナー |
|---:|---:|---:|---:|---|
| #01 | 20,478 | 4,395 | 16,082 | `AuxRecipeManager` (`dev.shadowsoffire.fastsuite.AuxRecipeManager`) |
| #02 | 4,445 | 4,373 | 71 | `Aether RecipeReloadListener` |
| #03 | 3,738 | 2,736 | 1,001 | `ServerAdvancementManager` |
| #04 | 2,690 | 2,689 | 0 | `DataMapLoader` (NeoForge) |
| #05 | 1,515 | 1,514 | 0 | `TagManager` |
| #06 | 363 | 342 | 20 | `LootModifierManager` (NeoForge) |
| #07 | 334 | 332 | 2 | `ServerFunctionLibrary` |
| #08 | 328 | 293 | 34 | `LootModifierManager` (NeoForge) |
| #09 | 291 | 239 | 52 | `Modonomicon BookDataManager` |
| #10 | 280 | 276 | 4 | `Moog's Structures StructureManifestReloadListener` |

### 分析と判断
- サーバーデータパック読み込み 22.4 秒のうち **20.5 秒（91.4%）が `AuxRecipeManager`（FastSuite のレシピ登録本体、`apply=16.1秒`）** に集中している。#02〜#05 は並列の prepare フェーズで吸収されており、クリティカルパスはほぼ `AuxRecipeManager` のみ。
- クライアントリソース読み込みも #01 `ClientModLoader`・#02 `ModelManager` が支配的であり、いずれも安全性を損なわずにスキップできる対象ではないため、データパック／リソースリロード本体には無理に介入しない（安全優先）。
- 一方、JEI フィルタの `TOOLTIPS` 遅延構築（-14.9秒）と JEIStuff ルート走査スキップ（-5.9秒）の組み合わせにより、ワールド読み込み全体で **約 21 秒の確実・安全な短縮** が見込める。
- Stage 3 では上記 #01 `AuxRecipeManager` と #02 `ModelManager` について、CPU 時間だけでなく heap 証拠を集めるため M25（リスナー別 heap-used 差分）と M26（`ModelBakery` staging map 数の監査）を追加した。どちらも既定OFFの**診断のみ**で、リロード対象やレシピデータを変更しない（実装詳細は `stage3-report.md`）。
