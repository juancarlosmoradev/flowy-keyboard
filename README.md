# Flowy Keyboard

A remote-first, privacy-conscious keyboard experiment for Android TV.

Flowy Keyboard is a system input method (IME), navigable with a TV remote. The project improves typing one measurable feature at a time. AI-assisted voice input is a product requirement; the provider and audio privacy model are being designed before remote transcription is added.

## Current status

- [Product brief](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Voice input architecture](docs/VOICE_ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture decision records](docs/decisions/)

## Initial technical direction

- Kotlin and Android's InputMethodService
- Native Android Views for the first keyboard surface
- Android Gradle Plugin with Kotlin DSL
- D-pad-first navigation and visible focus
- Local text handling by default; no network requests in the current prototype

Exact Android device and OS support remains an open product decision. Build-tool versions are pinned for reproducibility.

## Repository guide

See [CONTRIBUTING.md](CONTRIBUTING.md) for the feature workflow and documentation expectations.
