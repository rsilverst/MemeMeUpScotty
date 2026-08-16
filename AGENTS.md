# Meme Me Up Scotty — project instructions

**This file is the single source of truth for agent instructions in this repo.** It applies to
every AI coding tool (Claude Code, Codex CLI, Cursor, etc.). `CLAUDE.md` is only a stub that
imports this file — put new rules here, never there.

## What this is

A single-screen Android meme creator: type a prompt, an AI image generator (the Replicate API,
called directly from the device) materializes a picture, drop draggable top/bottom captions on
it, save or share. Star-Trek-themed ("Stardate") dark design language.

- **Package / application id**: `com.rsilverst.mememeupscotty`
- **Modules**: `:app` only — no backend, no server component, nothing to deploy
- **DI**: manual (wired in `MainActivity` + `NetworkModule`) — no Hilt/Dagger, deliberately
- **Personal-use only.** Bob has **no intention to publish this app.** The Replicate API token
  is compiled into the APK by design; never upload, share, or distribute a build, and never
  spend effort on distribution-readiness (proxy, Play policy, moderation) unless Bob asks.

## Required first steps for any agent

Before making non-trivial code, operational, or repository changes:

1. Read this file.
2. Read [`architecture.md`](architecture.md) **§1 before any agentic operation** — it is the
   authoritative source for the build/test runbooks, required `local.properties` keys, and the
   operational footguns. Read the relevant later section before touching that part of the
   system.
3. Before a change that could affect the persisted meme history (`filesDir/history` + the
   DataStore index), the R8 keep rules, or the release build, read the guardrails in §1.3.
4. There are no deploy targets in this repo — no backend, no hosting, no store listing.
   "Release" means a locally built, locally signed APK for Bob's own device, nothing more. If
   a request sounds like it involves publishing or distributing anything, stop and ask.

## Guidelines for agent and model behavior

- Always ask clarifying questions if there is ambiguity, before making non-trivial changes.
- Always give an overview of the actions you are about to take at the start of your response.
- Always provide a full summary of the actions you took at the end, as well as any things that I
  need to know, such as next steps, manual verification steps, gaps.
- If the changes you made or actions you performed would benefit from — or require — manual
  testing by the human user, include the set of steps the user should perform to validate what
  has been changed.
- Unless the user asks you to, do not waste tokens trying to "automatically" drive the app through
  user flows. That hides the behavior from the user and is typically done more efficiently by the
  human.
- Prefer the official CLIs (`gh`, `adb`, `./gradlew`) to verify real state rather than trusting
  what a doc claims. Docs in this repo can lag the code — e.g. the README cites review items
  from a `CODE_REVIEW.md` that no longer exists, and the Gradle token warning advertises a
  `REPLICATE_MODEL_ID` key that nothing reads. Verify before reporting something as live or
  tracked.

## Documentation rules

- `architecture.md` is the current-state architecture reference for this codebase. **Whenever you
  make a change that alters the architecture — new/removed modules, new persistence, networking,
  moved responsibilities, new screens, changed data flow — update `architecture.md` in the same
  change** so it stays accurate. Pure implementation changes that don't alter the documented
  structure don't require an update.
- `README.md` doubles as the setup guide and the statement of the **personal-use-only /
  do-not-distribute posture** — that section is load-bearing; keep it accurate.
- `CODE_REVIEW.md` and `UX_REVIEW.md` are **retired**: removed from git 2026-06-04 and now
  gitignored. The review-item IDs the README still cites (A1, A2, A5, A6, E10, E13…) are
  historical references to that removed review. Don't recreate the files; anything from them
  that is still live belongs in GitHub Issues.
- `mockups/` (`redesign.html`, `e5-history.html`) and `input_images/` are **design-reference
  artifacts** from past UI work — consult them for visual intent, never treat them as specs of
  current state.

## Tech debt & known bugs — GitHub Issues

**Tech debt & known bugs live in GitHub Issues** (`rsilverst/MemeMeUpScotty`), managed via the
`gh` CLI — NOT a markdown file. When you find a real problem that's out of scope for the
current task, open an issue with `gh issue create` instead of fixing it inline. Conventions:

- **Closing is on you — there is no automation.** No GitHub Action, no hook: the only things
  that ever close an issue are a **closing keyword** in a commit message reaching `main`
  (`Fixes #12` / `Closes #12` / `Resolves #12`) or an explicit `gh issue close`. A bare mention
  like `(#12 Part 1)` links the commit and closes **nothing**. If a commit finishes an issue,
  use the keyword; if the work lands across commits or only partly, close it by hand when the
  last piece ships.
- **Lead every issue body with a plain-language `TL;DR (for Bob):`** — he's the non-technical
  orchestrator; translate the technical item into what it means, why it matters, and when to
  care. Technical detail goes below the TL;DR.
- **Labels:** the repo currently carries only GitHub's default label set (verified 2026-08-15;
  the tracker is empty). Use `bug` / `enhancement` where they fit; create additional labels
  (e.g. `priority: high|medium|low`, `tech-debt`) only when a real grouping need appears, and
  record the scheme here when you do.
- A **declined** decision is recorded as a closed issue (`gh issue close --reason "not planned"`,
  `wontfix` label) so it isn't silently re-litigated.

## Build & test

```bash
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:installDebug           # build + install on a connected device/emulator
./gradlew testDebugUnitTest           # unit tests (34 tests — JUnit4 + hand-rolled fakes, no MockK)
./gradlew connectedDebugAndroidTest   # Compose UI tests (needs a device; 3 tests)
./gradlew lint                        # Android lint
./gradlew :app:assembleRelease        # R8-minified release (signed only if keystore keys present)
```

**`local.properties` is required and gitignored.** A fresh checkout needs `REPLICATE_API_TOKEN`
to generate images (builds fine without it, fails at runtime with `AuthRejected`). Optional:
`REPLICATE_BASE_URL` (proxy override) and the four `RELEASE_KEYSTORE_*` / `RELEASE_KEY_*`
signing properties — all four or release builds are unsigned. Full table: `architecture.md` §1.2.

## Build toolchain / JDK

- Modules compile to **Java 11** (`compileOptions` in `app/build.gradle.kts`). The Gradle
  daemon runs on whatever JDK launches Gradle — Gradle 9.7.0 / AGP 9.3.1 need **17+** (verified
  working on JDK 25); the foojay resolver convention fetches compile toolchains. There is no
  daemon JVM pin; don't add a vendor-specific one.
- **AGP 9.3.1 is an alpha-channel AGP with built-in Kotlin** — there is intentionally NO
  `org.jetbrains.kotlin.android` plugin (only the Compose compiler plugin and KSP). Do not add
  it back. Android Studio must be recent enough for AGP 9.3.x.
- `lint { disable += "Instantiatable" }` is a known AGP-9.3-alpha false positive
  (see the comment in `app/build.gradle.kts`) — remove it when an AGP upgrade fixes it, and
  don't copy it anywhere else.

## Architecture conventions

- **Layers**: `ui/` (Compose, single screen + ViewModel) → `data/repository/` →
  `data/network/` (Retrofit + Replicate). State flows up via `StateFlow`; generation state is
  a sealed class (`Idle` / `Loading` / `Success` / `Error`).
- The repository **never throws** — it returns `GenerationOutcome`, rethrowing only
  `CancellationException`. Failures are typed `GenerationError` variants; add a variant rather
  than overloading `Unexpected`.
- **String copy lives in `strings.xml`** — the UI maps error variants to resources; the data
  layer never produces user-facing text (raw `Unexpected.detail` is the one exception).
- **Captions are data, not pixels** — they stay editable and only get flattened at Save/Share.
  Anything drawn over the canvas that is chrome, not meme, must be gated on `!capturing`.
- Moshi `@JsonClass` DTO field names ARE the wire/persisted format, protected by scoped R8
  keep rules (`app/proguard-rules.pro`) — new Moshi DTOs outside `data/network/**` and
  `ui/viewmodel/**` need a matching rule.
- Persisted-history changes must stay backwards-compatible (legacy key, Moshi defaults,
  enum-name fallbacks) — guardrails in `architecture.md` §1.3.
- Tests use **hand-rolled fakes, no mocking library** — keep new tests in that style.
- Previews live beside their components using the shared `PreviewShell` / `PREVIEW_BG`.

Full detail, including rationale, is in `architecture.md`.

## Git workflow

- Stay on `main` — do not create or switch branches unless explicitly asked to.
- **Do not auto-commit.** Summarize the diff and wait for an explicit instruction to commit. When
  the user says "commit", take it at face value and run it; if the staging state is ambiguous, ask
  which changes to include rather than declining.

## Agent provenance (required)

Every AI agent that touches this repo **must identify which tool and which model it is**, so
changes are traceable. Fill in your own real tool name and model — never leave it generic.

- **Commits:** end the message with a trailer line — `Assisted-by: <tool>, <model>`
  (e.g. `Assisted-by: Codex CLI, GPT-5-Codex` or `Assisted-by: Claude Code, Fable 5`). Keep any
  tool-native `Co-Authored-By:` line as well.
- **GitHub issues / comments** you create: end with a provenance line — `— <tool>, <model>`
  (e.g. `— Claude Code, Fable 5` or `— Codex CLI, GPT-5-Codex`).
