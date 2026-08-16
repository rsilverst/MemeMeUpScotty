# Architecture: Meme Me Up Scotty

> Current-state architecture reference. **Keep this file updated whenever the architecture
> changes** — new/removed modules, new persistence, networking, moved responsibilities, new
> screens, changed data flow. Content current as of 2026-08-15 on `main`. Describe what the
> system IS; the *why* behind a change belongs in its commit and issue, not here. For known
> bugs and tech debt see GitHub Issues (`rsilverst/MemeMeUpScotty`, managed via `gh issue`).
>
> **§1 is operational** — build/test runbooks, required configuration, and the guardrails that
> protect the personal-use-only posture and the persisted meme history. Read §1 before any
> agentic operation. The architecture itself starts at §2.

## 1. Operations — runbooks, gotchas & guardrails

### 1.1 Build & test runbook

```bash
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:installDebug           # build + install on a connected device/emulator
./gradlew testDebugUnitTest           # unit tests (34 tests, JUnit4 + hand-rolled fakes)
./gradlew connectedDebugAndroidTest   # Compose UI tests (needs a device; 3 tests)
./gradlew lint                        # Android lint
./gradlew :app:assembleRelease        # R8-minified release (signed only if keys present, §1.2)
```

Unit tests verified passing on this runbook 2026-08-15 (Gradle 9.7.0 daemon on JDK 25).

### 1.2 `local.properties` keys (gitignored, required)

A fresh checkout builds but cannot generate images without the token. Every key falls back to
an environment variable of the same name (token/base-url only).

| Key | Required | Purpose |
|---|---|---|
| `REPLICATE_API_TOKEN` | **Yes** (to generate) | Replicate API token, compiled into `BuildConfig`. Missing token → loud Gradle warning + every generation fails with `AuthRejected`. |
| `REPLICATE_BASE_URL` | No (default `https://api.replicate.com/`) | Point the app at a proxy without touching code — the hook for the "thin proxy holds the token" plan (README "Distribution status"). |
| `RELEASE_KEYSTORE_FILE` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` | No | Release signing. **All four or nothing**: with any missing, release builds are unsigned (deliberate, so CI / fresh checkouts still build). |

### 1.3 Do-not-break guardrails

Invariants that protect the app's posture and the user's saved memes. **Flag any change
touching these to Bob before making it.**

- **Personal-use only — do not distribute the APK.** Bob has **no intention to publish this
  app**; distribution is a non-goal, not future work. The Replicate token ships inside
  `BuildConfig`; anyone with the APK can decompile it and bill the owner. The README's
  "Distribution status" section records this posture and what would have to change if the
  decision were ever reversed (proxy, signing, IP posture, moderation). Never upload, share,
  or publish a build, and never spend effort on distribution-readiness unless Bob asks.
- **`disable_safety_checker = true` is deliberate** (`ReplicateImageRepository`): the project
  brief for this single-user build explicitly says no content moderation. Do not "fix" or
  remove it. It gets revisited only if distribution ever becomes a goal.
- **Persisted-history compatibility.** Changes to `HistoryEntryDto` / `CaptionSnapshot` /
  `CaptionData` shapes must keep old persisted JSON decoding (new fields need Moshi-safe
  defaults). `readHistory` must keep decoding the legacy newline-joined `history_paths`
  format, and unknown style-enum names must keep falling back to defaults
  (`CaptionMapping.enumOrDefault`) rather than crashing.
- **Moshi DTO field names are the wire/persisted format.** The proguard keep rules are scoped
  to `@JsonClass` members in `data/network/**` and `ui/viewmodel/**`
  ([proguard-rules.pro](app/proguard-rules.pro)). A new Moshi DTO outside those packages needs
  its own rule; renaming a `@Json` name breaks the Replicate wire format or the saved history.
- **Cache-filename prefixes are a contract.** `generated_meme_` (repository),
  `gallery_meme_` / `shared_meme_` (ImageUtils) are matched by `cleanCacheDirectory` and by
  `MainViewModel.isEphemeralCacheFile` (which decides move-vs-copy into history). Add or rename
  a prefix in all places or orphaned files stop being cleaned / start being copied twice.
  Files under `filesDir/history/` are the permanent store — never bulk-delete them.
- **The WYSIWYG caption constants are shared.** `CAPTION_INNER_PADDING`,
  `CAPTION_DEFAULT_MAX_HEIGHT`, `CAPTION_STROKE_RATIO`, and `findBestFitFontSize` (all in
  [MemeTextOverlay.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/MemeTextOverlay.kt))
  are used by both the live canvas overlay and the history-thumbnail renderer; the thumbnail
  also mirrors the layouts' 16dp horizontal inset. Drift breaks the guarantee that a thumbnail
  wraps text exactly like the canvas did (§6.3).
- **The release keystore is gitignored and NOT preserved by git** (`*.jks` / `*.keystore`) —
  losing it means losing release signing.

### 1.4 Dev-environment gotchas

- **AGP 9.3.1 is an alpha-channel AGP** — Android Studio must be recent enough to open the
  project (README pins "any version that supports AGP 9.3.x").
- **There is no `org.jetbrains.kotlin.android` plugin — on purpose.** AGP 9 has built-in
  Kotlin support; the only Kotlin-adjacent plugins applied are the Compose compiler plugin and
  KSP. Do not add the standalone Kotlin Android plugin back.
- **`lint { disable += "Instantiatable" }`** in [app/build.gradle.kts](app/build.gradle.kts) is
  a false positive (AGP 9.3-alpha + activity 1.13 can't see that `ComponentActivity` extends
  `Activity`). Re-enable when an AGP upgrade fixes it; don't cargo-cult it into new modules.
- **The Gradle token warning advertises `REPLICATE_MODEL_ID` — nothing reads it.** The default
  model is the `ImageModel.JUGGERNAUT` enum entry; changing models is a code change (or the
  in-app picker at runtime). Ignore that line of the warning.
- Configuration cache and parallel mode are ON (`gradle.properties`); a build-script change
  invalidates the cache, which is expected noise, not a failure.

### 1.5 Commit & issue conventions (pointer)

Commit provenance trailers, the no-auto-commit rule, and the GitHub-Issues conventions
(TL;DR-for-Bob lead, labels, declined-decision closures) live in [`AGENTS.md`](AGENTS.md) —
they apply to every change, not just architectural ones.

## 2. Module structure & toolchain

Single-module Android app — no backend of its own; the device talks directly to the Replicate
HTTP API (or a proxy via `REPLICATE_BASE_URL`, §1.2).

- **Module:** `:app` only. Package / application id `com.rsilverst.mememeupscotty`;
  minSdk 26, target/compileSdk 37; versionCode 1, versionName 0.1.
- **Toolchain:** Gradle 9.7.0 (wrapper), AGP 9.3.1 (built-in Kotlin — §1.4), Kotlin 2.4.10,
  KSP 2.3.9, Compose BOM 2026.08.00, Material 3. Modules compile to **Java 11**
  (`compileOptions`); the daemon runs on whatever JDK launches Gradle (needs 17+, currently
  verified on 25); the foojay resolver convention fetches compile toolchains. Pins live in
  [gradle/libs.versions.toml](gradle/libs.versions.toml).
- **Key libraries:** Retrofit 3 + OkHttp 5 + Moshi 1.15 (KSP codegen) for the Replicate API;
  Coil 2.7 for image loading; Preferences DataStore 1.2 for the history index;
  core-splashscreen; kotlinx-coroutines.
- **Release build:** R8 + resource shrinking ON; signing config only materializes when all
  four keystore properties are present (§1.2).

## 3. App composition & dependency injection

DI is **manual and tiny** — all wiring happens in
[MainActivity.onCreate](app/src/main/java/com/rsilverst/mememeupscotty/MainActivity.kt):

```
NetworkModule (object singletons: Retrofit, ReplicateApi, imageDownloadClient)
  → ReplicateImageRepository(NetworkModule.replicateApi)
  → viewModelFactory { MainViewModel(repository, historyDir, historyDataStore) }
  → setContent { MemeMeUpScottyTheme { MemeScreen(viewModel) } }
```

- `historyDir` = `filesDir/history` (created on the spot); `historyDataStore` is a
  `preferencesDataStore("history_prefs")` Context extension declared in
  [MemeApplication.kt](app/src/main/java/com/rsilverst/mememeupscotty/MemeApplication.kt)
  (the Application class is otherwise empty).
- Single activity, single screen. **No navigation library, no routes** — adaptive layout is a
  width check, not a nav graph (§6.1).
- Splash: `installSplashScreen()` before `super.onCreate()`; manifest gives MainActivity the
  `Theme.MemeMeUpScotty.Starting` splash theme which swaps to the app theme post-splash.
  Edge-to-edge is enabled; `windowSoftInputMode="adjustResize"` so `imePadding()` works.
- On every activity create, a background coroutine deletes orphaned ephemeral cache files
  (`cleanCacheDirectory`, §7).
- `allowBackup=false` — the meme history is device-local and not backed up.

## 4. Data layer: Replicate API client

### 4.1 Network stack

[NetworkModule](app/src/main/java/com/rsilverst/mememeupscotty/data/network/NetworkModule.kt)
is an `object` holding the shared Moshi, the Retrofit instance, and **two** OkHttp clients:

- The API client adds `Authorization: Token <BuildConfig.REPLICATE_API_TOKEN>` (Replicate uses
  the `Token` scheme, not `Bearer`) and a logging interceptor (BODY in debug, NONE in release —
  keeps the auth header out of logcat). 60s connect/read/write timeouts.
- `imageDownloadClient` has **no auth interceptor** — final images live on
  `replicate.delivery` presigned CDN URLs that reject the Token header.

[ReplicateApi](app/src/main/java/com/rsilverst/mememeupscotty/data/network/ReplicateApi.kt) is
three endpoints: `GET v1/models/{owner}/{name}` (resolve latest version), `POST
v1/predictions` (create), `GET v1/predictions/{id}` (poll). All DTOs are `@JsonClass` codegen
data classes; their `@Json` names ARE the wire format (guardrail §1.3).

### 4.2 Generation flow & error taxonomy

[ReplicateImageRepository](app/src/main/java/com/rsilverst/mememeupscotty/data/repository/ImageRepository.kt)
implements the single-method `ImageRepository` interface. One generation:

1. Parse `owner/name` from the model id; fetch the model; take `latest_version.id`.
2. `POST /v1/predictions` with the version pinned, the composed prompt (user prompt +
   per-model positive suffix, §4.3), the per-model negative prompt, a random seed (captured so
   it can travel back to the UI as provenance), and `disable_safety_checker = true` (§1.3).
3. Poll the prediction: first delay 1.5s, +500ms per tick, capped at 3s, hard deadline 120s
   (→ `Timeout`). Terminal statuses: `succeeded` / `failed` / `canceled`.
4. On success, stream the first output URL into a `generated_meme_*.png` temp file in the
   caller's cacheDir via `imageDownloadClient` → `GenerationOutcome.Success(file, seed)`.

**The repository never throws** (it rethrows `CancellationException` for structured
concurrency and deletes the temp file on the way out; everything else becomes a value):

- `GenerationOutcome` = `Success(file, seed)` | `Failure(error)`.
- `GenerationError` is the typed failure taxonomy the UI switches on: `AuthRejected` (401),
  `OutOfCredit` (402), `ModelUnavailable` (404), `RateLimited(retryAfterSec)` (429, parsed
  from Replicate's `retry_after`), `Server(httpCode)` (5xx), `Timeout`, `Unexpected(detail)`.
  Add a new variant rather than overloading `Unexpected` when the UI should distinguish a
  condition. **String copy lives entirely in the resource layer** — the UI maps variants to
  `strings.xml` titles/details; only `Unexpected.detail` is shown raw (deliberate).

### 4.3 Per-model prompt engineering

`MODEL_PROMPT_CONFIGS` (companion of the repository) maps each Replicate model id to a
positive-suffix + negative-prompt pair, composed from three building blocks kept compact so
the full negative stays under SDXL's 77-token CLIP limit:

- `ANATOMY_AND_DUPLICATION_NEG` — the canonical SDXL anti-artifact terms (hands, limbs, twins).
- `PHOTOREAL_STYLE_NEG` — pushes photoreal models away from cartoon/anime; deliberately
  omitted for Blue Pencil (anime) and Proteus (painterly).
- `QUALITY_NEG` — includes `text`, because captions are our overlay, not baked pixels.

Flux Schnell gets no negative at all (rectified-flow model, no CFG) and a minimal suffix. The
map is **string-keyed on purpose** so the data layer stays independent of the UI's
`ImageModel` enum; an unknown id falls back to `DEFAULT_PROMPT_CONFIG`.

The `ImageModel` enum (7 models, id = Replicate path) lives in the ViewModel layer
([MainViewModel.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/viewmodel/MainViewModel.kt));
its display names/glyphs/descriptions live in the UI layer
([ImageModelMetadata.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/ImageModelMetadata.kt)).

## 5. State & persistence

### 5.1 `MainViewModel` state

Four `StateFlow`s: `generationState` (sealed `Idle` / `Loading` / `Success(file)` /
`Error(GenerationError)`), `selectedModel`, `generationHistory` (`List<HistoryEntry>`, newest
at index 0), and `activeEntry` (the entry currently on the canvas).

- **Cancellation:** `generateImage` cancels any in-flight `generationJob` first (a rapid
  second tap or mid-generation re-roll never races the previous attempt); user cancellation
  maps to `Idle`, not an error.
- **Background generation:** starting a generation clears `activeEntry`; if the user taps a
  history entry while loading, the finished image still appends to history but only claims the
  canvas if `activeEntry` is still null — browsing during a slow generation is never clobbered.

### 5.2 History persistence (files + DataStore index)

Two-part store, capped at **50 entries FIFO** (eviction deletes the backing file):

- **Files:** every image that reaches the canvas (generated or gallery-picked) is
  moved/copied into `filesDir/history/` as `history_meme_<ts>_<name>` (`persistFileOnDisk`
  renames ephemeral cache files, copies everything else — the `isEphemeralCacheFile` prefix
  contract, §1.3).
- **Index:** Preferences DataStore `history_prefs`, key `history_entries` = a Moshi JSON array
  of `HistoryEntryDto` (file *name*, resolved against the history dir on load, so the JSON
  survives path changes). The legacy key `history_paths` (newline-joined names, no
  provenance/captions) is still decoded as a fallback. Entries whose file no longer exists are
  dropped on load.
- **Merge rules:** the VM collects the DataStore forever. First emission merges persisted
  entries under live in-memory ones (`distinctBy { it.file }` — live captions win). Later
  emissions only fold in files not already tracked: **in-memory state is authoritative**, all
  writes go through this VM, so live captions are never clobbered.
- **Caption writes are debounced** (400ms after the last edit) so drags don't hit disk per
  pixel; `onCleared` flushes the last edits synchronously with `runBlocking` (viewModelScope
  is already dead there, and the payload is tiny).

### 5.3 Captions are data, not pixels

[HistoryEntry](app/src/main/java/com/rsilverst/mememeupscotty/ui/viewmodel/HistoryEntry.kt) =
file + provenance (`prompt` / `modelId` / `seed`, null for gallery picks and legacy entries) +
`CaptionSnapshot`. A snapshot holds two `CaptionData` slots (top/bottom) plus `refSize`, the
canvas width the offsets were last edited on (lets thumbnails place captions proportionally,
§6.3). `CaptionData` is pure primitives — text, visibility, offset, optional user-set size
(auto-fit until resized), and style stored as **enum names** (`font`/`fill`/`align`), mapped
to Compose types at the UI edge with fall-back-to-default for unknown names
([CaptionMapping.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/CaptionMapping.kt)).
Captions stay editable forever; pixels are only flattened at Save/Share (§7). Tapping a
history entry restores its model and prompt along with its captions.

## 6. UI layer

### 6.1 Screen composition & adaptive layout

One screen. [MemeScreen](app/src/main/java/com/rsilverst/mememeupscotty/ui/MemeScreen.kt) owns
the model-picker-open flag, the snackbar host, and error-string mapping; `MemeContent` hoists
everything else — prompt text, caption edit dispatch, save/share actions, the capture
`GraphicsLayer`, the photo-picker launcher — and dispatches on width: **≥840dp**
(Material adaptive "expanded") → `ExpandedLayout` (canvas left, controls right), else
`CompactLayout` (single scrolling column). Layouts
([MemeLayouts.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/MemeLayouts.kt)) are pure
presentation over that state. The prompt restores from the active entry keyed on its file path
(typing for the current image is never clobbered; gallery picks carry no prompt). Generation
errors show as a full-canvas state when the canvas is empty, as a snackbar when an image is
already displayed.

### 6.2 The canvas & caption overlays

[MemeCanvas](app/src/main/java/com/rsilverst/mememeupscotty/ui/MemeCanvas.kt) — 1:1 aspect
box showing the active image (Coil `AsyncImage`, `ContentScale.Crop`) or the empty (prompt
chips) / loading (scan-line "MATERIALIZING" animation + `T+NS` elapsed counter) / error
(retry) states.

- **Capture:** content is recorded into the hoisted `GraphicsLayer` **only while
  `capturing`** — recording every frame doubled draw work. Anything that is chrome, not meme
  (the "Use a photo" pill, caption focus chrome), is hidden when `capturing`; the capture
  waits 200ms (`CAPTURE_CHROME_FADE_BUFFER_MS`) for the 160ms chrome fade-out to settle before
  snapshotting.
- [MemeTextOverlay](app/src/main/java/com/rsilverst/mememeupscotty/ui/MemeTextOverlay.kt) —
  draggable (clamped inside the canvas), resizable (min 60×40dp), focusable caption. Focus
  chrome = dashed border + delete chip + style chip + resize handle. Font auto-fits via a
  binary search over 14–40sp (step 2) until the user resizes; all-caps is a
  `VisualTransformation` with a strict 1:1 offset mapping (international-keyboard-safe);
  outline is a black stroke at `0.15 ×` font size. Deleting a caption offers snackbar Undo
  (the restore closure re-applies the pre-delete `CaptionData`).

### 6.3 History strip & WYSIWYG thumbnails

[HistoryStrip](app/src/main/java/com/rsilverst/mememeupscotty/ui/HistoryStrip.kt) — LazyRow of
64dp thumbnails, newest first, auto-scrolls to index 0 on new entries; long-press → delete
dialog; header has a clear-all action and a "STARDATE n" label computed from the epoch. Since
captions aren't baked into files, each thumbnail paints a **read-only scaled caption preview**:
both the auto-fit font size and the text-box width are scaled from the canvas reference by the
same factor (`thumb / refSize`), so line wrapping matches the canvas exactly. This is the
WYSIWYG contract behind the shared constants guardrail (§1.3); entries without a `refSize`
(never-edited/legacy) render no preview.

### 6.4 Controls

[MemeControls.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/MemeControls.kt):
`HudStrip` (active model + "tap to change" → picker sheet), `PromptInput` (`ImeAction.Go`
fires Energize without dismissing the keyboard), `EnergizeButton` (tri-state:
ENERGIZE / RE-ENERGIZE / CANCEL — during loading the same button becomes the cancel action,
with a progress bar underneath), and `GhostButton` Save/Share (disabled while loading or with
no image). [ModelPicker.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/ModelPicker.kt)
is a `ModalBottomSheet` listing the 7 models with glyph/name/description.

### 6.5 Caption styling: `CaptionStyleSheet`

The style chip on a focused caption opens
[CaptionStyleSheet.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/CaptionStyleSheet.kt)
— a `ModalBottomSheet` editing that caption's font (Impact / Bold Sans / Serif), fill color
(white / yellow / black / red), alignment, outline toggle, and all-caps toggle. `MemeCanvas`
holds a `styleSheetTarget` (TOP/BOTTOM) and applies changes through the same `editCaptions`
path as every other caption edit (`CaptionData.withStyle`), so styles persist with the history
entry, re-stamp `refSize`, and render in thumbnails. Style changes preview live behind the
sheet's scrim. (History note: the wiring shipped in PR 10, was accidentally dropped in a
2026-06-24 canvas refactor, and was restored 2026-08-16.)

### 6.6 Theme & typography — "Stardate"

Dark-only design system ([theme/](app/src/main/java/com/rsilverst/mememeupscotty/ui/theme/)):
`Space900–400` backgrounds, `Plasma` cyan primary, `Photon` magenta accent, `Solar` gold,
`Red500` error, `TextHigh/Mid/Low`. One `darkColorScheme` — no light theme, no dynamic color.
Typography bundles four TTFs: Space Grotesk (display), Inter (body), JetBrains Mono
(labelMedium — the HUD/stardate voice), Anton (`MemeCaptionFontFamily`, the IMPACT-style
caption face). The screen background is a multi-stop "deep space" vertical gradient over
`Space900`. Previews live beside their components using the shared `PreviewShell` /
`PREVIEW_BG` ([PreviewShell.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/PreviewShell.kt)).

## 7. Save, share & import

All in [ImageUtils.kt](app/src/main/java/com/rsilverst/mememeupscotty/ui/ImageUtils.kt), all
operating on the flattened capture bitmap (§6.2):

- **Output format:** `WEBP_LOSSLESS` on API 30+, else PNG — both lossless (the old `WEBP`
  constant is lossy at every quality); WebP is ~25–30% smaller for meme content and preserves
  caption strokes exactly.
- **Save:** MediaStore insert into `Pictures/MemeMeUpScotty` with the `IS_PENDING` dance on
  API 29+; below 29 the legacy `WRITE_EXTERNAL_STORAGE` runtime-permission flow runs first
  (the manifest caps that permission at maxSdkVersion 28).
- **Share:** compress into `cacheDir/images/shared_meme_*.{webp,png}`, expose via
  `FileProvider` (authority `com.rsilverst.mememeupscotty.fileprovider`,
  [file_paths.xml](app/src/main/res/xml/file_paths.xml) maps `cache-path images/`), fire an
  `ACTION_SEND` chooser.
- **Import:** the system Photo Picker (no permission needed) → `copyUriToCache` streams the
  URI into a `gallery_meme_*` cache file → `MainViewModel.setLoadedImage` persists it into
  history like any generation.
- **Cleanup:** `cleanCacheDirectory` (activity create, §3) deletes the three ephemeral
  prefixes from `cacheDir` and `cacheDir/images`; the history dir is untouched.

## 8. Testing

- **Unit tests** (34, `./gradlew testDebugUnitTest`): `MainViewModelTest` (17 — generation
  lifecycle, cancellation, history CRUD/eviction/merge, caption persistence),
  `ReplicateImageRepositoryTest` (15 — HTTP-code→error mapping, polling, cancellation
  propagation), `ImageUtilsTest` (2 — cache cleanup semantics).
- **Test style: hand-rolled fakes, no mocking library** — `MockImageRepository` (with a
  `CompletableDeferred` gate so tests can observe the `Loading` state mid-flight),
  `FakeDataStore`, `FakeReplicateApi`. Keep new tests in this style.
- **Instrumented** (3, `./gradlew connectedDebugAndroidTest`, needs a device):
  `EnergizeButtonTest` pins the button's tri-state labels and click routing.

## 9. Cross-cutting invariants

1. **The repository never throws.** Failures are `GenerationOutcome.Failure` values; only
   `CancellationException` is rethrown (structured concurrency).
2. **The UI never reads human-readable strings from the data layer.** `GenerationError` is a
   typed taxonomy; copy lives in `strings.xml`. (`Unexpected.detail` passthrough is the one
   deliberate exception.)
3. **Captions are data until Save/Share.** Nothing bakes pixels except the capture path, and
   chrome never ships in a capture — anything drawn over the canvas must be gated on
   `!capturing`.
4. **Persisted formats are backwards-compatible** — legacy history key, Moshi defaults on new
   fields, enum-name fallbacks (§1.3, §5.2).
5. **In-memory ViewModel state is authoritative over DataStore emissions** — disk merges only
   ever add unknown entries, never overwrite live ones.
6. **DI stays manual.** New dependencies get wired in `MainActivity` / `NetworkModule`; no
   Hilt/Dagger.
7. **Moshi DTO field names are the wire/persisted format**, protected by `@JsonClass`-scoped
   R8 keep rules.
