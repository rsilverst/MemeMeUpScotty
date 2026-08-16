# Meme Me Up Scotty

A single-screen Android meme creator. Type a prompt, an AI image generator (Replicate)
materializes a picture, drop top/bottom captions on it, save or share. Star-Trek-themed
("Stardate") dark design language.

**Stack:** Kotlin, Jetpack Compose, Material 3, Retrofit + OkHttp + Moshi, Coil,
Kotlin Coroutines.

---

## Building

1. Install Android Studio (any version that supports AGP `9.3.x` — currently alpha) and
   the Android SDK with platform 37.
2. Create a Replicate API token at <https://replicate.com/account/api-tokens>.
3. Drop the token into `local.properties` at the repo root:

   ```properties
   REPLICATE_API_TOKEN=r8_your_token_here
   ```

4. `./gradlew :app:installDebug` (or run from Android Studio).

### Optional configuration

`local.properties` also accepts:

| Key | Default | Purpose |
|---|---|---|
| `REPLICATE_API_TOKEN` | — | **Required.** Replicate API token. |
| `REPLICATE_BASE_URL` | `https://api.replicate.com/` | Override the API host. Set this to a proxy URL once one exists (see "Distribution"). |

---

## Distribution status — read before sharing

**This build is personal-use only. Do not distribute the APK.** There is no intention to
publish this app — distribution is a non-goal, recorded as a declined decision in
[issue #1](https://github.com/rsilverst/MemeMeUpScotty/issues/1).

Today the Replicate API token is compiled into `BuildConfig` and ships inside the APK.
Anyone who installs the APK could decompile it and use the token, billing the owner.
This is intentional for a single-user build.

The original project brief specifies no content moderation, so model-level safety
filters are also disabled (`disable_safety_checker = true`). This is likewise a
personal-build decision.

If distribution were ever reconsidered, the following would need to land first:

- **Token proxy.** Stand up a thin proxy (Cloudflare Worker, Vercel function, Firebase
  function) that holds the token server-side. Point the app at it via `REPLICATE_BASE_URL`.
- **Release signing.** R8/minify and a signing config are already wired; a real keystore
  (the `RELEASE_KEYSTORE_*` / `RELEASE_KEY_*` keys in `local.properties`) and the Play
  requirements are not.
- **Star Trek IP.** App name and "Stardate" theme lean on Star Trek IP. Decide on rename,
  parody/fair-use posture, or licensing before any public release.
- **Content moderation.** Reconsider the no-safety-filter stance. Play Store's
  generative-AI policy expects safety filters on and an in-app reporting path.

---

## Documentation

- [`architecture.md`](architecture.md) — current-state architecture reference; operational
  runbooks and guardrails in §1.
- [`AGENTS.md`](AGENTS.md) — instructions for AI coding agents working in this repo.
- Tech debt & known bugs live in [GitHub Issues](https://github.com/rsilverst/MemeMeUpScotty/issues).
