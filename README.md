# SAMAR — Complete Android Project + Local AI

This is the **single unified project package**. The complete SAMAR Android app and its Local AI integration are in the same active source tree: `SAMAR2-main/`.

## What's included
- Complete SAMAR Android project (HUD, chat UI, voice input/TTS, accessibility service, offline chat engine, and existing app resources).
- Google LiteRT-LM Android runtime integration for an on-device generative model.
- One chat routing flow: configured secure cloud backend -> local generative model -> existing rule-based offline fallback.
- `LOCAL AI` button to select a compatible `.litertlm` model file and copy it into SAMAR's private app storage.
- Original Gemini source and previous milestone archives preserved in `project-history/`.

## Important status
- **KAVACH is intentionally postponed** and is not part of this milestone.
- Cloud endpoint is still blank, so cloud AI is not connected until a secure HTTPS backend is configured.
- LiteRT-LM runtime integration is present, but model weights are not bundled. A compatible `.litertlm` model must be imported by the user.
- No APK build or on-device inference is claimed as verified in this package.
- The local model has to be tested on the actual phone for RAM use, storage, speed, heat, and stability. Do not assume a particular model size will run well on every Poco M2 variant.
- No provider API keys should be embedded in the APK or committed to GitHub.

## Local AI setup
1. Build and install SAMAR after a successful Android build.
2. Obtain a `.litertlm` model compatible with the LiteRT-LM Android runtime and the phone's available resources.
3. Open SAMAR and tap **LOCAL AI**.
4. Select the model file. SAMAR copies it to private app storage; the original file is not modified.
5. The first model load may take several seconds. Inference is routed off the UI thread.

The model weights are intentionally not bundled in the APK or this source ZIP because they can be hundreds of MB or several GB.

## Runtime notes
- Runtime dependency: `com.google.ai.edge.litertlm:litertlm-android:0.18.0`.
- Default inference backend: CPU for broad compatibility; GPU/NPU acceleration is not assumed.
- LiteRT-LM initialization can take time and must remain off the UI thread.
- Text-to-Speech depends on the Android TTS engine and installed languages.
- Microphone permission is requested when voice input is used.
- SMS opens a prefilled draft; the user reviews and sends it.
- Accessibility requires manual enablement in Android settings.
- Stark-tech styling remains inside SAMAR's own UI only; it must not change system-wide display settings.

## Build status
The project includes a GitHub Actions workflow under `.github/workflows/`. Run the workflow and confirm a successful build before installing an APK. A ZIP integrity check is not an Android compilation test.
