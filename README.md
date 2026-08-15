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

**This build is personal-use only. Do not distribute the APK.**

Today the Replicate API token is compiled into `BuildConfig` and ships inside the APK.
Anyone who installs the APK could decompile it and use the token, billing the owner.
This is intentional for a single-user build and is tracked as item **A1** in
[CODE_REVIEW.md](./CODE_REVIEW.md).

The original project brief specifies no content moderation, so model-level safety
filters are also disabled (`disable_safety_checker = true`). This is a personal-build
decision; if distribution ever becomes a goal, A2/E10 in the review would need to be
revisited along with the Play Store generative-AI policy requirements.

If you ever decide to distribute, the following would need to land first:

- **A1.** Stand up a thin proxy (Cloudflare Worker, Vercel function, Firebase function)
  that holds the token server-side. Point the app at it via `REPLICATE_BASE_URL`.
- **A5.** R8/minify is on for release (PR 1). Add a release signing config before upload.
- **A6 / E13.** App name and "Stardate" theme lean on Star Trek IP. Decide on rename,
  parody/fair-use posture, or licensing before public release.
- **A2 / E10.** Reconsider the no-content-moderation stance. Play Store's generative-AI
  policy expects safety filters on and an in-app reporting path.
