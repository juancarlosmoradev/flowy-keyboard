# Flowy Keyboard

A remote-first, privacy-conscious keyboard experiment for Android TV.

Flowy Keyboard is a system input method (IME), navigable with a TV remote. It includes a Spanish (Chile) layout, long-press number and symbol alternates, cursor restoration, one-step clear-all, and optional offline speech dictation with a local Whisper model. Dictation audio stays on the device. Physical Android TV validation is still pending.

## Current status

- [Product brief](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Local voice dictation](docs/LOCAL_VOICE_DICTATION.md)
- [Persistent APK signing setup](docs/PERSISTENT_SIGNING.md)
- [Roadmap](docs/ROADMAP.md)
- [Android TV validation checklist](docs/TV_VALIDATION.md)
- [Architecture decision records](docs/decisions/)

## Technical direction

- Kotlin and Android's InputMethodService
- Native Android Views for the first keyboard surface
- Android Gradle Plugin with Kotlin DSL
- D-pad-first navigation and visible focus
- Local text handling and on-device speech recognition; no cloud transcription fallback

Exact Android TV device and OS support remains an open product decision. Build-tool versions are pinned for reproducibility.

## Repository guide

See [CONTRIBUTING.md](CONTRIBUTING.md) for the feature workflow and documentation expectations.
