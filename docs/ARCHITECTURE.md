# Architecture

## Initial structure

- `app`: Android application and input method service.
- `FlowyInputMethodService`: creates the keyboard view and sends edits through the active `InputConnection`.
- `VoiceCaptureActivity`: captures user-requested audio, manages the local model download, and returns a transcript.
- `WhisperNative` and `app/src/main/cpp`: JNI bridge and native Whisper.cpp inference.
- `MainActivity`: local test screen with a text field.

## Text flow

A remote selects a focused key. The key action calls `InputConnection` to commit text, delete text, or invoke the editor action. The operating system routes the change to the focused app. Voice dictation follows the same path and inserts its transcript at the active selection.

## Cursor behavior

Flowy records the latest valid selection reported by the editor through `onUpdateSelection`. When its input view starts, it restores that selection through `InputConnection.setSelection`. This relies on host apps reporting selection updates correctly; behavior must be validated across target TV devices and apps.

## Local speech flow

The user starts a visible microphone session and grants microphone permission. Flowy downloads a pinned, quantized Whisper model once, verifies its checksum, then records and transcribes audio locally through JNI. Audio does not leave the device. See [Local Voice Dictation](LOCAL_VOICE_DICTATION.md).

## Constraints

The input method does not own the host app's text field or cursor rendering. Some apps may not report selection updates consistently or may reject selection changes. Dictation is explicitly started by the user; background microphone listening is outside the current scope and requires separate OS and device research.

## Initial stack

Kotlin, Android `InputMethodService`, native Views, Gradle Kotlin DSL, CMake, and Whisper.cpp. Build versions and native dependencies are pinned for reproducibility and must be reviewed when the target device matrix is chosen.
