# AtmMemOpt Phase 1 バイトコード調査レポート

- **調査対象環境:** `D:\PrismLauncher\instances\All the Mods 10 - ATM10\minecraft\mods`（読み取り専用参照）
- **対象バージョン:** Minecraft 1.21.1 / NeoForge 21.1.266 / Java 21
- **解析ツール:** JDK 21 `javap -p -c` / `javap -v`

---

## 1. 調査対象Mod JAR一覧

| 役割 | JARファイル名 |
|---|---|
| Module 1 対象 | `owo-lib-neoforge-0.12.15.6-beta.1+1.21.jar` |
| Module 3 対象 | `irons_spellbooks-1.21.1-3.16.3.jar` |
| Module 4 対象 | `iris-neoforge-1.8.14-beta.1+mc1.21.1.jar`（内包: `META-INF/jarjar/glsl-transformer-3.0.0-pre3.jar`） |
| 競合確認（既存最適化Mod） | `ferritecore-7.0.3-neoforge.jar`, `modernfix-neoforge-5.27.27+mc1.21.1.jar`, `alltheleaks-1.1.16-1.21.1-neoforge.jar`, `invasiveopts-1.0.26.jar` |

---

## 2. Module 1: oωo-lib `DerivedComponentMap`

### 確認したクラス構造・シグネチャ
1. **`io.wispforest.owo.ext.OwoItem`**（`ItemMixin` により `net.minecraft.world.item.Item` に実装）
   - `default void deriveStackComponents(DataComponentMap source, DataComponentPatch.Builder target)` → デフォルト実装は空 (`return;`)。
2. **`io.wispforest.owo.ext.DerivedComponentMap` implements `DataComponentMap`**
   - フィールド:
     - `private final DataComponentMap base;`
     - `private final PatchedDataComponentMap delegate;`
   - コンストラクタ `<init>(DataComponentMap base)`:
     - `this.base = base;`
     - `this.delegate = new PatchedDataComponentMap(base);` ← **ここが `ItemStack` 1個につき追加の `PatchedDataComponentMap` を1個生成している直接原因。**
   - `public static DataComponentMap reWrapIfNeeded(DataComponentMap)`
   - `public void derive(ItemStack)`:
     - `delegate.restorePatch(DataComponentPatch.EMPTY)`
     - `DataComponentPatch.Builder builder = DataComponentPatch.builder();`
     - `stack.getItem().deriveStackComponents(stack.getComponents(), builder);`
     - `delegate.restorePatch(builder.build());`
   - `get(DataComponentType)` / `keySet()` は `delegate` に委譲。`equals` / `hashCode` は `base` を使用。
3. **`io.wispforest.owo.mixin.ext.ItemStackMixin`** (`@Mixin(ItemStack.class)`)
   - フィールド: `private DerivedComponentMap owo$derivedMap;`
   - `injectDerivedComponentMap` (`@Inject` on `<init>(ItemLike, int, PatchedDataComponentMap)` at `INVOKE Item.verifyComponentsAfterLoad`):
     - `components` の `prototype` (`owo$getBaseComponents()`) を取り出し、`DerivedComponentMap` でなければ `new DerivedComponentMap(base)` でラップして `owo$setBaseComponents(...)` で差し替える。
   - `deriveComponents1..4`: `<init>` / `applyComponentsAndValidate` / `applyComponents(DataComponentPatch)` / `applyComponents(DataComponentMap)` に Inject。いずれも冒頭で `if (this.owo$derivedMap == null) return;` を行ってから `derive(this)` を呼ぶ。
4. **`io.wispforest.owo.mixin.ext.ComponentMapImplMixin`** (`@Mixin(PatchedDataComponentMap.class)`)
   - `reWrapDerived`: `PatchedDataComponentMap.copy()` 等で `prototype` を読み出す際に `DerivedComponentMap.reWrapIfNeeded` を適用。
   - `prioritiseDerivedMap`: `equals` 比較時に `DerivedComponentMap` 側の `equals` を優先。

### メモリ肥大化の根本原因
Spark 計測で `ItemStack` 約299.6万個に対して `DerivedComponentMap` が約299.6万個、`PatchedDataComponentMap` が約606.7万個（約2倍）存在するのは、**全 `ItemStack` の生成時に `DerivedComponentMap` とその内部の `delegate: PatchedDataComponentMap` が無条件で生成されているため**。

### Phase 2 での最適化設計（2段構え・安全設計）
1. **`DerivedComponentMap` の `delegate` 遅延生成（Lazy化）:**
   - `<init>(DataComponentMap)` で `new PatchedDataComponentMap(base)` を作らず `delegate = null` のままにする。
   - `get()` / `keySet()` は `delegate != null ? delegate : base` を参照。
   - `derive(ItemStack)` では、まず `Item` のクラスが `deriveStackComponents` をオーバーライドしているか `ClassValue<Boolean>` キャッシュで O(1) 判定。
     - 未オーバーライドなら何もしない（`DataComponentPatch.Builder` の生成もスキップ）。
     - オーバーライドしている場合でも、`builder.build().isEmpty()` なら `delegate = null` のままにして `PatchedDataComponentMap` を確保しない。非空パッチが生成された時だけ `delegate` を確保して `restorePatch` する。
2. **`ItemStack` 側での不要な `DerivedComponentMap` 参照解除:**
   - `ItemStack.<init>(ItemLike, int, PatchedDataComponentMap)` の `TAIL` で、対象 `Item` が `deriveStackComponents` をオーバーライドしていない場合、`components` の `prototype` を `derivedMap.base` に戻し、`owo$derivedMap` を `null` にクリアする（`ItemStackMixin.deriveComponents1..4` は既に `null` チェック済み）。
   - `DerivedComponentMap.reWrapIfNeeded` も `delegate == null` のケースを安全に処理。
- **期待削減効果:** Old Gen から約299.6万個の `DerivedComponentMap` (91.4MB) ＋ 約299.6万個の `delegate` 用 `PatchedDataComponentMap` (約138MB+) ＝ **合計約230MB+ 削減**。

---

## 3. Module 2: `PatchedDataComponentMap` & 既存最適化Modとの競合分析

### 既存ModのMixin実装状況
1. **`ferritecore-7.0.3` (`PatchedDataComponentMapMixin`):**
   - `patch.isEmpty()` 時に `patch = Reference2ObjectMaps.emptyMap(); copyOnWrite = true;` を行う `saveMemoryIfEmpty` / `saveMemoryIfEmptyWithReturn` を実装済み。
2. **`invasiveopts-1.0.26` (`PatchedDataComponentMapMixin`):**
   - Accessories 向けに `PatchedDataComponentMap` に4フィールド（`changeCheckStack`, `itemStack`, `mutationEvent`, `inApplyPatchLock`）を追加し、`accessories$getMutationEvent(ItemStack)` を実装。
3. **`modernfix-5.27.27` / `alltheleaks-1.1.16`:**
   - `PatchedDataComponentMapAccessor` のみ（フィールド改変なし）。

### 重要な制約とModule 2方針
- `invasiveopts` が `PatchedDataComponentMap` インスタンスに `ItemStack` 参照を紐づけているため、**異なる `ItemStack` 間で可変の `PatchedDataComponentMap` インスタンス自体を直接共有するのは Accessories の mutationEvent 破壊リスクが高く危険**。
- 一方で、606.7万個の約半分（約300万個）は Module 1 の `DerivedComponentMap.delegate` 解消で確実に消滅する。
- Module 2 では、FerriteCore と競合しない範囲で以下を Phase 3 で検証・実装する：
  - `PatchedDataComponentMap.fromPatch` / `restorePatch` / `copy` における空パッチ時の不要な `Reference2ObjectArrayMap` 確保の防止（`Reference2ObjectMaps.emptyMap()` + `copyOnWrite = true` の徹底）。
  - `ItemStack` コピーや空パッチ生成パスでの重複割り当て削減。

---

## 4. Module 3: Iron's Spellbooks `SpellContainer` 系

### 確認したクラス構造・シグネチャ
1. **`io.redspace.ironsspellbooks.api.spells.SpellData`**（完全不変: `final AbstractSpell spell`, `final int spellLevel`, `final boolean locked`）
   - `public static final SpellData EMPTY`
   - `public static SpellData readFromBuffer(FriendlyByteBuf)` → 毎回 `new SpellData(...)`
   - **注意点:** `SpellData.equals()` は `spell` と `spellLevel` のみ比較し `locked` を無視しているため、インターン化のキーには必ず `(spell, spellLevel, locked)` の3要素すべてを含める必要がある。
2. **`io.redspace.ironsspellbooks.api.spells.SpellSlot`** (`record SpellSlot(SpellData spellData, int index)` — 完全不変)
   - `public static SpellSlot of(SpellData, int)` → 毎回 `new SpellSlot(spellData, index)`。
3. **`io.redspace.ironsspellbooks.capabilities.magic.SpellContainer`** implements `ISpellContainer`（不変データコンポーネント; 変更は `mutableCopy()` 経由）
   - フィールド: `SpellSlot[] slots`, `int maxSpells`, `int activeSlots`, `boolean spellWheel`, `boolean mustEquip`, `boolean improved`
   - `getAllSpells()` は毎回コピー配列を返すため内部 `slots` 配列は外部から変更されない。
   - `CODEC` (`lambda$static$4`), `STREAM_CODEC` (`lambda$static$7`), `Mutable.toImmutable()` が毎回 `new SpellContainer(...)` と `new SpellSlot[maxSpells]` を生成。
4. **`io.redspace.ironsspellbooks.api.spells.ISpellContainer`**
   - `create(int maxSpells, boolean spellWheel, boolean mustEquip)` → 毎回 `new SpellContainer(...)`
   - `getOrCreate(ItemStack)` → `stack.getOrDefault(SPELL_CONTAINER, new SpellContainer(1, false, false))`（**既にコンポーネントが存在しても毎回デフォルト `SpellContainer` と `SpellSlot[1]` をアロケーションしている**）。

### Phase 3 での最適化設計
- `SpellData` / `SpellSlot` / `SpellContainer`（および空の `SpellSlot[]`）を弱参照または有界キャッシュ（`ConcurrentHashMap` / 上限付き）でインターン共有化。
- `ISpellContainer.create(...)`、`ISpellContainer.getOrCreate(...)`、`SpellSlot.of(...)`、`SpellContainer.Mutable.toImmutable()`、および `CODEC` / `STREAM_CODEC` デコードパスを共有インスタンスへ差し替える。

---

## 5. Module 4: Iris / `glsl_transformer` シェーダーキャッシュ解放

### 確認したキャッシュ保持箇所
1. **`net.irisshaders.iris.pipeline.transform.TransformPatcher`**
   - `private static final Map<TransformPatcher$CacheKey, Map<PatchShaderType, String>> cache;`
   - `private static final EnumASTTransformer<Parameters, PatchShaderType> transformer;`（`ASTTransformer` → `ASTParser` を継承）
2. **`io.github.douira.glsl_transformer.ast.transform.ASTParser`**
   - `private static ASTParser INSTANCE;`（`_getInternalInstance()`）
   - `private EnhancedParser parser;`（実体は `CachingParser` / `TwoTierCachingParser`）
   - `private TypedTreeCache<ASTNode> buildCache;`（`LRUCache` 継承）
3. **`io.github.douira.glsl_transformer.parser.CachingParser` / `TwoTierCachingParser`**
   - `protected TypedTreeCache<CachingParser$CacheContents> parseCache;`（ここに ANTLR `BufferedTokenStream` / `CommonToken` 群が保持される）
   - `protected TypedTreeCache<CachingParser$CacheContents> secondaryCache;`
   - `public void setParseCacheSizeAndClear(int)`
4. **`io.github.douira.glsl_transformer.parser.EnhancedParser`**
   - `protected IntStream input;` / `protected BufferedTokenStream tokenStream;`（直近パースのストリーム参照）
5. **フックポイント:**
   - `net.irisshaders.iris.pipeline.PipelineManager.preparePipeline(NamespacedId)` の完了後、設定秒数（デフォルト30秒）後に非同期またはメインスレッドで上記キャッシュを一括クリア。
   - 手動コマンド `/atmmemopt purge-shader-cache` でも即時クリア可能にし、解放前後のエントリ数をログ・チャット出力する。

---

## 6. Module 5: 診断コマンド & `NativeImage` リーク検出

### 既存Mod (`alltheleaks`) との共存
- `alltheleaks-1.1.16` にも `NativeImageMixin` (`dev.uncandango.alltheleaks.mixin.core.main.NativeImageMixin`) が存在するが、標準の `@Inject` を使用しているため、こちらも `@Inject`（`@Unique` フィールド・衝突しないメソッド名）を使えば競合しない。
- Config `enableNativeImageDiagnostics` はデフォルト `false` とし、有効時のみ `NativeImage` のコンストラクタと `close()` をフックして、呼び出し元スタックトレース別の「生成数 / 解放数 / 未解放数」を集計する。
- コマンド:
  - `/atmmemopt status`: Heap / Non-Heap / Metaspace / DirectBuffer 使用量と各モジュール有効状態を出力。
  - `/atmmemopt diag nativeimage`: 未解放 `NativeImage` の多い呼び出し元 TOP20 をチャットおよびログに出力。
