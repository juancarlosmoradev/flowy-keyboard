# Android TV Validation

## Purpose

Record manual acceptance checks on a physical Android TV device. A successful CI build confirms APK assembly only; it does not confirm installation, remote focus, text editing, microphone access, or speech recognition on a TV.

## Build under evaluation

Download the `flowy-keyboard-debug` artifact from the latest successful **Android Build** run on `main`. Keep the run URL, commit SHA, and artifact together in the validation record.

## Setup

1. Install the APK on the target Android TV.
2. Open Flowy Keyboard and enable it in the system keyboard/input method settings.
3. Select Flowy Keyboard as the active input method.
4. Open the app's **Flowy Keyboard test** field.
5. Record the TV model, Android version, build commit, and date below.

## Acceptance checks

Mark each result as **Pass**, **Fail**, or **Blocked**. Include a short note for failures or blocked checks.

| Area | Check | Result | Notes |
| --- | --- | --- | --- |
| Installation | APK installs and Flowy can be enabled and selected as the active input method. |  |  |
| Remote focus | D-pad navigation reaches every key; the focused key is clearly visible at TV viewing distance. |  |  |
| Layout | Spanish (Chile) layout shows `ñ`, comma, and period in usable positions. |  |  |
| Text entry | Letters and punctuation appear in the test field. |  |  |
| Deletion | Backspace deletes one character at the current cursor. |  |  |
| Space | Space inserts a single space. |  |  |
| Editor action | The return/search key triggers the field's Search editor action. |  |  |
| Clear all | With text present, **Clear all** empties the field, moves the cursor to the start, and leaves Flowy ready for input. |  |  |
| Cursor | Place the cursor inside existing text, reopen Flowy, and confirm the selection remains at that position. Repeat in each target app. |  |  |
| Mic permission | First dictation requests microphone permission; granting it opens the listening state. |  |  |
| Dictation cancel | Cancel listening before speech is committed; confirm no transcript is inserted. |  |  |
| Local Spanish model | With a local Spanish model installed, dictation starts; without one, Flowy reports that local dictation is unavailable. |  |  |
| Offline dictation | After installing the device's local Spanish model, disable Wi-Fi/network and dictate a short Chilean Spanish phrase. Confirm transcription still works. |  |  |
| Insertion point | Dictate while the cursor is inside existing text; confirm the transcript is inserted at the active cursor and the search is not submitted. |  |  |

For speech checks, use non-sensitive sample phrases, for example: “Busca películas chilenas para ver hoy” and “¿Dónde encuentro configuración de teclado?”. Record the expected phrase and actual transcription. Do not record or upload audio.

## Validation record

- Date:
- Tester:
- TV model:
- Android version:
- Installed input method / speech service:
- Main commit SHA:
- GitHub Actions run:
- Overall result:
- Follow-up issues:

## Result boundaries

- **Build:** GitHub Actions reports whether the debug APK assembled and was uploaded.
- **Automated tests:** This workflow currently assembles the APK; it does not run UI or instrumented tests.
- **Device validation:** Only completed checks recorded above count as tested on Android TV. Do not infer TV compatibility from a successful build.
