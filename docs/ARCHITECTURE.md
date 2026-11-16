# Architecture

## Initial structure

- `app`: Android application and input method service.
- `FlowyInputMethodService`: creates the keyboard view and sends edits through the active `InputConnection`.
- `MainActivity`: local test screen with a text field.

## Text flow

A remote selects a focused key. The key action calls `InputConnection` to commit text, delete text, or invoke the editor action. The operating system routes the change to the focused app.

## Constraints

The input method does not own the host app's text field or cursor rendering. Cursor preservation must be validated against real editor behavior and tested across target TV devices. Voice and AI features are excluded until their permission, privacy, and platform constraints are documented.

## Initial stack

Kotlin, Android `InputMethodService`, native Views, Gradle Kotlin DSL. Build versions are pinned for reproducibility and must be reviewed when the target device matrix is chosen.
