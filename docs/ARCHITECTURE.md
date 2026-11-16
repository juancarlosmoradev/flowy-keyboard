# Architecture

## Initial structure

- `app`: Android application and input method service.
- `FlowyInputMethodService`: creates the keyboard view and sends edits through the active `InputConnection`.
- `MainActivity`: local test screen with a text field.

## Text flow

A remote selects a focused key. The key action calls `InputConnection` to commit text, delete text, or invoke the editor action. The operating system routes the change to the focused app.

## Cursor behavior

Flowy records the latest valid selection reported by the editor through `onUpdateSelection`. When its input view starts, it restores that selection through `InputConnection.setSelection`. This relies on host apps reporting selection updates correctly; behavior must be validated across target TV devices and apps.

## Constraints

The input method does not own the host app's text field or cursor rendering. Some apps may not report selection updates consistently or may reject selection changes. Voice and AI features are excluded until their permission, privacy, and platform constraints are documented.

## Initial stack

Kotlin, Android `InputMethodService`, native Views, Gradle Kotlin DSL. Build versions are pinned for reproducibility and must be reviewed when the target device matrix is chosen.
