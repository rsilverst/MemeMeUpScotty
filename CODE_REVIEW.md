# Code Review — Meme Me Up Scotty

**Reviewer:** Engineering TL (with input from UX Lead and PM)
**Scope:** Full review of all code, resources, tests, and product surface on `main` as of 2026-05-29.
**Build state at review:** ~2,025 LOC `MemeScreen.kt`, single Activity, single ViewModel, Replicate REST integration, dark "Stardate" Star-Trek-themed design system.

---

## Status Log

Track of what's been landed. Each entry: PR, items, date, deviations from the original recommendation.

| PR | Items | Date | Notes |
|---|---|---|---|
| **PR 1** | A3, A4, A5, C4 | 2026-05-29 | All four landed as recommended, **plus one unplanned addition**: `lint { disable += "Instantiatable" }` in `app/build.gradle.kts` to work around an AGP-alpha lint false positive on `ComponentActivity`. Should be removed once G1 (move off alpha AGP) lands. Release build (`assembleRelease`) and unit tests verified green. A4 implementation: chose the simpler `allowBackup="false"` route over populating the rule files; the `fullBackupContent`/`dataExtractionRules` manifest attrs were left in place but are inert. |
| **PR 2** | A1 *(interim)* | 2026-05-29 | **A2 and E10 were attempted and reverted same-day** on user feedback. Both were Play-Store generative-AI-policy items that do not apply to a personal-only build that the original brief explicitly said should have "no content moderation". A1 deviated from the "stand up a proxy" recommendation — proxy involves a hosting / billing decision out of scope for code work. Instead landed the **documented personal-build branch** the review offered as the alternative: new `README.md` with an explicit "Distribution status" section, plus a `REPLICATE_BASE_URL` BuildConfig seam (default `https://api.replicate.com/`) so a future proxy can be swapped in with one line in `local.properties`. A1 should be re-opened and a proxy stood up before any distribution. **A2 reverted:** restored `disable_safety_checker = true`. **E10 reverted:** removed the TopAppBar overflow + "Report content" menu item + supporting strings — pointless on a single-user app. Going forward, skip review items framed around Play Store generative-AI policy unless the distribution stance changes. Release build + unit tests green. |
| **PR 3** | B7 + prompt-quality expansion | 2026-05-29 | User asked whether better prompting could reduce real-world artifacts (extra/fused fingers, twin subjects, malformed limbs). Did B7 from the review plus an expanded artifact-focused negative prompt as one PR. Introduced a private `ModelPromptConfig` (positive suffix + negative prompt) in `ImageRepository.kt`, plus a `MODEL_PROMPT_CONFIGS` map keyed by Replicate model id. Photoreal models (Juggernaut, Stability, RealVis, DreamShaper) get a photoreal style suffix + anti-cartoon/anime negatives + the canonical anatomy/duplication negatives. Stylized models (Blue Pencil anime, Proteus painterly) get model-appropriate suffixes and **no** anti-cartoon/anime negatives. Flux Schnell gets a minimal natural-language suffix and **no** negative at all (rectified-flow architecture doesn't use CFG/negatives). Negative term selection is comment-explained inline. Release + unit tests green. Note: this also bumps the original "PR 3 — Split MemeScreen" item from the roadmap → PR 4. |
| **PR 6** | B2, B5, C7 | 2026-05-30 | Typed errors across the layer boundary. Introduced `GenerationOutcome` (`Success(file)` / `Failure(error)`) and `GenerationError` (`AuthRejected`, `OutOfCredit`, `ModelUnavailable`, `RateLimited(retryAfterSec)`, `Server(httpCode)`, `Timeout`, `Unexpected(detail)`) in `data/repository/ImageRepository.kt`. `ImageRepository.generateImage` now returns `GenerationOutcome`; repo's `errorFor` maps HTTP status → typed variant rather than building an English string. `GenerationState.Error` carries `GenerationError` instead of `String`. UI's `ErrorState` consumes the typed error via two pure mapping functions (`titleRes()`, `detailText()`) — the old `themedErrorTitle` keyword matcher is gone. Compile-time exhaustiveness now enforces UI updates when a variant is added. **B5**: `org.json.JSONObject` usage in `parseJsonField`/`parseJsonInt` replaced with a Moshi-codegen `ReplicateErrorBody` DTO; `NetworkModule` now exposes a shared `Moshi` instance that both Retrofit and the repo use. **C7**: hardcoded `"PROMPT"`, `"ENGINE"`, `"tap to change"` moved to `field_label_prompt`, `field_label_engine`, `hud_tap_to_change` in `strings.xml`. Test surface: `MockImageRepository` updated to new interface; added a third test asserting `AuthRejected` survives the repo → VM → state flow as the typed variant (not flattened). New strings: 7 detail resources (rate-limit has retry-with-seconds + no-retry variants) + a new `error_title_timeout` headline. `assembleDebug`, `assembleRelease`, `testDebugUnitTest` all green. |
| **PR 5** | D18, D19, D20, D21 | 2026-05-29 | UX polish batch from user testing. **D18**: `MemeCanvas.kt::LoadingState` — scan-line tween `2200ms → 1700ms` and replaced the single `pulseAlpha` with a `pulse` (0..1) that drives both `tint = lerp(Plasma500, Solar500, pulse).copy(alpha = 0.8 + 0.2*pulse)`, so the bolt warms to gold at peak per the mockup. **D20**: `PromptInput` gained a `minLines: Int = 1` parameter (guarded so a misconfigured caller with `singleLine = true` cannot crash BasicTextField); `Dock` and `ExpandedLayout` right-column now pass `minLines = 3`. **D19 + D21**: single-pass spacing rework. Canvas → HUD `12dp → 20dp` (both layouts), HUD → Dock `20dp → 24dp`, Dock vertical gap `12dp → 16dp`, expanded right column `18dp → 16dp`, paired Save/Share buttons `10dp → 8dp` (both layouts). `assembleDebug`, `assembleRelease`, `testDebugUnitTest` green. Note: this PR was inserted ahead of the original "PR 5 — Typed errors" item; subsequent PRs renumbered (+1) below. |
| **PR 4** | B1 | 2026-05-29 | Split the 2,025-line `MemeScreen.kt` into 8 files in `ui/`: `MemeScreen.kt` (324 — root + `MemeContent` state hoisting + topbar + snackbar host), `MemeLayouts.kt` (209 — `CompactLayout`, `ExpandedLayout`, `FieldLabel`), `MemeCanvas.kt` (570 — canvas + idle/loading/error states + `TransporterPad` + `themedErrorTitle`), `MemeTextOverlay.kt` (353 — overlay + handles + `AddTextPill` + `findBestFitFontSize`), `MemeControls.kt` (454 — `HudStrip`, `Dock`, `PromptInput`, `EnergizeButton`, `GhostButton`), `ModelPicker.kt` (280 — selector + sheet + card), `ImageModelMetadata.kt` (52 — `shortLabel`/`shortGlyph`/`displayNameRes`/`descriptionRes` extension props on `ImageModel`, now `internal`), `PreviewShell.kt` (53 — shared `PREVIEW_BG`, `PreviewShell`, `PreviewCanvasFrame`). Visibility tightened: composables called cross-file are `internal`, leaf helpers stayed `private`. All previews moved next to their components. Slightly different from the per-component subpackages the original review sketched (kept flat `ui/` for 8 files); the renamed `MemeLayouts.kt` bundles both layouts since they're sibling concerns. Behavior preserved verbatim — caught one subtle drift mid-split (`.clip(CircleShape).background(...)` → `.background(..., CircleShape)` on `ResizeHandle`) and reverted to the original. `assembleDebug`, `assembleRelease`, `testDebugUnitTest` all green. |
| **PR 7** | C1, C2, D3, D4 | 2026-06-01 | Capture correctness + undo for caption delete. **C1 + D4**: `captureCleanBitmap()` in `MemeScreen.kt` no longer waits a flat 2 frames before snapshotting — it now `delay(200)`s, which covers the 160ms fadeOut tween on the resize handle / delete chip's `AnimatedVisibility` exit transitions plus a small buffer. The saved bitmap no longer catches mid-fade chrome. Named constant `CAPTURE_CHROME_FADE_BUFFER_MS = 200L` with a comment pointing at the source-of-truth tween duration in `MemeTextOverlay.kt`. **C2**: `MemeCanvas.kt`'s `drawWithContent` block now branches on `capturing`: `if (capturing) { graphicsLayer.record { drawContent() } ; drawLayer(graphicsLayer) } else { drawContent() }`. The canvas no longer pays the layer-record cost on every recomposition during normal use; the layer is populated by the first frame after `capturing` flips true, which the 200ms delay then waits past. **D3**: deleting a caption no longer silently wipes the offset / size. Each `onDelete` lambda in `MemeCanvas` snapshots `(offset, size)` before clearing, then calls the new `onCaptionDeleted: (onUndo: () -> Unit) -> Unit` callback. `MemeContent` provides that callback: shows a `SnackbarDuration.Short` snackbar reading "Text box removed." with an "Undo" action that fires the restore lambda. The caption text itself is preserved by the existing `topText`/`bottomText` state in `MemeContent` (delete only affects visibility + transform), so undo restores everything. New strings: `caption_removed`, `undo`. `onCaptionDeleted` plumbed through `CompactLayout` and `ExpandedLayout`. `assembleDebug`, `assembleRelease`, `testDebugUnitTest` all green. |
| **PR 8** | C5, C6, C8 | 2026-06-01 | Inputs, drag bounds, and edge-to-edge insets. **C5**: `Dock`'s outer Column in `MemeControls.kt` now applies `windowInsetsPadding(WindowInsets.navigationBars)` so the Save/Share row doesn't sit under the 3-button nav bar in edge-to-edge mode. Kept on `Dock` itself (only `CompactLayout` calls it; tablet layout unaffected). **C6**: `PromptInput` gained an optional `onSubmit` parameter; the underlying `BasicTextField` now has `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go)` and `keyboardActions = KeyboardActions(onGo = { onSubmit?.invoke() })`. Both call sites — `Dock` in `CompactLayout`, the right-column input in `ExpandedLayout` — pass `onSubmit = onEnergize`. The existing `onEnergize` lambda already clears focus and early-returns on a blank prompt, so the Go key reuses the same code path. Captions intentionally left without an IME action (Enter means newline there). **C8**: `MemeCanvas` now tracks its rendered size with `onSizeChanged` and threads `parentSize: IntSize` to each `MemeTextOverlay`. The overlay tracks its own `positionInParent` via `onGloballyPositioned { coords -> positionInParent = coords.boundsInParent().topLeft }` and clamps drag by deriving the alignment anchor (`positionInParent - currentOffset`), clamping the desired absolute position to canvas bounds, then re-deriving the offset that would land there. Resize is also capped at parent dimensions so the handle can't grow the overlay past the canvas. Note: started with `coords.positionInParent()` per the original review sketch, but that extension wasn't resolving in Compose BoM 2026.05.01 even with the import; `boundsInParent().topLeft` is stable and gives the same value. `assembleDebug`, `assembleRelease`, `testDebugUnitTest` all green. |
| **PR 9** | E1 | 2026-06-03 | Pick-from-gallery as a first-class image source alongside AI generation. **Wiring**: `ImageUtils.kt` gained `copyUriToCache(context, uri, cacheDir): Result<File>` that streams a content URI into a `gallery_meme_*.img` cache file off-main. `MainViewModel.kt` gained `setLoadedImage(file: File)` — a thin setter that deletes the previously-tracked file, updates `lastGeneratedFile`, and flips state to `GenerationState.Success(file)`. The VM does not own the URI copy (no Context dependency); `MemeScreen` does the copy in a `coroutineScope.launch { … }` block and either calls `setLoadedImage` on success or shows a `load_image_failed` snackbar on failure. `MainActivity.onCreate` cache cleanup was widened from just `generated_meme_*` to also include `gallery_meme_*` and `shared_meme_*` (closes a sub-piece of C11 as a side effect). **UI**: uses `ActivityResultContracts.PickVisualMedia()` (system Photo Picker — no runtime permission, available on all supported API levels via the back-port). Two entry points: (a) a muted "Use a photo" pill on the empty state, sitting below the prompt chips as a visual alternative (transparent fill + plain border vs. the colored prompt chips, so it reads as a secondary action without competing); (b) a 32dp icon chip in the canvas top-right that swaps the current image — only rendered when `generationState is Success && showControls`, wrapped in `AnimatedVisibility(fadeIn/fadeOut tween 160)` to match the existing caption chrome and stay clean during capture. Both routes plumb a single `onPickImage` callback through `CompactLayout` / `ExpandedLayout` → `MemeCanvas`. Gallery-picked images land in the same `GenerationState.Success(File)` so capture, save, share, and caption overlays just work. Did not implement E3 (aspect ratios) — explicitly deferred. `assembleDebug`, `assembleRelease`, `testDebugUnitTest` all green. |
| **OOB — Release signing** | (release prep, not in review) | 2026-06-01 | Wires release `signingConfig` from four `local.properties` entries (`RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). When any property is missing, the `signingConfigs` block isn't created and the release variant stays unsigned, so fresh checkouts and CI without a keystore still build. `.gitignore` broadened to ignore the whole `.claude/` directory plus `*.jks` / `*.keystore` patterns. Not strictly a review item but enables a real distributable APK once A1 / A6 are addressed. Commit `22cef38`. |
| **OOB — Compact layout rebuild** | D1, IME keyboard fix | 2026-06-01 | The bottom-stacked prompt input collided with the soft keyboard on phones. Rebuilt `CompactLayout` so all inputs sit above the canvas (Prompt → HUD → Energize → Canvas → Save/Share row). Caption editing on the canvas now works under the keyboard because the scroll viewport shrinks by the IME inset; `imePadding()` on the scrolling Column lets default bring-into-view scroll the focused caption above the keyboard. **D1 (tap-outside-to-dismiss)**: a `pointerInput` tap handler on the scrolling Column calls `focusManager.clearFocus()` when the user taps empty space; taps on `BasicTextField`s are consumed by the field, so the outer handler only fires for empty-area taps. Dropped the now-unused `Dock` composable (and its two previews) since the compact layout inlines those pieces directly; the expanded tablet layout already inlined them and is unchanged. Commit `ba753fe`. |
| **OOB — Visual polish** | D11 | 2026-06-03 | **D11 (background gradient is almost flat)**: replaced the 3-stop `Space900 → 0xFF0D1128 → Space900` gradient with a 4-stop nebula gradient using deep indigo, warm magenta, and cold teal, so the "deep space" intent reads at typical viewport sizes. Also color-coded the three idle-state suggested-prompt chips: `PromptChip` refactored to accept `borderColor` / `textColor` / `backgroundColor`, and the chips now use `Plasma500` (blue), the new `Photon500` (purple), and `Solar500` (gold) derived with varying alpha. Copy edits: "Try one of these" → "Or try one of these", "Energize Again" → "Re-energize". Does not change D5 (chips still fill the prompt rather than auto-generating). Commit `fad071f`. |
| **PR 14** | C3, B9 | 2026-06-04 | Cancel during generation + drop dead enum field. **C3**: `MainViewModel` tracks the in-flight coroutine as `generationJob: Job?`. A fresh `generateImage()` cancels any previous attempt first — closes the F2 rapid-race gap as a side effect. New public `cancelGeneration()` cancels the job; the `launch` body catches `CancellationException`, flips state back to `Idle`, and re-throws so the coroutine machinery sees the cancellation cleanly. UI: `EnergizeButton` keeps the same real estate but swaps purpose during `Loading` — label "CANCEL", icon `Filled.Close`, container `Space600`, content `Red500`, `onClick = onCancel`. The MATERIALIZING animation + `T+NS` counter in the canvas are the "still working" signal so the button can be purely an abort affordance. `onCancel` threaded through `MemeScreen` → `CompactLayout`/`ExpandedLayout` → `EnergizeButton`; default `onCancel = {}` on the leaf composable keeps the previews compiling without ceremony. New string: `cancel`. Tests: VM test `cancelGeneration_duringLoading_returnsToIdle` exercises the cancel path with the existing `MockImageRepository` gate (gate stays closed so the only thing that can drive Loading → Idle is the cancel); the `EnergizeButtonTest` loading-state test was rewritten in place to assert "CANCEL" + enabled + click routes to `onCancel` (replaces, not adds — pinning the new behavior). 22/22 unit tests green (8 VM + 14 repo). **B9**: dropped the `label` field from `ImageModel` (the enum is now just `(val id: String)`); verified no remaining `.label` references first. Header comment on the enum now points at `ui/ImageModelMetadata.kt` for where display names actually live. |
| **PR 13** | B4, B6, C11 (pin), C12 + B3/B8/E12 deprioritized | 2026-06-04 | Quick-batch cleanup. **B4 (snake_case → camelCase)**: `latest_version`/`negative_prompt`/`disable_safety_checker`/`retry_after` on the four Replicate DTOs (`ReplicateModel`, `ReplicatePredictionInput`, `ReplicateErrorBody`) renamed to camelCase with `@Json(name = "…")` to preserve the wire format. Four call sites in `ImageRepository` updated; two test fixture call sites in `ReplicateImageRepositoryTest` updated. Wire-side JSON in test body strings (e.g. `"retry_after":42`) intentionally left snake_case. **B6 (modern ViewModelFactory)**: deleted the `MainViewModelFactory` class entirely; `MainActivity.onCreate` now builds the factory inline with `viewModelFactory { initializer { MainViewModel(imageRepository) } }` from `androidx.lifecycle.viewmodel.{viewModelFactory, initializer}`. Removed the now-unused `androidx.lifecycle.ViewModelProvider` import from `MainViewModel.kt`. **C11 (pin)**: just a documentation correction — the `shared_meme_*` leak was actually closed back in PR 9 when `MainActivity.onCreate` cache cleanup was widened to all three prefixes (`generated_meme_*`/`gallery_meme_*`/`shared_meme_*`); PR 9 noted it only as a "sub-piece" side effect. Pinning the row fully closed; no code change. **C12 (loading progress)**: small elapsed-time counter rendered in-theme as `"T+5S"` (Stardate vibe) using `MaterialTheme.typography.labelMedium` (mono) in `TextLow`. 6dp under the "MATERIALIZING" label. Driven by a local `LaunchedEffect(Unit) { while (true) { delay(1000); elapsedSec++ } }` inside `LoadingState`; conditional `if (elapsedSec > 0)` skips the noisy `T+0S` flash before any time has actually passed. Restarts whenever LoadingState re-enters composition (i.e. a new generation begins). **Deprioritized**: B3 (paper-only — the brief is aspirational, single-screen app doesn't need Nav3 or material3.adaptive), B8 (PR 11's `FakeReplicateApi` already injects at the `ReplicateApi` seam — NetworkModule's static-singleton shape doesn't actually block tests), E12 (single-user app — Play-distribution-grade hygiene that doesn't apply, same family as A2/E10). Build verified: `./gradlew :app:testDebugUnitTest` passes 21/21. Kotlin emits four "annotation target shifting in future Kotlin" warnings on the `@Json` lines — benign for Moshi codegen which reads the parameter-target annotation today, and the future-Kotlin behavior of also applying to property doesn't break anything for our use. Worth a one-line `-Xannotation-default-target=…` compiler arg if the warnings become noise. |
| **PR 11** | F1, F2 (partial), F3 (seed), F4 | 2026-06-04 | Test surface buildout. **F1**: new `ReplicateImageRepositoryTest` with a small file-private `FakeReplicateApi` and Retrofit `Response.success` / `Response.error` stubs (no MockWebServer dependency). 14 tests cover: invalid model id (no `/`); 401/402/404/429-with-retry-after/429-without/503 → typed `AuthRejected`/`OutOfCredit`/`ModelUnavailable`/`RateLimited(seconds)`/`RateLimited(null)`/`Server(503)`; model returned with `latest_version = null` → `Unexpected("no published version")`; `createPrediction` 402 → `OutOfCredit` (smoke-test that error mapping runs on the second API call too); poll terminal `status = "failed"` with error message → `Unexpected(error)`; poll terminal `status = "canceled"` → `Unexpected("...canceled")`; poll terminal `status = "succeeded"` with `output = null` → `Unexpected("no image")`; getPrediction HTTP failure mid-poll → `Timeout` (pinned to document current "null from poll = Timeout" semantics so a deliberate semantic change has a failing test); thrown `RuntimeException` from getModel → `Unexpected(message)`. Skipped: full happy-path download (needs MockWebServer for the `replicate.delivery` URL fetch) and wall-clock-driven 120s timeout (`System.currentTimeMillis` is real time, not virtual). **F2**: extended `MainViewModelTest` with `selectModel` updates the flow; `setLoadedImage` flips state to `Success`; `setLoadedImage` deletes the previously tracked file (real temp files, asserts `.exists()` before/after); successful `generateImage` after `setLoadedImage` deletes the previous file. Skipped rapid-back-to-back races + cancellation — the rapid case is a latent VM bug (no cancel of in-flight) that should be fixed before being pinned by tests. **F3**: minimal seed `EnergizeButtonTest` in `androidTest/.../ui/` exercises the three state-dependent renders (Idle/Loading/Success → "ENERGIZE"/"ENERGIZING…"/"RE-ENERGIZE", correct enabled state, clickable). Remaining critical paths (blank-prompt gating, error title + Retry, Save/Share gating, model picker select/dismiss) left as follow-ups. **F4**: removed `ExampleUnitTest` and `ExampleInstrumentedTest`. Build verified inside Android Studio (CLI build still blocked at the gradle script layer, unrelated to test code). |
| **PR 10** | E4 | 2026-06-03 | Per-caption text styling. **Data model**: new `ui/CaptionStyle.kt` with a `CaptionStyle` data class and three enums — `CaptionFont` (`IMPACT` = Anton + Black, `BOLD_SANS` = Inter + Bold, `SERIF` = system Serif + Bold), `CaptionFill` (`WHITE`, `YELLOW = #FFD53D`, `BLACK`, `RED = #E74C4C`), `CaptionAlign` (`LEFT`/`CENTER`/`RIGHT` → `TextAlign.Start`/`Center`/`End`). Defaults (IMPACT + WHITE + CENTER + outline-on) replicate the prior hardcoded rendering byte-for-byte so existing captions look unchanged. **Overlay**: `MemeTextOverlay` gained `style: CaptionStyle` and `onOpenStyleSheet: () -> Unit` params. Hardcoded `MemeCaptionFontFamily` / `FontWeight.Black` / `Color.White` / `TextAlign.Center` are gone — every value reads from `style`. The stroke pass is now gated on `style.outline`; the placeholder uses `fillColor.copy(alpha = …)` instead of always-white. `findBestFitFontSize` threads `fontWeight` through so non-Black weights measure honestly. New `StyleChip` (`Icons.Outlined.FormatColorText`, Plasma500 tint, Space600 fill, matching DeleteChip's visual idiom) sits at `Alignment.TopStart` inside the chrome's `AnimatedVisibility(fadeIn/fadeOut tween 160)` block — visible on focus, fades out for capture so it never bakes into the saved bitmap. **Sheet**: new `ui/CaptionStyleSheet.kt`, modeled on `ModelPickerSheet` (`ModalBottomSheet`, `skipPartiallyExpanded = true`, Space700 container, Space900 scrim 0.65α, 40×4dp Space500 drag handle). Four sections: font row (3 swatches rendered in their own typeface), color row (4 circular swatches, checkmark on selected with inverted tint for white/yellow), alignment row (3 icon buttons using `FormatAlignLeft/Center/Right`), outline `Switch`. `onStyleChange` fires on every interaction so the underlying caption updates live — the user sees their pick reflect immediately and dismisses when satisfied. **Wiring**: `MemeCanvas` now owns `topStyle` / `bottomStyle` (each a `CaptionStyle()`) plus a private `CaptionTarget? { TOP, BOTTOM }` for which caption the sheet is editing. Top and bottom captions style independently; opening the sheet from one caption never affects the other. The sheet is mounted at the function root (sibling of the canvas Box) keyed on the target, so the popup-window-based sheet doesn't fight the canvas's clip-RoundedCornerShape. **Strings**: 16 new resources (sheet title, four section labels, three font names, four color names, three alignment a11y labels, chip content description). Skipped E2 (multi-text-box) at user request — top + bottom keep their roles. Build verified inside Android Studio (CLI build is blocked at the gradle script layer by an in-flight AGP/Gradle interaction unrelated to this change). |

Legend in section headings / table: ✅ **DONE** = implemented; ⚠️ **DONE w/ deviation** = implemented but differs from the original recommendation in some material way; 🚫 **NOT APPLICABLE** = the original recommendation does not apply to this app's stance (e.g., distribution-grade policy items on a personal build); (blank) = not yet started.

---

## TL;DR

The app does what the brief asked: prompt → AI image → caption → save/share. The Stardate design system is strong and the engineering slice (network → repo → VM → Compose) is clean for its size. **But:** there are P0 distribution blockers (API token in APK, safety checker disabled, ProGuard off, trademark risk), the "meme creator" feature surface is narrower than users expect (AI-only source, 1:1 canvas, fixed top/bottom captions), and `MemeScreen.kt` is a 2,000-line god file that will not scale.

**Recommended order of work:**
1. **Group A — Security & Release-gating (P0).** Token handling, safety, ProGuard, logging, backup.
2. **Group B — Architecture cleanup (P1).** Split `MemeScreen.kt`, typed errors, DI, snake-case Moshi names.
3. **Group C — UX polish (P1).** Drag-bounds, undo for delete, cancel-during-generate, capture timing, accessibility/touch targets.
4. **Group D — Product gaps (P1/P2).** Image source picker, aspect ratios, N text boxes, prompt/generation history.
5. **Group E — Testing (P2).** Repo unit tests, UI/Compose tests, prune skeletons.
6. **Group F — Polish & i18n (P3).** Localize strings, rename app/trademark cleanup, theme phrasing.

---

# Group A — Security, Privacy, Release-gating (P0)

These block any public distribution. Even for internal/sideload, they are real risks.

### A1. Replicate API token is compiled into the APK [P0] ⚠️ **PARTIAL — INTERIM (PR 2, 2026-05-29)**
**File:** `app/build.gradle.kts:50`, `data/network/NetworkModule.kt:19`

`REPLICATE_API_TOKEN` is read from `local.properties` and baked into `BuildConfig` as a string constant. Anyone with the APK can extract it with `apktool` or `strings`. Replicate is a billed paid service — token theft becomes the user's bill. This pattern also violates Replicate's ToS (tokens are per-user, not embeddable).

It is also at risk via auto-backup (see A4).

**Action:** Choose a path before any distribution:
- **Personal-only build:** explicitly mark `release { signingConfig = … }` as internal-only, never ship to Play, and document in README.
- **Distributable:** stand up a thin proxy (Cloudflare Worker, Vercel function, Firebase Function) that holds the token server-side. The app calls the proxy; the proxy calls Replicate. ~50 LOC server, ~10 LOC client change.

**Implementation note (interim, PR 2):** PR 2 took the **personal-only documented** branch. The token still ships in BuildConfig — this is **not** the full fix; it is a documented interim state.
- New `README.md` has a "Distribution status — read before sharing" section flagging that the APK must not be distributed and listing what blocks distribution (this item plus A2, A5, A6, E10).
- A `REPLICATE_BASE_URL` BuildConfig field was added (defaults to `https://api.replicate.com/`, overridable in `local.properties`), so the proxy swap-in is one-line later: point at the proxy URL and the proxy can ignore or rewrite the `Authorization` header.
- Release signing config is **still not** in place; a separate PR should add a release keystore + `signingConfig` when distribution is imminent.

**Remaining work to close A1:** stand up the proxy, deploy, set `REPLICATE_BASE_URL` to it, optionally drop the `Authorization` header from the OkHttp interceptor if the proxy injects its own.

### A2. Model-level safety checker is disabled [P0 for distribution / P2 for personal] 🚫 **NOT APPLICABLE — personal-build stance (PR 2, 2026-05-29)**
**File:** `data/repository/ImageRepository.kt:40-43`

`disable_safety_checker = true` is hardcoded. Comment justifies it as "personal/single-user app." Google Play has a Generative AI Apps policy (effective 2024) that requires safety filtering, an in-app reporting mechanism, and a content policy. This combo will trip the policy check on submission.

**Action:** Default to safety-on. Add an internal-only debug toggle if needed. If distributing, also wire an in-app "Report" affordance.

**Implementation note (reverted PR 2, 2026-05-29):** initially removed `disable_safety_checker = true`, but the user pushed back: the original brief explicitly says "no content moderation," and this is a personal-only build that is not being distributed. Restored `disable_safety_checker = true`. **For this app, treat this item as Not Applicable.** Future reviews should skip Play-policy-derived recommendations unless the distribution stance changes.

### A3. OkHttp body logging is unconditional [P0] ✅ **DONE (PR 1, 2026-05-29)**
**File:** `data/network/NetworkModule.kt:12-14`

`HttpLoggingInterceptor.Level.BODY` runs in release. This logs request and response bodies, including any future PII, to logcat. It also adds non-trivial overhead per request.

**Action:**
```kotlin
level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
```

### A4. Auto-backup is enabled with no exclusions [P0 once A1 is fixed] ✅ **DONE (PR 1, 2026-05-29)**
**File:** `AndroidManifest.xml:9`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`

`android:allowBackup="true"` plus empty backup rule files means everything in the app's data dir gets cloud-backed to the user's Google account. As long as A1 stands (token in BuildConfig), the token itself is fine (it lives in code, not data). But once you move to per-user tokens or any cached prediction data, you will leak.

**Action:** Set `allowBackup="false"` for now, or populate `data_extraction_rules.xml` to exclude `sharedpref` and any future auth storage.

**Implementation note:** went with `allowBackup="false"`. The `fullBackupContent="@xml/backup_rules"` and `dataExtractionRules="@xml/data_extraction_rules"` manifest attributes were left in place — they're inert while `allowBackup="false"` but ready to populate when per-user persistence lands.

### A5. ProGuard / R8 disabled on release [P0] ⚠️ **DONE w/ deviation (PR 1, 2026-05-29)**
**File:** `app/build.gradle.kts:53-58`

```kotlin
release { optimization { enable = false } }
```
No minification, no shrinking, no obfuscation. APK size is bloated and the BuildConfig token sits in plain UTF-8 inside the DEX.

**Action:** Turn R8 on with `isMinifyEnabled = true`, `isShrinkResources = true`, add a `proguard-rules.pro`, and verify a release build runs end-to-end before merging.

**Implementation note (deviation):** R8 + shrink-resources are on, `proguard-rules.pro` added with keep rules for `data.network.**` (Moshi-codegen DTOs) and standard stack-trace attributes. `assembleRelease` produces a 3.6 MB unsigned APK and unit tests pass.

`lintVitalRelease` was failing with a known false positive ("MainActivity must extend android.app.Activity") on AGP `9.3.0-alpha06` + AndroidX activity `1.13`. Suppressed the `Instantiatable` check only, with an in-source comment pointing to **G1** (move off alpha AGP) as the long-term fix. The suppression should be removed when G1 lands.

### A6. Trademark exposure — Star Trek IP [P0 for Play / P2 internal]
App name "Meme Me Up Scotty", "Stardate" design system, "Energize", "Materializing", "Bridge", "Subspace link rejected", "Spock holding a banana" suggested prompt. This is heavy use of CBS/Paramount-owned brand language. The Trek voice is a strength of the app's identity — but legal exposure if distributed.

**Action (PM/legal):** Decide before launch:
- Keep theme + rebrand verbally (e.g., "starship engineer aesthetic" without Trek-specific words),
- File for parody/fair-use opinion, or
- Pursue licensing.

### A7. Hardcoded token committed risk audit [resolved — informational]
`local.properties` is correctly listed in `.gitignore` and was never committed (verified via `git log --all -- local.properties`). Good. But anyone who has a copy of this working tree (including AI agents, build artifacts, cloud sync, etc.) now holds a live token. Rotate the token in `local.properties` if any of those have touched it.

---

# Group B — Architecture & Engineering (P1)

### B1. `MemeScreen.kt` is 2,025 lines [P1] ✅ **DONE (PR 4, 2026-05-29)**
**File:** `app/src/main/java/com/rsilverst/mememeupscotty/ui/MemeScreen.kt`

Holds screen root, compact + expanded layouts, canvas, every state, every overlay, every input, dock, model picker, model metadata extension props, font-fitting algorithm, and 13 previews. This is the largest source of future friction.

**Action:** Split along the section comments that already exist:
```
ui/MemeScreen.kt           // root + state hoisting
ui/CompactLayout.kt
ui/ExpandedLayout.kt
ui/canvas/MemeCanvas.kt
ui/canvas/EmptyState.kt
ui/canvas/LoadingState.kt
ui/canvas/ErrorState.kt
ui/canvas/MemeTextOverlay.kt
ui/canvas/TransporterPad.kt
ui/HudStrip.kt
ui/Dock.kt
ui/ModelPickerSheet.kt
ui/model/ImageModelMetadata.kt   // shortLabel/shortGlyph/displayNameRes/descriptionRes
ui/text/FontFitting.kt
```
Previews live next to each component.

**Implementation note (PR 4, 2026-05-29):** consolidated to 8 files in a flat `ui/` package instead of the proposed 14-file subpackage layout — the per-state subdivisions (separate files for `EmptyState`, `LoadingState`, `ErrorState`) felt over-fragmented for one screen. Final shape: `MemeScreen.kt` (root + state hoisting + topbar + snackbar host), `MemeLayouts.kt` (`CompactLayout` + `ExpandedLayout` + `FieldLabel`), `MemeCanvas.kt` (canvas + all states + `TransporterPad` + `themedErrorTitle`), `MemeTextOverlay.kt` (overlay + handles + `AddTextPill` + `findBestFitFontSize`), `MemeControls.kt` (HUD + Dock + buttons), `ModelPicker.kt` (selector + sheet + card), `ImageModelMetadata.kt` (extension props), `PreviewShell.kt` (shared preview helpers). Largest file is now `MemeCanvas.kt` at 570 lines. Behavior preserved verbatim. See PR 4 row in the Status Log for the file-by-file breakdown.

### B2. Errors are stringly-typed across the layer boundary [P1] ✅ **DONE (PR 6, 2026-05-30)**
**File:** `data/repository/ImageRepository.kt:108-125`, `ui/MemeScreen.kt:935-945`

The repo builds a human-friendly English string, returns it as `Exception.message`. The UI then **string-matches** that English ("token" in lower, "401" in lower, "credit" in lower, …) to pick a themed title. This is fragile: any string tweak in the repo silently breaks the UI grouping, and it cannot be localized.

**Action:** Replace `Result<File>` with a sealed `GenerationOutcome`:
```kotlin
sealed class GenerationError {
    data object AuthRejected : GenerationError()
    data object OutOfCredit : GenerationError()
    data object ModelNotFound : GenerationError()
    data class RateLimited(val retryAfterSec: Int?) : GenerationError()
    data class Server(val code: Int) : GenerationError()
    data class Unknown(val message: String) : GenerationError()
}
```
UI maps each variant to title + detail.

### B3. Stated architecture vs. actual [P1] 🚫 **DEPRIORITIZED — paper-only (PR 13, 2026-06-04)**
The brief in `app/.agent/plan.md` lists **Jetpack Navigation 3** and **Compose Material Adaptive** as the navigation/adaptive strategy. Neither is in the dependencies. The app is single-screen and uses a hand-rolled `screenWidthDp >= 840` check.

**Action:** Either update the brief to reflect reality (recommended — single screen doesn't need Nav3), or adopt `androidx.compose.material3.adaptive` for the breakpoint and any future foldable support.

**Implementation note:** the brief is aspirational, the code is what it is — a single-screen app with one width breakpoint doesn't need Nav3 or material3.adaptive. Closing as paper-only; if the screen graph ever grows beyond one, reopen and revisit.

### B4. Snake-case Kotlin property names on data classes [P1] ✅ **DONE (PR 13, 2026-06-04)**
**File:** `data/network/ReplicateApi.kt:11-44`, `data/repository/ImageRepository.kt:43`

`latest_version`, `negative_prompt`, `disable_safety_checker` are Kotlin properties with snake_case. Works because Moshi serializes by property name. Violates Kotlin style and confuses readers about intent.

**Action:** Rename to camelCase and annotate:
```kotlin
@Json(name = "latest_version") val latestVersion: ReplicateModelVersion?
```

**Implementation note:** renamed `latest_version`/`negative_prompt`/`disable_safety_checker`/`retry_after` to camelCase with `@Json(name = "…")` to preserve the wire format. Updated the four call sites in `ImageRepository` and the two test fixture call sites in `ReplicateImageRepositoryTest`. Test JSON body strings (e.g. `"retry_after":42`) intentionally stay snake_case — that's still the wire-side spelling.

### B5. Mixed JSON parsers [P2] ✅ **DONE (PR 6, 2026-05-30)**
**File:** `data/repository/ImageRepository.kt:128-138`

`parseJsonField` / `parseJsonInt` use `org.json.JSONObject` to dig into error bodies even though Moshi is wired up.

**Action:** Define a `@JsonClass` `ReplicateError(detail: String?, retry_after: Int?)`, parse with Moshi.

### B6. Hand-rolled `ViewModelFactory` is dated [P2] ✅ **DONE (PR 13, 2026-06-04)**
**File:** `ui/viewmodel/MainViewModel.kt:81-91`, `MainActivity.kt:42-47`

Boilerplate. Modern pattern uses the `viewModel { initializer { … } }` API or Hilt.

**Action:** Replace with a `viewModelFactory { initializer { MainViewModel(repo) } }` declared in MainActivity, drop the factory class entirely.

**Implementation note:** dropped the `MainViewModelFactory` class entirely. `MainActivity` now builds the factory inline with `viewModelFactory { initializer { MainViewModel(imageRepository) } }` from `androidx.lifecycle.viewmodel.{viewModelFactory, initializer}`. Removed the now-unused `androidx.lifecycle.ViewModelProvider` import from `MainViewModel.kt`.

### B7. Hardcoded "photorealistic" style suffix conflicts with stylized models [P1 — bug] ✅ **DONE (PR 3, 2026-05-29)**
**File:** `data/repository/ImageRepository.kt:146-147`

```kotlin
"$userPrompt, photorealistic, sharp focus, natural lighting, high detail, cinematic photograph"
```
Appended to every prompt regardless of model. Users who select **Blue Pencil XL (anime)** or **Proteus (painterly)** get a photorealistic suffix that fights the model's strengths.

**Action:** Pull the style suffix into `ImageModel` metadata so each model contributes its own modifiers (or none).

**Implementation note (PR 3, 2026-05-29):** went a slightly different route from "put it on `ImageModel`" — the per-model config (`MODEL_PROMPT_CONFIGS` map keyed by Replicate model id, plus a private `ModelPromptConfig` data class) lives in `ImageRepository.kt`. Reasoning: `ImageModel` is in the `ui.viewmodel` package, and putting prompt-shaping data on a UI-layer enum would couple the data layer to UI. Keeping the configs string-keyed in the repository preserves layer separation; if `ImageModel.id` values change, the map keys need to follow. Also landed expanded anatomy/duplication negatives for the same artifacts users were actually seeing (extra/fused fingers, twins, mangled limbs) — that scope was outside B7 strictly but was the motivating reason to revisit this file. See the PR 3 row in the Status Log above for full details.

### B8. `NetworkModule` is an `object` singleton with no swappable seam [P2] 🚫 **DEPRIORITIZED — current tests don't need it (PR 13, 2026-06-04)**
**File:** `data/network/NetworkModule.kt`

Functional now. Becomes a problem as soon as you want a `FakeReplicateApi` for instrumentation tests, or a second network module for A/B'd proxies.

**Action:** Either Hilt (heavyweight) or a simple `interface NetworkProvider` with one prod and one test impl, injected from `MainActivity`.

**Implementation note:** PR 11's `FakeReplicateApi` injects at the `ReplicateApi` interface level (the seam that already exists), so the NetworkModule's static-singleton shape doesn't actually block the test surface. Revisit if a second prod backend (e.g. proxy A/B) lands.

### B9. ImageModel.label is dead code [P3] ✅ **DONE (PR 14, 2026-06-04)**
**File:** `ui/viewmodel/MainViewModel.kt:13-21`

The `label` field on `ImageModel` is unused — UI now reads `displayNameRes` via extension. Drop the field to avoid stale labels misleading future readers.

**Implementation note:** dropped. Enum is now just `ImageModel(val id: String)`. Verified no remaining `.label` references via grep before removing. Added a one-line header comment pointing future readers at `ui/ImageModelMetadata.kt` for display-name lookups.

---

# Group C — Code Correctness, Threading, Performance (P1–P2)

### C1. GraphicsLayer capture timing is racy [P1] ✅ **DONE (PR 7, 2026-06-01)**
**File:** `ui/MemeScreen.kt:249-256`

```kotlin
suspend fun captureCleanBitmap(): Bitmap {
    capturing = true
    withFrameNanos { }
    withFrameNanos { }
    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
    capturing = false
    return bitmap
}
```
Chrome (delete chip, resize handle) is animated out with `AnimatedVisibility(..., exit = fadeOut(tween(160)))`. Two frames at 16ms = 32ms, less than the 160ms fadeOut. The captured bitmap can include partially-faded chrome.

**Action:** Either use `snap()` for the chrome exit during capture, or replace the `withFrameNanos` wait with a `snapshotFlow { showChrome }.first { !it }` confirmation, then capture.

**Implementation note:** went with the simplest robust fix — replaced the two `withFrameNanos { }` calls with a single `kotlinx.coroutines.delay(CAPTURE_CHROME_FADE_BUFFER_MS)` (200ms). That covers the 160ms tween plus a small buffer so the recorded layer is clean by the time we snapshot. `snapshotFlow` won't observe the `AnimatedVisibility` exit animation (alpha is internal to the modifier), so the delay was the cleaner option. The constant lives next to `MemeContent` with a comment pointing back at `MemeTextOverlay.kt`'s tween duration as the source of truth.

### C2. `graphicsLayer.record` runs on every recomposition of the canvas [P2] ✅ **DONE (PR 7, 2026-06-01)**
**File:** `ui/MemeScreen.kt:582-587`

The `drawWithContent { graphicsLayer.record { … } drawLayer(graphicsLayer) }` block always records, even outside capture. Each recomposition pays the layer-record cost.

**Action:** Only record while `capturing` (or guarded by a "needs new snapshot" flag).

**Implementation note:** the `drawWithContent` block in `MemeCanvas.kt` now branches on `capturing`: it records into the layer + draws via `drawLayer(graphicsLayer)` only while `capturing`, and falls through to a plain `drawContent()` during normal use. Because `capturing` is a state read inside the draw block, flipping it true causes one immediate re-record; the C1 200ms delay then sits on top of that so the layer is current by the time `toImageBitmap()` runs.

### C3. Polling has no cancel UX [P1] ✅ **DONE (PR 14, 2026-06-04)**
**File:** `data/repository/ImageRepository.kt:74-85`, `ui/MemeScreen.kt` (Energize button)

Once the user presses Energize they cannot abort until the 120s timeout. Long predictions feel like a hang.

**Action:** Add a "Cancel" affordance during `Loading`. The repo already runs in `viewModelScope` so cancellation cascades correctly; just expose a `vm.cancel()` and surface a button.

**Implementation note:** `MainViewModel` now tracks the in-flight coroutine as `generationJob: Job?`. A fresh `generateImage()` cancels any previous attempt first (closes the rapid-back-to-back race from F2). New `cancelGeneration()` cancels the job; the `launch` body catches `CancellationException` and flips state back to `Idle` before re-throwing. UI: `EnergizeButton` swaps purpose during `Loading` — same button real estate, but the label becomes "CANCEL", the icon swaps from `Bolt` to `Close`, the container darkens to `Space600` with `Red500` content, and `onClick` routes to `onCancel`. The MATERIALIZING animation + `T+NS` counter in the canvas remain the "still working" signal. `onCancel` is threaded through `MemeScreen` → `CompactLayout` / `ExpandedLayout` → `EnergizeButton`. Tests: VM test pins `cancelGeneration()` during Loading → `Idle`; the `EnergizeButtonTest` loading-state test was rewritten to assert "CANCEL" + enabled + click routes to `onCancel` (the old test that pinned the now-removed disabled "ENERGIZING…" behavior was replaced, not added alongside).

### C4. `MainActivity.onCreate` cleanup uses `printStackTrace` [P3] ✅ **DONE (PR 1, 2026-05-29)**
**File:** `MainActivity.kt:37-39`

`printStackTrace()` goes nowhere useful in production. Same in `ImageRepository`/`ImageUtils`.

**Action:** Use `Log.w(TAG, …)` or your eventual telemetry hook.

**Implementation note:** swept three call sites (`MainActivity.onCreate` cache cleanup, `saveBitmapToGallery`, `shareBitmap`). `ImageRepository` did **not** have a `printStackTrace` to clean up — it swallows deletion errors silently and returns typed failures already, so nothing to do there.

### C5. No status-bar / nav-bar inset handling on the Dock [P1 — visual cutoff] ✅ **DONE (PR 8, 2026-06-01)**
**File:** `MainActivity.kt:27` (enableEdgeToEdge), `ui/MemeScreen.kt:1281+`

`enableEdgeToEdge()` is enabled, but the Dock at the bottom of the CompactLayout has no `WindowInsets.navigationBars` padding. On 3-button-nav devices the Save/Share buttons can sit under the nav bar.

**Action:** Apply `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` to the Dock container (or to the Scaffold's bottom-content via `bottomBar = { Dock(...) }`).

**Implementation note:** added `windowInsetsPadding(WindowInsets.navigationBars)` directly on the `Dock` Column in `MemeControls.kt`. Kept `Dock` as the inset boundary (rather than restructuring to a Scaffold `bottomBar`) — only `CompactLayout` calls `Dock`, so the change is contained and the tablet `ExpandedLayout` is unaffected. Comment in source notes that Scaffold without an explicit `bottomBar` does not always emit a bottom inset in its content paddingValues, which was the latent failure mode.

### C6. No IME action / soft-keyboard handling on inputs [P2] ✅ **DONE (PR 8, 2026-06-01)**
**File:** `ui/MemeScreen.kt` PromptInput, MemeTextOverlay

`BasicTextField` defaults have no `KeyboardOptions(imeAction = ImeAction.Go)` for the prompt, no `onDone = { onEnergize() }`. Typing a prompt and hitting Enter does nothing.

**Action:** Wire prompt input to submit on Done/Go.

**Implementation note:** `PromptInput` gained an optional `onSubmit: (() -> Unit)? = null` parameter; the underlying `BasicTextField` now sets `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go)` and `keyboardActions = KeyboardActions(onGo = { onSubmit?.invoke() })`. Both call sites (`Dock` in compact, the right-column input in `ExpandedLayout`) pass `onSubmit = onEnergize`. `onEnergize` already clears focus and early-returns on a blank prompt, so the Go key reuses the same path as the Energize button. Caption overlays (`MemeTextOverlay`) were intentionally left without an IME action — multi-line meme captions want Enter to insert a newline, and there's no obvious second action they could trigger.

### C7. Hardcoded English strings in code [P2] ✅ **DONE (PR 6, 2026-05-30)**
**File:** `ui/MemeScreen.kt:494,501,1236`

`"PROMPT"`, `"ENGINE"`, `"tap to change"` are literal in code. Block on i18n.

**Action:** Move to `strings.xml`.

### C8. No bounds-clamping on text overlay drag [P1 — UX bug] ✅ **DONE (PR 8, 2026-06-01)**
**File:** `ui/MemeScreen.kt:992-998`

`detectDragGestures` updates `offset` without clamping. Users can drag a text box completely off the canvas and lose it (the canvas clips at `RoundedCornerShape(20.dp)`; chrome appears only on focus, which is lost when not visible).

**Action:** Clamp `offset` to canvas bounds in the drag handler (and clamp on size change).

**Implementation note:** `MemeCanvas` now tracks its rendered size via `onSizeChanged` and threads `parentSize: IntSize` to each `MemeTextOverlay`. The overlay tracks its own `positionInParent` via `onGloballyPositioned { coords -> positionInParent = coords.boundsInParent().topLeft }` (note: used `boundsInParent().topLeft` — the `positionInParent()` extension function wasn't resolving in this Compose BoM, likely a packaging / API change; `boundsInParent()` is stable and gives the same value). The drag handler derives the alignment-based anchor as `positionInParent - currentOffset`, clamps the desired absolute position to `[0, parentWidth - overlayWidth] × [0, parentHeight - overlayHeight]`, then re-derives the offset that would land there. Net effect: the overlay rect cannot leave the canvas during drag. Resize is also capped — the previous `coerceAtLeast(minPx)` is now `coerceIn(minPx, parentDimension)`, so the resize handle can't be used to grow the overlay past the canvas.

### C9. Captions can overlap [P2]
Top and bottom captions can be dragged onto each other with no detection. Minor; flag for UX.

### C10. Pre-Q `WRITE_EXTERNAL_STORAGE` flow runs on Android 10+ unintentionally? [verified clean]
**File:** `AndroidManifest.xml:6`, `ui/MemeScreen.kt:284-292`

Manifest correctly scopes the permission to `maxSdkVersion="28"`; runtime check correctly gates on `SDK_INT < Q`. OK. Informational only.

### C11. `lastGeneratedFile` lifetime is fragile [P2] ✅ **DONE (PR 9 side effect; pinned PR 13, 2026-06-04)**
**File:** `ui/viewmodel/MainViewModel.kt:40-65`

Deleted on `onCleared` and on each next success. If the activity is killed without `onCleared` (process death), the file orphans in cache. The `MainActivity.onCreate` cleanup catches it — but only on next cold start, and only files named `generated_meme_*`. The shared file from `shareBitmap` is `shared_meme_*` and not cleaned anywhere.

**Action:** Either include `shared_meme_*` in the startup cleanup, or use a `WorkManager`-backed periodic cache cleanup.

**Implementation note:** the simpler of the two actions was taken back in PR 9: `MainActivity.onCreate` cleanup was widened to include `gallery_meme_*` *and* `shared_meme_*` alongside the original `generated_meme_*`. No `WorkManager` — for a personal-use app the cold-start sweep is sufficient (process death is rare and a single accumulated cache file isn't a real footprint problem). Pinning this closed now since the PR 9 row only noted it as a "sub-piece" side effect.

### C12. Loading state has no progress or elapsed indication [P2 — UX] ✅ **DONE (PR 13, 2026-06-04)**
The shimmer animates but doesn't communicate progress. SDXL Lightning often returns in 3-8s; the user has no idea if 30s is "normal" or "stuck."

**Action:** Show elapsed seconds, or a "Usually takes 5-10s" hint.

**Implementation note:** went with the elapsed-seconds option, rendered in-theme as `"T+5S"` (Stardate vibe) using `MaterialTheme.typography.labelMedium` (monospace `MonoFontFamily`) in `TextLow`. Sits 6dp under the "MATERIALIZING" label inside `LoadingState`. A local `LaunchedEffect(Unit) { while (true) { delay(1000); elapsedSec++ } }` ticks the counter, and the conditional `if (elapsedSec > 0)` keeps the first second clean (no `T+0S` flash before any time has actually passed).

---

# Group D — UX & Visual Design Review *(UX Lead)*

The Stardate design language is genuinely strong — consistent palette, fonts (Inter / Space Grotesk / Anton is a smart triple), the TransporterPad illustration, the "Energize" CTA, and the themed error titles. Top marks for personality and visual coherence. Flagged items are interaction friction, not visual quality.

### D1. No "tap outside to dismiss keyboard" [P2] ✅ **DONE (OOB compact-layout rebuild, 2026-06-01)**
Compact layout has a scrollable column but no outer `clickable` to clear focus. Users tap empty canvas margins expecting dismissal.

**Implementation note:** the compact-layout rebuild added a `pointerInput` tap handler on the scrolling Column that calls `focusManager.clearFocus()` on empty-space taps. Taps on `BasicTextField`s are consumed by the field, so the outer handler only fires for non-text-field taps. Commit `ba753fe`.

### D2. Disabled Save/Share has no tooltip [P3]
When `generationState` is not Success, the buttons are visibly dim but the user has no inline explanation. The snackbar fallback ("Nothing to save yet.") only fires on click. Consider an inline helper text.

### D3. No undo when deleting a text overlay [P1] ✅ **DONE (PR 7, 2026-06-01)**
Tap the X on a caption → caption gone, no confirmation, no undo, typed text lost. For a meme app this is brutal.

**Action:** On delete, show a snackbar with "Undo" that restores the previous text + offset + size for ~5s.

**Implementation note:** the caption text actually lives in `MemeContent`'s `topText`/`bottomText` state — `onDelete` only flips visibility and zeroes the transform, so the text was never lost. What was lost was the position/size. The fix: each `onDelete` lambda in `MemeCanvas.kt` snapshots `(offset, size)` before clearing, then calls a new `onCaptionDeleted: (onUndo: () -> Unit) -> Unit` callback. `MemeContent` provides it and shows a `SnackbarDuration.Short` snackbar with an "Undo" action that fires the restore lambda. New strings: `caption_removed`, `undo`. Short duration (~4s) is close to the recommended "~5s"; switched off the explicit timeline so the standard M3 snackbar UX applies consistently with the rest of the app's snackbars.

### D4. Capture catches mid-animation chrome [P1] ✅ **DONE (PR 7, 2026-06-01)** — see C1

### D5. Empty-state suggestions are tap-to-fill, not tap-to-generate [P2]
Tapping a suggested prompt fills the input but the user still has to scroll/find Energize. Recommend tap-to-fill-AND-generate, or add a "Use & generate" affordance.

### D6. Prompt input has no character count, no clear button [P3]
Long prompts are common. Clear-on-tap-X is a 5-LOC win.

### D7. Energize button label can confuse [P3]
After first success the label is `Energize Again` forever, even if the user changes the prompt drastically. Consider showing `Energize Again` only when the **prompt is unchanged** since last generation; otherwise show `Energize`.

### D8. Model switch doesn't auto-regenerate or hint [P2]
Picking a new model from the sheet quietly updates state; user must remember to re-press Energize. Consider auto-regenerating on model switch when a prompt already exists, or a tooltip after switching.

### D9. Re-roll (HUD Casino icon) is invisible feedback [P2]
Re-roll changes seed and regenerates. There's no toast/feedback indicating "new seed" — feels identical to Energize. Consider a small "seed: 84291" pill that pulses when re-rolled.

### D10. TopAppBar has no leading icon / branding mark [P3]
The launcher icon is a strong cyan mark — echoing it in the top bar would tie identity end-to-end.

### D11. Background gradient is almost flat [P3] ✅ **DONE (OOB visual polish, 2026-06-03)**
`Space900 → 0xFF0D1128 → Space900` are visually identical at most viewport sizes. The "deep space" intent is invisible. Either widen the gradient or replace with subtle starfield (cheap with a low-density Canvas).

**Implementation note:** went with the widened-gradient option — 4-stop nebula gradient using deep indigo, warm magenta, and cold teal. New `Photon500` theme color introduced; also color-coded the suggested-prompt chips (`Plasma500` / `Photon500` / `Solar500`) for added vibrancy. Commit `fad071f`.

### D12. App title in TopAppBar can wrap [P3]
"MEME ME UP SCOTTY" with `letterSpacing = 3.sp` is long on a 360dp phone. Verify on small devices; consider a shorter brand mark.

### D13. Touch targets below 48dp [P2 — a11y]
- `HudIconButton` is 32dp.
- `ResizeHandle` is 36dp.
- `DeleteChip` is 32dp.

Material guidance is 48dp minimum. Increase invisible touch areas (`.minimumInteractiveComponentSize()` or a transparent padding).

### D14. Caption stroke can fail on light-background images [P2]
The stroke is `fontSize * 0.15f`, which gets very thin at small font sizes. On a near-white generated image, the white fill + thin stroke disappears.

**Action:** Either bump stroke to a fixed minimum px, or add a slight drop shadow.

### D15. Accessibility hooks are minimal [P2]
- `BasicTextField` with custom decoration bypasses Material 3's semantics.
- Selected model in the ModelCard has no `semantics { selected = true }`.
- Custom Surface buttons miss `Role.Button` (Compose usually infers it; verify).
- TalkBack reads decorative glyphs ("J", "S", "R" …) without context.

**Action:** Audit with TalkBack on. Add `Modifier.semantics { … }` to Custom selectables; add `contentDescription` to model glyphs ("Juggernaut model glyph").

### D16. Dynamic font scaling untested [P2]
`findBestFitFontSize` searches 14–40sp for canvas captions, but UI text uses fixed `sp` values that should scale with user font preferences. Test at 1.3× and 1.5× system font scale.

### D17. Aspect ratio is locked to 1:1 [P1 — see also E1] 🚫 **DEPRIORITIZED — user request (2026-06-03)**
`MemeCanvas` is `aspectRatio(1f)`. Many meme platforms (Instagram landscape, Reels/TikTok 9:16) need other ratios.

**Implementation note:** same as E3 — user opted out. 1:1 is fine for the personal-use scope.

### D18. Materialize / loading animation polish [P3] ✅ **DONE (PR 5, 2026-05-29)** *(added 2026-05-29 from user testing)*
**File:** `ui/MemeCanvas.kt::LoadingState`

Two specific drifts from the mockup observed while running the app:
1. The scan-line animation feels a touch slow at `tween(2200, easing = LinearEasing)` (line 314). Try ~1600–1800ms — keeps the calm "computing" rhythm but reads more responsive.
2. The lightning bolt currently pulses Plasma500 (cyan) only — `tint = Plasma500.copy(alpha = pulseAlpha)` at line 382. The mockup pulses **into yellow** (Solar500 from the design tokens) on the breathing cycle. Lerp `tint` between Plasma500 (low pulse) and Solar500 (high pulse), or run two animated values (alpha + color) so the bolt warms to gold at the peak.

### D19. Tight spacing between canvas and HUD strip [P3] ✅ **DONE (PR 5, 2026-05-29)** *(added 2026-05-29 from user testing)*
**File:** `ui/MemeLayouts.kt`

`CompactLayout` puts a `Spacer(modifier = Modifier.height(12.dp))` between the canvas and the HUD strip (line 68). `ExpandedLayout` does the same at line 139. 12dp reads as cramped against a large canvas tile. Try 20–24dp. Worth eyeballing both compact and expanded together so they stay visually consistent.

### D20. Prompt input should default to multi-line [P2] ✅ **DONE (PR 5, 2026-05-29)** *(added 2026-05-29 from user testing)*
**File:** `ui/MemeControls.kt::PromptInput`

The `BasicTextField` at line 239 accepts `singleLine` but has no `minLines`. Even though the Dock passes `singleLine = false`, the field renders at 1 line height until the user types enough to wrap, so a fresh empty prompt looks like a one-liner. Add `minLines = 3` (or expose it as a parameter and pass 3 from the Dock, 1 from any future single-line caller) so the field opens at ~3 lines and visually invites a longer prompt.

### D21. Overall spacing audit across fields, pickers, buttons [P3] ✅ **DONE (PR 5, 2026-05-29)** *(added 2026-05-29 from user testing)*
**Files:** `ui/MemeLayouts.kt`, `ui/MemeControls.kt`, `ui/ModelPicker.kt`

D19 is one instance; this is the broader pass. The dock currently uses `Arrangement.spacedBy(12.dp)` between its rows (PromptInput → Energize → Save/Share row), `Arrangement.spacedBy(18.dp)` in the expanded layout's right column, and 10dp between paired buttons. None of these are wrong individually but they accumulate inconsistently between compact and expanded. Action: take a deliberate pass with a single spacing scale (e.g. 8 / 16 / 24dp) and reapply across `Dock`, `ExpandedLayout` right column, `ModelPickerSheet`, and the inter-row gaps in `CompactLayout`. Easier to do as one PR than dribble.

---

# Group E — PM / Product Gaps *(PM Lead)*

The brief targets four core jobs: prompt → generate → caption → save/share. All four work. **However**, "meme creator" implies a wider surface area; users coming from Mematic, ImgFlip, or similar will hit walls fast.

### E1. No way to start from a user-provided image [P1 — table stakes]
Real memes are **mostly** template-based or photo-based. AI-generated source is novel but narrow. Without "Pick from gallery" or a built-in template library, this is a "Stardate AI image captioner," not a meme creator.

**Action:** Add an image source picker on the canvas empty state:
- Generate (current)
- Pick from gallery
- Camera (P2)
- Template library (P2 — a 10-template starter set goes a long way)

### E2. Only 2 text boxes [P1] 🚫 **DEPRIORITIZED — user request (2026-06-03)**
Top + bottom only. Multi-panel memes (Drake, Distracted Boyfriend) need 3+. The overlay code is already generalized — extending to N is mostly removing the top/bottom binary.

**Action:** Replace `topVisible/bottomVisible` with a `List<CaptionLayer>` in state.

**Implementation note:** user explicitly asked to skip this for PR 10 ("for PR 10, skip E2, but do E4"). Top + bottom is fine for the personal-use scope. Revisit only if multi-panel templates show up as a need.

### E3. Square canvas locks out social use cases [P1] 🚫 **DEPRIORITIZED — user request (2026-06-03)**
See D17. Add 1:1, 4:5 (Instagram portrait), 9:16 (Reels/Stories), 16:9 (Twitter).

**Implementation note:** user explicitly opted out when scoping PR 9 ("let's do E1. do not do E3"). 1:1 is fine for the personal-use scope. D17 is the same item from the UX side and should be treated as deprioritized alongside.

### E4. No text styling [P1] ✅ **DONE (PR 10, 2026-06-03)**
Color, font, alignment, stroke width, drop shadow, all hidden. Anton white+stroke is iconic but offering yellow Impact, no-stroke serif, and one or two alternate fonts is cheap and unlocks variety.

**Implementation note:** per-caption styling via a new `CaptionStyle` data class — 3 fonts (Anton/Impact, Inter Bold, system Serif), 4 fills (white/yellow/black/red), 3 alignments, outline on/off. Style chip on the caption chrome (`Alignment.TopStart`, `Icons.Outlined.FormatColorText`) opens a `ModalBottomSheet` modeled on `ModelPickerSheet`. The sheet updates the underlying caption live so the user sees their picks reflect before dismiss. Top and bottom captions style independently. Defaults (Impact/white/centered/outline-on) preserve the original look. Stroke-width / drop-shadow not implemented in this PR — outline on/off is the toggle. See PR 10 row in the Status Log.

### E5. No generation history [P2]
After Energize, the previous image is gone. Users frequently want "the second one was actually better."

**Action:** Keep last N (e.g., 5) generations in memory; show a thumb strip below the canvas. P2 = persist across launches.

### E6. No prompt history [P2]
Recent prompts dropdown would be a 50-LOC addition with high power-user value.

### E7. No save-as-draft / persistence [P2]
Kill the app → lose your in-progress meme. Auto-save prompt + captions in a `DataStore` on every change.

### E8. No batch generation [P2]
Replicate models support `num_outputs`. Show a 2×2 grid of options to pick from.

### E9. No upscaling / output resolution control [P3]
SDXL outputs are ~1024px. For sharing to print or HD displays, optional 2× upscale via a separate Replicate model would be a nice-to-have.

### E10. No reporting / safety affordance [P0 if shipping] 🚫 **NOT APPLICABLE — personal-build stance (PR 2, 2026-05-29)**
Tied to A2. Even with safety on, Play policy expects an in-app "report this" path.

**Implementation note (attempted + reverted PR 2, 2026-05-29):** initially added a `MoreVert` overflow on the `BridgeTopBar` with a "Report content" item that fired a `mailto:` intent. User pushed back: "no fucking reason to have a report content feature." For a single-user personal app this affordance is dead weight (email-yourself-about-your-own-memes). All E10 code reverted: overflow + DropdownMenu removed from `BridgeTopBar`, `onReport` lambda removed from `MemeScreen`, related imports (`Intent`, `Uri`, `ActivityNotFoundException`, `MoreVert`, `Flag`) removed, and the supporting strings (`more_actions`, `report_content`, `report_email`, `report_subject`, `report_body_hint`, `report_no_email_app`) dropped from `strings.xml`. **For this app, treat this item as Not Applicable.**

### E11. No about/credits/attribution screen [P2]
Several Replicate models have specific attribution or non-commercial clauses. Compliance + courtesy.

### E12. No analytics [P3] 🚫 **NOT APPLICABLE — personal-build stance (PR 13, 2026-06-04)**
No way to know which models/prompts users prefer in the wild. Not blocking; just flag.

**Implementation note:** single-user app, no fleet to learn from. Same family as A2/E10 — Play-distribution-grade hygiene that doesn't apply here. Marking N/A; reopen if the distribution stance ever changes.

### E13. App name + theme = trademark exposure [P0 — see A6]

### E14. No share/save format choice [P3]
Saves PNG only. JPG would be smaller. WebP smaller still. Power users would appreciate a setting.

---

# Group F — Testing (P2)

### F1. Repository is untested [P1] ✅ **DONE (PR 11, 2026-06-04)**
`ReplicateImageRepository` has all the polling, status mapping, and friendly-error logic — **zero tests**. This is the riskiest single class in the codebase.

**Action:** Inject a `FakeReplicateApi` and cover:
- happy path
- `succeeded` with empty output
- `failed` with error message
- `canceled`
- timeout
- 401/402/404/429/5xx responses → correct typed errors (once B2 lands)

**Implementation note:** new `ReplicateImageRepositoryTest` with a small `FakeReplicateApi` and Retrofit `Response.success` / `Response.error` stubs. 14 tests cover: invalid model id; 401/402/404/429-with-retry-after/429-without/503 → typed variants; model missing `latest_version`; `createPrediction` HTTP error mapping; prediction-failed-with-error-message; prediction-canceled; prediction-succeeded-with-empty-output; getPrediction HTTP failure mid-poll → Timeout (pinned to document current behavior); network exception → `Unexpected(message)`. Skipped: full happy-path download (would need MockWebServer for the URL fetch) and wall-clock-driven 120s timeout (depends on `System.currentTimeMillis`, not virtual time). Both gaps noted in comments where relevant.

### F2. ViewModel coverage is thin [P2] ⚠️ **DONE w/ partial scope (PR 11, 2026-06-04)**
`MainViewModelTest` covers `Idle → Loading → Success/Error`. Missing:
- `selectModel` updates flow
- rapid back-to-back `generateImage` calls (race / overlapping requests)
- cancellation
- successful generation cleans up the previous file

**Implementation note:** added four tests covering `selectModel`, `setLoadedImage` flips to Success, `setLoadedImage` deletes the previously tracked file, and successful generation deletes the previously tracked file. Skipped rapid-back-to-back races and cancellation — both depend on `viewModelScope` interaction quirks; the rapid case in particular is a latent bug (later `generateImage` call doesn't cancel the earlier one) that should be fixed before being pinned by tests, not the other way round. Flag as a follow-up if the race becomes user-visible.

### F3. No UI / Compose tests [P2] ⚠️ **DONE w/ minimal seed (PR 11, 2026-06-04)**
Compose UI test deps are already wired (`androidx.compose.ui.test.junit4`). Cover the critical paths:
- prompt entry + Energize disabled until non-blank
- error state shows themed title + Retry
- Save/Share disabled until Success
- model picker selects and dismisses

**Implementation note:** added one demonstration test, `EnergizeButtonTest`, covering the three state-dependent renders (Idle → "ENERGIZE" + enabled + clickable; Loading → "ENERGIZING…" + disabled; Success → "RE-ENERGIZE" + enabled). The remaining critical paths (Energize disable on blank prompt, error state title + Retry, Save/Share gating, model picker select+dismiss) need either lifting state out of `MemeContent` so it can be tested in isolation, or full-screen UI tests with a real or fake VM — both bigger lifts. Left as a follow-up; the seed proves the pattern and the deps work. Note also: the "Energize disabled when prompt blank" path in the review is currently *not* implemented as a disabled-button — `onEnergize` early-returns on blank, the button stays enabled. That's a UX-spec discrepancy to resolve before pinning a test on it.

### F4. Stub tests should go [P3] ✅ **DONE (PR 11, 2026-06-04)**
`ExampleUnitTest` and `ExampleInstrumentedTest` are skeletons from the template. Delete.

**Implementation note:** both files removed.

### F5. No `ImageUtils` tests [P2]
`saveBitmapToGallery` and `shareBitmap` could be covered with Robolectric or instrumented tests against a fake MediaStore/FileProvider. Lower priority but they have a fair amount of branching (pre-Q vs Q+).

---

# Group G — Build, Dependencies, Tooling (P2)

### G1. AGP and Kotlin are on alpha/preview [P2]
- `agp = "9.3.0-alpha06"` — alpha AGP on main is a stability risk.
- `kotlin = "2.3.21"` — confirm this is the intended Kotlin compiler version; it's well ahead of the latest stable.

**Action:** Pin to the latest **stable** AGP + Kotlin for `main`; keep alpha versions on a feature branch.

### G2. Java target is 11 [P3]
Compose tooling is comfortable on 17 and gains some performance. Bump if your environment supports it.

### G3. Splash icon assets are vector — good. Launcher background unreviewed.
Verified: `ic_launcher.xml` and `ic_launcher_round.xml` exist in `mipmap-anydpi`. Foreground vector + cyan stroke matches splash. Confirm the launcher background (`ic_launcher_background.xml`) tone matches the splash brand color.

### G4. `.agent/plan.md` at root is empty [P3]
The root `/.agent/plan.md` is empty; the real plan lives at `app/.agent/plan.md`. Either consolidate or delete the empty file to avoid confusion.

### G5. `mockups/redesign.html` is checked in [P3]
Source-of-truth for the redesign. Either keep with a README note about its role, or move out of the repo.

---

# Summary Table

| # | Group | Severity | Title |
|---|---|---|---|
| A1 | Security | **P0** | ⚠️ API token compiled into APK *(PR 2 interim: documented personal-build + `REPLICATE_BASE_URL` seam; proxy still to do)* |
| A2 | Policy | **P0** (distribution) | 🚫 Safety checker disabled — N/A for personal build *(PR 2 attempted + reverted)* |
| A3 | Security | **P0** | ✅ OkHttp body logging in release *(PR 1)* |
| A4 | Privacy | **P0** | ✅ Auto-backup with no exclusions *(PR 1)* |
| A5 | Release | **P0** | ⚠️ R8 / ProGuard disabled *(PR 1, +`Instantiatable` lint disable)* |
| A6 | Legal | **P0** (distribution) | Star Trek trademark exposure |
| A7 | Security | — | Token-on-disk audit (informational) |
| B1 | Architecture | P1 | ✅ 2,025-LOC MemeScreen.kt *(PR 4, split into 8 files; largest now 570 LOC)* |
| B2 | Architecture | P1 | ✅ Stringly-typed errors across layers *(PR 6)* |
| B3 | Architecture | P1 | 🚫 Brief vs. actual divergence — paper-only |
| B4 | Code style | P1 | ✅ snake_case Kotlin properties *(PR 13, camelCase + `@Json(name=…)`)* |
| B5 | Code style | P2 | ✅ Mixed JSON parsers *(PR 6)* |
| B6 | Code style | P2 | ✅ Hand-rolled ViewModelFactory *(PR 13, inline `viewModelFactory { initializer {…} }`)* |
| B7 | Correctness | P1 | ✅ Photorealistic suffix on stylized models *(PR 3, also expanded artifact negatives)* |
| B8 | Architecture | P2 | 🚫 NetworkModule has no swappable seam — current tests inject at the `ReplicateApi` seam |
| B9 | Cleanup | P3 | ✅ Dead `ImageModel.label` field *(PR 14, dropped — UI reads `displayNameRes` instead)* |
| C1 | Correctness | P1 | ✅ GraphicsLayer capture is racy *(PR 7, `delay(200)` covers fadeOut)* |
| C2 | Performance | P2 | ✅ Layer records every frame *(PR 7, gated on `capturing`)* |
| C3 | UX | P1 | ✅ No cancel during generation *(PR 14, Energize button swaps to CANCEL during Loading; VM cancels Job, flips to Idle)* |
| C4 | Logging | P3 | ✅ `printStackTrace` in code *(PR 1)* |
| C5 | UX | P1 | ✅ No nav-bar inset on Dock *(PR 8, `windowInsetsPadding(WindowInsets.navigationBars)`)* |
| C6 | UX | P2 | ✅ No IME action on inputs *(PR 8, `ImeAction.Go` → onEnergize)* |
| C7 | i18n | P2 | ✅ Hardcoded English in code *(PR 6)* |
| C8 | UX | P1 | ✅ No drag-bounds clamping *(PR 8, clamp overlay rect inside canvas + cap resize)* |
| C9 | UX | P2 | Caption collision unhandled |
| C10 | Permissions | — | Pre-Q permission path verified |
| C11 | Correctness | P2 | ✅ `shared_meme_*` files leak *(PR 9 side effect + PR 13 pin — cold-start sweep widened to all three prefixes)* |
| C12 | UX | P2 | ✅ No loading progress *(PR 13, `T+5S` elapsed counter under MATERIALIZING)* |
| D1 | UX | P2 | ✅ No tap-outside-to-dismiss *(OOB compact-layout rebuild, `pointerInput` + `clearFocus`)* |
| D2 | UX | P3 | Disabled buttons silent |
| D3 | UX | P1 | ✅ No undo for caption delete *(PR 7, "Undo" snackbar restores offset + size)* |
| D4 | UX | P1 | ✅ Capture catches animating chrome *(PR 7, see C1)* |
| D5 | UX | P2 | Suggestions fill but don't generate |
| D6 | UX | P3 | No clear button on prompt |
| D7 | UX | P3 | Energize label outdated |
| D8 | UX | P2 | Model switch has no signal |
| D9 | UX | P2 | Re-roll has no feedback |
| D10 | Visual | P3 | TopAppBar has no brand mark |
| D11 | Visual | P3 | ✅ Background gradient is flat *(OOB visual polish, 4-stop nebula gradient + Photon500)* |
| D12 | Visual | P3 | Title may wrap on small phones |
| D13 | A11y | P2 | Touch targets below 48dp |
| D14 | Visual | P2 | Caption stroke fails on light |
| D15 | A11y | P2 | Semantics gaps |
| D16 | A11y | P2 | Font scaling unverified |
| D17 | UX | P1 | 🚫 Aspect ratio locked to 1:1 — deprioritized (personal-use scope) |
| D18 | Visual | P3 | ✅ Materialize anim: faster scan lines + bolt pulses to yellow *(PR 5)* |
| D19 | Visual | P3 | ✅ Canvas → HUD spacing tightness *(PR 5: 12 → 20dp)* |
| D20 | UX | P2 | ✅ Prompt input `minLines = 3` *(PR 5)* |
| D21 | Visual | P3 | ✅ Spacing scale unification *(PR 5)* |
| E1 | Product | P1 | ✅ Image source picker *(PR 9, "Use a photo" empty-state pill + canvas swap chip via PickVisualMedia)* |
| E2 | Product | P1 | 🚫 Only 2 text boxes — deprioritized (personal-use scope) |
| E3 | Product | P1 | 🚫 Square-only canvas — deprioritized (personal-use scope) |
| E4 | Product | P1 | ✅ No text styling *(PR 10, per-caption font/color/alignment/outline via chrome chip → bottom sheet)* |
| E5 | Product | P2 | No generation history |
| E6 | Product | P2 | No prompt history |
| E7 | Product | P2 | No draft persistence |
| E8 | Product | P2 | No batch generation |
| E9 | Product | P3 | No upscaling |
| E10 | Policy | **P0** (distribution) | 🚫 No report-content path — N/A for personal build *(PR 2 attempted + reverted)* |
| E11 | Product | P2 | No attribution screen |
| E12 | Product | P3 | 🚫 No analytics — N/A for personal build |
| E13 | Legal | P0 | (dup A6) |
| E14 | Product | P3 | No format choice on save |
| F1 | Tests | P1 | ✅ Repository untested *(PR 11, 14 tests via FakeReplicateApi + stubbed Retrofit Responses)* |
| F2 | Tests | P2 | ⚠️ ViewModel coverage thin *(PR 11, partial — added selectModel, setLoadedImage, cleanup; rapid-race + cancel skipped pending VM fix)* |
| F3 | Tests | P2 | ⚠️ No UI tests *(PR 11, minimal seed — EnergizeButtonTest; remaining critical paths still uncovered)* |
| F4 | Tests | P3 | ✅ Stub tests should be deleted *(PR 11)* |
| F5 | Tests | P2 | ImageUtils untested |
| G1 | Build | P2 | AGP/Kotlin on alpha |
| G2 | Build | P3 | JDK 11 → 17 |
| G3 | Build | — | Launcher assets verified |
| G4 | Cleanup | P3 | Empty root `.agent/plan.md` |
| G5 | Cleanup | P3 | `mockups/redesign.html` |

---

## Proposed working groups

When you're ready to tackle, I'd suggest pairing items so each PR is a coherent slice rather than a grab-bag:

- **PR 1 — Release-block kit:** A3 (logging), A4 (backup), A5 (R8), C4 (printStackTrace). ✅ **landed 2026-05-29**
- **PR 2 — Token + safety strategy:** A1 *(interim)*. A2 and E10 marked Not Applicable for this personal-only build. ⚠️ **landed 2026-05-29 — A1 is interim only.** The proxy was not stood up (requires hosting/billing decision); a `REPLICATE_BASE_URL` seam was added so the proxy is a one-line swap when ready.
- **PR 3 — Prompt quality (out-of-band):** B7 + expanded artifact negatives (hand/finger/limb/duplication terms), per-model positive suffix + negative prompt, Flux gets no negative. ✅ **landed 2026-05-29.** Inserted ahead of the original roadmap on user request after seeing artifacts in generated images. Subsequent PRs renumbered (+1) below.
- **PR 4 — Split MemeScreen:** B1. ✅ **landed 2026-05-29.** 8 files in `ui/`, flat package, behavior preserved.
- **PR 5 — UX polish (out-of-band):** D18, D19, D20, D21. ✅ **landed 2026-05-29.** From running the app: materialize animation pacing + color, canvas/HUD spacing, multi-line prompt default, spacing scale unification. Inserted ahead of the original roadmap on user request.
- **PR 6 — Typed errors:** B2, B5, C7. ✅ **landed 2026-05-30.** Sealed `GenerationOutcome` + `GenerationError`, Moshi error-body DTO, hardcoded English moved to resources.
- **PR 7 — Capture correctness + UX polish:** C1, C2, D4, D3 (undo). ✅ **landed 2026-06-01.** `captureCleanBitmap()` now `delay(200)`s past the 160ms chrome fadeOut; `MemeCanvas`'s `drawWithContent` records only while `capturing`; caption delete shows an "Undo" snackbar that restores the pre-delete offset + size.
- **PR 8 — Inputs, drag bounds, insets:** C5, C6, C8. ✅ **landed 2026-06-01.** Dock now lifts above the 3-button nav bar via `windowInsetsPadding(WindowInsets.navigationBars)`; the prompt input shows a "Go" IME action that fires Energize; caption drag clamps the rendered rect inside the canvas, and resize caps at parent dimensions.
- **PR 9 — Product expansion vol. 1:** E1 (image source picker). ✅ **landed 2026-06-03.** E3 (aspect ratios) deprioritized at user request — gallery-picked images land in the existing `GenerationState.Success(File)` and the rest of the pipeline (capture / save / share / captions) just works.
- **OOB — Release signing:** signingConfig wired from `local.properties`. ✅ **landed 2026-06-01.** Commit `22cef38`.
- **OOB — Compact-layout rebuild + D1:** moved Prompt/HUD/Energize above the canvas to dodge the IME, added tap-outside-to-dismiss. ✅ **landed 2026-06-01.** Commit `ba753fe`. Closes D1.
- **OOB — Visual polish:** D11 nebula gradient + color-coded prompt chips + Photon500 theme color + copy edits ("Re-energize"). ✅ **landed 2026-06-03.** Commit `fad071f`. Closes D11.
- **PR 10 — Text styling:** E4. ✅ **landed 2026-06-03.** Per-caption font/color/alignment/outline via a `StyleChip` on the chrome → `CaptionStyleSheet` bottom sheet; live preview as the user picks. E2 (N text boxes) and E3 (aspect ratios) deprioritized at user request.
- **PR 11 — Repo + UI tests:** F1, F2 (partial), F3 (seed), F4. ✅ **landed 2026-06-04.** F1 fully covered via `FakeReplicateApi` + stubbed Retrofit responses (14 tests). F2 has the safe extensions; rapid-race and cancellation deferred behind a VM fix. F3 has one demonstration test; remaining critical paths are follow-ups. F4 deleted.
- **PR 12 — Brand + legal:** A6, E13, E11. *(Likely deprioritized for personal-only scope; revisit before any distribution.)*
- **PR 13 — Quick-batch cleanup:** B4 (snake_case DTOs → camelCase + `@Json`), B6 (modern `viewModelFactory { initializer {…} }`), C11 (pin closed — already done as a PR 9 side effect), C12 (`T+5S` elapsed counter in LoadingState). Deprioritized in this PR: B3 (paper-only), B8 (current tests inject at the right seam), E12 (N/A for personal). ✅ **landed 2026-06-04.**
- **PR 14 — Cancel during generation + dead-field cleanup:** C3 (in-flight `Job` + `cancelGeneration()` + button swaps to CANCEL during Loading), B9 (drop `ImageModel.label`). ✅ **landed 2026-06-04.**

Happy to drive any one of these. Tell me which group to start with.
