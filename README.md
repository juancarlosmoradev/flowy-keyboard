# Flowy Keyboard

A remote-first, privacy-conscious keyboard experiment for Android TV.

Flowy Keyboard is intended to be a system input method (IME), navigable with a TV remote. The project will improve the typing experience one measurable feature at a time. AI-assisted features are later-stage work and will require a separate privacy and architecture decision.

## Current status

The repository is being initialized. See:

- [Product brief](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture decision records](docs/decisions/)

## Initial technical direction

- Kotlin and Android's `InputMethodService`
- Native Android Views for the first keyboard surface
- Android Gradle Plugin with Kotlin DSL
- D-pad-first navigation and visible focus
- Local text handling by default; no network requests or AI features in the initial prototype

Exact Android device and OS support remains an open product decision. Build-tool versions are pinned in the project and can be updated together after verifying the chosen development environment.

## Repository guide

See [CONTRIBUTING.md](CONTRIBUTING.md) for the feature workflow and documentation expectations.