# Flowy Keyboard

A remote-first, privacy-conscious keyboard experiment for Android TV.

Flowy Keyboard is a system input method (IME), navigable with a TV remote. It includes a Spanish (Chile) layout, cursor restoration, one-step clear-all, and opt-in speech dictation through Android's on-device recognizer. Dictation has no cloud fallback. Physical Android TV validation is still pending.

## Current status

- [Product brief](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Voice input architecture](docs/VOICE_ARCHITECTURE.md)
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
